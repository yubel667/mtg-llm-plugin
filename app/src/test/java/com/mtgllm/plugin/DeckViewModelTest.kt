package com.mtgllm.plugin

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtgllm.plugin.api.*
import com.mtgllm.plugin.data.CardDao
import com.mtgllm.plugin.data.CardEntity
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
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val application = mockk<Application>(relaxed = true)
    private val cardDao = mockk<CardDao>(relaxed = true)
    private val deckRecordDao = mockk<com.mtgllm.plugin.data.DeckRecordDao>(relaxed = true)
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
        
        val initSearchResponse = ScryfallSearchResponse(0, false, null, emptyList())
        coEvery { scryfallService.search(any()) } returns initSearchResponse
        
        val initDeckResponse = MoxfieldDeckResponse(
            name = "Init",
            mainboard = emptyMap(), commanders = emptyMap(), companions = emptyMap(), sideboard = emptyMap(), maybeboard = emptyMap()
        )
        coEvery { moxfieldService.getDeck(any()) } returns initDeckResponse
        
        viewModel = DeckViewModel(application, cardDao, deckRecordDao, scryfallService, moxfieldService, testDispatcher)
    }

    @Test
    fun `processDeck handles expanded names like Adventure or DFC`() = runTest {
        val input = "1 Brazen Borrower"
        
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Brazen Borrower // Petty Theft", "{1}{U}{U}", "Creature", "Flash", "3", "1", null)),
            notFound = emptyList()
        )

        viewModel.processDeck(input, "Adventure Deck", false, true, false)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", finalState is DeckProcessState.Success)
        val successState = finalState as DeckProcessState.Success
        assertTrue("Expected no failed cards but found ${successState.failedCards}", successState.failedCards.isEmpty())
    }

    @Test
    fun `history limit is respected when saving new records`() = runTest {
        val input = "1 Sol Ring"
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Sol Ring", "{1}", "Artifact", "Text", null, null, null)),
            notFound = emptyList()
        )
        
        viewModel.historyLimit = 10
        
        viewModel.processDeck(input, "History Test", false, true, false)
        advanceUntilIdle()
        
        coVerify(atLeast = 1) { deckRecordDao.trimRecords(any()) }
    }

    @Test
    fun `deleteRecord removes entry and reloads history`() = runTest {
        val record = com.mtgllm.plugin.data.DeckRecordEntity(1, "Old Deck", 12345, "old.txt", 60, "results")
        
        viewModel.deleteRecord(record)
        advanceUntilIdle()
        
        coVerify { deckRecordDao.deleteRecord(1) }
        coVerify { deckRecordDao.getAllRecords() }
    }

    @Test
    fun `loadHistory with query filters records`() = runTest {
        val records = listOf(
            com.mtgllm.plugin.data.DeckRecordEntity(1, "Dragons", 1, "d.txt", 60, "text"),
            com.mtgllm.plugin.data.DeckRecordEntity(2, "Elves", 2, "e.txt", 60, "text")
        )
        coEvery { deckRecordDao.getAllRecords() } returns records
        
        viewModel.loadHistory("Dragon")
        advanceUntilIdle()
        
        assertEquals(1, viewModel.history.value?.size)
        assertEquals("Dragons", viewModel.history.value?.get(0)?.name)
    }

    @Test
    fun `fetchGameChangers updates cache and last fetch time`() = runTest {
        val response = ScryfallSearchResponse(
            totalCards = 1,
            hasMore = false,
            nextPage = null,
            data = listOf(ScryfallCard("Black Lotus", "{0}", "Artifact", "Text", null, null, null))
        )
        coEvery { scryfallService.search("is:gamechanger") } returns response
        
        // Use LiveData test observers
        val listObserver = mockk<androidx.lifecycle.Observer<List<String>>>(relaxed = true)
        val timeObserver = mockk<androidx.lifecycle.Observer<Long>>(relaxed = true)
        viewModel.gameChangers.observeForever(listObserver)
        viewModel.lastGameChangerFetch.observeForever(timeObserver)
        
        viewModel.fetchGameChangers()
        advanceUntilIdle()
        
        val list = viewModel.gameChangers.value
        assertTrue("Expected list to contain Black Lotus but was $list", list?.contains("Black Lotus") == true)
        
        verify { listObserver.onChanged(any()) }
        verify { timeObserver.onChanged(any()) }
        
        viewModel.gameChangers.removeObserver(listObserver)
        viewModel.lastGameChangerFetch.removeObserver(timeObserver)
    }

    @Test
    fun `processDeck handles Scryfall not_found response`() = runTest {
        val input = "1 Real Card\n1 Fake Card"
        
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Real Card", "{G}", "Creature", "Text", "1", "1", null)),
            notFound = listOf(com.mtgllm.plugin.api.CardIdentifier("Fake Card"))
        )

        viewModel.processDeck(input, "Partial Test", false, true, false)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue(finalState is DeckProcessState.Success)
        val successState = finalState as DeckProcessState.Success
        assertEquals(listOf("Fake Card"), successState.failedCards)
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
        assertTrue(successState.failedCards.isEmpty())
    }

    @Test
    fun `processDeck with missing cards fetches from API`() = runTest {
        val input = "1 Sol Ring"
        
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Sol Ring", "{1}", "Artifact", "Tap to add CC", null, null, null)),
            notFound = emptyList()
        )

        viewModel.processDeck(input, "My Deck", false, true, false)
        advanceUntilIdle()
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", 
            finalState is DeckProcessState.Success)
        val successState = finalState as DeckProcessState.Success
        assertEquals(1, successState.cardCount)
        assertTrue(successState.failedCards.isEmpty())
    }
}
