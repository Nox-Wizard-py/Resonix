package com.noxwizard.resonix.betterlyrics.providers

import com.noxwizard.resonix.betterlyrics.BetterLyrics
import com.noxwizard.resonix.betterlyrics.TTMLParser
import com.noxwizard.resonix.betterlyrics.models.LyricsDocument
import com.noxwizard.resonix.betterlyrics.models.ProviderCapabilities
import com.noxwizard.resonix.betterlyrics.models.ProviderCategory
import com.noxwizard.resonix.betterlyrics.models.ProviderMetadata

/**
 * Direct TTML parsing provider.
 *
 * Fetches raw TTML from the BetterLyrics API and parses into [LyricsDocument],
 * preserving word timing, line end times, background vocals, and instrumental gaps.
 *
 * Category: ENHANCED (not PREMIUM because sync quality depends on the TTML source).
 */
class BetterLyricsTTMLProvider : BetterLyricsProvider {

    override val name: String = "BetterLyrics TTML"
    override val priority: Int = 20

    override suspend fun fetch(
        title: String,
        artist: String,
        durationSec: Int,
        spotifyTrackId: String?,
    ): LyricsDocument? = runCatching {
        val ttml = BetterLyrics.fetchTTML(
            artist = artist,
            title = title,
            duration = durationSec,
        ) ?: return null

        val doc = TTMLParser.parseTTMLDocument(ttml)
        if (doc.isEmpty) null else doc
    }.getOrNull()
}
