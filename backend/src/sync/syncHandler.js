/**
 * syncHandler.js — Strict WebSocket message dispatcher for ResonixSync.
 */

const roomManager = require('./roomManager');
const {
    getRoom, addClient, setHost, isHost,
    removeClient, getRoomOfClient,
    broadcast, destroyRoomIfEmpty,
    updateClientRtt, getLastKnownRtt, clearClientRtt,
    updateClientCompensation, getMaxRoomCompensation, clearClientCompensation,
    getMaxRoomRtt,
    markPeerReady, areAllPeersReady, clearReadyState,
    initAudioLoad, markAudioLoaded, areAllPeersAudioLoaded, clearAudioLoad,
} = roomManager;

/**
 * Compute dynamic execution lead time for the entire room.
 *
 * Uses the WORST (maximum) RTT and hardware compensation across
 * all connected clients — not just the sender's — so the slowest
 * peer always has enough runway to receive, compensate, and execute.
 *
 * Formula mirrors BeatSync's RoomManager.ts approach:
 *   rttDelay          = max(400, maxRoomRtt × 1.5 + 200)
 *   compensationDelay = maxRoomCompensation + 200
 *   leadTime          = min(1000, max(rttDelay, compensationDelay))
 *
 * Example — Host RTT=40ms, Guest RTT=300ms, Guest BT=250ms:
 *   rttDelay=650ms  compensationDelay=450ms  leadTime=650ms
 *
 * Hard cap at 1000ms — prevents one pathologically bad connection
 * from making the whole room wait more than 1 second.
 *
 * @param {string} roomCode
 * @returns {number} leadTimeMs
 */
function computeRoomLeadTime(roomCode) {
    const maxRoomRtt = getMaxRoomRtt(roomCode);
    const maxCompensation = getMaxRoomCompensation(roomCode);

    const rttDelay = Math.max(400, (maxRoomRtt * 1.5) + 200);
    const compensationDelay = maxCompensation + 200;
    const leadTime = Math.min(1000, Math.max(rttDelay, compensationDelay));

    console.log(
        `[sync] leadTime=${leadTime}ms ` +
        `(maxRoomRtt=${maxRoomRtt}ms ` +
        `maxComp=${maxCompensation}ms ` +
        `rttDelay=${rttDelay}ms ` +
        `compDelay=${compensationDelay}ms)`
    );

    return leadTime;
}

/**
 * Build the authoritative full room snapshot sent to late-joiners and reconnecting clients.
 *
 * Covers every field the Android client needs to hydrate state in a single message:
 * - queue, current track, live position, play state
 * - room users + host info
 * - loop/shuffle mode
 *
 * @param {object} room  Room object from roomManager.
 * @param {boolean} projectPosition  If true, project positionMs forward from lastPlayTimestamp.
 * @returns {object}  Plain object ready for JSON.stringify.
 */
function buildRoomSnapshot(room, projectPosition = false) {
    const now = Date.now();

    let livePositionMs = room.currentPositionMs || room.positionMs || 0;
    if (projectPosition && room.isPlaying) {
        const elapsed = room.lastPositionUpdate
            ? now - room.lastPositionUpdate
            : room.lastPlayTimestamp
                ? now - room.lastPlayTimestamp
                : 0;
        livePositionMs = Math.max(0, livePositionMs + elapsed);
    }

    return {
        type: 'room_state_snapshot',
        roomCode: room.code,
        hostClientId: room.hostClientId || null,
        users: room.users || [],
        // Playback
        isPlaying: room.isPlaying || false,
        positionMs: Math.max(0, livePositionMs),
        serverTime: now,
        // Current track
        currentTrackId: room.currentTrackId || null,
        currentTrackUrl: room.currentTrackUrl || room.currentAudioSource || null,
        currentTrackTitle: room.currentTrackTitle || '',
        currentTrackArtist: room.currentTrackArtist || '',
        currentTrackThumbnail: room.currentTrackThumbnail || '',
        // Queue — authoritative, full list
        queue: room.queue || [],
        // Playback settings
        loopMode: room.loopMode || 'none',
        shuffleEnabled: room.shuffleEnabled || false,
        globalVolume: room.globalVolume || 1.0,
        playbackPermission: room.playbackPermission || 'host_only',
    };
}

/**
 * Dispatch an inbound WebSocket message.
 *
 * @param {string} clientId Unique identifier for the client connection.
 * @param {string} raw      Raw JSON string.
 * @param {import('ws')} ws The WebSocket instance.
 */
function handleMessage(clientId, raw, ws) {
    let msg;
    try {
        msg = JSON.parse(raw);
    } catch {
        return;
    }

    // Attach clientId to ws for easier identification in roomManager
    ws.id = clientId;

    const ROOM_CODE_REGEX = /^HA[A-Z0-9]{5}$/;

    switch (msg.type) {
        case 'READY': {
            const { roomCode, clientId: readyClientId } = msg;
            roomManager.markPeerReady(roomCode, readyClientId);
            console.log(`[sync] READY from ${readyClientId} in room ${roomCode}`);

            if (roomManager.areAllPeersReady(roomCode)) {
                console.log(`[sync] All peers ready in room ${roomCode} — scheduling playback`);
                roomManager.clearReadyState(roomCode);

                const leadTimeMs = computeRoomLeadTime(roomCode);
                const serverTimeToExecute = Date.now() + leadTimeMs;

                roomManager.broadcast(roomCode, {
                    type: 'SCHEDULED_ACTION',
                    serverTimeToExecute,
                    scheduledAction: {
                        type: 'PLAY',
                        trackTimeSeconds: msg.trackTimeSeconds || 0,
                        audioSource: msg.audioSource || '',
                    },
                });
            }
            break;
        }

        case 'JOIN': {
            const { roomCode } = msg;
            roomManager.addClient(roomCode, clientId, ws);
            const room = roomManager.getRoom(roomCode);
            if (room.users.length === 1) {
                roomManager.setHost(roomCode, clientId);
            }

            // Full authoritative snapshot — replaces the thin ROOM_STATE emit
            const snapshot = buildRoomSnapshot(room, /* projectPosition= */ true);
            if (ws.readyState === 1) {
                ws.send(JSON.stringify(snapshot));
                console.log(`[sync:join] ${clientId} joined room ${roomCode} | hydrated: queue=${snapshot.queue.length} tracks isPlaying=${snapshot.isPlaying} pos=${snapshot.positionMs}ms`);
            }

            // If room is actively playing, also send PREPARE so client buffers before receiving SCHEDULED_ACTION
            if (room.isPlaying && clientId !== room.hostClientId) {
                ws.send(JSON.stringify({
                    type: 'PREPARE',
                    trackTimeSeconds: snapshot.positionMs / 1000,
                    audioSource: room.currentAudioSource || room.currentTrackUrl || '',
                }));
                console.log(`[sync:join] PREPARE sent to late-joiner ${clientId} in room ${roomCode}`);
            }
            break;
        }

        case 'SYNC': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode) {
                if (ws.readyState === 1) {
                    ws.send(JSON.stringify({
                        type: 'room_state_snapshot',
                        roomCode: null,
                        hostClientId: null,
                        isPlaying: false,
                        positionMs: 0,
                        queue: [],
                        users: [],
                    }));
                }
                console.log(`[sync:reconnect] SYNC from ${clientId} — not in any room`);
                return;
            }

            const room = roomManager.getRoom(roomCode);
            if (!room) return;

            const snapshot = buildRoomSnapshot(room, /* projectPosition= */ true);
            if (ws.readyState === 1) {
                ws.send(JSON.stringify(snapshot));
                console.log(`[sync:reconnect] ${clientId} rehydrated | room=${roomCode} queue=${snapshot.queue.length} isPlaying=${snapshot.isPlaying} pos=${snapshot.positionMs}ms`);
            }

            if (room.isPlaying) {
                ws.send(JSON.stringify({
                    type: 'PREPARE',
                    trackTimeSeconds: snapshot.positionMs / 1000,
                    audioSource: room.currentAudioSource || room.currentTrackUrl || '',
                }));
                console.log(`[sync:reconnect] PREPARE sent to reconnecting client ${clientId}`);
            }
            break;
        }

        case 'PLAY':
        case 'SEEK': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;

            const room = roomManager.getRoom(roomCode);
            if (room) {
                room.isPlaying = true;
                room.positionMs = (msg.trackTimeSeconds || 0) * 1000;
                room.currentAudioSource = msg.audioSource || '';
                room.lastPlayTimestamp = Date.now();
            }

            // Count non-host peers
            const peers = room ? room.users.filter(u => u.id !== clientId) : [];

            if (peers.length === 0) {
                // Solo session — skip load gate, schedule immediately
                const leadTimeMs = computeRoomLeadTime(roomCode);
                const serverTimeToExecute = Date.now() + leadTimeMs;
                roomManager.broadcast(roomCode, {
                    type: 'SCHEDULED_ACTION',
                    serverTimeToExecute,
                    scheduledAction: {
                        type: 'PLAY',
                        trackTimeSeconds: msg.trackTimeSeconds,
                        audioSource: msg.audioSource,
                    },
                });
                console.log(`[sync] Solo PLAY — scheduled immediately`);
                break;
            }

            // Multi-peer — initiate audio-load gate before scheduling
            roomManager.initAudioLoad(roomCode, msg.audioSource);
            roomManager.broadcast(roomCode, {
                type: 'LOAD_AUDIO',
                audioSource: msg.audioSource,
                trackTimeSeconds: msg.trackTimeSeconds,
            }, clientId); // exclude host — host loads locally

            console.log(`[sync] LOAD_AUDIO sent to ${peers.length} peer(s) in room ${roomCode}`);
            break;
        }

        case 'PAUSE': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;

            // Track room playback state
            const room = roomManager.getRoom(roomCode);
            if (room) {
                room.isPlaying = false;
                room.positionMs = (msg.trackTimeSeconds || 0) * 1000;
                room.lastPlayTimestamp = null; // clear on pause
            }

            const leadTimeMs = computeRoomLeadTime(roomCode);
            const serverTimeToExecute = Date.now() + leadTimeMs;

            roomManager.broadcast(roomCode, {
                type: 'SCHEDULED_ACTION',
                serverTimeToExecute,
                scheduledAction: {
                    type: 'PAUSE',
                    trackTimeSeconds: msg.trackTimeSeconds,
                    audioSource: msg.audioSource,
                },
            }, clientId);
            break;
        }

        case 'create_room': {
            const { roomCode, username, userId } = msg;
            if (!roomCode || !username) return;

            // Use client-provided userId so the Android localUserId matches backend state
            const resolvedId = userId || ws.id;

            if (!ROOM_CODE_REGEX.test(roomCode)) {
                if (ws.readyState === 1) {
                    ws.send(JSON.stringify({
                        type: "error",
                        code: "INVALID_ROOM_CODE",
                        message: "Room code format is invalid"
                    }));
                }
                return;
            }

            const room = roomManager.createRoom(roomCode, ws, username, resolvedId);
            if (!room) {
                if (ws.readyState === 1) {
                    ws.send(JSON.stringify({
                        type: "error",
                        code: "ROOM_ALREADY_EXISTS",
                        message: "A room with this code already exists"
                    }));
                }
                return;
            }

            if (ws.readyState === 1) {
                ws.send(JSON.stringify({
                    type: "join_success",
                    resolvedName: username,
                    room: room
                }));
            }

            // Immediately send room state to creator
            roomManager.broadcastRoom(roomCode);
            break;
        }

        case 'join_room': {
            const { roomCode, username, userId } = msg;
            if (!roomCode || !username) return;

            const resolvedId = userId || ws.id;

            if (!ROOM_CODE_REGEX.test(roomCode)) {
                if (ws.readyState === 1) {
                    ws.send(JSON.stringify({
                        type: "error",
                        code: "ROOM_NOT_FOUND",
                        message: "Room does not exist"
                    }));
                }
                return;
            }

            const room = roomManager.getRoom(roomCode);
            if (!room) {
                if (ws.readyState === 1) {
                    ws.send(JSON.stringify({
                        type: "error",
                        code: "ROOM_NOT_FOUND",
                        message: "Room does not exist"
                    }));
                }
                return;
            }

            roomManager.joinRoom(roomCode, ws, username, resolvedId);

            if (ws.readyState === 1) {
                ws.send(JSON.stringify({
                    type: "join_success",
                    resolvedName: username,
                    room: room
                }));

                // Full hydration snapshot — includes queue, track, position, users, loop/shuffle
                if (room.currentTrackId || room.currentTrackUrl) {
                    const now = Date.now();
                    let currentPos = room.currentPositionMs || 0;

                    if (room.isPlaying) {
                        const SYNC_BUFFER_MS = 2000;
                        const serverTimeToExecute = now + SYNC_BUFFER_MS;

                        if (room.lastPositionUpdate && room.lastPositionUpdate > 0) {
                            const elapsed = now - room.lastPositionUpdate;
                            if (elapsed > 0 && elapsed < 3600000) currentPos += elapsed;
                        }
                        currentPos = Math.max(0, currentPos);
                        const positionAtExecution = currentPos + SYNC_BUFFER_MS;

                        console.log(`[sync:join_room] late-join playing: trackId=${room.currentTrackId} posAtExec=${positionAtExecution}ms execAt=${serverTimeToExecute} queue=${(room.queue || []).length} tracks`);
                        ws.send(JSON.stringify({
                            type: "sync_snapshot",
                            trackId: String(room.currentTrackId),
                            url: room.currentTrackUrl,
                            title: room.currentTrackTitle || '',
                            artist: room.currentTrackArtist || '',
                            thumbnailUrl: room.currentTrackThumbnail || '',
                            positionMs: positionAtExecution,
                            serverTimeToExecute: serverTimeToExecute,
                            serverTime: now,
                            isPlaying: true,
                            // Queue hydration — prevents late joiner seeing empty queue
                            queue: room.queue || [],
                            loopMode: room.loopMode || 'none',
                            shuffleEnabled: room.shuffleEnabled || false,
                        }));
                    } else {
                        console.log(`[sync:join_room] late-join paused: trackId=${room.currentTrackId} pos=${currentPos}ms queue=${(room.queue || []).length} tracks`);
                        ws.send(JSON.stringify({
                            type: "sync_snapshot",
                            trackId: String(room.currentTrackId),
                            url: room.currentTrackUrl,
                            title: room.currentTrackTitle || '',
                            artist: room.currentTrackArtist || '',
                            thumbnailUrl: room.currentTrackThumbnail || '',
                            positionMs: currentPos,
                            serverTimeToExecute: now,
                            serverTime: now,
                            isPlaying: false,
                            // Queue hydration
                            queue: room.queue || [],
                            loopMode: room.loopMode || 'none',
                            shuffleEnabled: room.shuffleEnabled || false,
                        }));
                    }
                } else {
                    // No active track — still send queue if it exists (host may have queued tracks but not started)
                    if ((room.queue || []).length > 0) {
                        console.log(`[sync:join_room] no active track but queue has ${room.queue.length} items — sending queue_update`);
                        ws.send(JSON.stringify({
                            type: 'queue_update',
                            queue: room.queue,
                        }));
                    }
                }
            }

            roomManager.broadcastRoom(roomCode);
            console.log(`[sync:join_room] ${resolvedId} (${username}) joined room ${roomCode} | users: ${room.users.length}`);
            break;
        }

        case 'update_room_settings': {
            const room = findRoomBySocket(ws);
            if (!room) return;

            if (msg.globalVolume !== undefined) room.globalVolume = msg.globalVolume;
            if (msg.playbackPermission !== undefined) room.playbackPermission = msg.playbackPermission;
            if (msg.timingOffsetMs !== undefined) room.timingOffsetMs = msg.timingOffsetMs;

            roomManager.broadcastRoom(room.code);
            break;
        }

        case 'leave_room': {
            roomManager.removeSocketFromRoom(ws);
            break;
        }

        case 'NTP_REQUEST':
        case 'ntp_probe': {
            const t1 = Date.now();
            const { t0, clientRtt } = msg;
            if (t0 === undefined) return;

            // Update RTT tracking if client sent its measured RTT
            if (clientRtt != null) {
                updateClientRtt(clientId, clientRtt);
            }

            // Track hardware compensation — mirrors BeatSync's RoomManager compensation store
            if (msg.clientCompensationMs != null) {
                updateClientCompensation(clientId, msg.clientCompensationMs);
            }

            const t2 = Date.now();
            if (ws.readyState === 1) {
                ws.send(JSON.stringify({
                    type: msg.type === 'NTP_REQUEST' ? 'NTP_RESPONSE' : 'ntp_response',
                    t0: t0,
                    t1: t1,
                    t2: t2,
                    probeGroupId: msg.probeGroupId,
                    probeGroupIndex: msg.probeGroupIndex,
                }));
            }
            break;
        }

        case 'AUDIO_LOADED': {
            const { roomCode, audioSource } = msg;
            roomManager.markAudioLoaded(roomCode, clientId, audioSource);
            console.log(`[sync] AUDIO_LOADED from ${clientId} in room ${roomCode}`);

            if (roomManager.areAllPeersAudioLoaded(roomCode)) {
                roomManager.clearAudioLoad(roomCode);
                console.log(`[sync] All peers loaded — scheduling playback in room ${roomCode}`);

                const room = roomManager.getRoom(roomCode);
                const leadTimeMs = computeRoomLeadTime(roomCode);
                const serverTimeToExecute = Date.now() + leadTimeMs;

                // Broadcast to ALL (including host) — host also needs the SCHEDULED_ACTION
                roomManager.broadcast(roomCode, {
                    type: 'SCHEDULED_ACTION',
                    serverTimeToExecute,
                    scheduledAction: {
                        type: 'PLAY',
                        trackTimeSeconds: (room?.positionMs || 0) / 1000,
                        audioSource: room?.currentAudioSource || audioSource,
                    },
                });
            }
            break;
        }

        // Keep other essential playback sync logic but adapted to new structure
        case 'playback_sync': {
            const room = findRoomBySocket(ws);
            if (!room) return;

            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            function isAuthorized(user, room) {
                return (
                    user.role === "host" ||
                    user.role === "sudo" ||
                    room.playbackPermission === "everyone"
                );
            }

            if (!isAuthorized(sender, room)) {
                console.log("[BLOCKED] Unauthorized playback attempt:", sender.id);
                return;
            }

            if (msg.playbackEvent === 'play') {
                room.isPlaying = true;
                room.currentPositionMs = msg.positionMs || 0;
                room.lastPositionUpdate = Date.now();
            } else if (msg.playbackEvent === 'pause') {
                room.isPlaying = false;
                room.currentPositionMs = msg.positionMs || 0;
                room.lastPositionUpdate = Date.now();
            }

            // Broadcast to others in the room
            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;

            // Dynamic lead time — true room-wide worst-case RTT + max compensation
            const leadTimeMs = computeRoomLeadTime(room.code);
            const serverTimeToExecute = Date.now() + leadTimeMs;

            const payload = JSON.stringify({
                type: 'playback_sync',
                playbackEvent: msg.playbackEvent,
                trackId: msg.trackId,
                positionMs: msg.positionMs,
                serverTimeToExecute: serverTimeToExecute
            });

            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(payload);
                }
            });
            break;
        }

        case 'track_change': {
            const room = findRoomBySocket(ws);
            if (!room) return;

            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            const isAuthorized = (
                sender.role === 'host' ||
                sender.role === 'sudo' ||
                room.playbackPermission === 'everyone'
            );

            if (!isAuthorized) {
                console.log('[BLOCKED] Unauthorized track_change attempt:', sender.id);
                return;
            }

            room.currentTrackId = msg.trackId || '';
            room.currentTrackUrl = msg.url || '';
            room.currentTrackTitle = msg.title || '';
            room.currentTrackArtist = msg.artist || '';
            room.currentTrackThumbnail = msg.thumbnailUrl || '';
            room.isPlaying = true;
            room.currentPositionMs = 0;
            room.lastPositionUpdate = Date.now();

            console.log('[TRACK_CHANGE] Broadcasting from', sender.id, '- trackId:', msg.trackId, 'url:', msg.url);

            const payload = JSON.stringify({
                type: 'track_change',
                trackId: msg.trackId || '',
                url: msg.url || '',
                title: msg.title || '',
                artist: msg.artist || '',
                thumbnailUrl: msg.thumbnailUrl || '',
                startAt: msg.startAt || (Date.now() + 2000)
            });

            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;

            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(payload);
                }
            });
            break;
        }

        case 'seek': {
            const room = findRoomBySocket(ws);
            if (!room) return;

            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            const isAuthorized = (
                sender.role === 'host' ||
                sender.role === 'sudo' ||
                room.playbackPermission === 'everyone'
            );

            if (!isAuthorized) {
                console.log('[BLOCKED] Unauthorized seek attempt:', sender.id);
                return;
            }

            room.currentPositionMs = msg.position || 0;
            room.lastPositionUpdate = Date.now();

            const payload = JSON.stringify({
                type: 'seek',
                position: msg.position || 0,
                timestamp: msg.timestamp || Date.now()
            });

            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;

            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(payload);
                }
            });
            break;
        }

        case 'track_ready': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            const payload = JSON.stringify({
                type: 'track_ready',
                userId: sender.id,
                trackId: msg.trackId
            });

            // Send only to the host
            const hostUser = room.users.find(u => u.role === 'host');
            if (hostUser) {
                const sockets = roomManager.getRoomSockets(room.code);
                if (sockets) {
                    sockets.forEach(s => {
                        if ((s.userId || s.id) === hostUser.id && s.readyState === 1) {
                            s.send(payload);
                        }
                    });
                }
            }
            break;
        }

        case 'play_at': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender || sender.role !== 'host') return;

            const payload = JSON.stringify({
                type: 'play_at',
                startTime: msg.startTime
            });

            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;
            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(payload);
                }
            });
            break;
        }

        case 'pause_at': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender || sender.role !== 'host') return;

            const payload = JSON.stringify({
                type: 'pause_at',
                position: msg.position,
                timestamp: msg.timestamp
            });

            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;
            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(payload);
                }
            });
            break;
        }

        case 'stop': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            const isAuthorized = (
                sender.role === 'host' ||
                sender.role === 'sudo'
            );
            if (!isAuthorized) {
                console.log('[BLOCKED] Unauthorized stop attempt:', sender.id);
                return;
            }

            // Clear the server-side queue too
            room.queue = [];
            room.currentTrackId = null;
            room.currentTrackUrl = null;
            room.isPlaying = false;
            room.currentPositionMs = 0;
            room.lastPositionUpdate = Date.now();

            console.log('[STOP] Host stopped playback, clearing room queue');
            const stopPayload = JSON.stringify({ type: 'stop' });
            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;
            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(stopPayload);
                }
            });
            break;
        }

        case 'queue_update': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            const isAuthorized = (
                sender.role === 'host' ||
                sender.role === 'sudo'
            );
            if (!isAuthorized) {
                console.log('[BLOCKED] Unauthorized queue_update attempt:', sender.id);
                return;
            }

            // Persist authoritatively in room state
            room.queue = msg.queue || [];
            console.log(`[sync:queue] ${sender.id} updated queue in room ${room.code}: ${room.queue.length} tracks`);

            // QUEUE_UPDATED — incremental broadcast to all OTHER clients
            // (the sender already has the up-to-date queue locally)
            const queuePayload = JSON.stringify({
                type: 'queue_update',
                queue: room.queue
            });
            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;
            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(queuePayload);
                }
            });
            break;
        }

        case 'sync_update': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender) return;

            const isAuthorized = (
                sender.role === 'host' ||
                sender.role === 'sudo' ||
                room.playbackPermission === 'everyone'
            );
            if (!isAuthorized) return;

            room.currentPositionMs = msg.positionMs || 0;
            room.lastPositionUpdate = Date.now();

            const payload = JSON.stringify({
                type: 'sync_update',
                positionMs: msg.positionMs,
                serverTimeToExecute: Date.now()
            });

            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;
            sockets.forEach(s => {
                if (s !== ws && s.readyState === 1) {
                    s.send(payload);
                }
            });
            break;
        }

        // Add other cases if needed, but the focus is on sync fixes
        case 'transfer_host': {
            console.log("ACTION: transfer_host by socketId", ws.id);
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender || sender.role !== "host") return;
            
            const targetUser = room.users.find(u => u.id === msg.targetUserId);
            if (targetUser) {
                room.users.forEach(u => u.role = "guest");
                targetUser.role = "host";
                roomManager.broadcastRoom(room.code);
            }
            break;
        }

        case 'grant_sudo': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender || sender.role !== "host") return;
            
            const targetUser = room.users.find(u => u.id === msg.targetUserId);
            if (targetUser && targetUser.role === "guest") {
                targetUser.role = "sudo";
                roomManager.broadcastRoom(room.code);
            } else if (targetUser && targetUser.role === "sudo") {
                targetUser.role = "guest"; // toggle behavior if necessary, or just explicit grant/revoke
                roomManager.broadcastRoom(room.code);
            }
            break;
        }

        case 'kick_user': {
            console.log("ACTION: kick_user targetId", msg.targetUserId);
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender || sender.role !== "host") return;

            const targetIndex = room.users.findIndex(u => u.id === msg.targetUserId);
            if (targetIndex !== -1 && room.users[targetIndex].role !== "host") {
                room.users.splice(targetIndex, 1);
                
                // Disconnect the kicked client natively
                const sockets = roomManager.getRoomSockets(room.code);
                if (sockets) {
                    for (const s of sockets) {
                        if ((s.userId || s.id) === msg.targetUserId) {
                            s.send(JSON.stringify({ type: "kicked" }));
                            roomManager.removeSocketFromRoom(s);
                            break;
                        }
                    }
                }
                roomManager.broadcastRoom(room.code);
            }
            break;
        }

        case 'set_playback_permission': {
            const room = findRoomBySocket(ws);
            if (!room) return;
            const sender = room.users.find(u => u.id === (ws.userId || ws.id));
            if (!sender || sender.role !== "host") return;

            if (msg.mode === "everyone" || msg.mode === "host_only") {
                room.playbackPermission = msg.mode;
                roomManager.broadcastRoom(room.code);
            }
            break;
        }

    }
}

function handleDisconnect(clientId, ws) {
    if (ws) {
        roomManager.removeSocketFromRoom(ws);
    }
    // Clean up per-client state so stale values don't inflate future room headroom
    clearClientCompensation(clientId);
    clearClientRtt(clientId);
    console.log(`[sync] ${clientId} disconnected — RTT + compensation state cleared`);
}

function findRoomBySocket(ws) {
    for (const [roomCode, sockets] of roomManager.roomSockets.entries()) {
        if (sockets.has(ws)) {
            return roomManager.rooms.get(roomCode);
        }
    }
    return null;
}

module.exports = { handleMessage, handleDisconnect, findRoomBySocket };
