package com.mtgllm.plugin.utils

import android.app.Application
import com.mtgllm.plugin.data.CardEntity
import com.mtgllm.plugin.data.DeckRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class DeckProcessor(
    private val application: Application,
    private val repository: DeckRepository
) {
    suspend fun process(
        input: String,
        customName: String?,
        appendTimestamp: Boolean,
        includeSideboard: Boolean,
        includeMaybeboard: Boolean,
        includeGameChangers: Boolean,
        onProgress: (Int, String) -> Unit
    ): ProcessingResult {
        onProgress(0, "Parsing deck...")
        val deckInfo = DeckParser.parse(input, customName)
        if (deckInfo.cards.isEmpty()) {
            return ProcessingResult.Error("No valid cards found in the input.")
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
        onProgress(10, "Checking local cache...")
        val cachedCards = repository.getCards(cardNames)
        for (card in cachedCards) {
            oracleTexts[card.name] = card.oracleText
        }

        val missingNames = cardNames.filterNot { oracleTexts.containsKey(it) }

        // 2. Fetch from Scryfall if needed
        if (missingNames.isNotEmpty()) {
            onProgress(30, "Fetching ${missingNames.size} cards from Scryfall...")
            try {
                val newEntities = repository.fetchCardsFromScryfall(missingNames)
                for (entity in newEntities) {
                    oracleTexts[entity.name] = entity.oracleText
                }
                if (newEntities.isNotEmpty()) {
                    repository.insertCards(newEntities)
                }
            } catch (e: Exception) {
                return ProcessingResult.Error("API Error: ${e.localizedMessage}")
            }
        }

        // 3. Generate File
        onProgress(90, "Generating Oracle text file...")
        val gameChangers = if (includeGameChangers) repository.getCachedGameChangers() else null
        val resultFile = generateResultFile(deckInfo.rawText, filteredCards, deckName, appendTimestamp, oracleTexts, gameChangers)

        return if (resultFile != null) {
            val totalCount = filteredCards.sumOf { it.quantity }
            val failedOnes = cardNames.filterNot { oracleTexts.containsKey(it) }
            ProcessingResult.Success(resultFile, totalCount, failedOnes, deckName, deckInfo.rawText)
        } else {
            ProcessingResult.Error("Failed to generate file.")
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
            
            val file = File(application.cacheDir, fileName)
            file.writeText(content)
            file
        } catch (e: Exception) {
            null
        }
    }
}

sealed class ProcessingResult {
    data class Success(val file: File, val cardCount: Int, val failedCards: List<String>, val deckName: String, val rawInput: String) : ProcessingResult()
    data class Error(val message: String) : ProcessingResult()
}
