package com.example.helloworld

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.helloworld.api.CardIdentifier
import com.example.helloworld.api.RetrofitClient
import com.example.helloworld.api.ScryfallCollectionRequest
import com.example.helloworld.data.CardDatabase
import com.example.helloworld.data.CardEntity
import com.example.helloworld.utils.DeckParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class DeckProcessState {
    object Idle : DeckProcessState()
    data class Processing(val progress: Int, val message: String) : DeckProcessState()
    data class Success(val fileName: String) : DeckProcessState()
    data class Error(val message: String) : DeckProcessState()
}

class DeckViewModel(application: Application) : AndroidViewModel(application) {
    private val cardDao = CardDatabase.getDatabase(application).cardDao()
    private val scryfallService = RetrofitClient.scryfallService

    private val _state = MutableLiveData<DeckProcessState>(DeckProcessState.Idle)
    val state: LiveData<DeckProcessState> = _state

    fun processDeck(input: String) {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Parsing deck...")
            val deckInfo = withContext(Dispatchers.IO) { DeckParser.parse(input) }
            
            if (deckInfo.cards.isEmpty()) {
                _state.value = DeckProcessState.Error("No cards found in input.")
                return@launch
            }

            val cardNames = deckInfo.cards.map { it.name }.distinct()
            val oracleTexts = mutableMapOf<String, String>()
            
            // 1. Check Cache
            _state.value = DeckProcessState.Processing(10, "Checking local cache...")
            val cachedCards = withContext(Dispatchers.IO) { cardDao.getCards(cardNames) }
            cachedCards.forEach { oracleTexts[it.name] = it.oracleText }
            
            val missingNames = cardNames.filterNot { oracleTexts.containsKey(it) }
            
            // 2. Fetch from Scryfall if needed
            if (missingNames.isNotEmpty()) {
                _state.value = DeckProcessState.Processing(30, "Fetching ${missingNames.size} cards from Scryfall...")
                try {
                    val fetchedCards = fetchInBatches(missingNames)
                    fetchedCards.forEach { card ->
                        val text = card.oracleText ?: card.cardFaces?.joinToString("\n---\n") { it.oracleText ?: "" } ?: ""
                        oracleTexts[card.name] = text
                        // Save to cache
                        withContext(Dispatchers.IO) {
                            cardDao.insertCards(listOf(CardEntity(card.name, text)))
                        }
                    }
                } catch (e: Exception) {
                    _state.value = DeckProcessState.Error("API Error: ${e.localizedMessage}")
                    return@launch
                }
            }

            // 3. Generate File
            _state.value = DeckProcessState.Processing(90, "Generating Oracle text file...")
            val resultFile = withContext(Dispatchers.IO) {
                generateResultFile(deckInfo, oracleTexts)
            }

            if (resultFile != null) {
                _state.value = DeckProcessState.Success(resultFile.name)
                shareFile(resultFile)
            } else {
                _state.value = DeckProcessState.Error("Failed to generate file.")
            }
        }
    }

    private suspend fun fetchInBatches(names: List<String>): List<com.example.helloworld.api.ScryfallCard> {
        val result = mutableListOf<com.example.helloworld.api.ScryfallCard>()
        val chunks = names.chunked(75)
        chunks.forEachIndexed { index, chunk ->
            val request = ScryfallCollectionRequest(chunk.map { CardIdentifier(it) })
            val response = scryfallService.getCollection(request)
            result.addAll(response.data)
        }
        return result
    }

    private fun generateResultFile(deckInfo: com.example.helloworld.utils.DeckInfo, oracleTexts: Map<String, String>): File? {
        return try {
            val content = StringBuilder()
            content.append("Deck: ${deckInfo.name}\n")
            content.append("Generated on: ${Date()}\n\n")
            
            deckInfo.cards.forEach { card ->
                content.append("${card.quantity}x ${card.name}\n")
                content.append(oracleTexts[card.name] ?: "Oracle text not found.")
                content.append("\n\n--------------------------------\n\n")
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "${deckInfo.name.replace(" ", "_")}_$timestamp.txt"
            val file = File(getApplication<Application>().cacheDir, fileName)
            file.writeText(content.toString())
            file
        } catch (e: Exception) {
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
