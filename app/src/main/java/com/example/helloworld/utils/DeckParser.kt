package com.example.helloworld.utils

import org.jsoup.Jsoup
import java.io.IOException

data class ParsedCard(val quantity: Int, val name: String)
data class DeckInfo(val name: String, val cards: List<ParsedCard>)

object DeckParser {
    // Regex matches formats like "1 Sol Ring", "4x Lightning Bolt", "1 Arcane Signet (CLB) 298"
    private val CARD_PATTERN = Regex("""^(\d+)[xX]?\s+(.+?)(?:\s+\([A-Z0-9]{3,4}\)\s+\d+)?$""")

    fun parse(input: String): DeckInfo {
        if (input.startsWith("http://") || input.startsWith("https://")) {
            return parseUrl(input)
        }
        return parseText(input)
    }

    private fun parseText(text: String, deckName: String = "New Deck"): DeckInfo {
        val cards = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val matchResult = CARD_PATTERN.find(line)
                if (matchResult != null) {
                    val quantity = matchResult.groupValues[1].toIntOrNull() ?: 1
                    val name = matchResult.groupValues[2].trim()
                    if (name.isNotEmpty()) {
                        ParsedCard(quantity, name)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        return DeckInfo(deckName, cards)
    }

    private fun parseUrl(url: String): DeckInfo {
        return try {
            val doc = Jsoup.connect(url)
                .timeout(10000)
                .userAgent("MTG-LLM-Plugin/1.0")
                .get()
            val title = doc.title().split("|").firstOrNull()?.trim() ?: "New Deck"
            // Simple fallback: parse the entire text of the body
            parseText(doc.body().text(), deckName = title)
        } catch (e: IOException) {
            DeckInfo("Error Fetching Deck", emptyList())
        }
    }
}
