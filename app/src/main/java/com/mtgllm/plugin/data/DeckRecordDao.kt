package com.mtgllm.plugin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface DeckRecordDao {
    @Query("SELECT * FROM deck_records ORDER BY timestamp DESC")
    suspend fun getAllRecords(): List<DeckRecordEntity>

    @Insert
    suspend fun insertRecord(record: DeckRecordEntity)

    @Query("DELETE FROM deck_records WHERE id NOT IN (SELECT id FROM deck_records ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun trimRecords(limit: Int)

    @Query("DELETE FROM deck_records")
    suspend fun clearAll()
}
