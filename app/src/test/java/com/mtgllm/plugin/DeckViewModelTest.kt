package com.mtgllm.plugin

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtgllm.plugin.api.ScryfallCard
import com.mtgllm.plugin.api.ScryfallCollectionResponse
import com.mtgllm.plugin.api.ScryfallService
import com.mtgllm.plugin.api.MoxfieldService
import com.mtgllm.plugin.data.CardDao
import com.mtgllm.plugin.data.CardEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val cardDao = mockk<CardDao>(relaxed = true)
    private val scryfallService = mockk<ScryfallService>()
    private val moxfieldService = mockk<MoxfieldService>()
    
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var viewModel: DeckViewModel

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        every { application.cacheDir } returns File(".")
        viewModel = DeckViewModel(application, cardDao, scryfallService, moxfieldService, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(Log::class)
    }

    @Test
    fun `fetchMoxfieldDeck succeeds with commander`() = runTest {
        val url = "https://www.moxfield.com/decks/hyRL_mx_YE6"
        val response = com.mtgllm.plugin.api.MoxfieldDeckResponse(
            name = "Test Deck",
            commanders = mapOf("1" to com.mtgllm.plugin.api.MoxfieldCard(1, com.mtgllm.plugin.api.MoxfieldCardDetails("Attla, Palani's Nest"))),
            companions = emptyMap(),
            mainboard = mapOf("2" to com.mtgllm.plugin.api.MoxfieldCard(1, com.mtgllm.plugin.api.MoxfieldCardDetails("Sol Ring"))),
            sideboard = emptyMap(),
            maybeboard = emptyMap()
        )

        coEvery { moxfieldService.getDeck(any()) } returns response

        viewModel.fetchDeckFromUrl(url)
        advanceUntilIdle()

        val deckInfo = viewModel.moxfieldDeck.value
        assertEquals("Attla, Palani's Nest", deckInfo?.name)
        assertEquals(2, deckInfo?.cards?.size)
        assertEquals(com.mtgllm.plugin.utils.CardSection.COMMANDER, deckInfo?.cards?.get(0)?.section)
        assertTrue(viewModel.state.value is DeckProcessState.Idle)
    }

    @Test
    fun `processDeck with empty deck returns error`() = runTest {
        val input = "Random text"
        viewModel.processDeck(input)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Error state but was $finalState", 
            finalState is DeckProcessState.Error)
    }

    @Test
    fun `fetchMoxfieldDeck with invalid URL returns error`() = runTest {
        val url = "https://invalid.url"
        viewModel.fetchDeckFromUrl(url)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Error state but was $finalState", 
            finalState is DeckProcessState.Error)
    }

    @Test
    fun `fetchMoxfieldDeck API error returns error state`() = runTest {
        val url = "https://www.moxfield.com/decks/hyRL_mx_YE6"
        coEvery { moxfieldService.getDeck(any()) } throws Exception("Network Error")

        viewModel.fetchDeckFromUrl(url)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Error state but was $finalState", 
            finalState is DeckProcessState.Error)
        val errorState = finalState as DeckProcessState.Error
        assertTrue("Expected message to contain 'Network Error' but was '${errorState.message}'",
            errorState.message.contains("Network Error"))
    }

    @Test
    fun `processDeck with cached cards succeeds`() = runTest {
        val input = "1 Sol Ring"
        val cachedCard = CardEntity("Sol Ring", "Tap to add CC")
        
        coEvery { cardDao.getCards(any()) } returns listOf(cachedCard)

        viewModel.processDeck(input, "My Deck", false, true, false)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", 
            finalState is DeckProcessState.Success)
        val successState = finalState as DeckProcessState.Success
        assertEquals(1, successState.cardCount)
    }

    @Test
    fun `processDeck with missing cards fetches from API`() = runTest {
        val input = "1 Sol Ring"
        
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Sol Ring", "Tap to add CC", null))
        )

        viewModel.processDeck(input, "My Deck", false, true, false)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", 
            finalState is DeckProcessState.Success)
        val successState = finalState as DeckProcessState.Success
        assertEquals(1, successState.cardCount)
    }
}
