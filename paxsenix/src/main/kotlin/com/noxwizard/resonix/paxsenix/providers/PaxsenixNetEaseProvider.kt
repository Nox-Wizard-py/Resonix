package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Paxsenix NetEase provider.
 *
 * Delegates to PaxsenixLyrics.getNeteaseLyrics() — the AT-identical HTTP layer —
 * which handles search, duration matching (< 10s tolerance), klyric→lrc fallback.
 */
class PaxsenixNetEaseProvider(
    @Suppress("unused") private val confidenceThreshold: Float = 0.35f,
) : LyricsProvider {

    override val name: String = "NetEase (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.PREMIUM_WORD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val raw = PaxsenixLyrics.getNeteaseLyrics(
            title = track.title,
            artist = track.artist,
            durationSeconds = track.durationSec,
        ).getOrNull()

        if (raw.isNullOrBlank()) return@runCatching null

        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = LrcParser.parse(sanitized)

        if (parsed.lines.isEmpty() || parsed.lines.none { it.text.isNotBlank() }) return@runCatching null

        val syncType = if (parsed.syncType == SyncType.WORD_SYNCED) SyncType.WORD_SYNCED else SyncType.LINE_SYNCED

        LyricsDocument(
            rawText = sanitized,
            lines = parsed.lines,
            providerName = name,
            providerCategory = category,
            syncType = syncType,
        )
    }.getOrNull()
}
