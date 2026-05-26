package com.noxwizard.resonix.paxsenix.models

/**
 * The unified, fully parsed lyrics document emitted by all providers.
 * Eliminates parser duplication across the application.
 */
data class LyricsDocument(
    /** The original raw response from the provider. */
    val rawText: String,
    
    /** The parsed lines of lyrics. */
    val lines: List<LyricsLine>,
    
    /** The name of the provider that generated this document. */
    val providerName: String,
    
    /** The category of the provider, used for ranking. */
    val providerCategory: LyricsProviderCategory,
    
    /** The synchronization quality. */
    val syncType: SyncType,
    
    /** Additional metadata such as copyright or credit text. */
    val copyright: String? = null
) {
    val isWordSynced: Boolean get() = syncType == SyncType.WORD_SYNCED
    val isLineSynced: Boolean get() = syncType == SyncType.LINE_SYNCED || isWordSynced
    val isEmpty: Boolean get() = lines.isEmpty()
    
    companion object {
        val EMPTY = LyricsDocument(
            rawText = "",
            lines = emptyList(),
            providerName = "None",
            providerCategory = LyricsProviderCategory.FALLBACK,
            syncType = SyncType.UNSYNCED
        )
    }
}
