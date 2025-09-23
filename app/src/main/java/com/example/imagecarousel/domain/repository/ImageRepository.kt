package com.example.imagecarousel.domain.repository

import com.example.imagecarousel.domain.ImageResponse

interface ImageRepository {
    suspend fun getImages(count: Int = 5) : ImageResponse
}