package com.noxwizard.resonix.lyrics

import android.content.Context
import com.noxwizard.resonix.constants.EnableSimpMusicKey
import com.noxwizard.resonix.lyrics.simpmusic.SimpMusicLyrics
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get

object SimpMusicLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableSimpMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = SimpMusicLyrics.getLyrics(id, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        SimpMusicLyrics.getAllLyrics(id, duration, callback)
    }
}
