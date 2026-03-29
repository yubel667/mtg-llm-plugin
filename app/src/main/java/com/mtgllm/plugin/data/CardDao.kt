package com.mtgllm.plugin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE name = :name")
    suspend fun getCard(name: String): CardEntity?

    @Query("SELECT * FROM cards WHERE name IN (:names)")
    suspend fun getCards(names: List<String>): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<CardEntity>)

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCardCount(): Int

    @Query("DELETE FROM cards")
    suspend fun clearAll()
}
