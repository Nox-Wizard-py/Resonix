package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack

interface LyricsProvider {
    val name: String
    val category: LyricsProviderCategory

    suspend fun search(track: LyricsTrack): LyricsDocument?
}
