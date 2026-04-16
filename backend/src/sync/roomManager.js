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

function resolveDuplicateName(base, users) {
    let candidate = base;
    let count = 2;
    while (users.some(u => u.username === candidate)) {
        candidate = `${base} (${count})`;
        count++;
    }
    return candidate;
}

/**
 * Add [clientId] + their WebSocket to [roomCode].
 * Creates the room entry if it does not exist yet.
 */
function addClient(roomCode, clientId, ws, initialUsername) {
    if (!rooms.has(roomCode)) {
        rooms.set(roomCode, {
            hostClientId: null,
            clients: new Map(),
            globalVolume: 1.0,
            playbackPermission: 'Everyone',
            timingOffsetMs: 0
        });
    }
    const room = rooms.get(roomCode);

    const existingUsers = Array.from(room.clients.values());
    const resolvedName = resolveDuplicateName(initialUsername, existingUsers);

    room.clients.set(clientId, { ws, username: resolvedName, hasTempControl: false, clientId });
    clientRoomMap.set(clientId, roomCode);
    
    return resolvedName;
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

/** Update room-level settings (volume, permission, nudge) */
function updateRoomSettings(roomCode, settings) {
    const room = rooms.get(roomCode);
    if (!room) return;
    if (settings.globalVolume !== undefined) room.globalVolume = settings.globalVolume;
    if (settings.playbackPermission !== undefined) room.playbackPermission = settings.playbackPermission;
    if (settings.timingOffsetMs !== undefined) room.timingOffsetMs = settings.timingOffsetMs;
}

/** Disable or enable temp control for a client */
function toggleTempControl(roomCode, targetClientId) {
    const room = rooms.get(roomCode);
    if (!room) return;
    const client = room.clients.get(targetClientId);
    if (client) {
        client.hasTempControl = !client.hasTempControl;
    }
}

/**
 * Serialize [message] as JSON and send it to every client in [roomCode],
 * optionally skipping [excludeClientId].
 * Guards with ws.readyState === ws.OPEN before every send.
 */
function broadcast(roomCode, message, excludeClientId = null) {
    const room = rooms.get(roomCode);
    if (!room) return;
    const json = JSON.stringify(message);
    for (const [clientId, clientData] of room.clients) {
        if (clientId === excludeClientId) continue;
        const ws = clientData.ws;
        if (ws.readyState === 1 /* OPEN */) ws.send(json);
    }
}

function getRoomState(roomCode) {
    const room = rooms.get(roomCode);
    if (!room) return null;
    return {
        roomCode,
        hostId: room.hostClientId,
        globalVolume: room.globalVolume ?? 1.0,
        playbackPermission: room.playbackPermission ?? 'Everyone',
        timingOffsetMs: room.timingOffsetMs ?? 0,
        users: Array.from(room.clients.values()).map(c => ({
            userId: c.clientId,
            username: c.username,
            isHost: c.clientId === room.hostClientId,
            hasTempControl: c.hasTempControl
        }))
    };
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
    getRoomState,
    toggleTempControl,
    updateRoomSettings,
    resolveDuplicateName
};
