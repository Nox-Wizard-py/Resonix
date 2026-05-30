package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.MusixMatchLyricsParser
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Paxsenix Musixmatch provider.
 *
 * Delegates to PaxsenixLyrics.getMusixmatchLyrics() — the AT-identical HTTP layer —
 * which tries word-type first, then falls back to default (line-synced).
 */
class MusixMatchProvider(
    @Suppress("unused") private val confidenceThreshold: Float = 0.35f,
) : LyricsProvider {

    override val name: String = "MusixMatch (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val raw = PaxsenixLyrics.getMusixmatchLyrics(
            title = track.title,
            artist = track.artist,
            durationSeconds = track.durationSec,
        ).getOrNull()

        if (raw.isNullOrBlank()) return@runCatching null

        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = MusixMatchLyricsParser.parse(sanitized)

        if (parsed.lines.isEmpty() || parsed.lines.none { it.text.isNotBlank() }) return@runCatching null

        LyricsDocument(
            rawText = sanitized,
            lines = parsed.lines,
            providerName = name,
            providerCategory = category,
            syncType = parsed.syncType,
        )
    }.getOrNull()
}
