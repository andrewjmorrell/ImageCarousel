package com.example.imagecarousel.data.models

import android.graphics.Bitmap

data class ImageDto(
    val filename: String,
    val image: Bitmap
)

data class ImageResponseDto(
    val images: List<ImageDto>
)