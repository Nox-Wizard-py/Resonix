package com.noxwizard.resonix.paxsenix.parser

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.models.WordLine

private val TIME_TAG_REGEX = Regex("""^(\d{2}):(\d{2})\.(\d{2})$""")

object SpotifyLyricsParser {

    data class SpotifyLine(
        val timeTag: String,
        val words: String,
    )

    fun parse(lines: List<SpotifyLine>, syncTypeStr: String): LrcParser.ParseResult {
        val lyricsLines = lines.map { line ->
            val ms = parseTimeTag(line.timeTag)
            LyricsLine(text = line.words.trim(), startMs = ms)
        }.sortedBy { it.startMs }

        val syncType = when (syncTypeStr.uppercase()) {
            "LINE_SYNCED" -> SyncType.LINE_SYNCED
            "WORD_SYNCED" -> SyncType.WORD_SYNCED
            else          -> if (lyricsLines.isNotEmpty()) SyncType.LINE_SYNCED else SyncType.UNSYNCED
        }

        return LrcParser.ParseResult(lines = lyricsLines, syncType = syncType)
    }

    private fun parseTimeTag(tag: String): Long {
        val match = TIME_TAG_REGEX.matchEntire(tag.trim()) ?: return 0L
        val (min, sec, cs) = match.destructured
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + cs.toLong() * 10
    }
}
