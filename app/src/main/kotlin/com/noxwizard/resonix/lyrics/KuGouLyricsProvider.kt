package com.noxwizard.resonix.lyrics

import android.content.Context
import com.noxwizard.resonix.kugou.KuGou
import com.noxwizard.resonix.constants.EnableKugouKey
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int
    ): Result<String> =
        KuGou.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit
    ) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, callback)
    }
}


