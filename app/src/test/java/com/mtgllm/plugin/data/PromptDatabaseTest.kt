package com.mtgllm.plugin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PromptDatabaseTest {

    private val templates by lazy {
        val json = File("src/main/assets/prompts.json").readText()
        PromptDatabase.parseDefaultPromptTemplates(json)
    }

    @Test
    fun `default prompt asset contains curve and sideboard templates`() {
        val curve = templates.first { it.name == "Mana Curve Analysis" }
        val sideboard = templates.first { it.name == "Sideboard Replacement Analysis" }

        assertEquals(2, curve.introducedInVersion)
        assertEquals(2, sideboard.introducedInVersion)
        assertTrue(curve.content.contains("mana curve", ignoreCase = true))
        assertTrue(curve.content.contains("combo", ignoreCase = true))
        assertTrue(sideboard.content.contains("sideboard", ignoreCase = true))
        assertTrue(sideboard.content.contains("redundancy", ignoreCase = true))
    }

    @Test
    fun `new prompt templates append without changing existing prompts`() {
        val existingPrompts = templates
            .filter { it.introducedInVersion == 1 }
            .mapIndexed { index, template -> template.toEntity(position = index) }
            .plus(PromptEntity(id = 100, name = "My Custom Prompt", content = "Keep me", position = 20))

        val additions = PromptDatabase.getDefaultPromptsToInsert(
            templates,
            existingPrompts,
            lastSyncedVersion = 1
        )

        assertEquals(
            listOf("Mana Curve Analysis", "Sideboard Replacement Analysis"),
            additions.map { it.name }
        )
        assertEquals(listOf(21, 22), additions.map { it.position })
        assertTrue(additions.all { it.isDefault })
        assertEquals("Keep me", existingPrompts.first { it.id == 100 }.content)
    }

    @Test
    fun `upgrade merge does not overwrite a same-name user prompt`() {
        val existingPrompts = listOf(
            PromptEntity(
                id = 100,
                name = "Mana Curve Analysis",
                content = "My custom curve instructions",
                position = 4
            )
        )

        val additions = PromptDatabase.getDefaultPromptsToInsert(
            templates,
            existingPrompts,
            lastSyncedVersion = 1
        )

        assertEquals(listOf("Sideboard Replacement Analysis"), additions.map { it.name })
        assertEquals(5, additions.single().position)
        assertEquals("My custom curve instructions", existingPrompts.single().content)
    }

    @Test
    fun `already-synced prompt content version inserts nothing`() {
        val additions = PromptDatabase.getDefaultPromptsToInsert(
            templates,
            listOf(PromptEntity(name = "Existing", content = "Existing", position = 0)),
            lastSyncedVersion = 2
        )

        assertTrue(additions.isEmpty())
    }

    @Test
    fun `future prompt versions merge without code changes`() {
        val futureTemplates = templates + DefaultPromptTemplate(
            name = "Future Template",
            content = "A template introduced by a future release.",
            position = 10,
            introducedInVersion = 3
        )
        val existingPrompts = templates
            .filter { it.introducedInVersion == 1 }
            .map { it.toEntity() }

        val additions = PromptDatabase.getDefaultPromptsToInsert(
            futureTemplates,
            existingPrompts,
            lastSyncedVersion = 1
        )

        assertEquals(
            listOf(
                "Mana Curve Analysis",
                "Sideboard Replacement Analysis",
                "Future Template"
            ),
            additions.map { it.name }
        )
        assertEquals(listOf(8, 9, 10), additions.map { it.position })
    }
}
