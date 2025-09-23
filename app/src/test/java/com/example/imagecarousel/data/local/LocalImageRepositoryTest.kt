package com.example.imagecarousel.data.local

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.imagecarousel.domain.ImageResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LocalImageRepositoryTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var repository: LocalImageRepository

    @BeforeEach
    fun setup() {
        context = Mockito.mock(Context::class.java)
        assetManager = Mockito.mock(AssetManager::class.java)
        whenever(context.assets).thenReturn(assetManager)
        repository = LocalImageRepository(context)
    }

    @Test
    fun `getImages returns ImageResponse with decoded bitmaps`() = runTest {
        val fakeFileNames = arrayOf("image1.png", "image2.png")
        whenever(assetManager.list("image")).thenReturn(fakeFileNames)

        // any non-null InputStream is fine since we’ll mock decodeStream
        val fakeImageData = byteArrayOf(1,2,3)
        val inputStream = ByteArrayInputStream(fakeImageData)
        whenever(assetManager.open(Mockito.anyString())).thenReturn(inputStream)

        // Mock static BitmapFactory.decodeStream to return a fake Bitmap
        Mockito.mockStatic(BitmapFactory::class.java).use { staticMock ->
            val fakeBitmap = Mockito.mock(Bitmap::class.java)
            staticMock.`when`<Bitmap?> { BitmapFactory.decodeStream(Mockito.any()) }
                .thenReturn(fakeBitmap)

            val result: ImageResponse = repository.getImages(2)

            assertNotNull(result)
            assertEquals(2, result.images.size)
            assertTrue(result.images.all { it.bitmap != null })
        }
    }

    @Test
    fun `getImages returns empty when AssetManager throws IOException`() = runTest {
        whenever(assetManager.list("image")).thenThrow(IOException("boom"))

        val result: ImageResponse = repository.getImages(2)

        assertNotNull(result)
        assertTrue(result.images.isEmpty())
    }

    @Test
    fun `getImages skips invalid files`() = runTest {
        val fakeFileNames = arrayOf("bad.png")
        whenever(assetManager.list("image")).thenReturn(fakeFileNames)

        // Simulate IOException when trying to open the file
        whenever(assetManager.open("image/bad.png")).thenThrow(IOException("cannot open"))

        val result: ImageResponse = repository.getImages(1)

        assertNotNull(result)
        assertTrue(result.images.isEmpty())
    }
}