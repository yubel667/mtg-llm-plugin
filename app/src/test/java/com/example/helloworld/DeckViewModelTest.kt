package com.example.helloworld

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.helloworld.api.ScryfallCard
import com.example.helloworld.api.ScryfallCollectionResponse
import com.example.helloworld.api.ScryfallService
import com.example.helloworld.data.CardDao
import com.example.helloworld.data.CardEntity
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
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: DeckViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { application.cacheDir } returns File(".")
        viewModel = DeckViewModel(application, cardDao, scryfallService)
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

        viewModel.processDeck(input)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is DeckProcessState.Success)
    }

    @Test
    fun `processDeck with missing cards fetches from API`() = runTest {
        val input = "1 Sol Ring"
        
        coEvery { cardDao.getCards(any()) } returns emptyList()
        coEvery { scryfallService.getCollection(any()) } returns ScryfallCollectionResponse(
            data = listOf(ScryfallCard("Sol Ring", "Tap to add CC", null))
        )

        viewModel.processDeck(input)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is DeckProcessState.Success)
    }
}
