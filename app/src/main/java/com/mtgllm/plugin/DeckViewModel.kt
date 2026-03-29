package com.mtgllm.plugin

import android.app.Application
import android.content.Context
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
import com.mtgllm.plugin.data.DeckRecordDao
import com.mtgllm.plugin.data.DeckRecordEntity
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
    data class Success(val fileName: String, val cardCount: Int, val failedCards: List<String> = emptyList()) : DeckProcessState()
    data class Error(val message: String) : DeckProcessState()
}

class DeckViewModel @JvmOverloads constructor(
    application: Application,
    private val cardDao: CardDao? = null,
    private val deckRecordDao: DeckRecordDao? = null,
    private val scryfallService: ScryfallService? = null,
    private val moxfieldService: MoxfieldService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AndroidViewModel(application) {

    private val db = CardDatabase.getDatabase(application)
    private val realCardDao = cardDao ?: db.cardDao()
    private val realDeckRecordDao = deckRecordDao ?: db.deckRecordDao()
    private val realScryfallService = scryfallService ?: RetrofitClient.scryfallService
    private val realMoxfieldService = moxfieldService ?: RetrofitClient.moxfieldService
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

    private val _gameChangers = MutableLiveData<List<String>>(emptyList())
    val gameChangers: LiveData<List<String>> = _gameChangers

    private val _lastGameChangerFetch = MutableLiveData<Long>(0)
    val lastGameChangerFetch: LiveData<Long> = _lastGameChangerFetch

    init {
        refreshStats()
        loadHistory()
        loadGameChangers()
    }

    private fun loadGameChangers() {
        val cached = prefs.getString("game_changers_cache", "") ?: ""
        if (cached.isNotEmpty()) {
            _gameChangers.value = cached.split("\n")
        }
        _lastGameChangerFetch.value = prefs.getLong("game_changers_last_fetch", 0)
    }

    fun fetchGameChangers() {
        viewModelScope.launch {
            _state.value = DeckProcessState.Processing(0, "Fetching Game Changers from Scryfall...")
            try {
                // Using Scryfall search API with is:gamechanger filter
                val response = withContext(ioDispatcher) { realScryfallService.search("is:gamechanger") }
                val names = response.data.map { it.name }.sorted()
                
                if (names.isEmpty()) {
                    _state.value = DeckProcessState.Error("Game Changers Fetch Error: Scryfall returned no results for 'is:gamechanger'.")
                    return@launch
                }

                prefs.edit()
                    .putString("game_changers_cache", names.joinToString("\n"))
                    .putLong("game_changers_last_fetch", System.currentTimeMillis())
                    .apply()
                
                _gameChangers.value = names
                _lastGameChangerFetch.value = System.currentTimeMillis()
                _state.value = DeckProcessState.Idle
            } catch (e: Exception) {
                Log.e("DeckViewModel", "Error fetching game changers from Scryfall", e)
                _state.value = DeckProcessState.Error("Game Changers Fetch Error: ${e.localizedMessage}\n\nMake sure Scryfall API is reachable.")
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _cachedCardCount.value = withContext(ioDispatcher) { realCardDao.getCardCount() }
        }
    }

    fun loadHistory(query: String = "") {
        viewModelScope.launch {
            val allRecords = withContext(ioDispatcher) { realDeckRecordDao.getAllRecords() }
            if (query.isEmpty()) {
                _history.value = allRecords
            } else {
                _history.value = allRecords.filter { it.name.contains(query, ignoreCase = true) }
            }
        }
    }

    fun deleteRecord(record: DeckRecordEntity) {
        viewModelScope.launch {
            withContext(ioDispatcher) { realDeckRecordDao.deleteRecord(record.id) }
            loadHistory()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            withContext(ioDispatcher) { realCardDao.clearAll() }
            refreshStats()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(ioDispatcher) { realDeckRecordDao.clearAll() }
            loadHistory()
        }
    }

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
        includeMaybeboard: Boolean = false,
        includeGameChangers: Boolean = false
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
                    val chunks = missingNames.chunked(75)
                    val newEntities = mutableListOf<CardEntity>()
                    
                    for (chunk in chunks) {
                        val request = ScryfallCollectionRequest(chunk.map { CardIdentifier(it) })
                        val response = realScryfallService.getCollection(request)
                        
                        val notFoundSet = response.notFound?.map { it.name.lowercase() }?.toSet() ?: emptySet()
                        var dataIndex = 0
                        
                        for (requestedName in chunk) {
                            if (!notFoundSet.contains(requestedName.lowercase())) {
                                if (dataIndex < response.data.size) {
                                    val card = response.data[dataIndex]
                                    val text = formatOracleText(card)
                                    oracleTexts[requestedName] = text
                                    newEntities.add(CardEntity(requestedName, text))
                                    dataIndex++
                                }
                            }
                        }
                    }
                    
                    // Save to cache
                    if (newEntities.isNotEmpty()) {
                        withContext(ioDispatcher) {
                            realCardDao.insertCards(newEntities)
                        }
                        refreshStats()
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
                generateResultFile(
                    deckInfo.rawText, 
                    filteredCards, 
                    deckName, 
                    appendTimestamp, 
                    oracleTexts,
                    if (includeGameChangers) _gameChangers.value else null
                )
            }

            if (resultFile != null) {
                latestResultFile = resultFile
                val totalCount = filteredCards.sumOf { it.quantity }
                val failedOnes = cardNames.filterNot { oracleTexts.containsKey(it) }
                
                // Save to history
                val record = DeckRecordEntity(
                    name = deckName,
                    timestamp = System.currentTimeMillis(),
                    fileName = resultFile.name,
                    cardCount = totalCount,
                    resultText = resultFile.readText(),
                    rawInput = input
                )
                withContext(ioDispatcher) {
                    realDeckRecordDao.insertRecord(record)
                    realDeckRecordDao.trimRecords(historyLimit)
                }
                loadHistory()

                _state.value = DeckProcessState.Success(resultFile.name, totalCount, failedOnes)
                
                // Automatic share only if enabled AND no cards failed
                if (autoShareEnabled && failedOnes.isEmpty() && System.getProperty("java.runtime.name") == "Android Runtime") {
                    shareLatestFile()
                }
            } else {
                _state.value = DeckProcessState.Error("Failed to generate file.")
            }
        }
    }

    private fun formatOracleText(card: ScryfallCard): String {
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

    private fun generateResultFile(
        rawText: String,
        cards: List<ParsedCard>,
        deckName: String,
        appendTimestamp: Boolean,
        oracleTexts: Map<String, String>,
        gameChangers: List<String>? = null
    ): File? {
        return try {
            val totalCards = cards.sumOf { it.quantity }
            val uniqueCards = cards.size

            val content = buildString {
                append("=== DECK INFO ===\n")
                append("Name: $deckName\n")
                append("Total cards dumped: $totalCards ($uniqueCards unique)\n")
                append("Generated on: ${Date()}\n\n")

                append("=== DECKLIST ===\n\n")
                val sections = cards.groupBy { it.section }
                
                sections[CardSection.COMMANDER]?.let { list ->
                    append("[COMMANDER]\n")
                    list.forEach { append("${it.quantity}x ${it.name}\n") }
                    append("\n")
                }
                
                sections[CardSection.MAIN]?.let { list ->
                    append("[MAINBOARD]\n")
                    list.forEach { append("${it.quantity}x ${it.name}\n") }
                    append("\n")
                }
                
                sections[CardSection.SIDEBOARD]?.let { list ->
                    append("[SIDEBOARD]\n")
                    list.forEach { append("${it.quantity}x ${it.name}\n") }
                    append("\n")
                }
                
                sections[CardSection.MAYBOARD]?.let { list ->
                    append("[MAYBEBOARD]\n")
                    list.forEach { append("${it.quantity}x ${it.name}\n") }
                    append("\n")
                }

                if (!gameChangers.isNullOrEmpty()) {
                    append("=== COMMANDER GAME CHANGER LIST ===\n\n")
                    append(gameChangers.joinToString("\n"))
                    append("\n\n")
                }

                append("=== ORACLE TEXT APPENDED BELOW ===\n\n")

                for (card in cards) {
                    append("[${card.section}] ${card.quantity}x ${card.name}\n")
                    append(oracleTexts[card.name] ?: "!!! CARD NOT FOUND IN SCRYFALL !!!")
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

    fun shareLatestFile() {
        latestResultFile?.let { file ->
            shareFileInternal(file)
        }
    }

    fun shareRecord(record: DeckRecordEntity) {
        val file = File(getApplication<Application>().cacheDir, record.fileName)
        file.writeText(record.resultText)
        shareFileInternal(file)
    }

    private fun shareFileInternal(file: File) {
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

    fun getLatestResultText(): String? {
        return latestResultFile?.readText()
    }

    fun getLatestFileName(): String? {
        return latestResultFile?.name
    }

    fun saveFileToUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = getLatestResultText() ?: return@launch
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("DeckViewModel", "Error saving file to URI", e)
            }
        }
    }

    fun saveRecordToUri(context: Context, record: DeckRecordEntity, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(record.resultText.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("DeckViewModel", "Error saving record to URI", e)
            }
        }
    }
}
