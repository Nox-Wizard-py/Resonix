package com.noxwizard.resonix.paxsenix.models

data class ProviderMetadata(
    val providerName: String,
    val sourceUrl: String? = null,
    val confidence: Float = 0f,
    val trackId: String? = null,
    val language: String? = null,
)
