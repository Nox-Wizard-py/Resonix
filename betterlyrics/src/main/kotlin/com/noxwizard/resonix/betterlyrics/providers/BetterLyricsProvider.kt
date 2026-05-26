package com.noxwizard.resonix.betterlyrics.providers

import com.noxwizard.resonix.betterlyrics.models.LyricsDocument

/** Uniform contract for all betterlyrics structured providers. */
interface BetterLyricsProvider {
    val name: String
    val priority: Int get() = 50

    suspend fun fetch(
        title: String,
        artist: String,
        durationSec: Int = -1,
        spotifyTrackId: String? = null,
    ): LyricsDocument?
}
