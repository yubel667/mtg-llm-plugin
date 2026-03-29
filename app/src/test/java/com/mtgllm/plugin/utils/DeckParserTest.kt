package com.mtgllm.plugin.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckParserTest {

    @Test
    fun `parse simple decklist text`() {
        val input = """
            1 Sol Ring
            4x Lightning Bolt
            1 Arcane Signet (CLB) 298
        """.trimIndent()

        val result = DeckParser.parse(input)

        assertEquals("Sol Ring", result.name) // First card name
        assertEquals(3, result.cards.size)
        assertEquals(input, result.rawText)
    }

    @Test
    fun `parse decklist with sections`() {
        val input = """
            1 Sol Ring
            [SIDEBOARD]
            1 Lightning Bolt
            MAYBOARD:
            1 Arcane Signet
        """.trimIndent()

        val result = DeckParser.parse(input)
        assertEquals(3, result.cards.size)
        assertEquals(CardSection.MAIN, result.cards[0].section)
        assertEquals(CardSection.SIDEBOARD, result.cards[1].section)
        assertEquals(CardSection.MAYBOARD, result.cards[2].section)
    }

    @Test
    fun `parseMoxfieldResponse includes commanders`() {
        val response = com.mtgllm.plugin.api.MoxfieldDeckResponse(
            name = "My Deck",
            commanders = mapOf("1" to com.mtgllm.plugin.api.MoxfieldCard(1, com.mtgllm.plugin.api.MoxfieldCardDetails("Urza, Lord High Artificer"))),
            companions = null,
            mainboard = mapOf("2" to com.mtgllm.plugin.api.MoxfieldCard(1, com.mtgllm.plugin.api.MoxfieldCardDetails("Island"))),
            sideboard = null,
            maybeboard = null
        )

        val result = DeckParser.parseMoxfieldResponse(response)
        
        assertEquals("Urza, Lord High Artificer", result.name)
        assertEquals(2, result.cards.size)
        assertEquals(CardSection.COMMANDER, result.cards[0].section)
        assertTrue(result.rawText.contains("Commanders:"))
    }

    @Test
    fun `parse Mana Box like text`() {
        val input = """
            ManaBox Deck: Dragons
            Mainboard:
            1 The Ur-Dragon
            1 Dragonlord Dromoka
            1 Island
            Sideboard:
            1 Pyroblast
        """.trimIndent()

        val result = DeckParser.parse(input, "Dragons")
        
        assertEquals("Dragons", result.name)
        assertTrue(result.cards.any { it.name == "The Ur-Dragon" && it.section == CardSection.MAIN })
        assertTrue(result.cards.any { it.name == "Pyroblast" && it.section == CardSection.SIDEBOARD })
    }

    @Test
    fun `parse MTGTop8 like text`() {
        val input = """
            1 Sol Ring
            1 Mana Crypt
            Sideboard
            1 Marneus Calgar
        """.trimIndent()

        val result = DeckParser.parse(input, "Marneus Calgar")
        
        assertEquals("Marneus Calgar", result.name)
        assertEquals(3, result.cards.size)
        assertEquals(CardSection.MAIN, result.cards[0].section)
        assertEquals(CardSection.SIDEBOARD, result.cards[2].section)
    }

    @Test
    fun `parse decklist with empty lines`() {
        val input = """
            
            1 Sol Ring
            
            1 Arcane Signet
            
        """.trimIndent()

        val result = DeckParser.parse(input)
        assertEquals(2, result.cards.size)
    }
}
