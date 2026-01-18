package com.noxwizard.resonix.innertube.models.body

import com.noxwizard.resonix.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
    val context: Context,
    val videoIds: List<String>?,
    val playlistId: String?,
)

