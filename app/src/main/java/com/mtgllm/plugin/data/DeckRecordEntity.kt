package com.mtgllm.plugin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deck_records")
data class DeckRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timestamp: Long,
    val fileName: String,
    val cardCount: Int,
    val resultText: String,
    val rawInput: String = "" // Added to allow re-processing
)
