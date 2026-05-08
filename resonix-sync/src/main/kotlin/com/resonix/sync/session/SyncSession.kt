package com.resonix.sync.session

import android.content.Context
import android.util.Log
import com.resonix.sync.network.SyncMessage
import com.resonix.sync.network.SyncWebSocket
import com.resonix.sync.ntp.NtpEngine
import com.resonix.sync.ntp.NtpResult
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
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
        onResync = {
            Log.d(TAG, "Network change — resetting rolling window, stale state, requesting resync")
            ntpEngine.resetWindow()
            ntpEngine.resetStale()
            // Request current room state from server after reconnect;
            // brief delay to let the WebSocket re-handshake complete first
            scope.launch {
                delay(500L)
                webSocket.send(SyncMessage.RequestSync)
                Log.d(TAG, "RequestSync sent after network recovery")
            }
        },
    )

    private val ntpEngine = NtpEngine(
        webSocket = webSocket,
        compensationMs = scheduler.hardwareLatencyMs,
        nudgeMs = 0L,
    )

    // ── Incoming message router ───────────────────────────────────────────────

    init {
        webSocket.incoming
            .onEach { message -> handleIncoming(message) }
            .launchIn(scope)

        // Watch for WebSocket reconnection — restart heartbeat if we went stale
        scope.launch {
            state.collect { sessionState ->
                if (sessionState is SessionState.Stale) {
                    Log.w(TAG, "Session stale — waiting for WebSocket reconnect to restart heartbeat")
                }
            }
        }
    }

    private fun handleIncoming(message: SyncMessage) {
        when (message) {
            is SyncMessage.RoomState -> {
                Log.d(TAG, "RoomState: room=${message.roomCode} host=${message.hostClientId} " +
                    "playing=${message.isPlaying} pos=${message.positionMs}ms")
                isHost = message.hostClientId == clientId
                // If room is playing, a PREPARE message will follow from the server
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
            is SyncMessage.Prepare -> {
                Log.d(TAG, "PREPARE received — buffering: ${message.audioSource} at ${message.trackTimeSeconds}s")
                val p = scheduler.getPlayer() ?: return
                scope.launch(Dispatchers.Main) {
                    p.seekTo((message.trackTimeSeconds * 1000).toLong())
                    p.pause()
                    waitForBufferReady(p)
                    val roomCode = currentRoomCode ?: return@launch
                    webSocket.send(SyncMessage.Ready(
                        roomCode = roomCode,
                        clientId = clientId,
                    ))
                    Log.d(TAG, "Buffer ready — sent READY to server")
                }
            }
            is SyncMessage.LoadAudio -> {
                Log.d(TAG, "LOAD_AUDIO received — pre-buffering: ${message.audioSource}")
                val p = scheduler.getPlayer() ?: return
                scope.launch(Dispatchers.Main) {
                    p.seekTo((message.trackTimeSeconds * 1000).toLong())
                    p.pause()
                    waitForBufferReady(p)
                    val roomCode = currentRoomCode ?: return@launch
                    webSocket.send(SyncMessage.AudioLoaded(
                        roomCode = roomCode,
                        clientId = clientId,
                        audioSource = message.audioSource,
                    ))
                    Log.d(TAG, "Audio loaded — sent AUDIO_LOADED to server")
                }
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
            ntpEngine.startHeartbeat(
                onResult = { result ->
                    ntpEngine.markResponseReceived()
                    applyNtpResult(result)
                },
                onConnectionStale = {
                    Log.e(TAG, "Connection stale — emitting Stale state")
                    _state.value = SessionState.Stale
                }
            )
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
            ntpEngine.startHeartbeat(
                onResult = { result ->
                    ntpEngine.markResponseReceived()
                    applyNtpResult(result)
                },
                onConnectionStale = {
                    Log.e(TAG, "Connection stale — emitting Stale state")
                    _state.value = SessionState.Stale
                }
            )
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

    // ── NTP calibration ───────────────────────────────────────────────────────

    /**
     * Apply a fresh [NtpResult] from the heartbeat.
     * Always updates offset. Only emits [SessionState.Drifted] when drift exceeds threshold.
     */
    private fun applyNtpResult(result: NtpResult) {
        // Confidence gate — ported from BeatSync's measurement quality check
        if (result.confidence < MINIMUM_CONFIDENCE) {
            Log.w(TAG, "NTP result rejected — confidence too low: ${"%.2f".format(result.confidence)} " +
                "(need >= $MINIMUM_CONFIDENCE). Keeping previous offset=$currentOffsetMs")
            return
        }

        val previousOffset = currentOffsetMs
        val drift = kotlin.math.abs(result.offsetMs - previousOffset)

        currentOffsetMs = result.offsetMs
        scheduler.updateOffset(result.offsetMs)

        if (previousOffset != 0L && drift > DRIFT_THRESHOLD_MS) {
            Log.w(TAG, "Drift detected: ${drift}ms — offset updated to ${result.offsetMs}ms")
            _state.value = SessionState.Drifted(driftMs = drift)
        } else {
            _state.value = SessionState.Synced(offsetMs = result.offsetMs)
        }

        Log.d(TAG, "NTP applied: offset=${result.offsetMs}ms bestRtt=${result.rttMs}ms avgRtt=${result.averageRttMs}ms confidence=${"%.2f".format(result.confidence)}")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Tear down the session: cancel all coroutines and close the WebSocket.
     * Must be called from [ResonixSync.destroy].
     */
    fun destroy() {
        scope.cancel()
        webSocket.destroy()
        scheduler.destroy()
        _state.value = SessionState.Idle
        Log.d(TAG, "SyncSession destroyed")
    }

    private suspend fun waitForBufferReady(player: androidx.media3.exoplayer.ExoPlayer) {
        withContext(Dispatchers.Main) {
            if (player.playbackState == androidx.media3.common.Player.STATE_READY) return@withContext
            suspendCancellableCoroutine { cont ->
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == androidx.media3.common.Player.STATE_READY && cont.isActive) {
                            player.removeListener(this)
                            cont.resume(Unit)
                        }
                    }
                }
                player.addListener(listener)
                cont.invokeOnCancellation { player.removeListener(listener) }
            }
        }
    }

    companion object {
        private const val TAG = "SyncSession"

        /** Minimum drift that triggers a [SessionState.Drifted] update (ms). */
        private const val DRIFT_THRESHOLD_MS = 5L
        private const val MINIMUM_CONFIDENCE = 0.35f  // ~6/16 pure probes minimum
    }
}
