package com.noxwizard.resonix.paxsenix.utils

import com.noxwizard.resonix.paxsenix.models.LyricsTrack

/**
 * Generates a prioritized list of [LyricsTrack] query variants for progressive fallback.
 *
 * When the primary query fails to return valid candidates, the resolver retries
 * with progressively more aggressively normalized queries.
 *
 * Variant order (attempt in this order):
 *  1. Original — raw metadata as-is
 *  2. Normalized — feat/OST/from movie/emoji removed
 *  3. Aggressive — all brackets stripped, trailing decorators removed
 *  4. Title without decorators + primary artist
 *  5. Title only (no artist constraint)
 *  6. Normalized title + primary artist
 *  7. Aggressive title + primary artist (maximum stripping)
 */
object LyricsQueryVariantGenerator {

    /**
     * Generates up to 7 progressively normalized [LyricsTrack] variants from [original].
     * Deduplicates adjacent variants that produce the same (title, artist) pair.
     */
    fun generate(original: LyricsTrack): List<LyricsTrack> {
        val meta = MetadataNormalizationPipeline.normalize(original.title, original.artist)

        val variants = listOf(
            // 1. Original as-is
            original,

            // 2. Standard normalized
            original.copy(
                title = meta.normalizedTitle,
                artist = meta.normalizedArtist,
            ),

            // 3. Aggressive normalized
            original.copy(
                title = meta.aggressiveTitle,
                artist = meta.normalizedArtist,
            ),

            // 4. Normalized title + primary artist only
            original.copy(
                title = meta.normalizedTitle,
                artist = meta.primaryArtist,
            ),

            // 5. Title only — no artist constraint (pass empty to widen results)
            original.copy(
                title = meta.normalizedTitle,
                artist = "",
            ),

            // 6. Aggressive title + full normalized artist
            original.copy(
                title = meta.aggressiveTitle,
                artist = meta.primaryArtist,
            ),

            // 7. Aggressive title, empty artist — maximum fallback
            original.copy(
                title = meta.aggressiveTitle,
                artist = "",
            ),
        )

        // Deduplicate: skip consecutive variants with identical (title, artist) pairs
        val deduped = mutableListOf<LyricsTrack>()
        for (v in variants) {
            if (deduped.isEmpty() || deduped.last().let { it.title != v.title || it.artist != v.artist }) {
                if (v.title.isNotBlank()) deduped.add(v)
            }
        }
        return deduped
    }
}
