package com.example.imagecarousel.data.models

import android.graphics.Bitmap

//This is simple but could be expanded to include additional metadata
data class ImageDto(
    val image: Bitmap
)

data class ImageResponseDto(
    val images: List<ImageDto>
)