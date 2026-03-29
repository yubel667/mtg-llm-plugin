package com.mtgllm.plugin

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtgllm.plugin.api.ScryfallCard
import com.mtgllm.plugin.api.ScryfallCollectionResponse
import com.mtgllm.plugin.api.ScryfallService
import com.mtgllm.plugin.data.CardDao
import com.mtgllm.plugin.data.CardEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
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
    
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var viewModel: DeckViewModel

    @Before
    fun setup() {
        // Use UnconfinedTestDispatcher to avoid manual advanceUntilIdle in many cases
        // and ensure the test runs more predictably for simple flows.
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        
        every { application.cacheDir } returns File(".")
        viewModel = DeckViewModel(application, cardDao, scryfallService, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `processDeck with cached cards succeeds`() = runTest {
        val input = "1 Sol Ring"
        val cachedCard = CardEntity("Sol Ring", "Tap to add CC")
        
        coEvery { cardDao.getCards(any()) } returns listOf(cachedCard)

        viewModel.processDeck(input, "My Deck", false)
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", 
            finalState is DeckProcessState.Success)
    }

    @Test
    fun `processDeck with missing cards fetches from API`() = runTest {
        val input = "1 Sol Ring"
        
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Sol Ring", "Tap to add CC", null))
        )

        viewModel.processDeck(input, "My Deck", false)
        
        val finalState = viewModel.state.value
        assertTrue("Expected Success state but was $finalState", 
            finalState is DeckProcessState.Success)
    }
}
