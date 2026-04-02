package com.mtgllm.plugin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            INSTANCE?.let { database ->
                                populateDefaultPrompts(context, database.promptDao())
                            }
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun deleteDatabase(context: Context) {
            context.deleteDatabase("prompt_database")
            INSTANCE = null
        }

        suspend fun populateDefaultPrompts(context: Context, promptDao: PromptDao) {
            try {
                val jsonString = context.assets.open("prompts.json").bufferedReader().use { it.readText() }
                val listType = object : TypeToken<List<PromptEntity>>() {}.type
                val defaults: List<PromptEntity> = Gson().fromJson(jsonString, listType)
                
                // Ensure isDefault is set to true for these loaded prompts
                val finalDefaults = defaults.map { it.copy(isDefault = true) }
                promptDao.insertPrompts(finalDefaults)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
