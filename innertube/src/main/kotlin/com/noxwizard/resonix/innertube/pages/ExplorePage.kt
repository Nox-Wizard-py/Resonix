package com.noxwizard.resonix.innertube.pages

import com.noxwizard.resonix.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)

