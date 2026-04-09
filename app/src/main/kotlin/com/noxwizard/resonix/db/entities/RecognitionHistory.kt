package com.noxwizard.resonix.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_history")
data class RecognitionHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val coverArtUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)
