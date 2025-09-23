package com.example.imagecarousel.domain.usecases

import app.cash.turbine.test
import com.example.imagecarousel.domain.ImageResponse
import com.example.imagecarousel.domain.repository.DataState
import com.example.imagecarousel.domain.repository.ImageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GetImageUseCaseTest {

    private lateinit var repository: ImageRepository
    private lateinit var useCase: GetImagesUseCase

    @BeforeEach
    fun setup() {
        repository = Mockito.mock(ImageRepository::class.java)
        useCase = GetImagesUseCase(repository)
    }

    @Test
    fun `emits Loading then Success when repository returns images`() = runTest {
        val fakeResponse = ImageResponse(emptyList())
        whenever(repository.getImages(5)).thenReturn(fakeResponse)

        useCase.getImages(5).test {
            assertTrue(awaitItem() is DataState.Loading)
            val success = awaitItem()
            assertTrue(success is DataState.Success)
            assertEquals(fakeResponse, (success as DataState.Success).data)
            awaitComplete()
        }
    }

    @Test
    fun `emits Loading then Error when repository throws exception`() = runTest {
        whenever(repository.getImages(5)).thenThrow(RuntimeException("boom"))

        useCase.getImages(5).test {
            assertTrue(awaitItem() is DataState.Loading)
            val error = awaitItem()
            assertTrue(error is DataState.Error)
            assertEquals("boom", (error as DataState.Error).message)
            awaitComplete()
        }
    }
}