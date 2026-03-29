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

        assertEquals("Sol Ring", result.name)
        assertEquals(3, result.cards.size)
        assertEquals(input, result.rawText)
    }

    @Test
    fun `normalize double sided cards`() {
        val input = """
            1 Delver of Secrets / Insectile Aberration
            1 Bala Ged Recovery/Bala Ged Sanctuary
            1 Fire // Ice
            1 CardA/CardB
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals("Delver of Secrets // Insectile Aberration", result.cards[0].name)
        assertEquals("Bala Ged Recovery // Bala Ged Sanctuary", result.cards[1].name)
        assertEquals("Fire // Ice", result.cards[2].name)
        assertEquals("CardA // CardB", result.cards[3].name)
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
    fun `parse complex decklist with multiple sections`() {
        val input = """
            [COMMANDER]
            1 Eluge, the Shoreless Sea
            
            [CREATURES]
            1 Baral, Chief of Compliance
            1 Faerie Mastermind
            
            [ARTIFACTS]
            1 Sol Ring
            
            [SIDEBOARD]
            1 Pulse of the Grid
            
            [MAYBEBOARD]
            1 Chrome Mox
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals(CardSection.COMMANDER, result.cards.find { it.name == "Eluge, the Shoreless Sea" }?.section)
        assertEquals(CardSection.MAIN, result.cards.find { it.name == "Baral, Chief of Compliance" }?.section)
        assertEquals(CardSection.MAIN, result.cards.find { it.name == "Faerie Mastermind" }?.section)
        assertEquals(CardSection.MAIN, result.cards.find { it.name == "Sol Ring" }?.section)
        assertEquals(CardSection.SIDEBOARD, result.cards.find { it.name == "Pulse of the Grid" }?.section)
        assertEquals(CardSection.MAYBOARD, result.cards.find { it.name == "Chrome Mox" }?.section)
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

    @Test
    fun `parse Arena format decklist`() {
        val input = """
            Deck
            4 Lightning Bolt (A25) 141
            1 Sol Ring (C18) 222
            20 Mountain (UST) 215
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals(3, result.cards.size)
        assertEquals("Lightning Bolt", result.cards[0].name)
        assertEquals(4, result.cards[0].quantity)
        assertEquals("Sol Ring", result.cards[1].name)
        assertEquals("Mountain", result.cards[2].name)
        assertEquals(20, result.cards[2].quantity)
    }

    @Test
    fun `parse cards with special characters and long names`() {
        val input = """
            1 Lim-Dûl's Vault
            1 Asmoranomardicadaistinaculdacar
            1 "Ach! Hans, Run!"
            1 Théoden, King of Rohan
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals(4, result.cards.size)
        assertEquals("Lim-Dûl's Vault", result.cards[0].name)
        assertEquals("Asmoranomardicadaistinaculdacar", result.cards[1].name)
        assertEquals("\"Ach! Hans, Run!\"", result.cards[2].name)
        assertEquals("Théoden, King of Rohan", result.cards[3].name)
    }

    @Test
    fun `parse varied header formats`() {
        val input = """
            [ Commander ]
            1 Urza, Lord High Artificer
            
            Mainboard (60)
            1 Island
            
            Sideboard: 15 cards
            1 Pyroblast
            
            --- Maybeboard ---
            1 Sol Ring
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals(CardSection.COMMANDER, result.cards.find { it.name == "Urza, Lord High Artificer" }?.section)
        assertEquals(CardSection.MAIN, result.cards.find { it.name == "Island" }?.section)
        assertEquals(CardSection.SIDEBOARD, result.cards.find { it.name == "Pyroblast" }?.section)
        assertEquals(CardSection.MAYBOARD, result.cards.find { it.name == "Sol Ring" }?.section)
    }

    @Test
    fun `parse cards without quantities`() {
        val input = """
            Sol Ring
            Arcane Signet
            Island
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals(3, result.cards.size)
        assertEquals("Sol Ring", result.cards[0].name)
        assertEquals(1, result.cards[0].quantity)
        assertEquals("Island", result.cards[2].name)
    }

    @Test
    fun `parse decklist with trailing spaces and junk`() {
        val input = """
            1 Sol Ring   
            4x Lightning Bolt # Best card
            1 Arcane Signet *foil*
        """.trimIndent()

        val result = DeckParser.parse(input)
        
        assertEquals(3, result.cards.size)
        assertEquals("Sol Ring", result.cards[0].name)
        assertEquals("Lightning Bolt", result.cards[1].name)
        assertEquals("Arcane Signet", result.cards[2].name)
    }

    @Test
    fun `parse empty input`() {
        val result = DeckParser.parse("")
        assertEquals(0, result.cards.size)
        assertEquals("New Deck", result.name)
    }

    @Test
    fun `parse random text with no cards`() {
        val input = "!@# %^&* ()"
        val result = DeckParser.parse(input)
        assertEquals(0, result.cards.size)
    }
}
