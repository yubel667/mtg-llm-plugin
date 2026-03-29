package com.mtgllm.plugin.utils

import org.junit.Assert.assertEquals
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

        assertEquals("New Deck", result.name)
        assertEquals(3, result.cards.size)
        
        assertEquals(1, result.cards[0].quantity)
        assertEquals("Sol Ring", result.cards[0].name)
        
        assertEquals(4, result.cards[1].quantity)
        assertEquals("Lightning Bolt", result.cards[1].name)
        
        assertEquals(1, result.cards[2].quantity)
        assertEquals("Arcane Signet", result.cards[2].name)
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
