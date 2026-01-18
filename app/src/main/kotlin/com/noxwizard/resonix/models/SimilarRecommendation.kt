package com.noxwizard.resonix.models

import com.noxwizard.resonix.innertube.models.YTItem
import com.noxwizard.resonix.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)


