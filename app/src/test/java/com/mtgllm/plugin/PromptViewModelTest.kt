package com.mtgllm.plugin

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtgllm.plugin.data.DeckRepository
import com.mtgllm.plugin.data.PromptEntity
import com.mtgllm.plugin.utils.ProcessingResult
import com.mtgllm.plugin.utils.DeckProcessor
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class PromptViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val repository = mockk<DeckRepository>(relaxed = true)
    private val processor = mockk<DeckProcessor>(relaxed = true)
    
    private val promptsFlow = MutableStateFlow<List<PromptEntity>>(emptyList())
    
    private lateinit var viewModel: DeckViewModel
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        every { repository.getCachedGameChangers() } returns emptyList()
        every { repository.getLastGameChangerFetch() } returns 0L
        every { repository.getSelectedPromptId() } returns -1
        every { repository.getAllPromptsFlow() } returns promptsFlow
        
        viewModel = DeckViewModel(application, repository, processor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `prompts liveData updates when flow emits`() = runTest {
        val prompts = listOf(PromptEntity(1, "Test", "Content"))
        
        // Observe liveData to trigger flow collection
        viewModel.prompts.observeForever {}
        
        promptsFlow.value = prompts
        runCurrent()
        
        assertEquals(prompts, viewModel.prompts.value)
    }

    @Test
    fun `selectPrompt updates repository and liveData`() = runTest {
        viewModel.selectPrompt(5)
        
        verify { repository.setSelectedPromptId(5) }
        assertEquals(5, viewModel.selectedPromptId.value)
    }

    @Test
    fun `deletePrompt resets selection if deleted`() = runTest {
        val prompt = PromptEntity(1, "Test", "Content")
        viewModel.selectPrompt(1)

        viewModel.deletePrompt(prompt)
        advanceUntilIdle()
        
        coVerify { repository.deletePrompt(prompt) }
        assertEquals(-1, viewModel.selectedPromptId.value)
    }

    @Test
    fun `processDeck passes prompt content to processor`() = runTest {
        val input = "1 Sol Ring"
        val prompt = PromptEntity(1, "Analyze", "Analyze this deck")
        val resultFile = File.createTempFile("result", ".txt").apply { writeText("Oracle Data") }
        
        coEvery { repository.getPromptById(1) } returns prompt
        coEvery { 
            processor.process(any(), any(), any(), any(), any(), any(), "Analyze this deck", any()) 
        } returns ProcessingResult.Success(resultFile, 1, emptyList(), "Test Deck", input)

        viewModel.selectPrompt(1)
        viewModel.processDeck(input, "Test Deck")
        advanceUntilIdle()
        
        coVerify { 
            processor.process(input, "Test Deck", any(), any(), any(), any(), "Analyze this deck", any()) 
        }
        
        resultFile.delete()
    }

    @Test
    fun `resetPromptsToDefault calls repository and resets selection`() = runTest {
        coEvery { repository.resetPromptsToDefault() } just Runs
        viewModel.selectPrompt(1)

        viewModel.resetPromptsToDefault()
        advanceUntilIdle()
        
        coVerify { repository.resetPromptsToDefault() }
        assertEquals(-1, viewModel.selectedPromptId.value)
    }
}
