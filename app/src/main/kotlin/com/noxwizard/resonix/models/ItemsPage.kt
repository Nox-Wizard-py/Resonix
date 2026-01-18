package com.noxwizard.resonix.models

import com.noxwizard.resonix.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)


