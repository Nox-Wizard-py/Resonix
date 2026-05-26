package com.noxwizard.resonix.betterlyrics.parser

import com.noxwizard.resonix.betterlyrics.api.SpotifyLyricsResponse
import com.noxwizard.resonix.betterlyrics.models.LyricsDocument
import com.noxwizard.resonix.betterlyrics.models.LyricsLine
import com.noxwizard.resonix.betterlyrics.models.ProviderCapabilities
import com.noxwizard.resonix.betterlyrics.models.ProviderCategory
import com.noxwizard.resonix.betterlyrics.models.ProviderMetadata
import com.noxwizard.resonix.betterlyrics.models.SyncType
import com.noxwizard.resonix.betterlyrics.models.WordTiming

/**
 * Parses the akashrchandran Spotify Lyrics API response into [LyricsDocument].
 *
 * The Spotify response uses:
 * - `startTimeMs` / `endTimeMs` as String milliseconds
 * - `words` as the line text
 * - `syllables` list (empty in most responses — reserved for future karaoke)
 * - `syncType` as "LINE_SYNCED" or "WORD_SYNCED"
 *
 * Spotify DTOs are NOT exposed outside this parser.
 */
internal object SpotifyLyricsParser {

    private val PROVIDER = ProviderMetadata(
        providerName = "BetterLyrics Spotify",
        sourceName = "Spotify",
        category = ProviderCategory.PREMIUM,
        capabilities = ProviderCapabilities(
            supportsWordSync = true,
            supportsLineSync = true,
            supportsKaraoke = true,
        ),
    )

    fun parse(response: SpotifyLyricsResponse): LyricsDocument {
        if (response.error || response.lines.isEmpty()) return LyricsDocument.empty(PROVIDER)

        val raw = response.lines.filter { it.words.isNotBlank() }
        if (raw.isEmpty()) return LyricsDocument.empty(PROVIDER)

        val structuredLines = raw.mapIndexed { index, line ->
            val startMs = line.startTimeMs.toLongOrNull() ?: 0L

            val endMs = run {
                val explicit = line.endTimeMs.toLongOrNull() ?: 0L
                if (explicit > 0L) explicit
                else if (index < raw.lastIndex) raw[index + 1].startTimeMs.toLongOrNull() ?: (startMs + 5_000L)
                else startMs + 5_000L
            }

            val words = if (line.syllables.isNotEmpty()) {
                // Distribute syllables evenly within the line window for future karaoke
                line.syllables.mapIndexed { si, syllable ->
                    val step = if (line.syllables.size > 1) (endMs - startMs) / line.syllables.size else 0L
                    WordTiming(
                        word = syllable,
                        startTimeMs = startMs + si * step,
                        endTimeMs = startMs + (si + 1) * step,
                        syllables = listOf(syllable),
                    )
                }
            } else emptyList()

            LyricsLine(
                text = line.words,
                startTimeMs = startMs,
                endTimeMs = endMs,
                words = words,
            )
        }

        val withGaps = LyricsLine.detectInstrumentalGaps(structuredLines)

        val syncType = when (response.syncType.uppercase()) {
            "WORD_SYNCED"     -> SyncType.WORD_SYNCED
            "LINE_SYNCED"     -> SyncType.LINE_SYNCED
            else              -> SyncType.UNSYNCED
        }

        return LyricsDocument(
            provider = PROVIDER,
            syncType = syncType,
            lines = withGaps,
        )
    }
}
