package com.example.imagecarousel.data.mediastore

import android.content.Context
import com.example.imagecarousel.data.mappers.toDomain
import com.example.imagecarousel.data.models.ImageDto
import com.example.imagecarousel.data.models.ImageResponseDto
import com.example.imagecarousel.domain.ImageResponse
import com.example.imagecarousel.domain.repository.ImageRepository
import javax.inject.Inject

//Stubbed out repository that could be used to load images from the users photos
class MediaStoreImageRepository @Inject constructor(private val context: Context) : ImageRepository {
    override suspend fun getImages(count: Int): ImageResponse {
        val images = mutableListOf<ImageDto>()
        return ImageResponseDto(images).toDomain()
    }
}