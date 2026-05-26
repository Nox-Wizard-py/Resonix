package com.noxwizard.resonix.paxsenix.parser

import com.noxwizard.resonix.paxsenix.models.SyncType

object MusixMatchLyricsParser {

    fun parse(rawLrc: String): LrcParser.ParseResult {
        val cleaned = rawLrc
            .replace("\\n", "\n")
            .trim()

        val result = LrcParser.parse(cleaned)

        val syncType = if (result.lines.isNotEmpty()) SyncType.LINE_SYNCED else SyncType.UNSYNCED
        return result.copy(syncType = syncType)
    }
}
