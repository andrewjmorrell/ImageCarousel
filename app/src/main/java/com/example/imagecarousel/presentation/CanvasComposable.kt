package com.example.imagecarousel.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.example.imagecarousel.R
import com.example.imagecarousel.presentation.models.CanvasImage
import kotlin.collections.forEach
import kotlin.math.roundToInt

@Composable
fun CanvasComposable(
    modifier: Modifier = Modifier,
    initialOrientation: Int,
    canvasBounds: IntRect,
    onCanvasBoundsChange: (IntRect) -> Unit,
    canvasImages: List<CanvasImage>,
    draggingCanvasItemId: String?,
    viewModel: ImageCarouselViewModel,
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
            var frameOffset by remember(item.id) { mutableStateOf(item.offset) }
            var userScale by remember(item.id) { mutableStateOf(item.userScale) }

            LaunchedEffect(item.offset) { frameOffset = item.offset }
            LaunchedEffect(item.userScale) { userScale = item.userScale }

            val densityLocal = LocalDensity.current
            val bmpW = item.bitmap.width.toFloat()
            val bmpH = item.bitmap.height.toFloat()
            val canvasW = canvasBounds.width.toFloat()
            val canvasH = canvasBounds.height.toFloat()

            val initialFitFraction = 0.6f
            val scaleToFitCanvas = minOf(canvasW / bmpW, canvasH / bmpH, 1f) * initialFitFraction
            val (baseFrameWpx, baseFrameHpx) = remember(item.id, canvasBounds.width, canvasBounds.height) {
                val w = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
                val h = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)
                w to h
            }

            // Allow shrinking below base size but keep a minimum (48dp)
            val minFramePx = with(densityLocal) { 48.dp.toPx() }
            val minScaleW = (minFramePx / baseFrameWpx).coerceAtMost(1f)
            val minScaleH = (minFramePx / baseFrameHpx).coerceAtMost(1f)
            val minScale = maxOf(minScaleW, minScaleH)

            val frameScale = userScale.coerceIn(minScale, 8f)
            val frameWpx = baseFrameWpx * frameScale
            val frameHpx = baseFrameHpx * frameScale
            val (frameWdp, frameHdp) = remember(userScale, baseFrameWpx, baseFrameHpx) {
                with(densityLocal) { frameWpx.toDp() to frameHpx.toDp() }
            }

            Box(
                modifier = Modifier
                    .size(width = frameWdp, height = frameHdp)
                    .offset { IntOffset(frameOffset.x.roundToInt(), frameOffset.y.roundToInt()) }
                    .graphicsLayer { alpha = if (draggingCanvasItemId == item.id) 0f else 1f }
                    .clip(RoundedCornerShape(1.dp))
                    .clipToBounds()
                    .background(Color.Black)
                    .pointerInput("frameDragOrPinch-${item.id}") {
                        var pendingOffset: Offset? = null
                        var pendingScale: Float? = null
                        awaitEachGesture {
                            val first = awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.any { it.pressed }
                                if (!pressed) break

                                val pointers = event.changes.count { it.pressed }

                                if (pointers >= 2) {
                                    // Pinch to resize; cannot exceed canvas bounds
                                    val oldScale = userScale
                                    val zoom = event.calculateZoom()

                                    val maxScaleW = canvasBounds.width.toFloat() / baseFrameWpx
                                    val maxScaleH = canvasBounds.height.toFloat() / baseFrameHpx
                                    val maxAllowedScale = minOf(maxScaleW, maxScaleH, 8f)

                                    val newScale = (oldScale * zoom).coerceIn(minScale, maxAllowedScale)
                                    val k = if (oldScale == 0f) 1f else newScale / oldScale

                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val deltaTopLeft = centroid * (1f - k)
                                    val unclamped = frameOffset + deltaTopLeft

                                    val newW = baseFrameWpx * newScale
                                    val newH = baseFrameHpx * newScale
                                    val maxX = (canvasBounds.width - newW).coerceAtLeast(0f)
                                    val maxY = (canvasBounds.height - newH).coerceAtLeast(0f)

                                    frameOffset = Offset(
                                        x = unclamped.x.coerceIn(0f, maxX),
                                        y = unclamped.y.coerceIn(0f, maxY)
                                    )
                                    userScale = newScale
                                    pendingOffset = frameOffset
                                    pendingScale = newScale

                                    event.changes.forEach { c ->
                                        if (c.positionChange() != Offset.Zero) c.consume()
                                    }
                                } else {
                                    // Single finger: drag the frame (use current scale for clamping)
                                    val change = event.changes.first { it.pressed }
                                    val delta = change.positionChange()
                                    if (delta != Offset.Zero) {
                                        val currentScale = userScale.coerceIn(minScale, 8f)
                                        val currentFrameW = baseFrameWpx * currentScale
                                        val currentFrameH = baseFrameHpx * currentScale
                                        val maxX = (canvasBounds.width - currentFrameW).coerceAtLeast(0f)
                                        val maxY = (canvasBounds.height - currentFrameH).coerceAtLeast(0f)

                                        frameOffset = Offset(
                                            (frameOffset.x + delta.x).coerceIn(0f, maxX),
                                            (frameOffset.y + delta.y).coerceIn(0f, maxY)
                                        )
                                        pendingOffset = frameOffset
                                        change.consume()
                                    }
                                }
                            } while (true)
                            // Commit final values once per gesture
                            pendingOffset?.let { viewModel.updateOffset(item.id, it) }
                            pendingScale?.let { viewModel.updateUserScale(item.id, it) }
                            pendingOffset = null
                            pendingScale = null
                        }
                    }
            ) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}