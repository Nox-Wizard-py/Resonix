package com.noxwizard.resonix.innertube.models.body

import com.noxwizard.resonix.innertube.models.Context
import com.noxwizard.resonix.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)

