package com.noxwizard.resonix.playback

import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "SyncCoord"

/**
 * NTP-style clock-offset estimator and drift corrector for Listen Together.
 *
 * RFC 5905 min-RTT offset selection:
 *   clockOffset = offset from the probe with the lowest observed RTT.
 *   (Queuing delays can only ADD to RTT; min-RTT → least contamination.)
 *
 * Drift correction guards:
 *   - Only corrects when |drift| > 200 ms  (avoids jitter on tiny swings)
 *   - Only corrects when smoothedRtt < 300 ms (unstable network → skip)
 *   - 1 500 ms cooldown between corrections (prevents overcorrection loops)
 */
object PlaybackSyncCoordinator {

    // ── NTP config ──────────────────────────────────────────────────────────
    private const val INITIAL_PROBE_INTERVAL_MS = 500L
    private const val STEADY_PROBE_INTERVAL_MS  = 3000L
    private const val INITIAL_PROBE_COUNT       = 8
    private const val EWMA_ALPHA                = 0.2f
    private const val WINDOW_SIZE               = 16

    // ── Drift correction config ─────────────────────────────────────────────
    const val DRIFT_THRESHOLD_MS    = 200L   // seek only if drift exceeds this
    const val MAX_RTT_FOR_CORRECTION = 300L  // skip correction on unstable networks
    private const val CORRECTION_COOLDOWN_MS = 1500L

    // ── NTP sample ───────────────────────────────────────────────────────────
    private data class Sample(val offset: Long, val rtt: Long)

    // ── State ────────────────────────────────────────────────────────────────
    @Volatile var clockOffsetMs: Long = 0L
        private set

    @Volatile var smoothedRttMs: Long = 0L
        private set

    @Volatile var rawRttMs: Long = 0L
        private set

    private val _rttFlow = MutableStateFlow(0L)
    val rttFlow: StateFlow<Long> = _rttFlow

    private val _offsetFlow = MutableStateFlow(0L)
    val offsetFlow: StateFlow<Long> = _offsetFlow

    private val _pairsSent = MutableStateFlow(0)
    val pairsSent: StateFlow<Int> = _pairsSent

    private val _pureCount = MutableStateFlow(0)
    val pureCount: StateFlow<Int> = _pureCount

    private val _impureCount = MutableStateFlow(0)
    val impureCount: StateFlow<Int> = _impureCount

    private val _measurementCount = MutableStateFlow(0)
    val measurementCount: StateFlow<Int> = _measurementCount

    val wsState: MutableStateFlow<String> = MutableStateFlow("connecting")
    val audioState: MutableStateFlow<String> = MutableStateFlow("loading")

    @Volatile var isSynced: Boolean = false
        private set

    private var probeCount = 0
    private val samples = ArrayDeque<Sample>(WINDOW_SIZE)
    private var lastCorrectionTime = 0L

    private var probeJob: Job? = null
    private var scope: CoroutineScope? = null
    private var playerProvider: (() -> ExoPlayer)? = null
    private var isPlayingProvider: (() -> Boolean)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Safe to call multiple times — replaces provider refs without restarting probing.
     */
    fun init(
        coroutineScope: CoroutineScope,
        playerProvider: () -> ExoPlayer,
        isPlayingProvider: () -> Boolean,
    ) {
        scope = coroutineScope
        this.playerProvider = playerProvider
        this.isPlayingProvider = isPlayingProvider
    }

    /** Start clock probing when joining a room. */
    fun startProbing() {
        probeJob?.cancel()
        reset()
        probeJob = scope?.launch(Dispatchers.IO) {
            while (isActive) {
                sendNtpProbe()
                val interval = if (probeCount < INITIAL_PROBE_COUNT)
                    INITIAL_PROBE_INTERVAL_MS
                else
                    STEADY_PROBE_INTERVAL_MS
                delay(interval)
            }
        }
    }

    /** Stop probing and clear all state when leaving a room. */
    fun stop() {
        probeJob?.cancel()
        probeJob = null
        reset()
    }

    private fun reset() {
        probeCount = 0
        samples.clear()
        clockOffsetMs = 0L
        smoothedRttMs = 0L
        rawRttMs = 0L
        isSynced = false
        lastCorrectionTime = 0L
        _rttFlow.value = 0L
        _offsetFlow.value = 0L
        _pairsSent.value = 0
        _pureCount.value = 0
        _impureCount.value = 0
        _measurementCount.value = 0
        wsState.value = "connecting"
        audioState.value = "loading"
    }

    // ── NTP probe exchange ────────────────────────────────────────────────────

    private fun sendNtpProbe() {
        val t0 = System.currentTimeMillis()
        _pairsSent.value++
        SocketListenTogetherRepository.sendNtpProbe(t0)
    }

    /**
     * Feed a server NTP response into the estimator.
     *
     * @param t0  Client send time (echoed by server)
     * @param t1  Server receive time
     * @param t2  Server send time
     */
    fun onNtpResponse(t0: Long, t1: Long, t2: Long) {
        val t3 = System.currentTimeMillis()
        val rtt    = (t3 - t0) - (t2 - t1)
        rawRttMs = rtt
        val offset = ((t1 - t0) + (t2 - t3)) / 2

        if (rtt < 0) return // impossible — likely clock jump; discard

        // EWMA for RTT display/guard
        val previousRtt = _rttFlow.value
        val newRtt = if (previousRtt == 0L) rtt else (previousRtt * 0.8 + rtt * 0.2).toLong()
        smoothedRttMs = newRtt
        _rttFlow.value = newRtt

        val previousOffset = _offsetFlow.value
        val newOffset = if (previousOffset == 0L) offset else (previousOffset * 0.8 + offset * 0.2).toLong()
        _offsetFlow.value = newOffset

        Log.d("RTT_DEBUG", "raw=$rtt smoothed=$smoothedRttMs")

        // FIX 1: min-RTT offset selection via sliding Sample window
        samples.addLast(Sample(offset = offset, rtt = rtt))
        if (samples.size > WINDOW_SIZE) samples.removeFirst()

        val best = samples.minByOrNull { it.rtt }
        clockOffsetMs = best?.offset ?: offset
        
        _measurementCount.value = samples.size
        
        // Count pure vs impure based on whether this sample's offset matches the "best" offset
        if (clockOffsetMs == offset) {
            _pureCount.value++
        } else {
            _impureCount.value++
        }

        probeCount++

        if (!isSynced && probeCount >= INITIAL_PROBE_COUNT) {
            isSynced = true
            Log.d(TAG, "Clock synced | offset=${clockOffsetMs}ms smoothedRtt=${smoothedRttMs}ms after $probeCount probes")
        }

        Log.d(TAG, "NTP #$probeCount rtt=${rtt}ms offset=${offset}ms best=${clockOffsetMs}ms")
    }

    // ── Scheduling helpers ────────────────────────────────────────────────────

    /**
     * Milliseconds until [serverTimeToExecute] from the guest's perspective.
     * Negative → already past; caller should seek forward by `abs(value)`.
     */
    fun estimatedWaitMs(serverTimeToExecute: Long): Long {
        val estimatedServerNow = System.currentTimeMillis() + clockOffsetMs
        return serverTimeToExecute - estimatedServerNow
    }

    /**
     * Project [positionMs] (valid at server time [eventTimestampMs]) forward to now.
     * Used for seek / pause / sync-snapshot events that carry a server-epoch timestamp.
     */
    fun correctedPosition(positionMs: Long, eventTimestampMs: Long): Long {
        val estimatedServerNow = System.currentTimeMillis() + clockOffsetMs
        val elapsed = estimatedServerNow - eventTimestampMs
        return (positionMs + elapsed).coerceAtLeast(0L)
    }

    // ── Drift correction ──────────────────────────────────────────────────────

    /**
     * Called by PlayerConnection on `sync_update` receipt (guest only).
     *
     * Guards applied before seeking:
     *   1. |drift| must exceed [DRIFT_THRESHOLD_MS] (200 ms)
     *   2. [smoothedRttMs] must be below [MAX_RTT_FOR_CORRECTION] (300 ms)
     *   3. At least [CORRECTION_COOLDOWN_MS] must have elapsed since the last correction
     *
     * @param expectedPositionMs  Host position valid at [serverTimeToExecute]
     * @param serverTimeToExecute Server-absolute time stamp for the position
     */
    fun applyDriftCorrection(expectedPositionMs: Long, serverTimeToExecute: Long) {
        val player    = playerProvider?.invoke() ?: return
        val isPlaying = isPlayingProvider?.invoke() ?: return
        if (!isPlaying) return

        // Project expected position to "right now"
        val waitMs      = estimatedWaitMs(serverTimeToExecute)
        val expectedNow = (expectedPositionMs - waitMs).coerceAtLeast(0L)
        val actualNow   = player.currentPosition
        val drift       = expectedNow - actualNow

        Log.d(TAG, "Drift: expected=${expectedNow}ms actual=${actualNow}ms drift=${drift}ms rtt=${smoothedRttMs}ms")

        val now = System.currentTimeMillis()
        val cooldownOk  = now - lastCorrectionTime > CORRECTION_COOLDOWN_MS
        val driftLarge  = Math.abs(drift) > DRIFT_THRESHOLD_MS
        val networkOk   = smoothedRttMs < MAX_RTT_FOR_CORRECTION

        if (cooldownOk && driftLarge && networkOk) {
            Log.i(TAG, "Correcting drift ${drift}ms → seek to ${expectedNow}ms")
            lastCorrectionTime = now
            scope?.launch(Dispatchers.Main) {
                player.seekTo(expectedNow)
            }
        }
    }
}
