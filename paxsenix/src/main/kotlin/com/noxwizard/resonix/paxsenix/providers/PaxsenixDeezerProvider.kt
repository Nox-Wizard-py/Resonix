package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer

class PaxsenixDeezerProvider(
    private val confidenceThreshold: Float = 0.5f,
) : LyricsProvider {

    override val name: String = "Deezer (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.STANDARD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val cleanTitle = TrackNormalizer.cleanTitle(track.title)
        val cleanArtist = TrackNormalizer.cleanArtist(track.artist)

        val raw = PaxsenixLyrics.getLyrics(
            cleanTitle, cleanArtist, track.durationSec
        ).getOrNull() ?: return@runCatching null

        if (raw.isBlank()) return@runCatching null
        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = LrcParser.parse(sanitized)
        if (parsed.lines.isEmpty()) return@runCatching null

        val confidence = MatchScorer.score(track, track.title, track.artist, candidateDurationSec = track.durationSec)

        if (confidence < confidenceThreshold) return@runCatching null

        LyricsDocument(
            rawText = sanitized,
            lines = parsed.lines,
            providerName = name,
            providerCategory = category,
            syncType = parsed.syncType,
        )
    }.getOrNull()
}
