package com.noxwizard.resonix.playback

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.noxwizard.resonix.ui.screens.PlaybackPermission
import com.noxwizard.resonix.utils.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class RemoteRoomUser(
    val userId: String,
    val username: String,
    val role: String,
    val isHost: Boolean,
    val hasTempControl: Boolean
)

data class RemoteRoomState(
    val roomCode: String,
    val hostId: String,
    val users: List<RemoteRoomUser>,
    val timingOffsetMs: Int = 0,
    val globalVolume: Float = 1f,
    val playbackPermission: PlaybackPermission = PlaybackPermission.Everyone
)

sealed class RoomEvent {
    data class JoinSuccess(val resolvedName: String) : RoomEvent()
    data class Play(val trackId: String, val positionMs: Long) : RoomEvent()
    data class Pause(val positionMs: Long) : RoomEvent()
    data class Seek(val positionMs: Long) : RoomEvent()
    data class TrackChanged(val trackId: String) : RoomEvent()
    object RoomClosed : RoomEvent()
    data class Kicked(val userId: String) : RoomEvent()
    data class HostTransferred(val newHostId: String) : RoomEvent()
    object RoomNotFound : RoomEvent()
}

/** DataStore keys for persisting active session across process restarts. */
object ListenTogetherSessionKeys {
    val ROOM_CODE    = stringPreferencesKey("lt_room_code")
    val ROLE         = stringPreferencesKey("lt_role")    // "host" | "guest"
    val USERNAME     = stringPreferencesKey("lt_username")
    val IS_ACTIVE    = booleanPreferencesKey("lt_is_active")
}

/**
 * Central manager for Listen Together room lifecycle.
 *
 * Acts as a thin coordination layer between UI/ViewModel and the two underlying
 * sources of truth:
 *  - [SocketListenTogetherRepository] — real-time WebSocket state from Node.js backend
 *  - Local auxiliary state (volume, timing nudge, permissions — not synced to backend)
 *
 * For multi-device sync, all join/leave/kick/transfer actions go through the
 * socket repository which receives `room_updated` broadcasts and updates [roomState].
 */
object ListenTogetherManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Socket repo is the live source of truth for room presence
    val roomState: StateFlow<RemoteRoomState?> = SocketListenTogetherRepository.roomState

    // Legacy local events flow (kept for ViewModel compatibility)
    private val _events = MutableSharedFlow<RoomEvent>()
    val events = _events.asSharedFlow()

    var localUserId: String = UUID.randomUUID().toString()
        private set

    /**
     * Returns true if the local user is allowed to control playback.
     * Used as a guard in PlayerConnection to block song taps for restricted guests.
     */
    fun canControlPlayback(): Boolean {
        val state = roomState.value ?: return true // Not in a room → full control
        val user = state.users.find { it.userId == localUserId } ?: return false // In a room but user not found → block
        val isHost = user.isHost
        val isSudo = user.hasTempControl
        val isEveryoneAllowed = state.playbackPermission == PlaybackPermission.Everyone
        val result = isHost || isSudo || isEveryoneAllowed
        val roleLabel = when {
            isHost -> "host"
            isSudo -> "sudo"
            else   -> "guest"
        }
        android.util.Log.d("AUTH_UI", "[canControlPlayback] role=$roleLabel canControl=$result permission=${state.playbackPermission}")
        return result
    }

    private var _serverUrl: String = "wss://resonix-0pvb.onrender.com/ws"

    /** Call from Application.onCreate to configure the server URL. */
    fun init(serverUrl: String) {
        _serverUrl = serverUrl
        // Forward socket nav events to local events flow for ViewModel consumption
        scope.launch {
            SocketListenTogetherRepository.navigationEvents.collect { event ->
                _events.emit(event)
            }
        }
    }

    // ── Session persistence ────────────────────────────────────────────────────

    fun persistSession(context: Context, roomCode: String, role: String, username: String) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[ListenTogetherSessionKeys.ROOM_CODE] = roomCode
                prefs[ListenTogetherSessionKeys.ROLE]      = role
                prefs[ListenTogetherSessionKeys.USERNAME]  = username
                prefs[ListenTogetherSessionKeys.IS_ACTIVE] = true
            }
        }
    }

    fun clearSession(context: Context) {
        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[ListenTogetherSessionKeys.IS_ACTIVE] = false
                prefs[ListenTogetherSessionKeys.ROOM_CODE] = ""
                prefs[ListenTogetherSessionKeys.ROLE]      = ""
                prefs[ListenTogetherSessionKeys.USERNAME]  = ""
            }
        }
    }

    // ── Room management (delegates to socket) ─────────────────────────────────

    private fun generateRoomCode(): String {
        val chars = ('A'..'Z') + ('0'..'9')
        val suffix = (1..5).map { chars.random() }.joinToString("")
        return "HA$suffix"
    }

    /**
     * Create a room: generate a room code, connect socket, send create_room.
     * Navigation fires only after the backend sends join_success.
     */
    fun createRoom(username: String): String {
        val code = generateRoomCode()
        SocketListenTogetherRepository.connect(_serverUrl)
        SocketListenTogetherRepository.createRoom(code, username)
        return code
    }

    /**
     * Join an existing room via socket. Returns the username (may be resolved by server).
     * The actual resolved name flows through [SocketListenTogetherRepository.resolvedUsername]
     * after the server responds with `join_success`.
     */
    fun joinRoom(roomCode: String, username: String): String {
        SocketListenTogetherRepository.connect(_serverUrl)
        SocketListenTogetherRepository.joinRoom(roomCode, username)
        return username // server-resolved name is async; snackbar shown after join_success
    }

    fun leaveRoom() {
        // Send leave message first, then close socket after a brief delay so the
        // server's leave_room handler fires BEFORE it sees the socket close event.
        SocketListenTogetherRepository.leaveRoom()
        scope.launch {
            kotlinx.coroutines.delay(500)
            SocketListenTogetherRepository.disconnect()
        }
        scope.launch { _events.emit(RoomEvent.RoomClosed) }
    }

    fun kickUser(userId: String) {
        SocketListenTogetherRepository.kickUser(userId)
    }

    fun transferHost(newHostId: String) {
        SocketListenTogetherRepository.transferHost(newHostId)
    }

    fun grantSudo(userId: String) {
        SocketListenTogetherRepository.grantSudo(userId)
    }

    // ── Local-only settings (not broadcast to server) ─────────────────────────

    private val _localState = MutableStateFlow<RemoteRoomState?>(null)

    fun updateGlobalVolume(volume: Float) {
        SocketListenTogetherRepository.sendRoomSettings(globalVolume = volume)
    }

    fun updateTimingNudge(nudgeMs: Int) {
        val current = SocketListenTogetherRepository.roomState.value?.timingOffsetMs ?: 0
        SocketListenTogetherRepository.sendRoomSettings(timingOffsetMs = current + nudgeMs)
    }

    fun setPlaybackPermission(mode: String) {
        SocketListenTogetherRepository.setPlaybackPermission(mode)
    }

    // ── Playback sync (host/temp-control sends; guests receive via SharedFlow) ─

    fun broadcastPlay(trackId: String, positionMs: Long) {
        SocketListenTogetherRepository.sendPlaybackSync("play", trackId, positionMs)
        // Also emit locally so host ViewModel picks it up if needed
        scope.launch { _events.emit(RoomEvent.Play(trackId, positionMs)) }
    }

    fun broadcastPause(positionMs: Long) {
        SocketListenTogetherRepository.sendPlaybackSync("pause", "", positionMs)
        scope.launch { _events.emit(RoomEvent.Pause(positionMs)) }
    }

    fun broadcastSeek(positionMs: Long) {
        SocketListenTogetherRepository.sendPlaybackSync("seek", "", positionMs)
        scope.launch { _events.emit(RoomEvent.Seek(positionMs)) }
    }

    // ── Legacy helper (kept for QR scanner dup-resolve UX) ───────────────────

    fun resolveDuplicateName(base: String, existing: List<String>): String {
        if (base !in existing) return base
        var index = 2
        var candidate = "$base ($index)"
        while (candidate in existing) {
            index++
            candidate = "$base ($index)"
        }
        return candidate
    }
}
