package com.resonix.sync.session

import android.content.Context
import android.util.Log
import com.resonix.sync.network.SyncMessage
import com.resonix.sync.network.SyncWebSocket
import com.resonix.sync.ntp.NtpEngine
import com.resonix.sync.scheduler.PlaybackScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs

/**
 * Core session controller.
 *
 * Manages host/peer role, the NTP clock offset, scheduled command execution,
 * and periodic resync. All coroutines are scoped to this session's [SupervisorJob]
 * so that child failures don't tear down the entire session.
 *
 * **Host vs Peer:**
 * - The host sends [SyncMessage.Play], [SyncMessage.Pause], [SyncMessage.Seek] to the server.
 * - Peers only receive [SyncMessage.ScheduledPlay] / [SyncMessage.ScheduledPause] and
 *   forward them to [PlaybackScheduler].
 *
 * @param context   Application context for [SyncWebSocket] connectivity monitor.
 * @param serverUrl WebSocket server URL.
 * @param scheduler [PlaybackScheduler] that bridges scheduled commands to the ExoPlayer.
 */
internal class SyncSession(
    context: Context,
    serverUrl: String,
    private val scheduler: PlaybackScheduler,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val clientId: String = UUID.randomUUID().toString()

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)

    /**
     * Observable [SessionState] stream. Collect this to drive UI status indicators.
     */
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var currentOffsetMs: Long = 0L
    private var isHost: Boolean = false
    private var currentRoomCode: String? = null

    private val webSocket: SyncWebSocket = SyncWebSocket(
        context = context,
        serverUrl = serverUrl,
        onResync = { triggerResync() },
    )

    private val ntpEngine = NtpEngine(webSocket)

    // ── Incoming message router ───────────────────────────────────────────────

    init {
        webSocket.incoming
            .onEach { message -> handleIncoming(message) }
            .launchIn(scope)
    }

    private fun handleIncoming(message: SyncMessage) {
        when (message) {
            is SyncMessage.RoomState -> {
                Log.d(TAG, "RoomState received: room=${message.roomCode} host=${message.hostClientId}")
                isHost = message.hostClientId == clientId
            }
            is SyncMessage.ScheduledPlay -> {
                Log.d(TAG, "ScheduledPlay: executeAt=${message.serverTimeToExecute} pos=${message.trackTimeSeconds}s")
                scheduler.playAt(
                    globalTimestampMs = message.serverTimeToExecute,
                    positionMs = (message.trackTimeSeconds * 1000).toLong(),
                    ntpOffsetMs = currentOffsetMs,
                )
            }
            is SyncMessage.ScheduledPause -> {
                Log.d(TAG, "ScheduledPause: executeAt=${message.serverTimeToExecute}")
                scheduler.pauseAt(
                    globalTimestampMs = message.serverTimeToExecute,
                    ntpOffsetMs = currentOffsetMs,
                )
            }
            else -> Unit
        }
    }

    // ── Room management ───────────────────────────────────────────────────────

    /**
     * Join an existing room by its [code] and perform initial NTP calibration.
     *
     * Sets this client as a peer (non-host). The server will send a [SyncMessage.RoomState]
     * upon successful join.
     */
    fun joinRoom(code: String) {
        currentRoomCode = code
        isHost = false
        _state.value = SessionState.Connecting

        scope.launch {
            webSocket.send(SyncMessage.Join(roomCode = code, clientId = clientId))
            calibrate()
        }
    }

    /**
     * Create a new room and return its code. This client becomes the host.
     *
     * Generates a 6-character uppercase alphanumeric code locally. The server will
     * use this code as the room identifier upon receiving the [SyncMessage.Join].
     *
     * @return The generated room code.
     *
     * Note: Room code is generated client-side for MVP. In production, the server
     * should be the authority on room code generation to prevent collisions.
     */
    fun createRoom(): String {
        val code = (1..6)
            .map { ('A'..'Z').toList() + ('0'..'9').toList() }
            .map { it.random() }
            .joinToString("")
        currentRoomCode = code
        isHost = true
        _state.value = SessionState.Connecting

        scope.launch {
            webSocket.send(SyncMessage.Join(roomCode = code, clientId = clientId))
            calibrate()
        }

        return code
    }

    // ── Playback commands (host only) ─────────────────────────────────────────

    /**
     * Send a play command to the server. Peers will receive a [SyncMessage.ScheduledPlay].
     *
     * No-ops if this client is not the host.
     *
     * @param positionMs    Desired playback start position in milliseconds.
     * @param audioSource   Opaque audio source identifier.
     */
    fun play(positionMs: Long, audioSource: String) {
        if (!isHost) {
            Log.w(TAG, "play() ignored — this client is not the host")
            return
        }
        webSocket.send(SyncMessage.Play(trackTimeSeconds = positionMs / 1000.0, audioSource = audioSource))
    }

    /**
     * Send a pause command to the server. Peers will receive a [SyncMessage.ScheduledPause].
     *
     * No-ops if this client is not the host.
     *
     * @param positionMs  Current playback position at pause time in milliseconds.
     * @param audioSource Opaque audio source identifier.
     */
    fun pause(positionMs: Long, audioSource: String) {
        if (!isHost) {
            Log.w(TAG, "pause() ignored — this client is not the host")
            return
        }
        webSocket.send(SyncMessage.Pause(trackTimeSeconds = positionMs / 1000.0, audioSource = audioSource))
    }

    /**
     * Send a seek command to the server. Peers will receive a [SyncMessage.ScheduledPlay]
     * with the new position.
     *
     * No-ops if this client is not the host.
     *
     * @param positionMs  Target seek position in milliseconds.
     * @param audioSource Opaque audio source identifier.
     */
    fun seek(positionMs: Long, audioSource: String) {
        if (!isHost) {
            Log.w(TAG, "seek() ignored — this client is not the host")
            return
        }
        webSocket.send(SyncMessage.Seek(trackTimeSeconds = positionMs / 1000.0, audioSource = audioSource))
    }

    // ── Periodic resync ───────────────────────────────────────────────────────

    /**
     * Start a periodic resync loop on [Dispatchers.IO].
     *
     * Every [intervalMs], re-runs the NTP probe cycle. If the new offset differs
     * from the current one by more than 5 ms, updates internally and emits [SessionState.Drifted].
     *
     * @param intervalMs Poll interval in milliseconds (default 30,000 ms).
     */
    fun startPeriodicResync(intervalMs: Long = 30_000L) {
        scope.launch {
            while (true) {
                delay(intervalMs)
                Log.d(TAG, "Periodic resync triggered")
                calibrate(isResync = true)
            }
        }
    }

    // ── NTP calibration ───────────────────────────────────────────────────────

    private suspend fun calibrate(isResync: Boolean = false) {
        val result = ntpEngine.measure()

        if (result == null) {
            Log.e(TAG, "NTP calibration failed — no pure probes")
            return
        }

        val previousOffset = currentOffsetMs

        if (isResync) {
            val drift = abs(result.offsetMs - previousOffset)
            currentOffsetMs = result.offsetMs       // always update
            scheduler.updateOffset(result.offsetMs)  // always propagate

            if (drift > DRIFT_THRESHOLD_MS) {
                Log.w(TAG, "Clock drift detected: ${drift}ms (prev=$previousOffset new=${result.offsetMs})")
                _state.value = SessionState.Drifted(driftMs = drift)
                return
            } else {
                Log.d(TAG, "Resync OK — drift within threshold (${drift}ms)")
            }
        } else {
            currentOffsetMs = result.offsetMs
            scheduler.updateOffset(result.offsetMs)
        }

        Log.d(
            TAG,
            "Calibrated: offset=${result.offsetMs}ms rtt=${result.rttMs}ms confidence=${"%.2f".format(result.confidence)}"
        )
        _state.value = SessionState.Synced(offsetMs = currentOffsetMs)
    }

    /** Immediate resync triggered by a network change callback. */
    private fun triggerResync() {
        scope.launch { calibrate(isResync = true) }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Tear down the session: cancel all coroutines and close the WebSocket.
     * Must be called from [ResonixSync.destroy].
     */
    fun destroy() {
        scope.cancel()
        webSocket.destroy()
        _state.value = SessionState.Idle
        Log.d(TAG, "SyncSession destroyed")
    }

    companion object {
        private const val TAG = "SyncSession"

        /** Minimum drift that triggers a [SessionState.Drifted] update (ms). */
        private const val DRIFT_THRESHOLD_MS = 5L
    }
}
