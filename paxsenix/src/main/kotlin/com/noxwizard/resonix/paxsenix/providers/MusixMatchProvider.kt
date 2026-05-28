package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.MusixMatchLyricsParser
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer

class MusixMatchProvider(
    private val confidenceThreshold: Float = 0.5f,
) : LyricsProvider {

    override val name: String = "MusixMatch (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val cleanTitle = TrackNormalizer.cleanTitle(track.title)
        val cleanArtist = TrackNormalizer.cleanArtist(track.artist)

        val raw = PaxsenixLyrics.getMusixmatchLyrics(
            cleanTitle, cleanArtist, track.durationSec
        ).getOrNull() ?: return@runCatching null

        if (raw.isBlank()) return@runCatching null

        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = MusixMatchLyricsParser.parse(sanitized)
        if (parsed.lines.isEmpty()) return@runCatching null

        val confidence = MatchScorer.score(
            track = track,
            candidateTitle = track.title,
            candidateArtist = track.artist,
            candidateDurationSec = track.durationSec,
        )

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
