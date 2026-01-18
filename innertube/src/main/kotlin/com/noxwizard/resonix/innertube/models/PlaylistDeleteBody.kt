package com.noxwizard.resonix.innertube.models.body

import com.noxwizard.resonix.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)

