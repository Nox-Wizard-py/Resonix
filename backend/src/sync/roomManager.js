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

module.exports = {
    rooms,
    roomSockets,
    getRoom,
    getRoomSockets,
    createRoom,
    joinRoom,
    broadcastRoom,
    removeSocketFromRoom
};
