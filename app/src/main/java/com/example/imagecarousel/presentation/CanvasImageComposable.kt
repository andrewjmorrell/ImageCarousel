package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.example.imagecarousel.presentation.models.CanvasImage
import kotlin.math.roundToInt

@Composable
fun CanvasImageComposable(
    item: CanvasImage,
    canvasBounds: IntRect,
    draggingCanvasItemId: String?,
    onSetDraggingId: (String?) -> Unit,
    onSetDragBitmap: (Bitmap?) -> Unit,
    onSetIsDragging: (Boolean) -> Unit,
    onSetDragOffset: (Offset) -> Unit,
    onSetDragPreviewSizeDp: (Dp?, Dp?) -> Unit,
    viewModel: CarouselViewModel,
    hapticFeedback: HapticFeedback,
) {
    var frameOffset by remember(item.id) { mutableStateOf(item.offset) }
    var userScale by remember(item.id) { mutableStateOf(item.userScale) }
    var imageTranslation by remember(item.id) { mutableStateOf(item.imageTranslation) }
    var frameOriginInWindow by remember(item.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.offset) { frameOffset = item.offset }
    LaunchedEffect(item.userScale) { userScale = item.userScale }
    LaunchedEffect(item.imageTranslation) { imageTranslation = item.imageTranslation }

    LaunchedEffect(frameOffset) { viewModel.updateOffset(item.id, offset = frameOffset) }
    LaunchedEffect(userScale) { viewModel.updateUserScale(item.id, userScale) }
    LaunchedEffect(imageTranslation) { viewModel.updateImageTranslation(item.id, imageTranslation) }

    val densityLocal = LocalDensity.current
    val bmpW = item.bitmap.width.toFloat()
    val bmpH = item.bitmap.height.toFloat()
    val canvasW = canvasBounds.width.toFloat()
    val canvasH = canvasBounds.height.toFloat()

    val initialFitFraction = 0.6f
    val scaleToFitCanvas = minOf(canvasW / bmpW, canvasH / bmpH, 1f) * initialFitFraction
    val frameWpx = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
    val frameHpx = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)
    val frameWdp = with(densityLocal) { frameWpx.toDp() }
    val frameHdp = with(densityLocal) { frameHpx.toDp() }

    val currentZoom = userScale.coerceIn(1f, 8f)

    fun clampPanWithZoom(p: Offset, zoom: Float): Offset {
        val contentW = frameWpx * zoom
        val contentH = frameHpx * zoom
        val maxPanX = ((contentW - frameWpx) / 2f).coerceAtLeast(0f)
        val maxPanY = ((contentH - frameHpx) / 2f).coerceAtLeast(0f)
        return Offset(
            x = p.x.coerceIn(-maxPanX, maxPanX),
            y = p.y.coerceIn(-maxPanY, maxPanY)
        )
    }

    imageTranslation = clampPanWithZoom(imageTranslation, currentZoom)

    Box(
        modifier = Modifier
            .size(width = frameWdp, height = frameHdp)
            .offset { IntOffset(frameOffset.x.roundToInt(), frameOffset.y.roundToInt()) }
            .onGloballyPositioned { coords ->
                frameOriginInWindow = coords.positionInWindow()
            }
            .graphicsLayer { alpha = if (draggingCanvasItemId == item.id) 0f else 1f }
            // Long press to drag image on canvas
            .pointerInput("canvasLongPressDrag-${item.id}") {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        onSetDraggingId(item.id)
                        onSetDragBitmap(item.bitmap)
                        onSetIsDragging(true)
                        onSetDragOffset(frameOriginInWindow + start)
                        onSetDragPreviewSizeDp(
                            with(densityLocal) { frameWpx.toDp() },
                            with(densityLocal) { frameHpx.toDp() }
                        )
                    },
                    onDrag = { change, _ ->
                        onSetDragOffset(frameOriginInWindow + change.position)
                        change.consume()
                    },
                    onDragEnd = {
                        onSetDraggingId(null)
                        onSetIsDragging(false)
                        onSetDragBitmap(null)
                        onSetDragPreviewSizeDp(null, null)
                    }
                )
            }
            .clip(RoundedCornerShape(1.dp))
            .clipToBounds()
            .background(Color.Black)
            // Drag image logic here
            .pointerInput("frameDrag-${item.id}", draggingCanvasItemId) {
                if (draggingCanvasItemId != item.id) {
                    var dragFrame = false
                    detectDragGestures(
                        onDragStart = { start ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                            val borderPx = with(densityLocal) { 16.dp.toPx() }
                            dragFrame = start.x <= borderPx || start.y <= borderPx ||
                                    start.x >= frameWpx - borderPx || start.y >= frameHpx - borderPx
                        },
                        onDrag = { change, dragAmount ->
                            if (dragFrame) {
                                val new = frameOffset + dragAmount
                                val maxX = (canvasBounds.width - frameWpx).coerceAtLeast(0f)
                                val maxY = (canvasBounds.height - frameHpx).coerceAtLeast(0f)
                                frameOffset = Offset(
                                    new.x.coerceIn(0f, maxX),
                                    new.y.coerceIn(0f, maxY)
                                )
                                change.consume()
                            }
                        },
                        onDragEnd = { dragFrame = false }
                    )
                }
            }
            // Pinch and zoom logic here
            .pointerInput(item.id, draggingCanvasItemId) {
                if (draggingCanvasItemId != item.id) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldZoom = userScale
                        val newZoom = (oldZoom * zoom).coerceIn(1f, 8f)
                        val k = newZoom / oldZoom

                        val frameCenter = Offset(frameWpx / 2f, frameHpx / 2f)
                        val newTranslation = Offset(
                            x = k * imageTranslation.x - (k - 1f) * (centroid.x - frameCenter.x) + pan.x,
                            y = k * imageTranslation.y - (k - 1f) * (centroid.y - frameCenter.y) + pan.y
                        )

                        imageTranslation = clampPanWithZoom(newTranslation, newZoom)
                        userScale = newZoom
                    }
                }
            }
    ) {
        Image(
            bitmap = item.bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = currentZoom
                    scaleY = currentZoom
                    translationX = imageTranslation.x
                    translationY = imageTranslation.y
                }
        )
    }
}