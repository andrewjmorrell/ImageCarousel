package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.imagecarousel.R
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {

    val viewModel: CarouselViewModel = hiltViewModel<CarouselViewModel>()
    val state by viewModel.uiState.collectAsState()

    var canvasBounds by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }
    val canvasImages = viewModel.canvasImages

    val hapticFeedback = LocalHapticFeedback.current

    // State for cross-composable drag from carousel
    var isDragging by remember { mutableStateOf(false) }
    var dragBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var draggingCanvasItemId by remember { mutableStateOf<String?>(null) }
    var dragPreviewWidthDp by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
    var dragPreviewHeightDp by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }

    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    // Capture the device orientation once at app start; never update it on rotation
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    var initialOrientation by rememberSaveable { mutableStateOf<Int?>(null) }

    // Lock orientation to the app's initial orientation and load images
    LaunchedEffect(Unit) {
        if (initialOrientation == null) {
            initialOrientation = configuration.orientation
            val activity = context as? Activity
            if (activity != null) {
                activity.requestedOrientation = if (initialOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            }
        }
        viewModel.loadImages(8)
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .onGloballyPositioned { coords ->
                overlayOriginInWindow = coords.positionInWindow()
            }
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.error != null -> {
                Text("Error: ${state.error}")
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = stringResource(R.string.canvas_text),
                        color = Color.Black,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally),
                        fontSize = dimensionResource(R.dimen.text_height).value.sp
                    )

                    Box(
                        modifier = Modifier
                            .then(
                                if (initialOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                                    Modifier.fillMaxHeight()
                                } else {
                                    Modifier.fillMaxWidth()
                                }
                            )
                            .aspectRatio(1f)
                            .padding(horizontal = dimensionResource(R.dimen.canvas_padding))
                            .weight(1f)
                            .background(Color.Black)
                            .align(Alignment.CenterHorizontally)
                            .onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                val size = coords.size
                                canvasBounds = IntRect(
                                    pos.x.roundToInt(),
                                    pos.y.roundToInt(),
                                    (pos.x + size.width).roundToInt(),
                                    (pos.y + size.height).roundToInt()
                                )
                            }
                    ) {
                        canvasImages.forEach { item ->
                            var frameOffset by remember(item.id) { mutableStateOf(item.offset) }
                            var userScale by remember(item.id) { mutableStateOf(item.userScale) }

                            LaunchedEffect(item.offset) { frameOffset = item.offset }
                            LaunchedEffect(item.userScale) { userScale = item.userScale }

                            LaunchedEffect(frameOffset) {
                                viewModel.updateOffset(item.id, offset = frameOffset)
                            }
                            LaunchedEffect(userScale) {
                                viewModel.updateUserScale(item.id, userScale)
                            }

                            val densityLocal = LocalDensity.current
                            val bmpW = item.bitmap.width.toFloat()
                            val bmpH = item.bitmap.height.toFloat()
                            val canvasW = canvasBounds.width.toFloat()
                            val canvasH = canvasBounds.height.toFloat()

                            val initialFitFraction = 0.6f
                            val scaleToFitCanvas = minOf(canvasW / bmpW, canvasH / bmpH, 1f) * initialFitFraction
                            val baseFrameWpx = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
                            val baseFrameHpx = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)

                            // Allow shrinking well below the base size, but keep a sane visual minimum (e.g., 48dp)
                            val minFramePx = with(densityLocal) { 48.dp.toPx() }
                            val minScaleW = (minFramePx / baseFrameWpx).coerceAtMost(1f)
                            val minScaleH = (minFramePx / baseFrameHpx).coerceAtMost(1f)
                            val minScale = maxOf(minScaleW, minScaleH)

                            val frameScale = userScale.coerceIn(minScale, 8f)
                            val frameWpx = baseFrameWpx * frameScale
                            val frameHpx = baseFrameHpx * frameScale
                            val frameWdp = with(densityLocal) { frameWpx.toDp() }
                            val frameHdp = with(densityLocal) { frameHpx.toDp() }

                            Box(
                                modifier = Modifier
                                    .size(width = frameWdp, height = frameHdp)
                                    .offset { IntOffset(frameOffset.x.roundToInt(), frameOffset.y.roundToInt()) }
                                    .graphicsLayer { alpha = if (draggingCanvasItemId == item.id) 0f else 1f }
                                    // (Removed long-press drag to move image from canvas)
                                    .clip(RoundedCornerShape(1.dp))
                                    .clipToBounds()
                                    .background(Color.Black)
                                    .pointerInput("frameDragOrPinch-${item.id}") {
                                        awaitEachGesture {
                                            // Wait for first touch; don't require unconsumed so we always get it
                                            do {
                                                val event = awaitPointerEvent()
                                                val pressed = event.changes.any { it.pressed }
                                                if (!pressed) break

                                                val pointers = event.changes.count { it.pressed }

                                                if (pointers >= 2) {
                                                    // Multi-touch: pinch to resize the frame
                                                    val oldScale = userScale
                                                    val zoom = event.calculateZoom()
                                                    val newScale = (oldScale * zoom).coerceIn(minScale, 8f)
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

                                                    event.changes.forEach { c -> if (c.positionChange() != Offset.Zero) c.consume() }
                                                } else {
                                                    // Single finger: drag the frame
                                                    val change = event.changes.first { it.pressed }
                                                    val delta = change.positionChange()
                                                    if (delta != Offset.Zero) {
                                                        val new = frameOffset + delta
                                                        // Recompute current frame size from the latest scale to avoid stale clamps
                                                        val currentScale = userScale.coerceIn(minScale, 8f)
                                                        val currentFrameW = baseFrameWpx * currentScale
                                                        val currentFrameH = baseFrameHpx * currentScale
                                                        val maxX = (canvasBounds.width - currentFrameW).coerceAtLeast(0f)
                                                        val maxY = (canvasBounds.height - currentFrameH).coerceAtLeast(0f)
                                                        frameOffset = Offset(
                                                            new.x.coerceIn(0f, maxX),
                                                            new.y.coerceIn(0f, maxY)
                                                        )
                                                        change.consume()
                                                    }
                                                }
                                            } while (true)
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = item.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                )
                            }
                        }
                    }

                    Text(
                        text = stringResource(R.string.carousel_text),
                        color = Color.Black,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally),
                        fontSize = dimensionResource(R.dimen.text_height).value.sp
                    )

                    // Carousel
                    Carousel(
                        images = state.images,
                        onStartDrag = { bmp, startOffset ->
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragBitmap = bmp
                            dragOffset = startOffset
                            isDragging = true
                            // Preview should match the eventual frame size on the canvas
                            val bmpW = bmp.width.toFloat()
                            val bmpH = bmp.height.toFloat()
                            val canvasW = canvasBounds.width.toFloat()
                            val canvasH = canvasBounds.height.toFloat()
                            val initialFitFraction = 0.6f
                            val scaleToFitCanvas = minOf(canvasW / bmpW, canvasH / bmpH, 1f) * initialFitFraction
                            val frameWpx = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
                            val frameHpx = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)
                            dragPreviewWidthDp = with(density) { frameWpx.toDp() }
                            dragPreviewHeightDp = with(density) { frameHpx.toDp() }
                        },
                        onDrag = { _, pointerInWindow ->
                            dragOffset = pointerInWindow
                        },
                        onEndDrag = {
                            val bmp = dragBitmap
                            if (bmp != null && canvasBounds.contains(dragOffset)) {
                                // Convert drop from screen to canvas-local coords
                                val localX = dragOffset.x - canvasBounds.left
                                val localY = dragOffset.y - canvasBounds.top
                                val bmpW = bmp.width.toFloat()
                                val bmpH = bmp.height.toFloat()
                                val canvasW = canvasBounds.width.toFloat()
                                val canvasH = canvasBounds.height.toFloat()
                                val initialFitFraction = 0.6f
                                val scaleToFitCanvas =
                                    minOf(canvasW / bmpW, canvasH / bmpH, 1f) * initialFitFraction
                                val frameWpx = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
                                val frameHpx = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)
                                // Center the frame under the finger, then clamp inside canvas
                                val desiredTopLeftX = localX - frameWpx / 2f
                                val desiredTopLeftY = localY - frameHpx / 2f
                                val clampedX = desiredTopLeftX.coerceIn(0f, (canvasW - frameWpx).coerceAtLeast(0f))
                                val clampedY = desiredTopLeftY.coerceIn(0f, (canvasH - frameHpx).coerceAtLeast(0f))
                                viewModel.addCanvasImage(
                                    bm = bmp,
                                    offset = Offset(clampedX, clampedY)
                                )
                            }
                            isDragging = false
                            dragBitmap = null
                            dragPreviewWidthDp = null
                            dragPreviewHeightDp = null
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }

                // Drag preview overlay (follows the finger)
                if (isDragging && dragBitmap != null) {
                    // Prefer exact preview size (frame size). Fallback: fixed height with aspect.
                    val fallbackHeight = dimensionResource(id = R.dimen.drag_preview_height)
                    val (previewWidthDp, previewHeightDp) = if (dragPreviewWidthDp != null && dragPreviewHeightDp != null) {
                        Pair(dragPreviewWidthDp!!, dragPreviewHeightDp!!)
                    } else {
                        val aspect = dragBitmap!!.width.toFloat() / dragBitmap!!.height.toFloat()
                        val w = with(density) { (fallbackHeight.toPx(this) * aspect).dp }
                        Pair(w, fallbackHeight)
                    }

                    Image(
                        bitmap = dragBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(previewWidthDp, previewHeightDp)
                            .offset {
                                val previewWidthPx = with(density) { previewWidthDp.toPx() }
                                val previewHeightPx = with(density) { previewHeightDp.toPx() }
                                val localX = dragOffset.x - overlayOriginInWindow.x
                                val localY = dragOffset.y - overlayOriginInWindow.y
                                IntOffset(
                                    (localX - previewWidthPx / 2f).roundToInt(),
                                    (localY - previewHeightPx / 2f).roundToInt()
                                )
                            }
                    )
                }
            }
        }
    }
}