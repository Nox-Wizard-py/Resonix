package com.noxwizard.resonix.innertube.models.body

import com.noxwizard.resonix.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)

