package com.noxwizard.resonix.paxsenix.models

data class LyricsLine(
    val text: String,
    val startMs: Long,
    val endMs: Long = -1L,
    val words: List<WordLine> = emptyList(),
    val isTranslation: Boolean = false,
    val isRomanization: Boolean = false,
) {
    val hasWordSync: Boolean get() = words.isNotEmpty()
}
