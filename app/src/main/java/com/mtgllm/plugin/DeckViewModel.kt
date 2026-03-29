package com.mtgllm.plugin

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mtgllm.plugin.api.CardIdentifier
import com.mtgllm.plugin.api.MoxfieldService
import com.mtgllm.plugin.api.RetrofitClient
import com.mtgllm.plugin.api.ScryfallCard
import com.mtgllm.plugin.api.ScryfallCollectionRequest
import com.mtgllm.plugin.api.ScryfallService
import com.mtgllm.plugin.data.CardDao
import com.mtgllm.plugin.data.CardDatabase
import com.mtgllm.plugin.data.CardEntity
import com.mtgllm.plugin.utils.CardSection
import com.mtgllm.plugin.utils.DeckInfo
import com.mtgllm.plugin.utils.DeckParser
import com.mtgllm.plugin.utils.ParsedCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class DeckProcessState {
    data object Idle : DeckProcessState()
    data class Processing(val progress: Int, val message: String) : DeckProcessState()
    data class Success(val fileName: String, val cardCount: Int) : DeckProcessState()
    data class Error(val message: String) : DeckProcessState()
}

class DeckViewModel @JvmOverloads constructor(
    application: Application,
    private val cardDao: CardDao? = null,
    private val scryfallService: ScryfallService? = null,
    private val moxfieldService: MoxfieldService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val realCardDao = cardDao ?: CardDatabase.getDatabase(application).cardDao()
    private val realScryfallService = scryfallService ?: RetrofitClient.scryfallService
    private val realMoxfieldService = moxfieldService ?: RetrofitClient.moxfieldService

    private val _state = MutableLiveData<DeckProcessState>(DeckProcessState.Idle)
    val state: LiveData<DeckProcessState> = _state

    private val _moxfieldDeck = MutableLiveData<DeckInfo?>(null)
    val moxfieldDeck: LiveData<DeckInfo?> = _moxfieldDeck

    fun fetchDeckFromUrl(url: String) {
        if (url.contains("moxfield.com/decks/")) {
            fetchMoxfieldDeck(url)
        } else if (url.contains("mtgtop8.com/")) {
            fetchMtgTop8Deck(url)
        } else {
            _state.value = DeckProcessState.Error("Unsupported URL. Only Moxfield and MTGTop8 are supported.")
        }
    }

    private fun fetchMoxfieldDeck(url: String) {
        val deckId = extractMoxfieldId(url)
        if (deckId == null) {
            _state.value = DeckProcessState.Error("Invalid Moxfield URL")
            return
        }

        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Fetching from Moxfield...")
            try {
                val response = withContext(ioDispatcher) { realMoxfieldService.getDeck(deckId) }
                val deckInfo = DeckParser.parseMoxfieldResponse(response)
                _moxfieldDeck.value = deckInfo
                _state.value = DeckProcessState.Idle 
            } catch (e: Exception) {
                Log.e("DeckViewModel", "Error fetching Moxfield deck", e)
                _state.value = DeckProcessState.Error("Moxfield API Error: ${e.localizedMessage}")
            }
        }
    }

    private fun fetchMtgTop8Deck(url: String) {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Fetching from MTGTop8...")
            try {
                val deckInfo = withContext(ioDispatcher) { DeckParser.parse(url) }
                if (deckInfo.cards.isNotEmpty()) {
                    _moxfieldDeck.value = deckInfo
                    _state.value = DeckProcessState.Idle
                } else {
                    _state.value = DeckProcessState.Error("Could not find any cards on the MTGTop8 page.")
                }
            } catch (e: Exception) {
                Log.e("DeckViewModel", "Error fetching MTGTop8 deck", e)
                _state.value = DeckProcessState.Error("MTGTop8 Fetch Error: ${e.localizedMessage}")
            }
        }
    }

    private fun extractMoxfieldId(url: String): String? {
        val regex = Regex("moxfield.com/decks/([^/?#]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    fun processDeck(
        input: String,
        customName: String? = null,
        appendTimestamp: Boolean = true,
        includeSideboard: Boolean = true,
        includeMaybeboard: Boolean = false
    ) {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Parsing deck...")
            
            val deckInfo = withContext(ioDispatcher) { DeckParser.parse(input, customName) }
            if (deckInfo.cards.isEmpty()) {
                _state.value = DeckProcessState.Error("No valid cards found in the input.")
                return@launch
            }

            val filteredCards = deckInfo.cards.filter { 
                when(it.section) {
                    CardSection.COMMANDER -> true
                    CardSection.MAIN -> true
                    CardSection.SIDEBOARD -> includeSideboard
                    CardSection.MAYBOARD -> includeMaybeboard
                }
            }

            val deckName = customName ?: deckInfo.name
            val cardNames = filteredCards.map { it.name }.distinct()
            val oracleTexts = mutableMapOf<String, String>()

            // 1. Check Cache
            _state.value = DeckProcessState.Processing(10, "Checking local cache...")
            val cachedCards = withContext(ioDispatcher) { realCardDao.getCards(cardNames) }
            for (card in cachedCards) {
                oracleTexts[card.name] = card.oracleText
            }

            val missingNames = cardNames.filterNot { oracleTexts.containsKey(it) }

            // 2. Fetch from Scryfall if needed
            if (missingNames.isNotEmpty()) {
                _state.value = DeckProcessState.Processing(30, "Fetching ${missingNames.size} cards from Scryfall...")
                try {
                    val fetchedCards = fetchInBatches(missingNames)
                    val newEntities = mutableListOf<CardEntity>()
                    
                    for (card in fetchedCards) {
                        val text = card.oracleText ?: card.cardFaces?.joinToString("\n---\n") { it.oracleText ?: "" } ?: ""
                        oracleTexts[card.name] = text
                        newEntities.add(CardEntity(card.name, text))
                    }
                    
                    // Save to cache
                    withContext(ioDispatcher) {
                        realCardDao.insertCards(newEntities)
                    }
                } catch (e: Exception) {
                    Log.e("DeckViewModel", "Error fetching cards", e)
                    _state.value = DeckProcessState.Error("API Error: ${e.localizedMessage}")
                    return@launch
                }
            }

            // 3. Generate File
            _state.value = DeckProcessState.Processing(90, "Generating Oracle text file...")
            val resultFile = withContext(ioDispatcher) {
                generateResultFile(deckInfo.rawText, filteredCards, deckName, appendTimestamp, oracleTexts)
            }

            if (resultFile != null) {
                val totalCount = filteredCards.sumOf { it.quantity }
                _state.value = DeckProcessState.Success(resultFile.name, totalCount)
                // In test environment, skip sharing to avoid Android class dependency
                if (System.getProperty("java.runtime.name") == "Android Runtime") {
                    shareFile(resultFile)
                }
            } else {
                _state.value = DeckProcessState.Error("Failed to generate file.")
            }
        }
    }

    private suspend fun fetchInBatches(names: List<String>): List<ScryfallCard> {
        val result = mutableListOf<ScryfallCard>()
        val chunks = names.chunked(75) // Scryfall batch limit is 75
        for (chunk in chunks) {
            val request = ScryfallCollectionRequest(chunk.map { CardIdentifier(it) })
            val response = realScryfallService.getCollection(request)
            result.addAll(response.data)
        }
        return result
    }

    private fun generateResultFile(
        rawText: String,
        cards: List<ParsedCard>,
        deckName: String,
        appendTimestamp: Boolean,
        oracleTexts: Map<String, String>
    ): File? {
        return try {
            val totalCards = cards.sumOf { it.quantity }
            val uniqueCards = cards.size

            val content = buildString {
                append("=== DECK INFO ===\n")
                append("Name: $deckName\n")
                append("Total cards dumped: $totalCards ($uniqueCards unique)\n")
                append("Generated on: ${Date()}\n\n")

                append("=== ORIGINAL DECKLIST ===\n\n")
                append(rawText)
                append("\n\n=== ORACLE TEXT APPENDED BELOW ===\n\n")

                for (card in cards) {
                    append("[${card.section}] ${card.quantity}x ${card.name}\n")
                    append(oracleTexts[card.name] ?: "Oracle text not found.")
                    append("\n\n--------------------------------\n\n")
                }
            }

            val safeName = deckName.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val fileName = if (appendTimestamp) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                "${safeName}_$timestamp.txt"
            } else {
                "$safeName.txt"
            }
            
            val file = File(getApplication<Application>().cacheDir, fileName)
            file.writeText(content)
            file
        } catch (e: Exception) {
            Log.e("DeckViewModel", "Error generating file", e)
            null
        }
    }

    private fun shareFile(file: File) {
        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Oracle Text File")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
