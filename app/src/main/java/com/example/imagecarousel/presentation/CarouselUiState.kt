package com.example.imagecarousel.presentation

import com.example.imagecarousel.domain.Image

data class CarouselUiState(
    val loading: Boolean = false,
    val images: List<Image> = emptyList(),
    val error: String? = null
)