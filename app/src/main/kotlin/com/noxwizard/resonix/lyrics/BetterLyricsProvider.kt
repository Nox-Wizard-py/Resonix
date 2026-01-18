package com.noxwizard.resonix.lyrics

import android.content.Context
import com.noxwizard.resonix.betterlyrics.BetterLyrics
import com.noxwizard.resonix.constants.EnableBetterLyricsKey
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, callback)
    }
}


