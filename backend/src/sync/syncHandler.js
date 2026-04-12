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

        case 'JOIN': {
            const { roomCode } = msg;
            if (!roomCode) {
                console.warn(`[sync] JOIN missing roomCode from ${clientId}`);
                return;
            }

            roomManager.addClient(roomCode, clientId, ws);
            const room = roomManager.getRoom(roomCode);

            // First client in the room becomes the host
            if (room.clients.size === 1) {
                roomManager.setHost(roomCode, clientId);
            }

            // Send room state snapshot to the joining client
            if (ws.readyState === ws.OPEN) {
                ws.send(JSON.stringify({
                    type: 'ROOM_STATE',
                    roomCode,
                    hostClientId: room.hostClientId,
                    isPlaying: false,
                    positionMs: 0,
                }));
            }

            console.log(`[sync] ${clientId} joined room ${roomCode} | clients: ${room.clients.size}`);
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

        case 'PLAY':
        case 'SEEK': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;

            const serverTimeToExecute = Date.now() + SCHEDULE_OFFSET_MS;
            roomManager.broadcast(roomCode, {
                type: 'SCHEDULED_ACTION',
                serverTimeToExecute,
                scheduledAction: {
                    type: 'PLAY',
                    trackTimeSeconds: msg.trackTimeSeconds,
                    audioSource: msg.audioSource,
                },
            }, clientId); // exclude the host — it plays back locally
            break;
        }

        case 'PAUSE': {
            const roomCode = roomManager.getRoomOfClient(clientId);
            if (!roomCode || !roomManager.isHost(roomCode, clientId)) return;

            const serverTimeToExecute = Date.now() + SCHEDULE_OFFSET_MS;
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
    if (!roomCode) return;
    roomManager.removeClient(roomCode, clientId);
    roomManager.destroyRoomIfEmpty(roomCode);
    console.log(`[sync] ${clientId} disconnected from room ${roomCode}`);
}

module.exports = { handleMessage, handleDisconnect };
