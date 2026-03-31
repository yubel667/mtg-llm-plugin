package com.mtgllm.plugin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeckRecordEntity::class], version = 1, exportSchema = false)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun deckRecordDao(): DeckRecordDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getDatabase(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "history_database"
                )
                // NO fallbackToDestructiveMigration() here to protect user history
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        fun deleteDatabase(context: Context) {
            context.deleteDatabase("history_database")
            INSTANCE = null
        }
    }
}
