package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.lrclib.LrcLib
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Adapter wrapping the existing :lrclib module.
 * LRCLib returns synced LRC strings.
 */
class LrcLibProvider : LyricsProvider {

    override val name: String = "LRCLib"
    override val category: LyricsProviderCategory = LyricsProviderCategory.STANDARD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val lrcString = LrcLib.getLyrics(
            title = track.title,
            artist = track.artist,
            duration = track.durationSec,
            album = track.album,
        ).getOrNull() ?: return null

        val sanitized = LyricsPayloadSanitizer.sanitize(lrcString)
        val parsed = LrcParser.parse(sanitized)
        if (parsed.lines.isEmpty()) return null

        val confidence = MatchScorer.score(
            track = track,
            candidateTitle = track.title,
            candidateArtist = track.artist,
            candidateDurationSec = track.durationSec,
        )

        LyricsDocument(
            rawText = sanitized,
            lines = parsed.lines,
            providerName = name,
            providerCategory = category,
            syncType = parsed.syncType,
        )
    }.getOrNull()
}
