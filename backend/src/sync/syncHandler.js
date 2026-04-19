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

        // Add other cases if needed, but the focus is on sync fixes
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
