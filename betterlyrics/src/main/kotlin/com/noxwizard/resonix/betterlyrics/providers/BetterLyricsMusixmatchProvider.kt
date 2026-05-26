package com.noxwizard.resonix.betterlyrics.providers

import com.noxwizard.resonix.betterlyrics.BetterLyrics
import com.noxwizard.resonix.betterlyrics.models.LyricsDocument
import com.noxwizard.resonix.betterlyrics.models.LyricsLine
import com.noxwizard.resonix.betterlyrics.models.ProviderCapabilities
import com.noxwizard.resonix.betterlyrics.models.ProviderCategory
import com.noxwizard.resonix.betterlyrics.models.ProviderMetadata
import com.noxwizard.resonix.betterlyrics.models.SyncType
import com.noxwizard.resonix.betterlyrics.models.WordTiming

/**
 * Uses the BetterLyrics TTML pipeline which is backed by MusixMatch synchronization.
 *
 * BetterLyrics' MusixMatch-sourced lyrics are known for extremely accurate
 * synced timings — particularly for Bollywood and international content.
 *
 * Returns a [LyricsDocument] with word-level precision when available.
 */
class BetterLyricsMusixmatchProvider : BetterLyricsProvider {

    override val name: String = "BetterLyrics MusixMatch"
    override val priority: Int = 15

    private val provider = ProviderMetadata(
        providerName = "BetterLyrics MusixMatch",
        sourceName = "MusixMatch",
        category = ProviderCategory.PREMIUM,
        capabilities = ProviderCapabilities(
            supportsWordSync = true,  // when TTML contains span timing
            supportsLineSync = true,
            supportsKaraoke = true,
        ),
    )

    override suspend fun fetch(
        title: String,
        artist: String,
        durationSec: Int,
        spotifyTrackId: String?,
    ): LyricsDocument? = runCatching {
        // BetterLyrics TTML API is backed by MusixMatch internally
        val ttml = BetterLyrics.fetchTTML(
            artist = artist,
            title = title,
            duration = durationSec,
        ) ?: return null

        // Parse to structured document using the extended TTMLParser
        val doc = com.noxwizard.resonix.betterlyrics.TTMLParser.parseTTMLDocument(ttml)
        if (doc.isEmpty) return null

        // Re-stamp the provider metadata with MusixMatch identity
        doc.copy(
            provider = provider.copy(
                capabilities = provider.capabilities.copy(
                    supportsWordSync = doc.hasWordSync,
                ),
            ),
        )
    }.getOrNull()
}
