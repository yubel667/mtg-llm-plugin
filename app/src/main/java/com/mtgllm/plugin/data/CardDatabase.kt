package com.mtgllm.plugin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [CardEntity::class, DeckRecordEntity::class, PromptEntity::class], version = 4, exportSchema = false)
abstract class CardDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun deckRecordDao(): DeckRecordDao
    abstract fun promptDao(): PromptDao

    companion object {
        @Volatile
        private var INSTANCE: CardDatabase? = null

        fun getDatabase(context: Context): CardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CardDatabase::class.java,
                    "card_database"
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
                    name = "EDH: Power Level & Swaps",
                    content = "Analyze this Commander (EDH) deck. Provide a power level estimation (1-10), evaluate the mana curve, synergy between the commander and the 99, and suggest 5 specific card swaps to improve its consistency or power.",
                    isDefault = true
                ),
                PromptEntity(
                    name = "Competitive: Meta & Sideboard",
                    content = "Analyze this competitive decklist for the current meta. Identify its primary win conditions, key weaknesses, and suggest sideboard adjustments against the most prevalent top-tier decks.",
                    isDefault = true
                ),
                PromptEntity(
                    name = "Strategic: Weaknesses & Threats",
                    content = "What are the top 3 cards or archetypes this deck struggles against? Suggest specific mainboard or sideboard answers to these threats and explain the correct line of play.",
                    isDefault = true
                ),
                PromptEntity(
                    name = "Budget: Top 10 Upgrades",
                    content = "Suggest 10 budget-friendly upgrades (under $5 each) for this deck that provide the most significant impact on its performance. Explain why each card is a good fit.",
                    isDefault = true
                ),
                PromptEntity(
                    name = "Summarize Strategy",
                    content = "Provide a comprehensive summary of this deck's overall strategy, key interactions, and most important 'game changer' cards that the opponent should be aware of.",
                    isDefault = true
                )
            )
            promptDao.insertPrompts(defaults)
        }
    }
}
