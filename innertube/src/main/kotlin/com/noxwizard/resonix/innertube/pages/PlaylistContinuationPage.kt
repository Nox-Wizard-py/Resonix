package com.noxwizard.resonix.innertube.pages

import com.noxwizard.resonix.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)

