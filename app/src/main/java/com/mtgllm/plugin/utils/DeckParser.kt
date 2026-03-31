package com.mtgllm.plugin.utils

enum class CardSection { MAIN, SIDEBOARD, MAYBOARD, COMMANDER }
data class ParsedCard(val quantity: Int, val name: String, val section: CardSection = CardSection.MAIN)
data class DeckInfo(val name: String, val cards: List<ParsedCard>, val rawText: String)

object DeckParser {
    // Matches: 
    // "1 Sol Ring"
    // "4x Lightning Bolt"
    // "1 Arcane Signet (CLB) 298"
    // "Sol Ring" (quantity defaults to 1) - but must start with a word character or quote
    // Supports trailing comments like # or *
    private val LINE_PATTERN = Regex("""^(?:(\d+)[xX]?\s+)?([a-zA-Z0-9"].+?)(?:\s+\([A-Z0-9]{3,4}\)(?:\s+\d+)?)?(?:\s*[#*].*)?$""")

    fun parse(input: String, defaultName: String? = null): DeckInfo {
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

        val defaultName = response.commanders?.values?.firstOrNull()?.card?.name ?: response.name
        return DeckInfo(defaultName, cards, rawText)
    }

    private fun parseText(text: String, deckName: String? = null): DeckInfo {
        val cards = mutableListOf<ParsedCard>()
        var currentSection = CardSection.MAIN
        
        text.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            
            val upper = trimmed.uppercase()
            
            // Check for section headers - Order matters! Check Maybeboard before Sideboard.
            val isHeader = when {
                upper.contains("MAYBEBOARD") || upper.contains("MAYBOARD") || upper.contains("CONSIDERING") -> { currentSection = CardSection.MAYBOARD; true }
                upper.contains("SIDEBOARD") -> { currentSection = CardSection.SIDEBOARD; true }
                upper.contains("COMMANDER") -> { currentSection = CardSection.COMMANDER; true }
                trimmed.startsWith("[") && trimmed.endsWith("]") -> { currentSection = CardSection.MAIN; true }
                trimmed.startsWith("---") && trimmed.endsWith("---") -> { true }
                trimmed.endsWith(":") -> {
                    if (upper != "SIDEBOARD:" && upper != "MAYBEBOARD:" && upper != "COMMANDER:") {
                        currentSection = CardSection.MAIN
                    }
                    true
                }
                upper.startsWith("MAINBOARD") || upper.startsWith("CREATURES") || upper.startsWith("ARTIFACTS") || 
                upper.startsWith("INSTANTS") || upper.startsWith("SORCERIES") || upper.startsWith("ENCHANTMENTS") || 
                upper.startsWith("LANDS") || upper.startsWith("PLANESWALKERS") || upper == "DECK" -> { 
                    currentSection = CardSection.MAIN
                    true 
                }
                else -> false
            }
            
            if (!isHeader) {
                val lineMatch = LINE_PATTERN.find(trimmed)
                if (lineMatch != null) {
                    val quantity = lineMatch.groupValues[1].toIntOrNull() ?: 1
                    var name = lineMatch.groupValues[2].trim()

                    // Normalize split cards and double-faced cards (e.g., "Fire // Ice", "Dead // Gone", "CardA/CardB")
                    // Scryfall Collection API works best when only the front face is requested.
                    if (name.contains("/")) {
                        val parts = name.split(Regex("/+"))
                        if (parts.isNotEmpty()) {
                            name = parts[0].trim()
                        }
                    }

                    if (name.isNotEmpty()) {
                        cards.add(ParsedCard(quantity, name, currentSection))
                    }
                }
            }
        }
        
        val finalName = deckName ?: cards.firstOrNull { it.section == CardSection.COMMANDER }?.name 
                         ?: cards.firstOrNull { it.section == CardSection.MAIN }?.name 
                         ?: "New Deck"
        return DeckInfo(finalName, cards, text)
    }
}
