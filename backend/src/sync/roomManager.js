/**
 * roomManager.js — In-memory room state for ResonixSync.
 *
 * All state is held in two Maps:
 *   rooms        : roomCode → { hostClientId, clients: Map<clientId, ws> }
 *   clientRoomMap: clientId → roomCode   (reverse lookup for fast disconnect)
 *
 * No persistence — server restart clears all rooms.
 */

/** @type {Map<string, { hostClientId: string|null, clients: Map<string, import('ws')> }>} */
const rooms = new Map();

/** @type {Map<string, string>} clientId → roomCode */
const clientRoomMap = new Map();

// ── Room accessors ────────────────────────────────────────────────────────────

/** Return the room object for [roomCode], or undefined if it does not exist. */
function getRoom(roomCode) {
    return rooms.get(roomCode);
}

/**
 * Add [clientId] + their WebSocket to [roomCode].
 * Creates the room entry if it does not exist yet.
 */
function addClient(roomCode, clientId, ws) {
    if (!rooms.has(roomCode)) {
        rooms.set(roomCode, { hostClientId: null, clients: new Map() });
    }
    rooms.get(roomCode).clients.set(clientId, ws);
    clientRoomMap.set(clientId, roomCode);
}

/** Mark [clientId] as the host of [roomCode]. */
function setHost(roomCode, clientId) {
    const room = rooms.get(roomCode);
    if (room) room.hostClientId = clientId;
}

/** Return true if [clientId] is the current host of [roomCode]. */
function isHost(roomCode, clientId) {
    return rooms.get(roomCode)?.hostClientId === clientId;
}

/** Remove [clientId] from [roomCode] and erase the reverse lookup entry. */
function removeClient(roomCode, clientId) {
    rooms.get(roomCode)?.clients.delete(clientId);
    clientRoomMap.delete(clientId);
}

/** Return the room code that [clientId] is currently in, or undefined. */
function getRoomOfClient(clientId) {
    return clientRoomMap.get(clientId);
}

// ── Broadcast ─────────────────────────────────────────────────────────────────

/**
 * Serialize [message] as JSON and send it to every client in [roomCode],
 * optionally skipping [excludeClientId].
 * Guards with ws.readyState === ws.OPEN before every send.
 */
function broadcast(roomCode, message, excludeClientId = null) {
    const room = rooms.get(roomCode);
    if (!room) return;
    const json = JSON.stringify(message);
    for (const [clientId, ws] of room.clients) {
        if (clientId === excludeClientId) continue;
        if (ws.readyState === ws.OPEN) ws.send(json);
    }
}

/**
 * Delete [roomCode] from the rooms Map if it has no remaining clients.
 * Should be called after every removeClient().
 */
function destroyRoomIfEmpty(roomCode) {
    const room = rooms.get(roomCode);
    if (room && room.clients.size === 0) {
        rooms.delete(roomCode);
        console.log(`[sync] room destroyed: ${roomCode}`);
    }
}

module.exports = {
    getRoom,
    addClient,
    setHost,
    isHost,
    removeClient,
    getRoomOfClient,
    broadcast,
    destroyRoomIfEmpty,
};
