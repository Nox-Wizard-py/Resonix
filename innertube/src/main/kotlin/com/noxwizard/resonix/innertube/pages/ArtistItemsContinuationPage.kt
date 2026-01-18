package com.noxwizard.resonix.innertube.pages

import com.noxwizard.resonix.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)

