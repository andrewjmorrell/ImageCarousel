package com.example.imagecarousel.presentation.models

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import java.util.UUID

data class CanvasImage(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    var offset: Offset = Offset.Zero, // top-left position of the frame within the canvas
    var userScale: Float = 1f,
    var imageTranslation: Offset = Offset.Zero
)