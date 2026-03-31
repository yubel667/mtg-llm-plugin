package com.mtgllm.plugin.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptDao {
    @Query("SELECT * FROM prompts ORDER BY name ASC")
    fun getAllPromptsFlow(): Flow<List<PromptEntity>>

    @Query("SELECT * FROM prompts")
    suspend fun getAllPrompts(): List<PromptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompt(prompt: PromptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrompts(prompts: List<PromptEntity>)

    @Update
    suspend fun updatePrompt(prompt: PromptEntity)

    @Delete
    suspend fun deletePrompt(prompt: PromptEntity)

    @Query("DELETE FROM prompts")
    suspend fun deleteAll()

    @Query("SELECT * FROM prompts WHERE id = :id")
    suspend fun getPromptById(id: Int): PromptEntity?
}
