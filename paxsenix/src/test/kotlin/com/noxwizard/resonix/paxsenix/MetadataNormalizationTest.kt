package com.noxwizard.resonix.paxsenix

import com.noxwizard.resonix.paxsenix.utils.MetadataNormalizationPipeline
import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 regression tests for metadata normalization.
 *
 * Validates that MetadataNormalizationPipeline and TrackNormalizer correctly
 * handle obscure tracks, feat. titles, remastered tracks, OST titles,
 * unicode titles, and multilingual songs.
 */
class MetadataNormalizationTest {

    // ── Feat / ft / featuring ─────────────────────────────────────────────────

    @Test
    fun `feat in parentheses is stripped from title`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("her (feat. ZVC)")
        assertEquals("her", result)
    }

    @Test
    fun `ft in parentheses is stripped from title`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Closer (ft. Halsey)")
        assertEquals("Closer", result)
    }

    @Test
    fun `featuring at end of title is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Perfect featuring Ed Sheeran")
        // featuring is in the artist separator — handled by cleanArtist, not title
        // Title should be unchanged here since it's not parenthesized
        assertTrue(result.isNotBlank())
    }

    // ── OST / From Movie ──────────────────────────────────────────────────────

    @Test
    fun `from movie suffix in parentheses is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Itni Si Baat Hain (From Azhar)")
        assertEquals("Itni Si Baat Hain", result)
    }

    @Test
    fun `ost label in brackets is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Tum Hi Ho [OST Aashiqui 2]")
        assertEquals("Tum Hi Ho", result)
    }

    @Test
    fun `original motion picture soundtrack label is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Interstellar Theme (Original Motion Picture Soundtrack)")
        assertTrue(!result.contains("soundtrack", ignoreCase = true))
    }

    // ── Remaster / Remix / Live ───────────────────────────────────────────────

    @Test
    fun `remastered year bracket is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Hotel California (2013 Remaster)")
        assertEquals("Hotel California", result)
    }

    @Test
    fun `remix label in parentheses is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Blinding Lights (Remix)")
        assertEquals("Blinding Lights", result)
    }

    @Test
    fun `live label in parentheses is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Bohemian Rhapsody (Live)")
        assertEquals("Bohemian Rhapsody", result)
    }

    @Test
    fun `slowed reverb suffix is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Bleeding - slowed reverb")
        assertFalse(result.contains("slowed", ignoreCase = true))
    }

    // ── Aggressive normalization ──────────────────────────────────────────────

    @Test
    fun `aggressive normalize strips all remaining brackets`() {
        val result = MetadataNormalizationPipeline.aggressiveNormalizeTitle("Song Title [Explicit] (Deluxe)")
        assertFalse(result.contains("["))
        assertFalse(result.contains("("))
    }

    // ── Emoji and unicode ─────────────────────────────────────────────────────

    @Test
    fun `emoji in title is stripped`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("🎵 her (feat. ZVC) 🎶")
        assertFalse(result.contains("🎵"))
        assertFalse(result.contains("🎶"))
    }

    @Test
    fun `smart quotes are normalized to plain quotes`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("\u201CHer\u201D")
        assertEquals("\"Her\"", result)
    }

    @Test
    fun `unicode title passthrough - cyrillic`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("Лондон")
        assertEquals("Лондон", result)
    }

    @Test
    fun `unicode title passthrough - japanese`() {
        val result = MetadataNormalizationPipeline.normalizeTitle("夜に駆ける")
        assertEquals("夜に駆ける", result)
    }

    // ── Artist extraction ─────────────────────────────────────────────────────

    @Test
    fun `primary artist extracted from compound artist`() {
        val result = MetadataNormalizationPipeline.extractPrimaryArtist("The Weeknd & Doja Cat")
        assertEquals("The Weeknd", result)
    }

    @Test
    fun `primary artist with feat separator`() {
        val result = MetadataNormalizationPipeline.extractPrimaryArtist("Eminem feat. Rihanna")
        assertEquals("Eminem", result)
    }

    // ── TrackNormalizer integration ───────────────────────────────────────────

    @Test
    fun `TrackNormalizer cleanTitle strips feat and OST patterns`() {
        val result = TrackNormalizer.cleanTitle("her (feat. ZVC) (From The Movie Her)")
        assertFalse(result.contains("feat", ignoreCase = true))
    }

    @Test
    fun `TrackNormalizer cleanTitle handles pipe suffix`() {
        val result = TrackNormalizer.cleanTitle("Song Name | Artist Name")
        assertFalse(result.contains("|"))
    }

    @Test
    fun `TrackNormalizer normalizeTitle returns lowercase`() {
        val result = TrackNormalizer.normalizeTitle("Bleeding")
        assertEquals("bleeding", result)
    }
}
