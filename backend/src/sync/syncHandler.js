/**
 * syncHandler.js — Strict WebSocket message dispatcher for ResonixSync.
 */

const roomManager = require('./roomManager');

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

            console.log("JOIN_ATTEMPT:", roomCode);
            console.log("ROOM_EXISTS:", roomManager.rooms.has(roomCode));
            console.log("CURRENT_ROOMS:", Array.from(roomManager.rooms.keys()));

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
            }

            roomManager.broadcastRoom(roomCode);
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

            // Broadcast to others in the room
            const sockets = roomManager.getRoomSockets(room.code);
            if (!sockets) return;

            const payload = JSON.stringify({
                type: 'playback_sync',
                playbackEvent: msg.playbackEvent,
                trackId: msg.trackId,
                positionMs: msg.positionMs,
                serverTimeToExecute: Date.now() + 200 // 200ms window
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
