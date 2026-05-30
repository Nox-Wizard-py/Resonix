package com.noxwizard.resonix.paxsenix.resolver

import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.providers.LyricsProvider
import com.noxwizard.resonix.paxsenix.resolver.LyricsIntegrityValidator.ValidationMode
import com.noxwizard.resonix.paxsenix.resolver.LyricsIntegrityValidator.ValidationResult
import com.noxwizard.resonix.paxsenix.utils.LyricsQueryVariantGenerator
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
    val validationMode: ValidationMode,
    /** Provider fetch latency in ms. */
    val providerLatencyMs: Long,
    /** Final composite score used for ranking (sync-weighted, Phase 10 formula). */
    val finalScore: Float,
)

/**
 * Orchestrates all [LyricsProvider]s using a collect-all → validate → rank → select strategy.
 *
 * Ranking formula (Phase 10):
 *   finalScore = (syncTierWeight × 0.55) + (metadataAccuracy × 0.25)
 *              + (providerReliability × 0.10) + (confidence × 0.10)
 *
 * WORD_SYNCED always beats LINE_SYNCED unless confidence is catastrophically low.
 *
 * Adaptive validation (Phase 4):
 * - MAINSTREAM mode: strict thresholds (default)
 * - OBSCURE mode: relaxed thresholds, activates on retry when MAINSTREAM returns nothing
 *
 * Query variant retry (Phase 3):
 * - On zero valid candidates, retry with next progressive query variant
 * - Maximum 3 variant retries (4 total attempts including original)
 *
 * Debug logging:
 * - Structured [Resolver] block logged per candidate when [debugLogging] = true.
 */
class LyricsResolver(
    providers: List<LyricsProvider>,
    private val minConfidence: Float = 0.35f,
    private val debugLogging: Boolean = false,
) {
    private val orderedProviders: List<LyricsProvider> =
        providers.sortedByDescending { it.category.reliabilityWeight }

    // ─── Public API ───────────────────────────────────────────────────────────

    suspend fun resolve(track: LyricsTrack): LyricsDocument? {
        val candidates = resolveWithVariants(track)
        if (debugLogging) printDebugLog(track, candidates)
        // Phase 4: MAINSTREAM first, OBSCURE fallback
        return (candidates
            .filter { it.passesArtistGate && it.validationResult is ValidationResult.Accept && it.confidence >= minConfidence }
            .maxByOrNull { it.finalScore }
            ?: candidates
                .filter { it.passesArtistGate && it.validationResult is ValidationResult.Accept }
                .maxByOrNull { it.finalScore })?.result

    }

    suspend fun resolveAll(track: LyricsTrack): List<LyricsDocument> =
        resolveWithVariants(track)
            .filter { it.passesArtistGate && it.validationResult is ValidationResult.Accept && it.confidence >= minConfidence }
            .map { it.result }

    suspend fun resolveRanked(track: LyricsTrack): List<RankedLyricsCandidate> =
        resolveWithVariants(track)

    // ─── Variant Retry Logic (Phase 3 + 4) ───────────────────────────────────

    private suspend fun resolveWithVariants(track: LyricsTrack): List<RankedLyricsCandidate> {
        val variants = LyricsQueryVariantGenerator.generate(track)

        // Attempt 1: original query, MAINSTREAM validation
        val firstPass = collectAndRank(variants[0], ValidationMode.MAINSTREAM)
        val firstPassValid = firstPass.filter {
            it.passesArtistGate &&
            it.validationResult is ValidationResult.Accept &&
            it.confidence >= minConfidence
        }

        if (firstPassValid.isNotEmpty()) {
            if (debugLogging) printDebugLog(track, firstPass)
            return firstPass
        }

        // Attempt 2+: try remaining variants with OBSCURE mode (max 3 more retries)
        val allCandidates = firstPass.toMutableList()

        for (variant in variants.drop(1).take(3)) {
            if (variant.title == track.title && variant.artist == track.artist) continue

            val retryCandidates = collectAndRank(variant, ValidationMode.OBSCURE)
            val retryValid = retryCandidates.filter {
                it.passesArtistGate &&
                it.validationResult is ValidationResult.Accept &&
                it.confidence >= minConfidence
            }

            allCandidates.addAll(retryCandidates)

            if (retryValid.isNotEmpty()) break
        }

        if (debugLogging) printDebugLog(track, allCandidates)
        return allCandidates.sortedByDescending { it.finalScore }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private suspend fun collectAndRank(
        track: LyricsTrack,
        validationMode: ValidationMode,
    ): List<RankedLyricsCandidate> = coroutineScope {
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

                val fetchStart = System.currentTimeMillis()
                val result = withTimeoutOrNull(timeoutMs) {
                    runCatching { provider.search(track) }.getOrNull()
                }
                val providerLatencyMs = System.currentTimeMillis() - fetchStart

                if (result == null || result.isEmpty) {
                    channel.send(null)
                    return@launch
                }

                val breakdown = MatchScorer.breakdown(
                    track = track,
                    candidateTitle = result.providerName.let { track.title }, // use request title
                    candidateArtist = track.artist,
                    candidateDurationSec = track.durationSec,
                )

                val validation = LyricsIntegrityValidator.validate(
                    track = track,
                    result = result,
                    candidateTitle = track.title,
                    candidateArtist = track.artist,
                    candidateDurationMs = if (track.durationMs > 0) track.durationMs else -1L,
                    mode = validationMode,
                )

                // Phase 10 scoring formula:
                // syncTierWeight × 0.55 + metadataAccuracy × 0.25 + providerReliability × 0.10 + confidence × 0.10
                val syncWeight = result.syncType.preferenceScore().toFloat() / SyncType.WORD_SYNCED.preferenceScore().toFloat()
                val providerReliability = provider.category.reliabilityWeight
                val finalScore = (syncWeight * 0.55f) +
                                 (breakdown.finalScore * 0.25f) +
                                 (providerReliability * 0.10f) +
                                 (breakdown.finalScore * 0.10f)

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
                        validationMode = validationMode,
                        providerLatencyMs = providerLatencyMs,
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

                // Early exit: WORD_SYNCED high-confidence result in MAINSTREAM mode
                val accepted = candidate.passesArtistGate &&
                               candidate.validationResult is ValidationResult.Accept &&
                               candidate.confidence >= minConfidence

                if (accepted && candidate.syncType == SyncType.WORD_SYNCED && candidate.finalScore >= 0.80f) {
                    jobs.forEach { it.cancel() }
                    // Drain remaining nulls so channel doesn't block
                    val remaining = orderedProviders.size - received
                    repeat(remaining) { channel.tryReceive() }
                    break
                }
            }
        }

        candidates.sortedByDescending { it.finalScore }
    }

    // ─── Debug logging ────────────────────────────────────────────────────────

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

                sb.appendLine("  [${i + 1}] Provider:    ${c.providerName}  $artistGateTag  [${c.validationMode}]")
                sb.appendLine("       SyncType:    ${c.syncType}")
                sb.appendLine("       TitleScore:  ${"%.2f".format(c.titleScore)}")
                sb.appendLine("       ArtistScore: ${"%.2f".format(c.artistScore)}")
                sb.appendLine("       DurScore:    ${"%.2f".format(c.durationScore)}")
                sb.appendLine("       Confidence:  ${"%.2f".format(c.confidence)}")
                sb.appendLine("       Latency:     ${c.providerLatencyMs}ms")
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
