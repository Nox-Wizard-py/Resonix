/**
 * roomManager.js — Strict Map-based room storage for ResonixSync.
 */

// roomCode -> { code: string, users: [ { id: string, name: string, role: "host" | "guest" } ] }
const rooms = new Map();

// roomCode -> Set<WebSocket>
const roomSockets = new Map();

function getRoom(roomCode) {
    return rooms.get(roomCode);
}

function getRoomSockets(roomCode) {
    return roomSockets.get(roomCode);
}

function addClient(roomCode, clientId, ws) {
    let room = rooms.get(roomCode);
    if (!room) {
        room = {
            code: roomCode,
            users: [],
            isPlaying: false,
            positionMs: 0,
            currentAudioSource: '',
            hostClientId: clientId
        };
        rooms.set(roomCode, room);
    }
    
    // Add to users if not present
    if (!room.users.find(u => u.id === clientId)) {
        room.users.push({
            id: clientId,
            role: room.users.length === 0 ? "host" : "guest",
            joinedAt: Date.now()
        });
    }

    if (!roomSockets.has(roomCode)) {
        roomSockets.set(roomCode, new Set());
    }
    roomSockets.get(roomCode).add(ws);
}

function setHost(roomCode, clientId) {
    const room = rooms.get(roomCode);
    if (!room) return;
    room.hostClientId = clientId;
    room.users.forEach(u => u.role = (u.id === clientId) ? "host" : "guest");
}

function isHost(roomCode, clientId) {
    const room = rooms.get(roomCode);
    if (!room) return false;
    return room.hostClientId === clientId || room.users.some(u => u.id === clientId && u.role === "host");
}

function getRoomOfClient(clientId) {
    for (const [roomCode, sockets] of roomSockets.entries()) {
        for (const s of sockets) {
            if ((s.userId || s.id) === clientId) {
                return roomCode;
            }
        }
    }
    return null;
}

function broadcast(roomCode, messageObj, excludeClientId = null) {
    const sockets = roomSockets.get(roomCode);
    if (!sockets) return;
    const payload = JSON.stringify(messageObj);
    for (const s of sockets) {
        if (excludeClientId && (s.userId || s.id) === excludeClientId) continue;
        if (s.readyState === 1) {
            s.send(payload);
        }
    }
}

function destroyRoomIfEmpty(roomCode) {
    const room = rooms.get(roomCode);
    if (!room || room.users.length > 0) return;
    rooms.delete(roomCode);
    roomSockets.delete(roomCode);
}

/**
 * Creates a room instance. ONLY called by "create_room".
 */
function createRoom(roomCode, socket, username, userId) {
    if (rooms.has(roomCode)) return null;

    const room = {
        code: roomCode,
        globalVolume: 1.0,
        playbackPermission: "host_only",
        timingOffsetMs: 0,
        users: [{
            id: userId || socket.id,
            name: username || "Unknown",
            role: "host",
            joinedAt: Date.now()
        }]
    };
    
    // Store the custom userId on the socket so we can identify them when they disconnect
    socket.userId = userId || socket.id;

    rooms.set(roomCode, room);
    roomSockets.set(roomCode, new Set([socket]));
    
    return room;
}

/**
 * Adds a user to an existing room. ONLY called by "join_room".
 */
function joinRoom(roomCode, socket, username, userId) {
    const room = rooms.get(roomCode);
    if (!room) return null;

    room.users.push({
        id: userId || socket.id,
        name: username || "Unknown",
        role: "guest",
        joinedAt: Date.now()
    });

    socket.userId = userId || socket.id;

    if (!roomSockets.has(roomCode)) {
        roomSockets.set(roomCode, new Set());
    }
    roomSockets.get(roomCode).add(socket);

    return room;
}

/**
 * Broadcasts room state to all sockets in the room.
 */
function broadcastRoom(roomCode) {
    const room = rooms.get(roomCode);
    const sockets = roomSockets.get(roomCode);
    if (!room || !sockets) return;

    const payload = JSON.stringify({
        type: "room_updated",
        roomCode: room.code,
        users: room.users,
        // Include hostId for backward compatibility or future use if needed, 
        // but the prompt says users should be IDENTICAL.
        // We might need to keep some other state like volume if we want to preserve features,
        // but I will stick to the requested structure first.
        state: room // Some client code expects "state" object
    });

    sockets.forEach(s => {
        if (s.readyState === 1 /* OPEN */) {
            s.send(payload);
        }
    });
}

function removeSocketFromRoom(socket) {
    for (const [roomCode, sockets] of roomSockets.entries()) {
        if (sockets.has(socket)) {
            sockets.delete(socket);
            const room = rooms.get(roomCode);
            if (!room) continue;

            const targetId = socket.userId || socket.id;
            const leavingUser = room.users.find(u => u.id === targetId);
            const wasHost = leavingUser && leavingUser.role === "host";
            
            room.users = room.users.filter(u => u.id !== targetId);

            if (room.users.length === 0) {
                rooms.delete(roomCode);
                roomSockets.delete(roomCode);

                sockets.forEach(s => {
                    if (s.readyState === 1) {
                        s.send(JSON.stringify({ type: "room_closed" }));
                    }
                });
                return { roomCode, closed: true };
            }

            if (wasHost) {
                // FIFO Host Selection: The user with the earliest joinedAt becomes host
                const newHost = room.users.sort((a, b) => a.joinedAt - b.joinedAt)[0];
                newHost.role = "host";

                // Broadcast host transferred event to all remaining clients
                const transferPayload = JSON.stringify({
                    type: "host_transferred",
                    newHostId: newHost.id,
                    users: room.users
                });
                sockets.forEach(s => {
                    if (s.readyState === 1) {
                        s.send(transferPayload);
                    }
                });
            }

            broadcastRoom(roomCode);
            return { roomCode, closed: false };
        }
    }
    return null;
}

const clientRttMap = new Map(); // clientId → last known rttMs

function updateClientRtt(clientId, rttMs) {
    clientRttMap.set(clientId, rttMs);
}

function getLastKnownRtt(clientId) {
    return clientRttMap.get(clientId) || null;
}

function clearClientRtt(clientId) {
    clientRttMap.delete(clientId);
}

// clientId → compensationMs (hardware latency + nudge reported by client via NTP probe)
const clientCompensationMap = new Map();

function updateClientCompensation(clientId, compensationMs) {
    clientCompensationMap.set(clientId, compensationMs);
}

/**
 * Returns the highest compensation value across all users currently in the room.
 * Mirrors BeatSync's RoomManager.getMaxClientCompensation().
 * Ensures the schedule has enough headroom for the slowest/highest-latency device.
 */
function getMaxRoomCompensation(roomCode) {
    const room = rooms.get(roomCode);
    if (!room) return 0;
    let max = 0;
    for (const user of room.users) {
        const comp = clientCompensationMap.get(user.id) || 0;
        if (comp > max) max = comp;
    }
    return max;
}

function clearClientCompensation(clientId) {
    clientCompensationMap.delete(clientId);
}

/**
 * Returns the highest RTT observed across all clients currently in a room.
 * Used by computeRoomLeadTime() to ensure the slowest peer has enough runway
 * to receive and execute scheduled commands.
 */
function getMaxRoomRtt(roomCode) {
    const room = rooms.get(roomCode);
    if (!room) return 0;
    let maxRtt = 0;
    for (const user of room.users) {
        const rtt = clientRttMap.get(user.id) || 0;
        if (rtt > maxRtt) maxRtt = rtt;
    }
    return maxRtt;
}

const peerReadyMap = new Map(); // roomCode → Set<clientId>

function markPeerReady(roomCode, clientId) {
    if (!peerReadyMap.has(roomCode)) {
        peerReadyMap.set(roomCode, new Set());
    }
    peerReadyMap.get(roomCode).add(clientId);
}

function areAllPeersReady(roomCode) {
    const room = rooms.get(roomCode);
    if (!room) return false;
    const readySet = peerReadyMap.get(roomCode) || new Set();
    // All non-host clients must be ready
    for (const user of room.users) {
        if (user.role === "host") continue;
        if (!readySet.has(user.id)) return false;
    }
    return true;
}

function clearReadyState(roomCode) {
    peerReadyMap.delete(roomCode);
}

// ── Audio-load gate ────────────────────────────────────────────────────────────
// roomCode → { audioSource: string, loadedClients: Set<clientId> }
const audioLoadMap = new Map();

function initAudioLoad(roomCode, audioSource) {
    audioLoadMap.set(roomCode, {
        audioSource,
        loadedClients: new Set(),
    });
}

function markAudioLoaded(roomCode, clientId, audioSource) {
    const entry = audioLoadMap.get(roomCode);
    if (!entry || entry.audioSource !== audioSource) return;
    entry.loadedClients.add(clientId);
}

/**
 * Returns true when all non-host users have confirmed the audio source is loaded.
 * Mirrors areAllPeersReady() — skips the host (host loads locally).
 */
function areAllPeersAudioLoaded(roomCode) {
    const room = rooms.get(roomCode);
    const entry = audioLoadMap.get(roomCode);
    if (!room || !entry) return false;
    for (const user of room.users) {
        if (user.role === 'host') continue;
        if (!entry.loadedClients.has(user.id)) return false;
    }
    return true;
}

function clearAudioLoad(roomCode) {
    audioLoadMap.delete(roomCode);
}

module.exports = {
    rooms,
    roomSockets,
    getRoom,
    getRoomSockets,
    createRoom,
    joinRoom,
    broadcastRoom,
    removeSocketFromRoom,
    updateClientRtt,
    getLastKnownRtt,
    clearClientRtt,
    getMaxRoomRtt,
    updateClientCompensation,
    getMaxRoomCompensation,
    clearClientCompensation,
    markPeerReady,
    areAllPeersReady,
    clearReadyState,
    initAudioLoad,
    markAudioLoaded,
    areAllPeersAudioLoaded,
    clearAudioLoad,
    addClient,
    setHost,
    isHost,
    getRoomOfClient,
    broadcast,
    destroyRoomIfEmpty
};
