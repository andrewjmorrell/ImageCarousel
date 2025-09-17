package com.example.imagecarousel.data.local

import android.content.Context
import android.graphics.BitmapFactory
import com.example.imagecarousel.data.mappers.toDomain
import com.example.imagecarousel.data.models.ImageDto
import com.example.imagecarousel.data.models.ImageResponseDto
import com.example.imagecarousel.domain.ImageResponse
import com.example.imagecarousel.domain.repository.ImageRepository
import java.io.IOException
import javax.inject.Inject

class LocalImageRepository @Inject constructor(private val context: Context): ImageRepository {
    override suspend fun getImages(count: Int): ImageResponse {
        val assetManager = context.assets
        return try {
            val imageFiles = assetManager.list("image") ?: emptyArray()
            val dto = ImageResponseDto(
                images = imageFiles.mapNotNull { fileName ->
                    try {
                        val inputStream = assetManager.open("image/$fileName")
                        val bitmap =
                            BitmapFactory.decodeStream(inputStream).also { inputStream.close() }
                        bitmap?.let { ImageDto(filename = fileName, image =it) }
                    } catch (e: IOException) {
                        null
                    }
                }
            )
            dto.toDomain()
        } catch (e: IOException) {
            ImageResponseDto(emptyList()).toDomain()
        }
    }
}