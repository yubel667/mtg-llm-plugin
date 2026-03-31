package com.mtgllm.plugin

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtgllm.plugin.data.DeckRecordEntity
import com.mtgllm.plugin.data.DeckRepository
import com.mtgllm.plugin.utils.CardSection
import com.mtgllm.plugin.utils.DeckInfo
import com.mtgllm.plugin.utils.DeckProcessor
import com.mtgllm.plugin.utils.ParsedCard
import com.mtgllm.plugin.utils.ProcessingResult
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class DeckViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val repository = mockk<DeckRepository>(relaxed = true)
    private val processor = mockk<DeckProcessor>(relaxed = true)
    
    private lateinit var viewModel: DeckViewModel
    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        every { repository.getCachedGameChangers() } returns emptyList()
        every { repository.getLastGameChangerFetch() } returns 0L
        
        viewModel = DeckViewModel(application, repository, processor)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `processDeck calls processor and updates state on success`() = runTest {
        val input = "1 Sol Ring"
        val resultFile = File.createTempFile("result", ".txt").apply { writeText("Oracle Data") }
        
        coEvery { 
            processor.process(any(), any(), any(), any(), any(), any(), any(), any()) 
        } returns ProcessingResult.Success(resultFile, 1, emptyList(), "Test Deck", input)

        viewModel.processDeck(input, "Test Deck")
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", finalState is DeckProcessState.Success)
        assertEquals("result", (finalState as DeckProcessState.Success).fileName.take(6))
        
        coVerify { repository.insertRecord(any(), any()) }
        
        resultFile.delete()
    }

    @Test
    fun `processDeck updates state on error`() = runTest {
        val input = "invalid"
        coEvery { 
            processor.process(any(), any(), any(), any(), any(), any(), any(), any()) 
        } returns ProcessingResult.Error("Parsing Error")

        viewModel.processDeck(input)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue(finalState is DeckProcessState.Error)
        assertEquals("Parsing Error", (finalState as DeckProcessState.Error).message)
    }

    @Test
    fun `fetchGameChangers updates liveData and repository`() = runTest {
        val names = listOf("Sol Ring", "Mana Crypt")
        coEvery { repository.fetchGameChangers() } returns names
        every { repository.getLastGameChangerFetch() } returns 12345L

        viewModel.fetchGameChangers()
        advanceUntilIdle()
        
        assertEquals(names, viewModel.gameChangers.value)
        assertEquals(12345L, viewModel.lastGameChangerFetch.value)
        verify { repository.saveGameChangers(names) }
    }

    @Test
    fun `fetchDeckFromUrl handles Moxfield URL`() = runTest {
        val url = "https://www.moxfield.com/decks/test_id"
        val deckInfo = DeckInfo("Moxfield Deck", listOf(ParsedCard(1, "Island")), "1 Island")
        
        coEvery { repository.fetchMoxfieldDeck("test_id") } returns deckInfo

        viewModel.fetchDeckFromUrl(url)
        advanceUntilIdle()
        
        assertEquals(deckInfo, viewModel.moxfieldDeck.value)
        assertTrue(viewModel.state.value is DeckProcessState.Idle)
    }

    @Test
    fun `clearHistory calls repository and reloads`() = runTest {
        coEvery { repository.clearHistory() } just Runs
        coEvery { repository.getHistory() } returns emptyList()

        viewModel.clearHistory()
        advanceUntilIdle()
        
        coVerify { repository.clearHistory() }
        assertEquals(0, viewModel.history.value?.size)
    }
}
