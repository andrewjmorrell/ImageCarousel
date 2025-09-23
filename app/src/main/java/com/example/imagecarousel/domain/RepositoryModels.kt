package com.example.imagecarousel.domain

import android.graphics.Bitmap

data class Image(
    val bitmap: Bitmap? = null
)

data class ImageResponse(
    val images: List<Image>
)