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
internal class NtpEngine(private val webSocket: SyncWebSocket) {

    // ── Constants (ported from @beatsync/shared constants.ts) ────────────────

    /** Inter-departure gap between the two probes in a pair (ms). */
    private val PROBE_GAP_MS = 25L

    /** Maximum allowed drift between client-side and server-side inter-arrival gaps (ms). */
    private val PROBE_GAP_TOLERANCE_MS = 5L

    /** Number of probe pairs fired per measurement cycle. */
    private val PROBE_PAIR_COUNT = 8

    /** Maximum wait for a single probe response before timing out (ms). */
    private val PROBE_RESPONSE_TIMEOUT_MS = 1_500L

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

    // ── Probe state (reset on each cycle) ────────────────────────────────────

    private var probeGroupCounter = 0
    private var pendingFirstProbe: Measurement? = null
    private var pendingFirstProbeGroupId: Int? = null
    private var pureCount = 0
    private var impureCount = 0

    private fun resetProbeState() {
        probeGroupCounter = 0
        pendingFirstProbe = null
        pendingFirstProbeGroupId = null
        pureCount = 0
        impureCount = 0
    }

    // ── Public API ────────────────────────────────────────────────────────────

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

            Log.d(TAG, "Pair $pairIndex/$PROBE_PAIR_COUNT done. Pure so far: ${pureMeasurements.size}")
        }

        if (pureMeasurements.isEmpty()) {
            Log.w(TAG, "No pure measurements collected — sync cycle failed")
            return@withContext null
        }

        buildResult(pureMeasurements)
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
        val best = measurements.minByOrNull { it.roundTripDelay }!!
        val total = pureCount + impureCount
        val confidence = if (total > 0) pureCount.toFloat() / total else 0f

        return NtpResult(
            offsetMs = best.clockOffset,
            rttMs = best.roundTripDelay,
            confidence = confidence,
        )
    }

    companion object {
        private const val TAG = "NtpEngine"
    }
}
