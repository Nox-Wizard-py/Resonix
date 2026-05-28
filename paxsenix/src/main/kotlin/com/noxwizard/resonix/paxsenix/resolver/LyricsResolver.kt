package com.noxwizard.resonix.paxsenix.resolver

import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.providers.LyricsProvider
import com.noxwizard.resonix.paxsenix.resolver.LyricsIntegrityValidator.ValidationResult
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Per-candidate scoring snapshot — used for ranking and debug output.
 */
data class RankedLyricsCandidate(
    val result: LyricsDocument,
    val providerName: String,
    val titleScore: Float,
    val artistScore: Float,
    val durationScore: Float,
    val confidence: Float,
    val syncType: SyncType,
    val passesArtistGate: Boolean,
    val validationResult: ValidationResult,
    /** Final composite score used for ranking (sync-weighted). */
    val finalScore: Float,
)

/**
 * Orchestrates all [LyricsProvider]s using a collect-all → validate → rank → select strategy.
 *
 * Ranking formula (Part 6):
 *   finalScore = (syncWeight * 0.70) + (confidence * 0.30)
 *
 * This ensures WORD_SYNCED lyrics always beat LINE_SYNCED lyrics unless confidence is catastrophically low.
 *
 * Validation (Part 4):
 * - Every candidate passes through [LyricsIntegrityValidator] before ranking.
 * - Rejected candidates are logged but excluded from selection.
 *
 * Debug logging:
 * - Structured [Resolver] block logged per candidate when [debugLogging] = true.
 */
class LyricsResolver(
    providers: List<LyricsProvider>,
    private val minConfidence: Float = 0.5f,
    private val debugLogging: Boolean = false,
) {
    private val orderedProviders: List<LyricsProvider> =
        providers.sortedByDescending { it.category.reliabilityWeight }

    // ─── Public API ───────────────────────────────────────────────────────────

    suspend fun resolve(track: LyricsTrack): LyricsDocument? {
        val candidates = collectAndRank(track)
        if (debugLogging) printDebugLog(track, candidates)
        val selected = candidates
            .filter { it.passesArtistGate && it.validationResult is ValidationResult.Accept && it.confidence >= minConfidence }
            .maxByOrNull { it.finalScore }
            ?: candidates  // fallback: relax confidence, keep validation gates
                .filter { it.passesArtistGate && it.validationResult is ValidationResult.Accept }
                .maxByOrNull { it.finalScore }
        return selected?.result
    }

    suspend fun resolveAll(track: LyricsTrack): List<LyricsDocument> =
        collectAndRank(track)
            .filter { it.passesArtistGate && it.validationResult is ValidationResult.Accept && it.confidence >= minConfidence }
            .map { it.result }

    suspend fun resolveRanked(track: LyricsTrack): List<RankedLyricsCandidate> =
        collectAndRank(track)

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun collectAndRank(track: LyricsTrack): List<RankedLyricsCandidate> = coroutineScope {
        val channel = Channel<RankedLyricsCandidate?>(orderedProviders.size)
        val candidates = mutableListOf<RankedLyricsCandidate>()

        val jobs = orderedProviders.map { provider ->
            launch {
                val timeoutMs = when (provider.category) {
                    LyricsProviderCategory.PREMIUM_WORD_SYNC -> 8000L
                    LyricsProviderCategory.PREMIUM_LINE_SYNC -> 7000L
                    LyricsProviderCategory.STANDARD_SYNC -> 6000L
                    LyricsProviderCategory.FALLBACK -> 4000L
                    LyricsProviderCategory.EXPERIMENTAL -> 3000L
                }
                
                val result = withTimeoutOrNull(timeoutMs) {
                    runCatching { provider.search(track) }.getOrNull()
                }

                if (result == null || result.isEmpty) {
                    channel.send(null)
                    return@launch
                }

                val breakdown = MatchScorer.breakdown(
                    track = track,
                    candidateTitle = track.title,
                    candidateArtist = track.artist,
                    candidateDurationSec = track.durationSec,
                )

                val validation = LyricsIntegrityValidator.validate(
                    track = track,
                    result = result,
                    candidateTitle = track.title,
                    candidateArtist = track.artist,
                    candidateDurationMs = if (track.durationMs > 0) track.durationMs else -1L,
                )

                val syncWeight = result.syncType.preferenceScore().toFloat() / SyncType.WORD_SYNCED.preferenceScore().toFloat()
                val finalScore = (syncWeight * 0.70f) + (breakdown.finalScore * 0.30f)

                channel.send(
                    RankedLyricsCandidate(
                        result = result,
                        providerName = provider.name,
                        titleScore = breakdown.titleScore,
                        artistScore = breakdown.artistScore,
                        durationScore = breakdown.durationScore,
                        confidence = breakdown.finalScore,
                        syncType = result.syncType,
                        passesArtistGate = breakdown.passesArtistGate,
                        validationResult = validation,
                        finalScore = finalScore,
                    )
                )
            }
        }

        var received = 0
        while (received < orderedProviders.size) {
            val candidate = channel.receive()
            received++
            if (candidate != null) {
                candidates.add(candidate)
                
                val accepted = candidate.passesArtistGate && 
                               candidate.validationResult is ValidationResult.Accept && 
                               candidate.confidence >= minConfidence
                
                if (accepted && candidate.syncType == SyncType.WORD_SYNCED && candidate.finalScore >= 0.85f) {
                    // Early exit condition met
                    jobs.forEach { it.cancel() }
                    break
                }
            }
        }

        candidates.sortedByDescending { it.finalScore }
    }

    // ─── Debug logging (Part 4) ───────────────────────────────────────────────

    private fun printDebugLog(track: LyricsTrack, candidates: List<RankedLyricsCandidate>) {
        val sb = StringBuilder()
        sb.appendLine("\n[Resolver] ═══════════════════════════════════════════════")
        sb.appendLine("  Track:    \"${track.title}\" – ${track.artist}")
        track.durationMs.takeIf { it > 0 }?.let { sb.appendLine("  Duration: ${it / 1000}s") }
        sb.appendLine()

        if (candidates.isEmpty()) {
            sb.appendLine("  ⚠ No candidates returned by any provider.")
        } else {
            candidates.forEachIndexed { i, c ->
                val validTag = when (c.validationResult) {
                    is ValidationResult.Accept -> "PASSED"
                    is ValidationResult.Reject -> "REJECTED"
                }
                val artistGateTag = if (c.passesArtistGate) "✓" else "✗ ARTIST GATE FAIL"
                val accepted = c.passesArtistGate &&
                        c.validationResult is ValidationResult.Accept &&
                        c.confidence >= minConfidence

                sb.appendLine("  [${i + 1}] Provider:    ${c.providerName}  $artistGateTag")
                sb.appendLine("       SyncType:    ${c.syncType}")
                sb.appendLine("       TitleScore:  ${"%.2f".format(c.titleScore)}")
                sb.appendLine("       ArtistScore: ${"%.2f".format(c.artistScore)}")
                sb.appendLine("       DurScore:    ${"%.2f".format(c.durationScore)}")
                sb.appendLine("       Confidence:  ${"%.2f".format(c.confidence)}")
                sb.appendLine("       Validation:  $validTag")
                if (c.validationResult is ValidationResult.Reject) {
                    sb.appendLine("       Reason:      ${c.validationResult.reason}")
                }
                sb.appendLine("       FINAL SCORE: ${"%.2f".format(c.finalScore)}  → ${if (accepted) "SELECTED" else "REJECTED"}")
                sb.appendLine()
            }

            val selected = candidates.firstOrNull {
                it.passesArtistGate &&
                        it.validationResult is ValidationResult.Accept &&
                        it.confidence >= minConfidence
            }
            sb.appendLine("  ► Winner: ${selected?.providerName ?: "NONE — all candidates rejected"}")
        }
        sb.append("═══════════════════════════════════════════════════════════")
        println(sb.toString())
    }
}
