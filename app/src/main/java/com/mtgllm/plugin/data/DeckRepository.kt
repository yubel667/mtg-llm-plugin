package com.mtgllm.plugin.data

import android.content.Context
import android.util.Log
import com.mtgllm.plugin.api.CardIdentifier
import com.mtgllm.plugin.api.MoxfieldService
import com.mtgllm.plugin.api.ScryfallCollectionRequest
import com.mtgllm.plugin.api.ScryfallService
import com.mtgllm.plugin.utils.DeckInfo
import com.mtgllm.plugin.utils.DeckParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

class DeckRepository(
    private val context: Context,
    private val cardDao: CardDao,
    private val deckRecordDao: DeckRecordDao,
    private val promptDao: PromptDao,
    private val scryfallService: ScryfallService,
    private val moxfieldService: MoxfieldService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val prefs = context.getSharedPreferences("mtg_deck_prefs", Context.MODE_PRIVATE)

    // Prompt methods
    fun getAllPromptsFlow() = promptDao.getAllPromptsFlow()
    
    suspend fun getAllPrompts() = withContext(ioDispatcher) {
        promptDao.getAllPrompts()
    }

    suspend fun insertPrompt(prompt: PromptEntity) = withContext(ioDispatcher) {
        promptDao.insertPrompt(prompt)
    }

    suspend fun updatePrompt(prompt: PromptEntity) = withContext(ioDispatcher) {
        promptDao.updatePrompt(prompt)
    }

    suspend fun deletePrompt(prompt: PromptEntity) = withContext(ioDispatcher) {
        promptDao.deletePrompt(prompt)
    }

    suspend fun resetPromptsToDefault() = withContext(ioDispatcher) {
        promptDao.deleteAll()
        CardDatabase.populateDefaultPrompts(promptDao)
    }

    suspend fun getPromptById(id: Int) = withContext(ioDispatcher) {
        promptDao.getPromptById(id)
    }

    fun getSelectedPromptId(): Int {
        return prefs.getInt("selected_prompt_id", -1)
    }

    fun setSelectedPromptId(id: Int) {
        prefs.edit().putInt("selected_prompt_id", id).apply()
    }

    suspend fun getCardCount(): Int = withContext(ioDispatcher) {
        cardDao.getCardCount()
    }

    suspend fun clearCardCache() = withContext(ioDispatcher) {
        cardDao.clearAll()
    }

    suspend fun getCards(names: List<String>): List<CardEntity> = withContext(ioDispatcher) {
        cardDao.getCards(names)
    }

    suspend fun insertCards(cards: List<CardEntity>) = withContext(ioDispatcher) {
        cardDao.insertCards(cards)
    }

    suspend fun fetchCardsFromScryfall(names: List<String>): List<CardEntity> = withContext(ioDispatcher) {
        val entities = mutableListOf<CardEntity>()
        val chunks = names.chunked(75)
        
        for (chunk in chunks) {
            val request = ScryfallCollectionRequest(chunk.map { CardIdentifier(it) })
            val response = scryfallService.getCollection(request)
            
            // Map Scryfall responses back to requested names
            val foundCardsMap = mutableMapOf<String, com.mtgllm.plugin.api.ScryfallCard>()
            for (card in response.data) {
                foundCardsMap[card.name.lowercase()] = card
                card.cardFaces?.forEach { face ->
                    foundCardsMap[face.name.lowercase()] = card
                }
            }
            
            for (requestedName in chunk) {
                val lowerRequested = requestedName.lowercase()
                val foundCard = foundCardsMap[lowerRequested] ?: run {
                    // Fuzzy match for split cards
                    if (requestedName.contains("/")) {
                        val parts = requestedName.split(Regex("/+")).map { it.trim().lowercase() }
                        response.data.find { scryfallCard ->
                            val scryfallNameLower = scryfallCard.name.lowercase()
                            scryfallNameLower == lowerRequested || parts.all { part -> scryfallNameLower.contains(part) }
                        }
                    } else null
                }
                
                foundCard?.let {
                    val text = formatOracleText(it)
                    entities.add(CardEntity(requestedName, text))
                }
            }
        }
        entities
    }

    private fun formatOracleText(card: com.mtgllm.plugin.api.ScryfallCard): String {
        return if (card.cardFaces != null) {
            card.cardFaces.joinToString("\n---\n") { face ->
                buildString {
                    append("${face.name} ${face.manaCost ?: ""}\n")
                    append("${face.typeLine ?: ""}\n")
                    if (!face.oracleText.isNullOrEmpty()) append("${face.oracleText}\n")
                    if (!face.power.isNullOrEmpty()) append("${face.power}/${face.toughness}\n")
                }
            }
        } else {
            buildString {
                append("${card.name} ${card.manaCost ?: ""}\n")
                append("${card.typeLine ?: ""}\n")
                if (!card.oracleText.isNullOrEmpty()) append("${card.oracleText}\n")
                if (!card.power.isNullOrEmpty()) append("${card.power}/${card.toughness}\n")
            }
        }
    }

    suspend fun getHistory(): List<DeckRecordEntity> = withContext(ioDispatcher) {
        deckRecordDao.getAllRecords()
    }

    suspend fun insertRecord(record: DeckRecordEntity, limit: Int) = withContext(ioDispatcher) {
        deckRecordDao.insertRecord(record)
        deckRecordDao.trimRecords(limit)
    }

    suspend fun deleteRecord(id: Long) = withContext(ioDispatcher) {
        deckRecordDao.deleteRecord(id)
    }

    suspend fun clearHistory() = withContext(ioDispatcher) {
        deckRecordDao.clearAll()
    }

    suspend fun fetchMoxfieldDeck(deckId: String): DeckInfo = withContext(ioDispatcher) {
        val response = moxfieldService.getDeck(deckId)
        DeckParser.parseMoxfieldResponse(response)
    }

    suspend fun fetchMtgTop8Deck(url: String): DeckInfo = withContext(ioDispatcher) {
        val deckId = Regex("""d=(\d+)""").find(url)?.groupValues?.get(1)
        val bodyText = if (deckId != null) {
            Jsoup.connect("https://www.mtgtop8.com/mtgo?d=$deckId")
                .timeout(10000)
                .userAgent("MTG-LLM-Plugin/1.0")
                .ignoreContentType(true)
                .execute()
                .body()
        } else {
            Jsoup.connect(url)
                .timeout(10000)
                .userAgent("MTG-LLM-Plugin/1.0")
                .get()
                .body().text()
        }
        DeckParser.parse(bodyText, url)
    }

    suspend fun fetchGameChangers(): List<String> = withContext(ioDispatcher) {
        val response = scryfallService.search("is:gamechanger")
        response.data.map { it.name }.sorted()
    }

    fun getCachedGameChangers(): List<String> {
        val cached = prefs.getString("game_changers_cache", "") ?: ""
        return if (cached.isNotEmpty()) cached.split("\n") else emptyList()
    }

    fun saveGameChangers(names: List<String>) {
        prefs.edit()
            .putString("game_changers_cache", names.joinToString("\n"))
            .putLong("game_changers_last_fetch", System.currentTimeMillis())
            .apply()
    }

    fun getLastGameChangerFetch(): Long {
        return prefs.getLong("game_changers_last_fetch", 0)
    }
}
