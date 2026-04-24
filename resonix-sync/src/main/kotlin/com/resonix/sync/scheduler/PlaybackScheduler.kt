package com.resonix.sync.scheduler

import android.os.SystemClock
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Precision playback scheduler that bridges global server-time commands to ExoPlayer.
 *
 * BeatSync's `calculateWaitTimeMilliseconds` approach:
 * ```
 * waitMs = max(0, serverTimeToExecute - (epochNow() + clockOffset))
 * ```
 * is ported here using [SystemClock.elapsedRealtimeNanos] for the coroutine spin-wait,
 * combined with [System.currentTimeMillis] for computing the initial coarse delay.
 *
 * **Scheduling algorithm:**
 * 1. Compute a coarse delay from the NTP-adjusted current time.
 * 2. Suspend the coroutine for `(coarseDelay - FINE_SPIN_THRESHOLD_MS)`.
 * 3. Spin in a tight nano-loop for the final [FINE_SPIN_THRESHOLD_MS] to achieve
 *    sub-millisecond firing precision against `SystemClock.elapsedRealtimeNanos()`.
 * 4. Call [ExoPlayer.seekTo] + [ExoPlayer.play] (or [ExoPlayer.pause]) on the main thread.
 *
 * @param player Initial ExoPlayer instance. Can be swapped via [attachPlayer].
 */
internal class PlaybackScheduler(player: ExoPlayer? = null) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var player: ExoPlayer? = player

    /** Stores the latest NTP offset so pending jobs can recalibrate wait times. */
    private val ntpOffsetMs = AtomicLong(0L)

    private var playJob: Job? = null
    private var pauseJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attach or replace the [ExoPlayer] instance that receives scheduled commands.
     */
    fun attachPlayer(player: ExoPlayer) {
        this.player = player
        Log.d(TAG, "ExoPlayer attached")
    }

    /**
     * Update the NTP clock offset used for wait-time calculations.
     * Safe to call from any thread.
     *
     * @param offsetMs New NTP offset in milliseconds (server_time - local_time).
     */
    fun updateOffset(offsetMs: Long) {
        ntpOffsetMs.set(offsetMs)
    }

    /**
     * Schedule a play command to fire at [globalTimestampMs] (server epoch ms).
     *
     * Cancels any pending play job before scheduling the new one. Uses a
     * coroutine-based coarse delay + nano-spin loop for precision firing.
     *
     * @param globalTimestampMs Server epoch ms at which to fire – from `ScheduledPlay.serverTimeToExecute`.
     * @param positionMs        ExoPlayer seek position in milliseconds.
     * @param ntpOffsetMs       Current NTP offset (may be overridden by [updateOffset] before firing).
     */
    fun playAt(globalTimestampMs: Long, positionMs: Long, ntpOffsetMs: Long) {
        this.ntpOffsetMs.set(ntpOffsetMs)
        playJob?.cancel()
        playJob = scope.launch {
            waitUntil(globalTimestampMs)
            val p = player ?: run {
                Log.w(TAG, "playAt fired but no ExoPlayer attached")
                return@launch
            }
            // ExoPlayer API calls must be on main thread
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                p.seekTo(positionMs)
                p.play()
                Log.d(TAG, "play() fired at positionMs=$positionMs")
            }
        }
    }

    /**
     * Schedule a pause command to fire at [globalTimestampMs] (server epoch ms).
     *
     * @param globalTimestampMs Server epoch ms – from `ScheduledPause.serverTimeToExecute`.
     * @param ntpOffsetMs       Current NTP offset.
     */
    fun pauseAt(globalTimestampMs: Long, ntpOffsetMs: Long) {
        this.ntpOffsetMs.set(ntpOffsetMs)
        pauseJob?.cancel()
        pauseJob = scope.launch {
            waitUntil(globalTimestampMs)
            val p = player ?: run {
                Log.w(TAG, "pauseAt fired but no ExoPlayer attached")
                return@launch
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                p.pause()
                Log.d(TAG, "pause() fired")
            }
        }
    }

    /**
     * Schedule a seek+play command at global server-time [globalTimestampMs].
     *
     * Equivalent to [playAt] but semantically distinct to callers.
     *
     * @param positionMs        Target seek position in milliseconds.
     * @param globalTimestampMs Server epoch ms at which to execute.
     * @param ntpOffsetMs       Current NTP offset.
     */
    fun seekAt(positionMs: Long, globalTimestampMs: Long, ntpOffsetMs: Long) {
        playAt(globalTimestampMs, positionMs, ntpOffsetMs)
    }

    // ── Precision wait logic ─────────────────────────────────────────────────

    /**
     * Wait until the local clock (adjusted by NTP offset) reaches [targetServerMs].
     *
     * Two-phase approach:
     * 1. Coarse sleep via coroutine [delay] for most of the wait, leaving
     *    [FINE_SPIN_THRESHOLD_NS] for the high-resolution spin.
     * 2. Nano-spin against [SystemClock.elapsedRealtimeNanos] to achieve
     *    sub-millisecond precision.
     */
    private suspend fun waitUntil(targetServerMs: Long) {
        // localExecutionTime = targetServerMs - ntpOffsetMs
        // Since offset = serverTime - localTime, we have:
        //   serverTime ≈ localTime + offset
        //   localTime to fire = targetServerMs - offset
        val localExecutionMs = targetServerMs - ntpOffsetMs.get()
        val nowMs = System.currentTimeMillis()
        val coarseWaitMs = localExecutionMs - nowMs

        if (coarseWaitMs <= 0) {
            Log.w(TAG, "Command already past due by ${-coarseWaitMs}ms — firing immediately")
            return
        }

        // Phase 1: coarse coroutine sleep (leave FINE_SPIN_THRESHOLD_MS for spin)
        val coarseSleepMs = max(0L, coarseWaitMs - FINE_SPIN_THRESHOLD_MS)
        if (coarseSleepMs > 0) delay(coarseSleepMs)

        // Phase 2: high-resolution nano-spin
        val targetNanos = SystemClock.elapsedRealtimeNanos() +
            (max(0L, (localExecutionMs - System.currentTimeMillis())) * 1_000_000L)

        @Suppress("ControlFlowWithEmptyBody")
        while (SystemClock.elapsedRealtimeNanos() < targetNanos) { /* spin */ }
    }

    companion object {
        private const val TAG = "PlaybackScheduler"

        /**
         * Final fine-grained spin threshold in milliseconds.
         * We stop the coarse [delay] this many ms early and spin the rest.
         */
        private const val FINE_SPIN_THRESHOLD_MS = 10L
    }
}
