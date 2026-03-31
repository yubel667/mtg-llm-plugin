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
                    name = "Bracket Analysis",
                    content = "Perform an official Commander Bracket Analysis (1-5). Identify all 'Game Changer' cards (GC) and determine the bracket: Bracket 1 (Exhibition): 0 GC, no 2-card combos, no extra turns. Bracket 2 (Core): 0 GC, Precon-level. Bracket 3 (Upgraded): 1-3 GC, high synergy, no early combos. Bracket 4 (Optimized): 4+ GC, explosive starts, efficient combos. Bracket 5 (cEDH): Max power, tournament meta.",
                    isDefault = true,
                    position = 0
                ),
                PromptEntity(
                    name = "EDH Power Level",
                    content = "Estimate the deck's power level (1-10). Evaluate mana curve, synergy with commander, and consistency. Suggest 5 specific card swaps to improve performance.",
                    isDefault = true,
                    position = 1
                ),
                PromptEntity(
                    name = "Deck Summary",
                    content = "Provide a concise summary of the deck's strategy, primary win conditions, key card interactions, and most impactful cards.",
                    isDefault = true,
                    position = 2
                ),
                PromptEntity(
                    name = "Competitive Meta",
                    content = "Analyze for the current competitive meta. Identify wincons, key vulnerabilities, and suggest sideboard or mainboard adjustments against top-tier decks.",
                    isDefault = true,
                    position = 3
                ),
                PromptEntity(
                    name = "Strategic Threats",
                    content = "Identify the top 3 archetypes or cards this deck struggles against. Suggest specific answers and explain the optimal lines of play to counter them.",
                    isDefault = true,
                    position = 4
                ),
                PromptEntity(
                    name = "Budget Upgrades",
                    content = "Suggest 10 budget-friendly upgrades (under $5 each). Explain how each card improves the deck's synergy or power.",
                    isDefault = true,
                    position = 5
                )
            )
            promptDao.insertPrompts(defaults)
        }
    }
}
