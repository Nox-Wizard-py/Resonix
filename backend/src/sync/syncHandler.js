/**
 * syncHandler.js — WebSocket message dispatcher for ResonixSync.
 *
 * Handles all message types sent by the Android resonix-sync module:
 *   JOIN        — register client in a room; first client becomes host
 *   NTP_REQUEST — echo back t1/t2 timestamps for clock-offset measurement
 *   PLAY / SEEK — host triggers a SCHEDULED_ACTION broadcast to all peers
 *   PAUSE       — host triggers a SCHEDULED_ACTION broadcast to all peers
 *
 * All broadcasts include a 200 ms execution window so peers have time to
 * receive and schedule the command before it fires.
 */

const roomManager = require('./roomManager');

// Execution window added to every scheduled action (ms).
const SCHEDULE_OFFSET_MS = 200;

// Grace period (ms) before a host-disconnected room is destroyed.
const HOST_GRACE_PERIOD_MS = 30_000;

// Track pending host-reconnect timers: roomCode → NodeJS.Timeout
const hostGraceTimers = new Map();

/**
 * Dispatch an inbound WebSocket message for [clientId].
 *
 * @param {string} clientId  Stable per-connection UUID assigned at connect time.
 * @param {string} raw       Raw UTF-8 string received from the WebSocket.
 * @param {import('ws')} ws  The sender's WebSocket instance.
 */
function handleMessage(clientId, raw, ws) {
    let msg;
    try {
        msg = JSON.parse(raw);
    } catch {
        return; // silently ignore malformed frames
    }

    switch (msg.type) {

        case 'join_room': {
            const { roomCode, username, userId } = msg;
            if (!roomCode || !username) return;

            const finalClientId = userId || clientId;

            // If there's a pending host-grace timer for this room and this is the original host reconnecting
            if (hostGraceTimers.has(roomCode)) {
                const room = roomManager.getRoom(roomCode);
                if (room && room.pendingHostId === finalClientId) {
                    clearTimeout(hostGraceTimers.get(roomCode));
                    hostGraceTimers.delete(roomCode);
                    room.pendingHostId = null;
                    // Restore as host
                    roomManager.addClient(roomCode, finalClientId, ws, username);
                    roomManager.setHost(roomCode, finalClientId);
                    const state = roomManager.getRoomState(roomCode);
                    roomManager.broadcast(roomCode, { type: 'room_updated', state });
                    if (ws.readyState === 1) ws.send(JSON.stringify({ type: 'join_success', resolvedName: username }));
                    break;
                }
            }

            const resolvedName = roomManager.addClient(roomCode, finalClientId, ws, username);
            const room = roomManager.getRoom(roomCode);

            // First client in the room becomes the host
            if (room.clients.size === 1) {
                roomManager.setHost(roomCode, finalClientId);
            }

            // Broadcast new room state
            const state = roomManager.getRoomState(roomCode);
            roomManager.broadcast(roomCode, { type: 'room_updated', state });

            // Tell joining user their resolved name
            if (ws.readyState === 1) {
                ws.send(JSON.stringify({ type: 'join_success', resolvedName }));
            }
            break;
        }

        case 'leave_room': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode) return;

            const isHost = roomManager.isHost(roomCode, clientId);
            roomManager.removeClient(roomCode, clientId);

            if (isHost) {
                roomManager.broadcast(roomCode, { type: 'room_closed' });
                // Destroy room immediately if host leaves
                const room = roomManager.getRoom(roomCode);
                if (room) {
                    for (const clientData of room.clients.values()) {
                        roomManager.removeClient(roomCode, clientData.clientId);
                    }
                }
                roomManager.destroyRoomIfEmpty(roomCode);
            } else {
                roomManager.destroyRoomIfEmpty(roomCode);
                
                // If room still exists, broadcast updated state
                if (roomManager.getRoom(roomCode)) {
                    roomManager.broadcast(roomCode, {
                        type: 'room_updated',
                        state: roomManager.getRoomState(roomCode)
                    });
                }
            }
            break;
        }

        case 'transfer_host': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;
            
            const { newHostId } = msg;
            if (newHostId) {
                roomManager.setHost(roomCode, newHostId);
                roomManager.broadcast(roomCode, {
                    type: 'room_updated',
                    state: roomManager.getRoomState(roomCode)
                });
            }
            break;
        }

        case 'kick_user': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;
            
            const { targetUserId } = msg;
            if (targetUserId) {
                const room = roomManager.getRoom(roomCode);
                if (room) {
                    const clientNode = room.clients.get(targetUserId);
                    if (clientNode) {
                        try {
                            if (clientNode.ws.readyState === 1) {
                                clientNode.ws.send(JSON.stringify({ type: 'kicked' }));
                            }
                        } catch(e) {}
                    }
                }
                roomManager.removeClient(roomCode, targetUserId);
                roomManager.broadcast(roomCode, {
                    type: 'room_updated',
                    state: roomManager.getRoomState(roomCode)
                });
            }
            break;
        }

        case 'temp_control': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;

            const { targetUserId } = msg;
            if (targetUserId) {
                roomManager.toggleTempControl(roomCode, targetUserId);
                roomManager.broadcast(roomCode, {
                    type: 'room_updated',
                    state: roomManager.getRoomState(roomCode)
                });
            }
            break;
        }

        case 'NTP_REQUEST': {
            // Capture t1 immediately on receipt, t2 immediately before send
            // to minimise asymmetric server-processing contamination.
            const t1 = Date.now();
            const t2 = Date.now();
            if (ws.readyState === ws.OPEN) {
                ws.send(JSON.stringify({
                    type: 'NTP_RESPONSE',
                    t0: msg.t0,
                    t1,
                    t2,
                    probeGroupId: msg.probeGroupId,
                    probeGroupIndex: msg.probeGroupIndex,
                }));
            }
            break;
        }

        case 'playback_sync': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode) return;
            const room = roomManager.getRoom(roomCode);
            if (!room) return;

            // Only host or user with temp_control can trigger playback_sync
            const isHost = roomManager.isHost(roomCode, clientId);
            const clientData = room.clients.get(clientId);
            const hasTempControl = clientData ? clientData.hasTempControl : false;

            if (isHost || hasTempControl) {
                const serverTimeToExecute = Date.now() + SCHEDULE_OFFSET_MS;
                roomManager.broadcast(roomCode, {
                    type: 'playback_sync',
                    playbackEvent: msg.playbackEvent,
                    trackId: msg.trackId,
                    positionMs: msg.positionMs,
                    serverTimeToExecute
                }, clientId); // Exclude the sender
            }
            break;
        }

        case 'update_room_settings': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode) return;

            // Allow host OR temp-control user to update settings
            const room = roomManager.getRoom(roomCode);
            const isHostUser = roomManager.isHost(roomCode, clientId);
            const clientData = room ? room.clients.get(clientId) : null;
            const hasTempControl = clientData ? clientData.hasTempControl : false;
            if (!isHostUser && !hasTempControl) return;

            roomManager.updateRoomSettings(roomCode, {
                globalVolume: msg.globalVolume,
                playbackPermission: msg.playbackPermission,
                timingOffsetMs: msg.timingOffsetMs
            });

            roomManager.broadcast(roomCode, {
                type: 'room_updated',
                state: roomManager.getRoomState(roomCode)
            });
            break;
        }

        default:
            console.warn(`[sync] unknown message type: ${msg.type} from ${clientId}`);
    }
}

/**
 * Clean up all room state for [clientId] on WebSocket close or error.
 *
 * @param {string} clientId
 */
function handleDisconnect(clientId) {
    const roomCode = roomManager.getRoomOfClient(clientId);
    if (!roomCode) return; // already removed (e.g. explicit leave_room before socket closed)

    const isHost = roomManager.isHost(roomCode, clientId);
    roomManager.removeClient(roomCode, clientId);

    if (isHost) {
        const room = roomManager.getRoom(roomCode);
        if (!room) return;

        // Start a grace period — give the host 30 seconds to reconnect before destroying the room
        room.pendingHostId = clientId;
        console.log(`[sync] host ${clientId} lost. Grace period started for room ${roomCode}`);

        const timer = setTimeout(() => {
            hostGraceTimers.delete(roomCode);
            const r = roomManager.getRoom(roomCode);
            if (!r) return;

            // Pick next guest as new host if any, else close room
            const remaining = Array.from(r.clients.values());
            if (remaining.length > 0) {
                const newHost = remaining[0];
                roomManager.setHost(roomCode, newHost.clientId);
                r.pendingHostId = null;
                roomManager.broadcast(roomCode, {
                    type: 'room_updated',
                    state: roomManager.getRoomState(roomCode)
                });
                console.log(`[sync] host grace expired. New host: ${newHost.clientId}`);
            } else {
                roomManager.broadcast(roomCode, { type: 'room_closed' });
                roomManager.destroyRoomIfEmpty(roomCode);
                console.log(`[sync] host grace expired. Room ${roomCode} closed (empty).`);
            }
        }, HOST_GRACE_PERIOD_MS);

        hostGraceTimers.set(roomCode, timer);
    } else {
        roomManager.destroyRoomIfEmpty(roomCode);
        if (roomManager.getRoom(roomCode)) {
            roomManager.broadcast(roomCode, {
                type: 'room_updated',
                state: roomManager.getRoomState(roomCode)
            });
        }
    }
    console.log(`[sync] ${clientId} disconnected from room ${roomCode}`);
}

module.exports = { handleMessage, handleDisconnect };
