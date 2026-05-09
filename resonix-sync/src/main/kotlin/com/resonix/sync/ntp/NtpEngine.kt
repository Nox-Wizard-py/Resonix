package com.resonix.sync.ntp

import android.os.SystemClock
import android.util.Log
import com.resonix.sync.network.SyncMessage
import com.resonix.sync.network.SyncWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Huygens-coded NTP engine ported from beatsync.gg's ntp.ts.
 *
 * ## Algorithm overview
 * Each probe cycle fires [PROBE_PAIR_COUNT] coded probe pairs. A "coded pair" is two NTP
 * requests sent exactly [PROBE_GAP_MS] apart. The server echoes the inter-departure gap
 * as an inter-arrival gap. If those two gaps match within [PROBE_GAP_TOLERANCE_MS], the
 * pair is classified as **pure** (unaffected by queuing / TCP HOL blocking / GC pauses).
 *
 * Clock offset is then calculated using RFC 5905 §10 min-RTT selection: the lowest-RTT
 * pure measurement has the least asymmetric queuing contamination, so its offset wins.
 *
 * ## Time source
 * `SystemClock.elapsedRealtimeNanos()` is used instead of `performance.now()`. This
 * clock is monotonic and not affected by wall-clock adjustments (NTP, DST, etc.), making
 * it safe for measuring short inter-probe gaps. Absolute epoch millis are measured via
 * `System.currentTimeMillis()` for the NTP packet timestamps (t0/t3).
 *
 * @param webSocket The live [SyncWebSocket] used to send NTP probe packets.
 */
internal class NtpEngine(
    private val webSocket: SyncWebSocket,
    private val compensationMs: Long = 0L,
    private val nudgeMs: Long = 0L,
) {

    // ── Constants (ported from @beatsync/shared constants.ts) ────────────────

    /** Inter-departure gap between the two probes in a pair (ms). */
    private val PROBE_GAP_MS = 25L

    /** Breathing gap between probe pairs to let network buffers drain (ms). */
    private val INTER_PAIR_DELAY_MS = 10L

    /** Maximum allowed drift between client-side and server-side inter-arrival gaps (ms). */
    private val PROBE_GAP_TOLERANCE_MS = 5L

    /** Number of probe pairs fired per measurement cycle. */
    private val PROBE_PAIR_COUNT = 16

    /** Maximum number of pure measurements to retain in the rolling window. */
    private val ROLLING_WINDOW_SIZE = 32

    /** Minimum pure measurements in window before offset is considered stable. */
    private val MIN_WINDOW_FOR_STABLE_OFFSET = 8

    /** Interval between probes during initial rapid-fire phase (ms). */
    private val INITIAL_INTERVAL_MS = 50L

    /** Interval between probes during steady-state phase (ms). */
    private val STEADY_STATE_INTERVAL_MS = 2500L

    /** Maximum wait for a single probe response before timing out (ms). */
    private val PROBE_RESPONSE_TIMEOUT_MS = 1_500L

    /** 1.5 × steady-state interval — mirrors BeatSync's RESPONSE_TIMEOUT_MS exactly. */
    private val RESPONSE_TIMEOUT_MS = 3750L

    // ── Internal measurement record ───────────────────────────────────────────

    /**
     * Four-timestamp NTP measurement record (RFC 5905 §8).
     *
     * - t0: client send time (epoch ms)
     * - t1: server receive time (epoch ms, from server response)
     * - t2: server send time (epoch ms, from server response)
     * - t3: client receive time (epoch ms)
     */
    private data class Measurement(
        val t0: Long,
        val t1: Long,
        val t2: Long,
        val t3: Long,
        val roundTripDelay: Long,
        val clockOffset: Long,
        val probeGroupId: Int,
        val probeGroupIndex: Int,
    )

    /**
     * Rolling window of all accumulated pure measurements across all cycles.
     * Capped at [ROLLING_WINDOW_SIZE] — oldest measurements are evicted when full.
     * Persists across individual [measure()] calls unlike [pureMeasurements].
     */
    private val rollingWindow = ArrayDeque<Measurement>(ROLLING_WINDOW_SIZE)

    // ── Probe state (reset on each cycle) ────────────────────────────────────

    private var probeGroupCounter = 0
    private var pendingFirstProbe: Measurement? = null
    private var pendingFirstProbeGroupId: Int? = null
    private var pureCount = 0
    private var impureCount = 0

    /** Timestamp of the last outbound probe — null if no probe is in-flight. */
    private var lastProbeTimestampMs: Long? = null

    /** Whether the heartbeat has been stopped due to stale connection. */
    @Volatile
    private var isStale: Boolean = false

    private fun resetProbeState() {
        probeGroupCounter = 0
        pendingFirstProbe = null
        pendingFirstProbeGroupId = null
        pureCount = 0
        impureCount = 0
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Clear the rolling window.
     * Must be called when the session reconnects after a network drop —
     * stale measurements from a previous connection are no longer valid.
     */
    fun resetWindow() {
        rollingWindow.clear()
        Log.d(TAG, "Rolling window reset")
    }

    /**
     * Mark that a valid NTP response was received.
     * Resets the stale-connection timer.
     *
     * Must be called by [SyncSession] after every successful [NtpResult]
     * from the heartbeat — mirrors BeatSync's `markNTPResponseReceived()`.
     */
    fun markResponseReceived() {
        lastProbeTimestampMs = null
        isStale = false
        Log.d(TAG, "NTP response acknowledged — stale timer reset")
    }

    /**
     * Reset stale state — called when WebSocket reconnects.
     * Allows heartbeat to resume after a connection recovery.
     */
    fun resetStale() {
        isStale = false
        lastProbeTimestampMs = null
        Log.d(TAG, "Stale state reset — heartbeat can resume")
    }

    /**
     * Run a full NTP measurement cycle on [Dispatchers.IO].
     *
     * Fires [PROBE_PAIR_COUNT] coded probe pairs, collects responses, validates gap purity,
     * and returns the best [NtpResult] or null if no pure probes were received.
     */
    suspend fun measure(): NtpResult? = withContext(Dispatchers.IO) {
        resetProbeState()
        val pureMeasurements = mutableListOf<Measurement>()

        repeat(PROBE_PAIR_COUNT) { pairIndex ->
            val groupId = probeGroupCounter++

            // Send first probe
            val first = sendProbeAndAwait(groupId, probeGroupIndex = 0) ?: return@repeat
            pendingFirstProbe = first
            pendingFirstProbeGroupId = groupId

            // Wait exactly PROBE_GAP_MS before sending second (mirrors setTimeout in ntp.ts)
            delay(PROBE_GAP_MS)

            // Send second probe
            val second = sendProbeAndAwait(groupId, probeGroupIndex = 1) ?: return@repeat

            // Validate pair purity
            val validated = validateProbePair(first, second, groupId) ?: return@repeat
            pureMeasurements.add(validated)

            // Add to rolling window — evict oldest if at capacity
            if (rollingWindow.size >= ROLLING_WINDOW_SIZE) {
                rollingWindow.removeFirst()
            }
            rollingWindow.addLast(validated)

            Log.d(TAG, "Pair $pairIndex/$PROBE_PAIR_COUNT done. " +
                "Cycle pure: ${pureMeasurements.size} | Window: ${rollingWindow.size}/$ROLLING_WINDOW_SIZE")

            // Breathing room between pairs — lets network buffers drain before next pair
            if (pairIndex < PROBE_PAIR_COUNT - 1) {
                delay(INTER_PAIR_DELAY_MS)
            }
        }

        if (pureMeasurements.isEmpty()) {
            Log.w(TAG, "No pure measurements collected — sync cycle failed")
            return@withContext null
        }

        buildResult(pureMeasurements)
    }

    /**
     * Starts a continuous NTP heartbeat — ported from BeatSync's two-phase probe strategy.
     *
     * Phase 1 (rapid-fire): Fires probe pairs every [INITIAL_INTERVAL_MS] until
     * [PROBE_PAIR_COUNT] pure measurements have been collected.
     *
     * Phase 2 (steady-state): Fires a single probe pair every [STEADY_STATE_INTERVAL_MS]
     * indefinitely, keeping the offset permanently fresh.
     *
     * The [onResult] callback is invoked after every successful measurement with the
     * latest [NtpResult]. The caller (SyncSession) should update offset and scheduler.
     *
     * Must be called from a coroutine scope. Runs entirely on [Dispatchers.IO].
     * Cancel the parent scope or job to stop the heartbeat.
     *
     * @param onResult Callback invoked with each fresh [NtpResult].
     */
    suspend fun startHeartbeat(
        onResult: (NtpResult) -> Unit,
        onConnectionStale: () -> Unit,
    ) = withContext(Dispatchers.IO) {
        var pureMeasurementsCollected = 0
        isStale = false

        // Phase 1 — rapid fire
        while (pureMeasurementsCollected < PROBE_PAIR_COUNT) {
            if (isStale) {
                Log.w(TAG, "Heartbeat stopped — connection stale during Phase 1")
                return@withContext
            }

            // Check if previous probe timed out
            val lastProbe = lastProbeTimestampMs
            if (lastProbe != null && (System.currentTimeMillis() - lastProbe) > RESPONSE_TIMEOUT_MS) {
                Log.e(TAG, "NTP probe timed out — connection stale")
                isStale = true
                onConnectionStale()
                return@withContext
            }

            lastProbeTimestampMs = System.currentTimeMillis()
            val result = measure()
            if (result != null) {
                lastProbeTimestampMs = null // response received
                pureMeasurementsCollected++
                onResult(result)
                Log.d(TAG, "Heartbeat phase 1: $pureMeasurementsCollected/$PROBE_PAIR_COUNT")
            }
            delay(INITIAL_INTERVAL_MS)
        }

        Log.d(TAG, "Heartbeat entering steady-state")

        // Phase 2 — steady state
        while (true) {
            if (isStale) {
                Log.w(TAG, "Heartbeat stopped — connection stale during Phase 2")
                return@withContext
            }

            delay(STEADY_STATE_INTERVAL_MS)

            // Check stale before firing
            val lastProbe = lastProbeTimestampMs
            if (lastProbe != null && (System.currentTimeMillis() - lastProbe) > RESPONSE_TIMEOUT_MS) {
                Log.e(TAG, "NTP steady-state probe timed out — connection stale")
                isStale = true
                onConnectionStale()
                return@withContext
            }

            lastProbeTimestampMs = System.currentTimeMillis()
            val result = measure()
            if (result != null) {
                lastProbeTimestampMs = null // response received
                onResult(result)
                Log.d(TAG, "Heartbeat steady-state: offset=${result.offsetMs}ms rtt=${result.rttMs}ms")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Send a single NTP probe and wait for the matching response.
     *
     * Captures t0 right before sending and t3 immediately on response receipt to minimise
     * measurement latency. Computes RTT and offset per RFC 5905.
     *
     * @return A [Measurement] or null on timeout/socket error.
     */
    private suspend fun sendProbeAndAwait(groupId: Int, probeGroupIndex: Int): Measurement? {
        val t0 = System.currentTimeMillis()

        webSocket.send(
            SyncMessage.NtpProbe(
                t0 = t0,
                probeGroupId = groupId,
                probeGroupIndex = probeGroupIndex,
                clientRtt = if (pendingFirstProbe != null) pendingFirstProbe!!.roundTripDelay else null,
                clientCompensationMs = compensationMs,
                clientNudgeMs = nudgeMs,
            )
        )

        val response = withTimeoutOrNull(PROBE_RESPONSE_TIMEOUT_MS) {
            webSocket.incoming
                .filterIsInstance<SyncMessage.NtpResponse>()
                .filter { it.probeGroupId == groupId && it.probeGroupIndex == probeGroupIndex }
                .first()
        } ?: run {
            Log.w(TAG, "Probe timeout: groupId=$groupId index=$probeGroupIndex")
            return null
        }

        val t3 = System.currentTimeMillis()
        val t1 = response.t1
        val t2 = response.t2

        // RFC 5905 §8: RTT = (t3 - t0) - (t2 - t1)
        val rtt = (t3 - t0) - (t2 - t1)
        // RFC 5905 §8: offset = ((t1 - t0) + (t2 - t3)) / 2
        val offset = ((t1 - t0) + (t2 - t3)) / 2

        return Measurement(
            t0 = t0,
            t1 = t1,
            t2 = t2,
            t3 = t3,
            roundTripDelay = rtt,
            clockOffset = offset,
            probeGroupId = groupId,
            probeGroupIndex = probeGroupIndex,
        )
    }

    /**
     * Huygens gap-purity validator (ported from validateProbePair in ntp.ts).
     *
     * Compares client inter-departure gap vs server inter-arrival gap. If they match
     * within [PROBE_GAP_TOLERANCE_MS], the pair is pure and we return the best
     * (lowest RTT) measurement. Otherwise returns null and logs the impure pair.
     */
    private fun validateProbePair(first: Measurement, second: Measurement, groupId: Int): Measurement? {
        val clientGap = second.t0 - first.t0
        val serverGap = second.t1 - first.t1
        val gapDrift = kotlin.math.abs(serverGap - clientGap)
        val isPure = gapDrift <= PROBE_GAP_TOLERANCE_MS

        if (isPure) pureCount++ else impureCount++

        val total = pureCount + impureCount
        val pureRate = if (total > 0) (pureCount * 100 / total) else 0

        if (!isPure) {
            Log.d(
                TAG,
                "[CodedProbe] IMPURE #$groupId | clientGap=${clientGap}ms serverGap=${serverGap}ms " +
                    "drift=${gapDrift}ms | pure: $pureCount/$total ($pureRate%)"
            )
            return null
        }

        // Best probe = lowest RTT (least queuing contamination)
        val best = if (first.roundTripDelay <= second.roundTripDelay) first else second

        Log.d(
            TAG,
            "[CodedProbe] PURE #$groupId | clientGap=${clientGap}ms serverGap=${serverGap}ms " +
                "drift=${gapDrift}ms | bestRTT=${best.roundTripDelay}ms offset=${best.clockOffset}ms | " +
                "pure: $pureCount/$total ($pureRate%)"
        )

        return best
    }

    /**
     * Select the best offset using min-RTT selection (ported from calculateOffsetEstimate in ntp.ts).
     *
     * Queuing delays can only ADD to RTT, never subtract. The lowest-RTT measurement is
     * closest to true propagation delay — its offset has the least asymmetric queuing noise.
     */
    private fun buildResult(measurements: List<Measurement>): NtpResult {
        // Use rolling window if it has enough measurements — more accurate than single cycle
        val source = if (rollingWindow.size >= MIN_WINDOW_FOR_STABLE_OFFSET) {
            Log.d(TAG, "Using rolling window (${rollingWindow.size} measurements) for offset estimate")
            rollingWindow.toList()
        } else {
            Log.d(TAG, "Window not yet stable (${rollingWindow.size}/$MIN_WINDOW_FOR_STABLE_OFFSET) — using cycle measurements")
            measurements
        }

        // Min-RTT selection across entire source pool
        val best = source.minByOrNull { it.roundTripDelay }!!
        val averageRtt = source.map { it.roundTripDelay }.average().toLong()
        val total = pureCount + impureCount
        val confidence = if (total > 0) pureCount.toFloat() / total else 0f

        // Window stability bonus — confidence gets a boost when window is full
        val windowBonus = if (rollingWindow.size >= ROLLING_WINDOW_SIZE) 0.1f else 0f
        val adjustedConfidence = (confidence + windowBonus).coerceAtMost(1.0f)

        return NtpResult(
            offsetMs = best.clockOffset,
            rttMs = best.roundTripDelay,
            averageRttMs = averageRtt,
            confidence = adjustedConfidence,
        )
    }

    companion object {
        private const val TAG = "NtpEngine"
    }
}
