package com.noxwizard.resonix.betterlyrics.models

enum class SyncType {
    UNSYNCED,
    LINE_SYNCED,
    WORD_SYNCED,
    SYLLABLE_SYNCED;

    val preferenceScore: Int get() = ordinal + 1
}
