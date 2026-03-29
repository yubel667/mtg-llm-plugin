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

        assertEquals("Sol Ring", result.name) // First card name
        assertEquals(3, result.cards.size)
        assertEquals(input, result.rawText)
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
