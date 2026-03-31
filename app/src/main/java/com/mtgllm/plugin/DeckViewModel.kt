package com.mtgllm.plugin

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mtgllm.plugin.api.RetrofitClient
import com.mtgllm.plugin.data.CardDatabase
import com.mtgllm.plugin.data.DeckRecordEntity
import com.mtgllm.plugin.data.DeckRepository
import com.mtgllm.plugin.utils.DeckInfo
import com.mtgllm.plugin.utils.DeckProcessor
import com.mtgllm.plugin.utils.ProcessingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class DeckProcessState {
    data object Idle : DeckProcessState()
    data class Processing(val progress: Int, val message: String) : DeckProcessState()
    data class Success(val fileName: String, val cardCount: Int, val failedCards: List<String> = emptyList()) : DeckProcessState()
    data class Error(val message: String) : DeckProcessState()
}

class DeckViewModel(
    application: Application,
    private val repository: DeckRepository,
    private val processor: DeckProcessor
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("mtg_deck_prefs", Context.MODE_PRIVATE)

    private val _state = MutableLiveData<DeckProcessState>(DeckProcessState.Idle)
    val state: LiveData<DeckProcessState> = _state

    private val _moxfieldDeck = MutableLiveData<DeckInfo?>(null)
    val moxfieldDeck: LiveData<DeckInfo?> = _moxfieldDeck

    private val _history = MutableLiveData<List<DeckRecordEntity>>(emptyList())
    val history: LiveData<List<DeckRecordEntity>> = _history

    private val _cachedCardCount = MutableLiveData<Int>(0)
    val cachedCardCount: LiveData<Int> = _cachedCardCount

    private var latestResultFile: File? = null

    var autoShareEnabled: Boolean
        get() = prefs.getBoolean("auto_share", true)
        set(value) = prefs.edit().putBoolean("auto_share", value).apply()

    var historyLimit: Int
        get() = prefs.getInt("history_limit", 100)
        set(value) = prefs.edit().putInt("history_limit", value).apply()

    var askBeforeDeleteEnabled: Boolean
        get() = prefs.getBoolean("ask_before_delete", true)
        set(value) = prefs.edit().putBoolean("ask_before_delete", value).apply()

    var autoGameChangerEnabled: Boolean
        get() = prefs.getBoolean("auto_game_changer", true)
        set(value) = prefs.edit().putBoolean("auto_game_changer", value).apply()

    private val _gameChangers = MutableLiveData<List<String>>(emptyList())
    val gameChangers: LiveData<List<String>> = _gameChangers

    private val _lastGameChangerFetch = MutableLiveData<Long>(0)
    val lastGameChangerFetch: LiveData<Long> = _lastGameChangerFetch

    init {
        refreshStats()
        loadHistory()
        _gameChangers.value = repository.getCachedGameChangers()
        _lastGameChangerFetch.value = repository.getLastGameChangerFetch()
    }

    fun fetchGameChangers() {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Fetching Game Changers from Scryfall...")
            try {
                val names = repository.fetchGameChangers()
                if (names.isEmpty()) {
                    _state.value = DeckProcessState.Error("Game Changers Fetch Error: Scryfall returned no results.")
                    return@launch
                }
                repository.saveGameChangers(names)
                _gameChangers.value = names
                _lastGameChangerFetch.value = repository.getLastGameChangerFetch()
                _state.value = DeckProcessState.Idle
            } catch (e: Exception) {
                _state.value = DeckProcessState.Error("Game Changers Fetch Error: ${e.localizedMessage}")
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _cachedCardCount.value = repository.getCardCount()
        }
    }

    fun loadHistory(query: String = "") {
        viewModelScope.launch {
            val allRecords = repository.getHistory()
            _history.value = if (query.isEmpty()) allRecords else allRecords.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    fun deleteRecord(record: DeckRecordEntity) {
        viewModelScope.launch {
            repository.deleteRecord(record.id)
            loadHistory()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCardCache()
            refreshStats()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            loadHistory()
        }
    }

    fun fetchDeckFromUrl(url: String) {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Fetching deck from URL...")
            try {
                val deckInfo = when {
                    url.contains("moxfield.com/decks/") -> {
                        val deckId = Regex("moxfield.com/decks/([^/?#]+)").find(url)?.groupValues?.get(1)
                        if (deckId == null) throw Exception("Invalid Moxfield URL")
                        repository.fetchMoxfieldDeck(deckId)
                    }
                    url.contains("mtgtop8.com/") -> repository.fetchMtgTop8Deck(url)
                    else -> throw Exception("Unsupported URL. Only Moxfield and MTGTop8 are supported.")
                }
                _moxfieldDeck.value = deckInfo
                _state.value = DeckProcessState.Idle
            } catch (e: Exception) {
                _state.value = DeckProcessState.Error("Fetch Error: ${e.localizedMessage}")
            }
        }
    }

    fun processDeck(
        input: String,
        customName: String? = null,
        appendTimestamp: Boolean = true,
        includeSideboard: Boolean = true,
        includeMaybeboard: Boolean = false,
        includeGameChangers: Boolean = false
    ) {
        viewModelScope.launch {
            val result = processor.process(
                input, customName, appendTimestamp, includeSideboard, includeMaybeboard, includeGameChangers
            ) { progress, message ->
                _state.value = DeckProcessState.Processing(progress, message)
            }

            when (result) {
                is ProcessingResult.Success -> {
                    latestResultFile = result.file
                    repository.insertRecord(
                        DeckRecordEntity(
                            name = result.deckName,
                            timestamp = System.currentTimeMillis(),
                            fileName = result.file.name,
                            cardCount = result.cardCount,
                            resultText = result.file.readText(),
                            rawInput = result.rawInput
                        ),
                        historyLimit
                    )
                    loadHistory()
                    _state.value = DeckProcessState.Success(result.file.name, result.cardCount, result.failedCards)
                    
                    if (autoShareEnabled && result.failedCards.isEmpty() && System.getProperty("java.runtime.name") == "Android Runtime") {
                        // Success state triggers sharing in Activity
                    }
                }
                is ProcessingResult.Error -> {
                    _state.value = DeckProcessState.Error(result.message)
                }
            }
        }
    }

    fun getLatestResultText(): String? = latestResultFile?.readText()
    fun getLatestFileName(): String? = latestResultFile?.name

    fun shareLatestFile(context: Context) {
        val file = latestResultFile ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Oracle Text"))
    }

    fun saveFileToUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = getLatestResultText() ?: return@launch
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("DeckViewModel", "Error saving file", e)
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DeckViewModel::class.java)) {
                val db = CardDatabase.getDatabase(application)
                val repository = DeckRepository(
                    application,
                    db.cardDao(),
                    db.deckRecordDao(),
                    RetrofitClient.scryfallService,
                    RetrofitClient.moxfieldService
                )
                val processor = DeckProcessor(application, repository)
                @Suppress("UNCHECKED_CAST")
                return DeckViewModel(application, repository, processor) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
