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
import com.mtgllm.plugin.api.RetrofitClient
import com.mtgllm.plugin.api.ScryfallCollectionRequest
import com.mtgllm.plugin.data.CardDatabase
import com.mtgllm.plugin.data.CardEntity
import com.mtgllm.plugin.utils.DeckInfo
import com.mtgllm.plugin.utils.DeckParser
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
    data class Success(val fileName: String) : DeckProcessState()
    data class Error(val message: String) : DeckProcessState()
}

class DeckViewModel @JvmOverloads constructor(
    application: Application,
    private val cardDao: com.mtgllm.plugin.data.CardDao? = null,
    private val scryfallService: com.mtgllm.plugin.api.ScryfallService? = null,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
) : AndroidViewModel(application) {

    private val realCardDao = cardDao ?: CardDatabase.getDatabase(application).cardDao()
    private val realScryfallService = scryfallService ?: RetrofitClient.scryfallService

    private val _state = MutableLiveData<DeckProcessState>(DeckProcessState.Idle)
    val state: LiveData<DeckProcessState> = _state

    fun processDeck(input: String, customName: String? = null, appendTimestamp: Boolean = true) {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Parsing deck...")
            
            val deckInfo = withContext(ioDispatcher) { DeckParser.parse(input) }
            if (deckInfo.cards.isEmpty()) {
                _state.value = DeckProcessState.Error("No valid cards found in the input.")
                return@launch
            }

            val deckName = customName ?: deckInfo.name
            val cardNames = deckInfo.cards.map { it.name }.distinct()
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
                generateResultFile(deckInfo, deckName, appendTimestamp, oracleTexts)
            }

            if (resultFile != null) {
                _state.value = DeckProcessState.Success(resultFile.name)
                // In test environment, skip sharing to avoid Android class dependency
                if (System.getProperty("java.runtime.name") == "Android Runtime") {
                    shareFile(resultFile)
                }
            } else {
                _state.value = DeckProcessState.Error("Failed to generate file.")
            }
        }
    }

    private suspend fun fetchInBatches(names: List<String>): List<com.mtgllm.plugin.api.ScryfallCard> {
        val result = mutableListOf<com.mtgllm.plugin.api.ScryfallCard>()
        val chunks = names.chunked(75) // Scryfall batch limit is 75
        for (chunk in chunks) {
            val request = ScryfallCollectionRequest(chunk.map { CardIdentifier(it) })
            val response = realScryfallService.getCollection(request)
            result.addAll(response.data)
        }
        return result
    }

    private fun generateResultFile(
        deckInfo: DeckInfo,
        deckName: String,
        appendTimestamp: Boolean,
        oracleTexts: Map<String, String>
    ): File? {
        return try {
            val content = buildString {
                append("=== ORIGINAL DECKLIST ===\n\n")
                append(deckInfo.rawText)
                append("\n\n=== ORACLE TEXT APPENDED BELOW ===\n\n")
                append("Generated on: ${Date()}\n\n")

                for (card in deckInfo.cards) {
                    append("${card.quantity}x ${card.name}\n")
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
