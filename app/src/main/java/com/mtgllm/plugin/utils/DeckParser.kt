package com.mtgllm.plugin.utils

import org.jsoup.Jsoup
import java.io.IOException

enum class CardSection { MAIN, SIDEBOARD, MAYBOARD, COMMANDER }
data class ParsedCard(val quantity: Int, val name: String, val section: CardSection = CardSection.MAIN)
data class DeckInfo(val name: String, val cards: List<ParsedCard>, val rawText: String)

object DeckParser {
    // Regex matches formats like "1 Sol Ring", "4x Lightning Bolt", "1 Arcane Signet (CLB) 298"
    private val LINE_PATTERN = Regex("""^(\d+)[xX]?\s+(.+?)(?:\s+\([A-Z0-9]{3,4}\)\s+\d+)?$""")

    fun parse(input: String, defaultName: String? = null): DeckInfo {
        if (input.contains("moxfield.com/decks/")) {
            return parseUrl(input)
        }
        return parseText(input, defaultName)
    }

    fun parseMoxfieldResponse(response: com.mtgllm.plugin.api.MoxfieldDeckResponse): DeckInfo {
        val cards = mutableListOf<ParsedCard>()
        
        response.commanders?.values?.forEach { 
            cards.add(ParsedCard(it.quantity, it.card.name, CardSection.COMMANDER))
        }
        response.companions?.values?.forEach { 
            cards.add(ParsedCard(it.quantity, it.card.name, CardSection.COMMANDER))
        }
        response.mainboard.values.forEach { 
            cards.add(ParsedCard(it.quantity, it.card.name, CardSection.MAIN))
        }
        response.sideboard?.values?.forEach { 
            cards.add(ParsedCard(it.quantity, it.card.name, CardSection.SIDEBOARD))
        }
        response.maybeboard?.values?.forEach { 
            cards.add(ParsedCard(it.quantity, it.card.name, CardSection.MAYBOARD))
        }

        // Create a raw text representation for the output
        val rawText = buildString {
            if (!response.commanders.isNullOrEmpty()) {
                append("Commanders:\n")
                response.commanders.values.forEach { append("${it.quantity} ${it.card.name}\n") }
                append("\n")
            }
            if (!response.companions.isNullOrEmpty()) {
                append("Companions:\n")
                response.companions.values.forEach { append("${it.quantity} ${it.card.name}\n") }
                append("\n")
            }
            append("Mainboard:\n")
            response.mainboard.values.forEach { append("${it.quantity} ${it.card.name}\n") }
            if (!response.sideboard.isNullOrEmpty()) {
                append("\nSideboard:\n")
                response.sideboard.values.forEach { append("${it.quantity} ${it.card.name}\n") }
            }
            if (!response.maybeboard.isNullOrEmpty()) {
                append("\nMaybeboard:\n")
                response.maybeboard.values.forEach { append("${it.quantity} ${it.card.name}\n") }
            }
        }

        // Use first commander name as deck name if available
        val defaultName = response.commanders?.values?.firstOrNull()?.card?.name ?: response.name
        return DeckInfo(defaultName, cards, rawText)
    }

    private fun parseText(text: String, deckName: String? = null): DeckInfo {
        val cards = mutableListOf<ParsedCard>()
        var currentSection = CardSection.MAIN
        
        text.lines().forEach { line ->
            val trimmed = line.trim()
            val upper = trimmed.uppercase()
            
            when {
                upper.contains("SIDEBOARD") -> currentSection = CardSection.SIDEBOARD
                upper.contains("MAYBOARD") -> currentSection = CardSection.MAYBOARD
                upper.contains("CONSIDERING") -> currentSection = CardSection.MAYBOARD
                trimmed.isEmpty() -> {} 
                else -> {
                    val lineMatch = LINE_PATTERN.find(trimmed)
                    if (lineMatch != null) {
                        val quantity = lineMatch.groupValues[1].toIntOrNull() ?: 1
                        val name = lineMatch.groupValues[2].trim()
                        if (name.isNotEmpty()) {
                            cards.add(ParsedCard(quantity, name, currentSection))
                        }
                    }
                }
            }
        }
        
        val finalName = deckName ?: cards.firstOrNull { it.section == CardSection.COMMANDER }?.name 
                         ?: cards.firstOrNull { it.section == CardSection.MAIN }?.name 
                         ?: "New Deck"
        return DeckInfo(finalName, cards, text)
    }

    private fun parseUrl(url: String): DeckInfo {
        return try {
            val doc = Jsoup.connect(url)
                .timeout(10000)
                .userAgent("MTG-LLM-Plugin/1.0")
                .get()
            
            // Extract title more cleanly
            val title = doc.title().replace(" - ManaBox", "").replace(" - Moxfield", "").trim()
            
            // For Mana Box, let's try to get text from the specific containers if possible
            // but doc.text() is usually a good fallback if we fix the spacing.
            val bodyText = doc.body().text() 
            
            val info = parseText(bodyText, deckName = title)
            
            // If still empty, try selecting all elements that look like they have numbers
            if (info.cards.isEmpty()) {
                val altText = doc.select("div, span, li").joinToString("\n") { it.ownText() }
                return parseText(altText, deckName = title)
            }
            
            info
        } catch (e: IOException) {
            DeckInfo("Error Fetching Deck", emptyList(), "URL: $url\nError: ${e.localizedMessage}")
        }
    }
}
