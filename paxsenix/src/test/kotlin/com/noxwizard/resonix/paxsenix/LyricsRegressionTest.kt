package com.noxwizard.resonix.paxsenix

import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.utils.LyricsQueryVariantGenerator
import com.noxwizard.resonix.paxsenix.utils.MetadataNormalizationPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 regression tests for obscure song recovery.
 *
 * Validates that query variant generation produces normalized fallbacks
 * that would recover obscure tracks, feat. titles, remastered tracks,
 * OST tracks, unicode titles, and multilingual songs.
 *
 * These are unit tests for the normalization + variant layers.
 * Full integration tests require a real provider connection.
 */
class LyricsRegressionTest {

    // ── "her (feat. ZVC)" ─────────────────────────────────────────────────────

    @Test
    fun `her feat ZVC - normalized title strips feat`() {
        val track = LyricsTrack(title = "her (feat. ZVC)", artist = "Various", durationMs = 200_000L)
        val variants = LyricsQueryVariantGenerator.generate(track)
        assertTrue("Expected at least 2 variants", variants.size >= 2)
        // Variant 2+ should have feat stripped
        val hasCleanHer = variants.any { it.title.equals("her", ignoreCase = true) }
        assertTrue("Expected a variant with title='her'", hasCleanHer)
    }

    @Test
    fun `her feat ZVC - no variant has feat in title`() {
        val track = LyricsTrack(title = "her (feat. ZVC)", artist = "Various", durationMs = 200_000L)
        val variants = LyricsQueryVariantGenerator.generate(track)
        // After normalization, no variant beyond the original should contain feat
        val normalized = variants.drop(1)
        assertTrue("Expected normalized variants", normalized.isNotEmpty())
        normalized.forEach { v ->
            assertFalse(
                "Normalized variant '${v.title}' still contains feat",
                v.title.contains("feat", ignoreCase = true)
            )
        }
    }

    // ── "Itni Si Baat Hain (From Azhar)" ────────────────────────────────────

    @Test
    fun `Itni Si Baat Hain From Azhar - from suffix stripped in normalized variant`() {
        val track = LyricsTrack(
            title = "Itni Si Baat Hain (From Azhar)",
            artist = "KK",
            durationMs = 260_000L
        )
        val variants = LyricsQueryVariantGenerator.generate(track)
        val hasCleanTitle = variants.any {
            it.title.equals("Itni Si Baat Hain", ignoreCase = true)
        }
        assertTrue("Expected variant with stripped 'From Azhar' suffix", hasCleanTitle)
    }

    // ── "Bleeding" — short undecorated title ─────────────────────────────────

    @Test
    fun `Bleeding - generates non-empty variants`() {
        val track = LyricsTrack(title = "Bleeding", artist = "Ed Sheeran", durationMs = 215_000L)
        val variants = LyricsQueryVariantGenerator.generate(track)
        assertTrue("Expected at least 1 variant", variants.isNotEmpty())
        // Original should be first
        assertEquals("Bleeding", variants[0].title)
    }

    @Test
    fun `Bleeding slowed reverb - slowed suffix stripped`() {
        val track = LyricsTrack(title = "Bleeding - slowed reverb", artist = "Ed Sheeran", durationMs = 215_000L)
        val variants = LyricsQueryVariantGenerator.generate(track)
        val hasClean = variants.any { it.title.equals("Bleeding", ignoreCase = true) }
        assertTrue("Expected a variant with just 'Bleeding'", hasClean)
    }

    // ── Unicode title — Cyrillic ──────────────────────────────────────────────

    @Test
    fun `Cyrillic title passes through normalization unchanged`() {
        val title = "Лондон"
        val normalized = MetadataNormalizationPipeline.normalizeTitle(title)
        assertEquals("Cyrillic title should be unchanged by normalization", title, normalized)
    }

    // ── Remastered track ──────────────────────────────────────────────────────

    @Test
    fun `Hotel California 2013 Remaster - remaster stripped`() {
        val track = LyricsTrack(
            title = "Hotel California (2013 Remaster)",
            artist = "Eagles",
            durationMs = 391_000L
        )
        val variants = LyricsQueryVariantGenerator.generate(track)
        val hasClean = variants.any { it.title.equals("Hotel California", ignoreCase = true) }
        assertTrue("Expected variant with 'Hotel California' only", hasClean)
    }

    // ── Multilingual (Hindi + English) ───────────────────────────────────────

    @Test
    fun `Hindi OST title - OST stripped in normalized variant`() {
        val track = LyricsTrack(
            title = "Tum Hi Ho (OST Aashiqui 2)",
            artist = "Arijit Singh",
            durationMs = 262_000L
        )
        val variants = LyricsQueryVariantGenerator.generate(track)
        val hasClean = variants.any { it.title.equals("Tum Hi Ho", ignoreCase = true) }
        assertTrue("Expected variant with stripped OST suffix", hasClean)
    }

    // ── Query variant deduplication ───────────────────────────────────────────

    @Test
    fun `variants are deduplicated for simple titles`() {
        val track = LyricsTrack(title = "Bleeding", artist = "Ed Sheeran", durationMs = 215_000L)
        val variants = LyricsQueryVariantGenerator.generate(track)
        // No two consecutive variants should have identical (title, artist) pairs
        for (i in 1 until variants.size) {
            val same = variants[i].title == variants[i - 1].title &&
                       variants[i].artist == variants[i - 1].artist
            assertFalse("Consecutive duplicate variants at index $i", same)
        }
    }

    @Test
    fun `variants list is non-empty for all inputs`() {
        val tracks = listOf(
            LyricsTrack("her (feat. ZVC)", "Various"),
            LyricsTrack("Itni Si Baat Hain (From Azhar)", "KK"),
            LyricsTrack("Bleeding", "Ed Sheeran"),
            LyricsTrack("夜に駆ける", "YOASOBI"),
            LyricsTrack("Hotel California (2013 Remaster)", "Eagles"),
        )
        tracks.forEach { track ->
            val variants = LyricsQueryVariantGenerator.generate(track)
            assertTrue("Expected non-empty variants for '${track.title}'", variants.isNotEmpty())
        }
    }
}
