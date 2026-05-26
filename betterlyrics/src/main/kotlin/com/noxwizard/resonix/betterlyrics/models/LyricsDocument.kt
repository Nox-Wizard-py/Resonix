package com.noxwizard.resonix.betterlyrics.models

/**
 * Top-level render-ready lyrics document.
 *
 * This is the primary output type from all betterlyrics providers.
 * The rendering layer consumes [lines] directly — no LRC conversion needed.
 */
data class LyricsDocument(
    val provider: ProviderMetadata,
    val syncType: SyncType,
    val lines: List<LyricsLine>,
    val hasWordSync: Boolean = lines.any { it.hasWordSync },
    val hasBackgroundVocals: Boolean = lines.any { it.isBackgroundVocal },
    val hasTranslations: Boolean = lines.any { it.hasTranslation },
    val hasRomanizedLyrics: Boolean = lines.any { it.hasRomanized },
    val hasInstrumentalGaps: Boolean = lines.any { it.isInstrumental },
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        fun empty(provider: ProviderMetadata) = LyricsDocument(
            provider = provider,
            syncType = SyncType.UNSYNCED,
            lines = emptyList(),
        )
    }
}
