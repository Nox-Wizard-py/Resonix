package com.noxwizard.resonix.playback

import android.util.Log
import com.noxwizard.resonix.ui.screens.PlaybackPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "SocketLTRepo"

/** Playback events received from the server on the guest side. */
sealed class PlaybackEvent {
    data class Play(val trackId: String, val positionMs: Long, val serverTimeToExecute: Long) : PlaybackEvent()
    data class Pause(val positionMs: Long, val serverTimeToExecute: Long) : PlaybackEvent()
    data class Seek(val positionMs: Long, val serverTimeToExecute: Long) : PlaybackEvent()
}

/**
 * Real-time room repository backed by the Node.js WebSocket backend at /ws.
 *
 * Exposes:
 *  - [roomState] — live StateFlow of room presence from `room_updated` events
 *  - [playbackEvents] — SharedFlow of playback commands from `playback_sync` events
 *  - [navigationEvents] — SharedFlow of room lifecycle events (kicked, closed)
 *
 * All socket events use lowercase snake_case matching the updated syncHandler.js contract.
 */
object SocketListenTogetherRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Exposed state ─────────────────────────────────────────────────────────

    private val _roomState = MutableStateFlow<RemoteRoomState?>(null)
    val roomState: StateFlow<RemoteRoomState?> = _roomState.asStateFlow()

    private val _playbackEvents = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    val playbackEvents: SharedFlow<PlaybackEvent> = _playbackEvents.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 4)
    val navigationEvents: SharedFlow<RoomEvent> = _navigationEvents.asSharedFlow()

    // Local metadata the server doesn't store
    var timingOffsetMs: Int = 0
    var globalVolume: Float = 1f
    var playbackPermission: PlaybackPermission = PlaybackPermission.Everyone

    // The resolved username returned by the server on join_success
    var resolvedUsername: String = ""

    // ── Socket internals ──────────────────────────────────────────────────────

    private var socket: WebSocket? = null
    private val destroyed = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val intentionalDisconnect = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)

    // Stored for auto-reconnect after backgrounding
    private var lastServerUrl: String = ""
    private var lastRoomCode: String = ""
    private var lastUsername: String = ""

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    // ── Connection ────────────────────────────────────────────────────────────

    fun connect(serverUrl: String) {
        if (connected.get()) return
        intentionalDisconnect.set(false)
        isReconnecting.set(false)
        destroyed.set(false)
        lastServerUrl = serverUrl
        val request = Request.Builder().url(serverUrl).build()
        socket = client.newWebSocket(request, listener)
        Log.d(TAG, "Connecting to $serverUrl")
    }

    fun disconnect() {
        intentionalDisconnect.set(true)
        socket?.close(1000, "room left")
        socket = null
        connected.set(false)
        _roomState.value = null
        lastRoomCode = ""
        lastUsername = ""
    }

    // ── Outgoing events ───────────────────────────────────────────────────────

    fun joinRoom(roomCode: String, username: String) {
        lastRoomCode = roomCode
        lastUsername = username
        send(JSONObject().apply {
            put("type", "join_room")
            put("roomCode", roomCode)
            put("username", username)
            put("userId", ListenTogetherManager.localUserId)
        })
    }

    fun createRoom(roomCode: String, username: String) {
        lastRoomCode = roomCode
        lastUsername = username
        send(JSONObject().apply {
            put("type", "create_room")
            put("roomCode", roomCode)
            put("username", username)
            put("userId", ListenTogetherManager.localUserId)
        })
    }

    fun leaveRoom() {
        intentionalDisconnect.set(true)
        send(JSONObject().apply { put("type", "leave_room") })
        _roomState.value = null
    }

    fun transferHost(targetUserId: String) {
        val roomCode = _roomState.value?.roomCode ?: return
        Log.d(TAG, "SEND: transfer_host -> $targetUserId")
        send(JSONObject().apply {
            put("type", "transfer_host")
            put("roomCode", roomCode)
            put("targetUserId", targetUserId)
        })
    }

    fun kickUser(targetUserId: String) {
        val roomCode = _roomState.value?.roomCode ?: return
        Log.d(TAG, "SEND: kick_user -> $targetUserId")
        send(JSONObject().apply {
            put("type", "kick_user")
            put("roomCode", roomCode)
            put("targetUserId", targetUserId)
        })
    }

    fun grantSudo(targetUserId: String) {
        val roomCode = _roomState.value?.roomCode ?: return
        Log.d(TAG, "SEND: grant_sudo -> $targetUserId")
        send(JSONObject().apply {
            put("type", "grant_sudo")
            put("roomCode", roomCode)
            put("targetUserId", targetUserId)
        })
    }

    fun sendPlaybackSync(event: String, trackId: String, positionMs: Long) {
        send(JSONObject().apply {
            put("type", "playback_sync")
            put("playbackEvent", event)
            put("trackId", trackId)
            put("positionMs", positionMs)
        })
    }

    fun sendRoomSettings(globalVolume: Float? = null, timingOffsetMs: Int? = null) {
        send(JSONObject().apply {
            put("type", "update_room_settings")
            globalVolume?.let { put("globalVolume", it.toDouble()) }
            timingOffsetMs?.let { put("timingOffsetMs", it) }
        })
    }

    fun setPlaybackPermission(mode: String) {
        val roomCode = _roomState.value?.roomCode ?: return
        Log.d(TAG, "SEND: set_playback_permission -> $mode")
        send(JSONObject().apply {
            put("type", "set_playback_permission")
            put("roomCode", roomCode)
            put("mode", mode)
        })
    }

    private fun send(obj: JSONObject) {
        val ws = socket ?: run {
            Log.w(TAG, "send() called but socket is null — message dropped: $obj")
            return
        }
        ws.send(obj.toString())
    }

    // ── Incoming message router ───────────────────────────────────────────────

    private val listener: WebSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected.set(true)
            Log.d(TAG, "WebSocket open")
            // Auto-rejoin ONLY if this was an unexpected reconnect (not a fresh join)
            if (isReconnecting.get() && !intentionalDisconnect.get() && lastRoomCode.isNotEmpty() && lastUsername.isNotEmpty()) {
                Log.d(TAG, "Reconnected — rejoining $lastRoomCode as $lastUsername")
                joinRoom(lastRoomCode, lastUsername)
            }
            // Once connected, clear the reconnecting flag
            isReconnecting.set(false)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected.set(false)
            Log.w(TAG, "WebSocket failure: ${t.message}")
            // Only auto-reconnect if not intentionally disconnected and we had a room going
            if (!intentionalDisconnect.get() && lastRoomCode.isNotEmpty() && lastServerUrl.isNotEmpty()) {
                Log.d(TAG, "Unexpected disconnect — scheduling reconnect in 3s")
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    if (!intentionalDisconnect.get()) {
                        Log.d(TAG, "Reconnecting to $lastServerUrl, room=$lastRoomCode")
                        isReconnecting.set(true)
                        val request = okhttp3.Request.Builder().url(lastServerUrl).build()
                        socket = client.newWebSocket(request, this@SocketListenTogetherRepository.listener)
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected.set(false)
            Log.d(TAG, "WebSocket closed: $code $reason")
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val obj = JSONObject(raw)
            when (obj.getString("type")) {

                "join_success" -> {
                    resolvedUsername = obj.optString("resolvedName", "")
                    Log.d(TAG, "join_success resolvedName=$resolvedUsername")
                    scope.launch { _navigationEvents.emit(RoomEvent.JoinSuccess(resolvedUsername)) }
                }

                "room_updated" -> {
                    val stateObj = obj.getJSONObject("state")
                    val newState = parseRoomState(stateObj)
                    _roomState.value = newState
                    Log.d(TAG, "room_updated → ${newState.users.size} users in ${newState.roomCode}")
                }

                "playback_sync" -> {
                    val event = obj.optString("playbackEvent")
                    val trackId = obj.optString("trackId", "")
                    val positionMs = obj.optLong("positionMs", 0L)
                    val serverTime = obj.optLong("serverTimeToExecute", System.currentTimeMillis())
                    val pe: PlaybackEvent = when (event) {
                        "pause" -> PlaybackEvent.Pause(positionMs, serverTime)
                        "seek"  -> PlaybackEvent.Seek(positionMs, serverTime)
                        else    -> PlaybackEvent.Play(trackId, positionMs, serverTime)
                    }
                    scope.launch { _playbackEvents.emit(pe) }
                }

                "room_closed" -> {
                    _roomState.value = null
                    scope.launch { _navigationEvents.emit(RoomEvent.RoomClosed) }
                }

                "host_transferred" -> {
                    val newHostId = obj.optString("newHostId", "")
                    scope.launch { _navigationEvents.emit(RoomEvent.HostTransferred(newHostId)) }
                }

                "kicked" -> {
                    val localId = ListenTogetherManager.localUserId
                    _roomState.value = null
                    scope.launch { _navigationEvents.emit(RoomEvent.Kicked(localId)) }
                }

                "error" -> {
                    val code = obj.optString("code")
                    if (code == "ROOM_NOT_FOUND") {
                        Log.e(TAG, "Room not found on server")
                        scope.launch { _navigationEvents.emit(RoomEvent.RoomNotFound) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message: ${e.message} raw=$raw")
        }
    }

    // ── Parsing helper ────────────────────────────────────────────────────────

    private fun parseRoomState(obj: JSONObject): RemoteRoomState {
        val roomCode = obj.optString("code", obj.optString("roomCode", ""))
        val globalVolume = obj.optDouble("globalVolume", 1.0).toFloat()
        val timingOffsetMs = obj.optInt("timingOffsetMs", 0)
        val playbackPermissionStr = obj.optString("playbackPermission", "Everyone")
        val playbackPermission = when (playbackPermissionStr.lowercase()) {
            "host_only" -> com.noxwizard.resonix.ui.screens.PlaybackPermission.HostOnly
            "everyone" -> com.noxwizard.resonix.ui.screens.PlaybackPermission.Everyone
            else -> {
                try {
                    com.noxwizard.resonix.ui.screens.PlaybackPermission.valueOf(playbackPermissionStr)
                } catch (e: IllegalArgumentException) {
                    com.noxwizard.resonix.ui.screens.PlaybackPermission.Everyone
                }
            }
        }
        val usersArray: JSONArray = obj.optJSONArray("users") ?: JSONArray()
        val users = (0 until usersArray.length()).map { i ->
            val u = usersArray.getJSONObject(i)
            val role = u.optString("role", "guest")
            RemoteRoomUser(
                userId = u.optString("id", ""),
                username = u.optString("name", "Unknown").ifEmpty { "Unknown" },
                role = role,
                isHost = role == "host",
                hasTempControl = role == "sudo" || u.optBoolean("hasTempControl", false)
            )
        }
        val hostId = users.find { it.isHost }?.userId ?: ""
        return RemoteRoomState(
            roomCode = roomCode,
            hostId = hostId,
            users = users,
            timingOffsetMs = timingOffsetMs,
            globalVolume = globalVolume,
            playbackPermission = playbackPermission
        )
    }
}
