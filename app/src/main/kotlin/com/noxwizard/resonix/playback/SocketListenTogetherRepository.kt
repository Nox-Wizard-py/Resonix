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

/** A single item in the shared Listen Together queue. */
data class SharedQueueItem(
    val trackId: String,
    val url: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String
)

/** Playback events received from the server on the guest side. */
sealed class PlaybackEvent {
    data class TrackReady(val userId: String, val trackId: String) : PlaybackEvent()
    data class PlayAt(val startTime: Long) : PlaybackEvent()
    data class PauseAt(val positionMs: Long, val timestamp: Long) : PlaybackEvent()
    data class Play(val trackId: String, val positionMs: Long, val serverTimeToExecute: Long) : PlaybackEvent()
    data class Pause(val positionMs: Long, val serverTimeToExecute: Long) : PlaybackEvent()
    data class Seek(val positionMs: Long, val timestamp: Long) : PlaybackEvent()
    data class TrackChange(val trackId: String, val url: String, val title: String, val artist: String, val thumbnailUrl: String, val startAt: Long) : PlaybackEvent()
    /** Host → guests: current playback position for drift correction. */
    data class SyncUpdate(val positionMs: Long, val serverTimeToExecute: Long) : PlaybackEvent()
    /** Server → late-joining guest: catch up to current room playback position. */
    data class SyncSnapshot(
        val trackId: String,
        val url: String,
        val positionMs: Long,        // track position AT serverTimeToExecute
        val serverTimeToExecute: Long, // server clock time to start playback
        val serverTime: Long,         // server clock at send time (for NTP correction)
        val isPlaying: Boolean,
        val title: String = "",
        val artist: String = "",
        val thumbnailUrl: String = ""
    ) : PlaybackEvent()
    object Stop : PlaybackEvent()
    data class QueueUpdate(val queue: List<SharedQueueItem>) : PlaybackEvent()
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

    private val _playbackEvents = MutableSharedFlow<PlaybackEvent>(replay = 1, extraBufferCapacity = 8)
    val playbackEvents: SharedFlow<PlaybackEvent> = _playbackEvents.asSharedFlow()

    // Dedicated replay-1 flow for SyncSnapshot — guarantees late-joining MusicService gets it
    private val _syncSnapshotEvent = MutableSharedFlow<PlaybackEvent.SyncSnapshot>(replay = 1, extraBufferCapacity = 2)
    val syncSnapshotEvent: SharedFlow<PlaybackEvent.SyncSnapshot> = _syncSnapshotEvent.asSharedFlow()

    private val _navigationEvents = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 4)
    val navigationEvents: SharedFlow<RoomEvent> = _navigationEvents.asSharedFlow()

    private val _sharedQueue = MutableStateFlow<List<SharedQueueItem>>(emptyList())
    val sharedQueue: StateFlow<List<SharedQueueItem>> = _sharedQueue.asStateFlow()

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
        PlaybackSyncCoordinator.wsState.value = "connecting"
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

    fun sendTrackChange(trackId: String, url: String, title: String, artist: String, thumbnailUrl: String, startAt: Long) {
        send(JSONObject().apply {
            put("type", "track_change")
            put("trackId", trackId)
            put("url", url)
            put("title", title)
            put("artist", artist)
            put("thumbnailUrl", thumbnailUrl)
            put("startAt", startAt)
        })
    }

    fun sendSeek(position: Long) {
        send(JSONObject().apply {
            put("type", "seek")
            put("position", position)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun sendTrackReady(trackId: String) {
        send(JSONObject().apply {
            put("type", "track_ready")
            put("trackId", trackId)
        })
    }

    fun sendPlayAt(startTime: Long) {
        send(JSONObject().apply {
            put("type", "play_at")
            put("startTime", startTime)
        })
    }

    fun sendStop() {
        send(JSONObject().apply { put("type", "stop") })
    }

    /**
     * NTP probe — server echoes t0, t1 (receive), t2 (send) back as ntp_response.
     * Only meaningful when backend supports the ntp_probe handler.
     */
    fun sendNtpProbe(t0: Long) {
        send(JSONObject().apply {
            put("type", "ntp_probe")
            put("t0", t0)
        })
    }

    /**
     * Host → server → guests: broadcast current playback position for drift correction.
     * positionMs is the host's current ExoPlayer position.
     */
    fun sendSyncUpdate(positionMs: Long) {
        send(JSONObject().apply {
            put("type", "sync_update")
            put("positionMs", positionMs)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun sendQueueUpdate(queue: List<SharedQueueItem>) {
        val arr = org.json.JSONArray()
        queue.forEach { item ->
            arr.put(JSONObject().apply {
                put("trackId", item.trackId)
                put("url", item.url)
                put("title", item.title)
                put("artist", item.artist)
                put("thumbnailUrl", item.thumbnailUrl)
            })
        }
        send(JSONObject().apply {
            put("type", "queue_update")
            put("queue", arr)
        })
    }

    fun sendPauseAt(position: Long) {
        send(JSONObject().apply {
            put("type", "pause_at")
            put("position", position)
            put("timestamp", System.currentTimeMillis())
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
            PlaybackSyncCoordinator.wsState.value = "open"
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
            PlaybackSyncCoordinator.wsState.value = "error"
            Log.w(TAG, "WebSocket failure: ${t.message}")
            // Only auto-reconnect if not intentionally disconnected and we had a room going
            if (!intentionalDisconnect.get() && lastRoomCode.isNotEmpty() && lastServerUrl.isNotEmpty()) {
                Log.d(TAG, "Unexpected disconnect — scheduling reconnect in 3s")
                scope.launch {
                    kotlinx.coroutines.delay(3000)
                    if (!intentionalDisconnect.get()) {
                        Log.d(TAG, "Reconnecting to $lastServerUrl, room=$lastRoomCode")
                        isReconnecting.set(true)
                        PlaybackSyncCoordinator.wsState.value = "connecting"
                        val request = okhttp3.Request.Builder().url(lastServerUrl).build()
                        socket = client.newWebSocket(request, this@SocketListenTogetherRepository.listener)
                    }
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected.set(false)
            PlaybackSyncCoordinator.wsState.value = "closed"
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

                "track_change" -> {
                    val event = PlaybackEvent.TrackChange(
                        trackId = obj.optString("trackId", ""),
                        url = obj.optString("url", ""),
                        title = obj.optString("title", ""),
                        artist = obj.optString("artist", ""),
                        thumbnailUrl = obj.optString("thumbnailUrl", ""),
                        startAt = obj.optLong("startAt", System.currentTimeMillis() + 2000)
                    )
                    Log.d(TAG, "RECEIVE: track_change -> $event")
                    scope.launch { _playbackEvents.emit(event) }
                }

                "seek" -> {
                    val event = PlaybackEvent.Seek(
                        positionMs = obj.optLong("position", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    Log.d(TAG, "RECEIVE: seek -> $event")
                    scope.launch { _playbackEvents.emit(event) }
                }

                "track_ready" -> {
                    val event = PlaybackEvent.TrackReady(
                        userId = obj.optString("userId", ""),
                        trackId = obj.optString("trackId", "")
                    )
                    Log.d(TAG, "RECEIVE: track_ready -> $event")
                    scope.launch { _playbackEvents.emit(event) }
                }

                "play_at" -> {
                    val event = PlaybackEvent.PlayAt(
                        startTime = obj.optLong("startTime", 0L)
                    )
                    Log.d(TAG, "RECEIVE: play_at -> $event")
                    scope.launch { _playbackEvents.emit(event) }
                }

                "pause_at" -> {
                    val event = PlaybackEvent.PauseAt(
                        positionMs = obj.optLong("position", 0L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                    Log.d(TAG, "RECEIVE: pause_at -> $event")
                    scope.launch { _playbackEvents.emit(event) }
                }

                "ntp_response" -> {
                    val t0 = obj.optLong("t0", 0L)
                    val t1 = obj.optLong("t1", 0L)
                    val t2 = obj.optLong("t2", 0L)
                    PlaybackSyncCoordinator.onNtpResponse(t0, t1, t2)
                }

                "sync_update" -> {
                    val positionMs = obj.optLong("positionMs", 0L)
                    val serverTimeToExecute = obj.optLong("serverTimeToExecute", System.currentTimeMillis())
                    Log.d(TAG, "RECEIVE: sync_update positionMs=$positionMs")
                    scope.launch {
                        _playbackEvents.emit(PlaybackEvent.SyncUpdate(positionMs, serverTimeToExecute))
                    }
                }

                "sync_snapshot" -> {
                    val trackId   = obj.optString("trackId", "")
                    val url       = obj.optString("url", "")

                    if (trackId.isBlank() || url.isBlank()) {
                        Log.w(TAG, "SYNC_DEBUG: Invalid snapshot: trackId or url missing")
                        return
                    }

                    val positionMs = obj.optLong("positionMs", 0L)
                    val serverTimeToExecute = obj.optLong("serverTimeToExecute", System.currentTimeMillis() + 2000L)
                    val serverTime = obj.optLong("serverTime", System.currentTimeMillis())
                    val isPlaying  = obj.optBoolean("isPlaying", false)
                    val title = obj.optString("title", "")
                    val artist = obj.optString("artist", "")
                    val thumbnailUrl = obj.optString("thumbnailUrl", "")
                    Log.d(TAG, "RECEIVE: sync_snapshot trackId=$trackId pos=$positionMs execAt=$serverTimeToExecute isPlaying=$isPlaying")
                    val snapshot = PlaybackEvent.SyncSnapshot(trackId, url, positionMs, serverTimeToExecute, serverTime, isPlaying, title, artist, thumbnailUrl)
                    scope.launch {
                        _syncSnapshotEvent.emit(snapshot)   // replay=1 — MusicService never misses it
                        _playbackEvents.emit(snapshot)
                    }
                }

                "stop" -> {
                    Log.d(TAG, "RECEIVE: stop")
                    _sharedQueue.value = emptyList()
                    scope.launch { _playbackEvents.emit(PlaybackEvent.Stop) }
                }

                "queue_update" -> {
                    val arr = obj.optJSONArray("queue") ?: org.json.JSONArray()
                    val newQueue = (0 until arr.length()).map { i ->
                        val item = arr.getJSONObject(i)
                        SharedQueueItem(
                            trackId = item.optString("trackId", ""),
                            url = item.optString("url", ""),
                            title = item.optString("title", ""),
                            artist = item.optString("artist", ""),
                            thumbnailUrl = item.optString("thumbnailUrl", "")
                        )
                    }
                    if (newQueue != _sharedQueue.value) {
                        _sharedQueue.value = newQueue
                        Log.d(TAG, "RECEIVE: queue_update -> ${newQueue.size} tracks")
                        scope.launch { _playbackEvents.emit(PlaybackEvent.QueueUpdate(newQueue)) }
                    }
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
