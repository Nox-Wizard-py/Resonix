package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.parser.LrcParser
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Paxsenix KuGou provider.
 *
 * Delegates to PaxsenixLyrics.getKugouLyrics() — the HTTP layer —
 * which handles search via paxsenix kugou/search, duration matching (< 10s tolerance),
 * and extracts the "lyrics" field from kugou/lyrics response.
 *
 * Previously used the :kugou module directly; now uses the Paxsenix API endpoint.
 */
class KuGouProvider(
    @Suppress("unused") private val confidenceThreshold: Float = 0.35f,
) : LyricsProvider {

    override val name: String = "KuGou (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.STANDARD_SYNC

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val raw = PaxsenixLyrics.getKugouLyrics(
            title = track.title,
            artist = track.artist,
            durationSeconds = track.durationSec,
        ).getOrNull()

        if (raw.isNullOrBlank()) return@runCatching null

        val sanitized = LyricsPayloadSanitizer.sanitize(raw)
        val parsed = LrcParser.parse(sanitized)

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
