package com.mtgllm.plugin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [PromptEntity::class], version = 1, exportSchema = false)
abstract class PromptDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao

    companion object {
        @Volatile
        private var INSTANCE: PromptDatabase? = null

        fun getDatabase(context: Context): PromptDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PromptDatabase::class.java,
                    "prompt_database"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let { database ->
                                populateDefaultPrompts(database.promptDao())
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateDefaultPrompts(promptDao: PromptDao) {
            val defaults = listOf(
                PromptEntity(
                    name = "EDH Power Level",
                    content = "Analyze this Commander deck. Estimate power level (1-10), evaluate mana curve, synergy with commander, and suggest 5 card swaps for power/consistency.",
                    isDefault = true,
                    position = 0
                ),
                PromptEntity(
                    name = "Deck Summary",
                    content = "Provide a concise summary of the deck's strategy, win conditions, key interactions, and most important 'Game Changer' cards.",
                    isDefault = true,
                    position = 1
                ),
                PromptEntity(
                    name = "Bracket Analysis",
                    content = "Perform an official Commander Bracket Analysis (1-5). Identify all 'Game Changer' cards, count them, and determine the bracket based on the 'Rule of 3' (1-2 GC = Bracket 3, 4+ GC = Bracket 4, etc.). Check for 2-card combos and fast mana.",
                    isDefault = true,
                    position = 2
                ),
                PromptEntity(
                    name = "Competitive Meta",
                    content = "Analyze for competitive meta. Identify wincons, key weaknesses, and suggest sideboard adjustments against top-tier decks.",
                    isDefault = true,
                    position = 3
                ),
                PromptEntity(
                    name = "Strategic Threats",
                    content = "Identify the top 3 cards/archetypes this deck struggles against. Suggest specific answers and explain the correct lines of play.",
                    isDefault = true,
                    position = 4
                ),
                PromptEntity(
                    name = "Budget Upgrades",
                    content = "Suggest 10 budget-friendly upgrades (under $5 each) with high impact. Explain why each card fits the strategy.",
                    isDefault = true,
                    position = 5
                )
            )
            promptDao.insertPrompts(defaults)
        }
    }
}
