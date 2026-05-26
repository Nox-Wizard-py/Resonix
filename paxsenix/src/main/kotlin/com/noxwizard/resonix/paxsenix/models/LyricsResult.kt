package com.noxwizard.resonix.paxsenix.models

data class LyricsResult(
    val lines: List<LyricsLine>,
    val syncType: SyncType,
    val provider: ProviderMetadata,
    val rawLrc: String? = null,
    val isTranslated: Boolean = false,
    val isRomanized: Boolean = false,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        fun empty(providerName: String) = LyricsResult(
            lines = emptyList(),
            syncType = SyncType.UNSYNCED,
            provider = ProviderMetadata(providerName, confidence = 0f),
        )
    }
}
