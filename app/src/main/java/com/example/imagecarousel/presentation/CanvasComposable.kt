package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntRect
import com.example.imagecarousel.R
import com.example.imagecarousel.presentation.models.CanvasImage
import kotlin.math.roundToInt

@Composable
fun CanvasComposable(
    modifier: Modifier = Modifier,
    initialOrientation: Int,
    canvasImages: List<CanvasImage>,
    canvasBounds: IntRect,
    onCanvasBoundsChange: (IntRect) -> Unit,
    draggingCanvasItemId: String?,
    onSetDraggingId: (String?) -> Unit,
    onSetDragBitmap: (Bitmap?) -> Unit,
    onSetIsDragging: (Boolean) -> Unit,
    onSetDragOffset: (Offset) -> Unit,
    onSetDragPreviewSizeDp: (androidx.compose.ui.unit.Dp?, androidx.compose.ui.unit.Dp?) -> Unit,
    viewModel: CarouselViewModel,
    hapticFeedback: HapticFeedback,
) {
    Box(
        modifier = modifier
            .then(
                if (initialOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .aspectRatio(1f)
            .padding(horizontal = dimensionResource(R.dimen.canvas_padding))
            .background(Color.Black)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val size = coords.size
                onCanvasBoundsChange(
                    IntRect(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        (pos.x + size.width).roundToInt(),
                        (pos.y + size.height).roundToInt()
                    )
                )
            }
    ) {
        canvasImages.forEach { item ->
            CanvasImageComposable(
                item = item,
                canvasBounds = canvasBounds,
                draggingCanvasItemId = draggingCanvasItemId,
                onSetDraggingId = onSetDraggingId,
                onSetDragBitmap = onSetDragBitmap,
                onSetIsDragging = onSetIsDragging,
                onSetDragOffset = onSetDragOffset,
                onSetDragPreviewSizeDp = onSetDragPreviewSizeDp,
                viewModel = viewModel,
                hapticFeedback = hapticFeedback
            )
        }
    }
}