package com.noxwizard.resonix.lyrics

import android.content.Context
import com.noxwizard.resonix.lrclib.LrcLib
import com.noxwizard.resonix.constants.EnableLrcLibKey
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}


