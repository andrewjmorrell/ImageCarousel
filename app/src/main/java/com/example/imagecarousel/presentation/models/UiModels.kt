package com.example.imagecarousel.presentation.models

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.UUID

data class CanvasImage(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    var offset: Offset = Offset.Zero, // top-left position of the frame within the canvas
    var userScale: Float = 1f,        // user-controlled zoom (starts at 1f)
    val frameSize: Dp = 160.dp // fixed visible box; does NOT change on zoom
)