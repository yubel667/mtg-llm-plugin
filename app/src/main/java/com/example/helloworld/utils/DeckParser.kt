package com.example.helloworld.utils

import org.jsoup.Jsoup
import java.util.regex.Pattern

data class ParsedCard(val quantity: Int, val name: String)
data class DeckInfo(val name: String, val cards: List<ParsedCard>)

object DeckParser {
    private val CARD_PATTERN = Pattern.compile("^(\\d+)[xX]?\\s+(.+?)(?:\\s+\\((?:[A-Z0-9]{3,4})\\)\\s+\\d+)?$")

    fun parse(input: String): DeckInfo {
        if (input.startsWith("http")) {
            return parseUrl(input)
        }
        return parseText(input)
    }

    private fun parseText(text: String): DeckInfo {
        val cards = mutableListOf<ParsedCard>()
        text.lines().forEach { line ->
            val matcher = CARD_PATTERN.matcher(line.trim())
            if (matcher.find()) {
                val qty = matcher.group(1)?.toInt() ?: 1
                val name = matcher.group(2)?.trim() ?: ""
                if (name.isNotEmpty()) {
                    cards.add(ParsedCard(qty, name))
                }
            }
        }
        return DeckInfo("New Deck", cards)
    }

    private fun parseUrl(url: String): DeckInfo {
        return try {
            val doc = Jsoup.connect(url).get()
            val title = doc.title().split("|").first().trim()
            val bodyText = doc.body().text()
            // This is a simplified fallback, real scrapers might need more logic
            // But for now we try to parse the text of the page
            val info = parseText(doc.text())
            info.copy(name = title)
        } catch (e: Exception) {
            DeckInfo("New Deck", emptyList())
        }
    }
}
