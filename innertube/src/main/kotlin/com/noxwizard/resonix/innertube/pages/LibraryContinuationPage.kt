package com.noxwizard.resonix.innertube.pages

import com.noxwizard.resonix.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)

