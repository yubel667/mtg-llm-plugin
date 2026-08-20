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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Database(entities = [PromptEntity::class], version = 1, exportSchema = false)
abstract class PromptDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao

    companion object {
        private const val PREFS_NAME = "mtg_deck_prefs"
        private const val DEFAULT_PROMPTS_VERSION_KEY = "default_prompts_content_version"
        private const val LEGACY_DEFAULT_PROMPTS_VERSION = 1

        @Volatile
        private var INSTANCE: PromptDatabase? = null

        private val defaultPromptsMutex = Mutex()

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
                                syncDefaultPrompts(context, database.promptDao())
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

        suspend fun syncDefaultPrompts(context: Context, promptDao: PromptDao) {
            defaultPromptsMutex.withLock {
                try {
                    val templates = loadDefaultPromptTemplates(context)
                    val existingPrompts = promptDao.getAllPrompts()
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastSyncedVersion = if (existingPrompts.isEmpty()) {
                        0
                    } else {
                        prefs.getInt(DEFAULT_PROMPTS_VERSION_KEY, LEGACY_DEFAULT_PROMPTS_VERSION)
                    }

                    val promptsToInsert = getDefaultPromptsToInsert(
                        templates,
                        existingPrompts,
                        lastSyncedVersion
                    )
                    if (promptsToInsert.isNotEmpty()) {
                        promptDao.insertPrompts(promptsToInsert)
                    }

                    prefs.edit()
                        .putInt(DEFAULT_PROMPTS_VERSION_KEY, latestContentVersion(templates))
                        .apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        suspend fun resetDefaultPrompts(context: Context, promptDao: PromptDao) {
            defaultPromptsMutex.withLock {
                try {
                    val templates = loadDefaultPromptTemplates(context)
                    promptDao.deleteAll()
                    promptDao.insertPrompts(templates.map { it.toEntity() })
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(DEFAULT_PROMPTS_VERSION_KEY, latestContentVersion(templates))
                        .apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        private fun loadDefaultPromptTemplates(context: Context): List<DefaultPromptTemplate> {
            val json = context.assets.open("prompts.json").bufferedReader().use { it.readText() }
            return parseDefaultPromptTemplates(json)
        }

        internal fun parseDefaultPromptTemplates(json: String): List<DefaultPromptTemplate> {
            val listType = object : TypeToken<List<DefaultPromptTemplate>>() {}.type
            val templates: List<DefaultPromptTemplate> = Gson().fromJson(json, listType)

            require(templates.isNotEmpty()) { "prompts.json must contain at least one template." }
            require(templates.all { it.name.isNotBlank() && it.content.isNotBlank() }) {
                "Every default prompt must have a name and content."
            }
            require(templates.all { it.introducedInVersion >= 1 }) {
                "Every default prompt must have a positive introducedInVersion."
            }
            require(templates.size == templates.map { it.name.lowercase() }.toSet().size) {
                "Default prompt names must be unique."
            }
            require(templates.size == templates.map { it.position }.toSet().size) {
                "Default prompt positions must be unique."
            }

            return templates
        }

        internal fun getDefaultPromptsToInsert(
            templates: List<DefaultPromptTemplate>,
            existingPrompts: List<PromptEntity>,
            lastSyncedVersion: Int
        ): List<PromptEntity> {
            if (existingPrompts.isEmpty()) {
                return templates.sortedBy { it.position }.map { it.toEntity() }
            }

            val existingNames = existingPrompts.mapTo(mutableSetOf()) { it.name.lowercase() }
            var nextPosition = (existingPrompts.maxOfOrNull { it.position } ?: -1) + 1

            return templates
                .asSequence()
                .filter { it.introducedInVersion > lastSyncedVersion }
                .filter { it.name.lowercase() !in existingNames }
                .sortedBy { it.position }
                .map { it.toEntity(position = nextPosition++) }
                .toList()
        }

        private fun latestContentVersion(templates: List<DefaultPromptTemplate>): Int {
            return templates.maxOf { it.introducedInVersion }
        }
    }
}
