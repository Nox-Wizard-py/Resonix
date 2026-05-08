package com.resonix.sync.scheduler

import android.os.SystemClock
import android.util.Log
import android.media.AudioManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
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
internal class PlaybackScheduler(
    context: Context,
    player: ExoPlayer? = null,
) {
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var player: ExoPlayer? = player

    /** Stores the latest NTP offset so pending jobs can recalibrate wait times. */
    private val ntpOffsetMs = AtomicLong(0L)

    private var playJob: Job? = null
    private var pauseJob: Job? = null

    private val spinExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "resonix-sync-spin").also { it.priority = Thread.MAX_PRIORITY }
    }.asCoroutineDispatcher()

    /** Hardware latency compensation — queried once and cached. */
    internal val hardwareLatencyMs: Long = queryHardwareLatencyMs()

    /** User-facing manual nudge offset in ms. Positive = delay playback. Negative = advance. */
    @Volatile
    private var nudgeMs: Long = 0L

    /**
     * Update the manual nudge offset.
     * Called from [ResonixSync] when the user adjusts the sync slider.
     *
     * @param nudgeMs Positive = intentionally delay this device's playback.
     *                Negative = advance this device's playback.
     */
    fun updateNudge(nudgeMs: Long) {
        this.nudgeMs = nudgeMs
        Log.d(TAG, "Nudge updated: ${nudgeMs}ms")
    }

    /** Total compensation applied before each scheduled playback event. */
    private fun totalCompensationMs(): Long = hardwareLatencyMs + nudgeMs

    /**
     * Query Android's audio hardware latency in milliseconds.
     *
     * Uses [AudioManager] output latency — the time between AudioTrack.write()
     * and actual sound emission from the speaker/headphones/Bluetooth sink.
     *
     * Fallback chain:
     * 1. AudioManager output latency via reflection (most accurate, all APIs)
     * 2. Fixed 80ms conservative estimate (safe fallback for unknown hardware)
     *
     * Bluetooth A2DP typically adds 150-250ms on top of this — detected via
     * AudioManager.isBluetoothA2dpOn() and compensated separately.
     */
    private fun queryHardwareLatencyMs(): Long {
        return try {
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Reflect into AudioManager.getOutputLatency(streamType) — not public API
            // but stable across all Android versions in practice
            val method = AudioManager::class.java.getMethod("getOutputLatency", Int::class.java)
            val baseLatencyMs = (method.invoke(audioManager, AudioManager.STREAM_MUSIC) as Int).toLong()

            // Add Bluetooth A2DP penalty if active
            @Suppress("DEPRECATION")
            val bluetoothPenaltyMs = if (audioManager.isBluetoothA2dpOn) {
                Log.d(TAG, "Bluetooth A2DP detected — adding penalty")
                BLUETOOTH_LATENCY_PENALTY_MS
            } else 0L

            val total = baseLatencyMs + bluetoothPenaltyMs
            Log.d(TAG, "Hardware latency: base=${baseLatencyMs}ms bt=${bluetoothPenaltyMs}ms total=${total}ms")
            total
        } catch (e: Exception) {
            Log.w(TAG, "Hardware latency query failed: ${e.message} — using fallback ${FALLBACK_LATENCY_MS}ms")
            FALLBACK_LATENCY_MS
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attach or replace the [ExoPlayer] instance that receives scheduled commands.
     */
    fun attachPlayer(player: ExoPlayer) {
        this.player = player
        Log.d(TAG, "ExoPlayer attached")
    }

    /** Returns the currently attached ExoPlayer instance or null. */
    internal fun getPlayer(): ExoPlayer? = player

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
     * If the command arrives past-due (schedule already expired), [waitUntil] returns
     * the missed delta in ms and [positionMs] is projected forward to compensate —
     * mirroring BeatSync's client-side reschedule logic in global.tsx schedulePlay().
     *
     * @param globalTimestampMs Server epoch ms at which to fire – from `ScheduledPlay.serverTimeToExecute`.
     * @param positionMs        ExoPlayer seek position in milliseconds.
     * @param ntpOffsetMs       Current NTP offset (may be overridden by [updateOffset] before firing).
     */
    fun playAt(globalTimestampMs: Long, positionMs: Long, ntpOffsetMs: Long) {
        this.ntpOffsetMs.set(ntpOffsetMs)
        playJob?.cancel()
        playJob = scope.launch {
            // waitUntil returns 0L if on time, or the missed delta ms if past-due
            val missedDeltaMs = waitUntil(globalTimestampMs)
            // Project position forward so audio starts at the correct point, not the stale one
            val correctedPositionMs = positionMs + missedDeltaMs
            val p = player ?: run {
                Log.w(TAG, "playAt fired but no ExoPlayer attached")
                return@launch
            }
            // ExoPlayer API calls must be on main thread
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                p.seekTo(correctedPositionMs)
                p.play()
                if (missedDeltaMs > 0L) {
                    Log.w(TAG, "Past-due correction: +${missedDeltaMs}ms → seekTo($correctedPositionMs)")
                } else {
                    Log.d(TAG, "play() fired at positionMs=$correctedPositionMs")
                }
            }
        }
    }

    /**
     * Schedule a pause command to fire at [globalTimestampMs] (server epoch ms).
     *
     * Pause does not need position correction — pausing a few ms late is imperceptible.
     * The missed delta is logged only.
     *
     * @param globalTimestampMs Server epoch ms – from `ScheduledPause.serverTimeToExecute`.
     * @param ntpOffsetMs       Current NTP offset.
     */
    fun pauseAt(globalTimestampMs: Long, ntpOffsetMs: Long) {
        this.ntpOffsetMs.set(ntpOffsetMs)
        pauseJob?.cancel()
        pauseJob = scope.launch {
            val missedDeltaMs = waitUntil(globalTimestampMs)
            val p = player ?: run {
                Log.w(TAG, "pauseAt fired but no ExoPlayer attached")
                return@launch
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                p.pause()
                if (missedDeltaMs > 0L) {
                    Log.w(TAG, "Pause past-due by ${missedDeltaMs}ms — fired immediately")
                } else {
                    Log.d(TAG, "pause() fired on time")
                }
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

    fun destroy() {
        playJob?.cancel()
        pauseJob?.cancel()
        spinExecutor.close()
        Log.d(TAG, "PlaybackScheduler destroyed")
    }

    // ── Precision wait logic ─────────────────────────────────────────────────

    /**
     * Wait until the local clock (adjusted by NTP offset) reaches [targetServerMs].
     *
     * Two-phase approach:
     * 1. Coarse sleep via coroutine [delay] for most of the wait, leaving
     *    [FINE_SPIN_THRESHOLD_MS] for the high-resolution spin.
     * 2. Nano-spin against [SystemClock.elapsedRealtimeNanos] to achieve
     *    sub-millisecond precision.
     *
     * @return 0L if the command fired on time; the missed delta in ms if the command
     *         was already past-due. Callers (e.g. [playAt]) use this to project
     *         [positionMs] forward so audio starts at the correct track position.
     */
    private suspend fun waitUntil(targetServerMs: Long): Long {
        // Subtract total compensation so playback is fired EARLIER by the hardware latency amount
        // This way sound actually emerges from the speaker at the correct scheduled time
        val localExecutionMs = targetServerMs - ntpOffsetMs.get() - totalCompensationMs()
        Log.d(TAG, "Scheduling: target=${targetServerMs} localExec=${localExecutionMs} " +
            "ntpOffset=${ntpOffsetMs.get()}ms compensation=${totalCompensationMs()}ms " +
            "(hw=${hardwareLatencyMs}ms nudge=${nudgeMs}ms)")
        val nowMs = System.currentTimeMillis()
        val coarseWaitMs = localExecutionMs - nowMs

        if (coarseWaitMs <= 0) {
            val missedDeltaMs = -coarseWaitMs
            Log.w(TAG, "Command past-due by ${missedDeltaMs}ms — applying position correction")
            return missedDeltaMs
        }

        // Phase 1 — coarse sleep on IO
        val coarseSleepMs = max(0L, coarseWaitMs - FINE_SPIN_THRESHOLD_MS)
        if (coarseSleepMs > 0) delay(coarseSleepMs)

        // Phase 2 — precision nano-spin on dedicated MAX_PRIORITY thread
        withContext(spinExecutor) {
            val targetNanos = SystemClock.elapsedRealtimeNanos() +
                (max(0L, (localExecutionMs - System.currentTimeMillis())) * 1_000_000L)

            @Suppress("ControlFlowWithEmptyBody")
            while (SystemClock.elapsedRealtimeNanos() < targetNanos) { /* spin */ }
        }

        return 0L
    }

    companion object {
        private const val TAG = "PlaybackScheduler"

        /**
         * Final fine-grained spin threshold in milliseconds.
         * We stop the coarse [delay] this many ms early and spin the rest.
         */
        private const val FINE_SPIN_THRESHOLD_MS = 5L

        /** Conservative fallback when hardware latency cannot be queried. */
        private const val FALLBACK_LATENCY_MS = 80L

        /**
         * Additional penalty for Bluetooth A2DP encoding + transmission buffer.
         * A2DP codec pipeline (SBC/AAC/aptX) adds ~150ms on most devices.
         */
        private const val BLUETOOTH_LATENCY_PENALTY_MS = 150L
    }
}
