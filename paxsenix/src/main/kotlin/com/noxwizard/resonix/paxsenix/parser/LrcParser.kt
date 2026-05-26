package com.noxwizard.resonix.paxsenix.parser

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.SyncType

private val LRC_LINE_REGEX = Regex("""^\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)$""")

object LrcParser {

    data class ParseResult(
        val lines: List<LyricsLine>,
        val syncType: SyncType,
    )

    fun parse(lrc: String): ParseResult {
        val lines = mutableListOf<LyricsLine>()

        for (raw in lrc.lines()) {
            val match = LRC_LINE_REGEX.matchEntire(raw.trim()) ?: continue
            val (min, sec, centis, text) = match.destructured
            val ms = min.toLong() * 60_000 +
                    sec.toLong() * 1_000 +
                    centis.padEnd(3, '0').toLong()
            lines += LyricsLine(text = text.trim(), startMs = ms)
        }

        val sorted = lines.sortedBy { it.startMs }
        return ParseResult(
            lines = sorted,
            syncType = if (sorted.isNotEmpty()) SyncType.LINE_SYNCED else SyncType.UNSYNCED,
        )
    }

    fun toLrc(lines: List<LyricsLine>): String = buildString {
        lines.forEach { line ->
            val min = line.startMs / 60_000
            val sec = (line.startMs % 60_000) / 1_000
            val cs = (line.startMs % 1_000) / 10
            appendLine("[%02d:%02d.%02d]%s".format(min, sec, cs, line.text))
        }
    }
}
