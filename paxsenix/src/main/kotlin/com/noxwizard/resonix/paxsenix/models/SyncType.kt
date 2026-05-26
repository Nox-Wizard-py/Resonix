package com.noxwizard.resonix.paxsenix.models

enum class SyncType {
    UNSYNCED,
    LINE_SYNCED,
    WORD_SYNCED,
    SYLLABLE_SYNCED;

    val isSynced: Boolean get() = this != UNSYNCED

    fun preferenceScore(): Int = when (this) {
        SYLLABLE_SYNCED -> 4
        WORD_SYNCED     -> 3
        LINE_SYNCED     -> 2
        UNSYNCED        -> 1
    }
}
