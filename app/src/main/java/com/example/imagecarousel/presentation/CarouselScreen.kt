package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.imagecarousel.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselScreen(modifier: Modifier = Modifier) {

    val viewModel: CarouselViewModel = hiltViewModel<CarouselViewModel>()
    val state by viewModel.uiState.collectAsState()

    var canvasBounds by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }
    val canvasImages = viewModel.canvasImages

    // State for cross-composable drag from carousel
    var isDragging by remember { mutableStateOf(false) }
    var dragBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    // State for canvas item being dragged via long-press
    var draggingCanvasItemId by remember { mutableStateOf<String?>(null) }

    var rootOriginInWindow by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    LaunchedEffect(Unit) { viewModel.loadImages() }

    Scaffold { paddingValues ->
        Box(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.White)
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
                        Text(text = stringResource(R.string.app_name),
                            color = Color.Black,
                            modifier = Modifier.align(alignment = Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp)
                        // Canvas fills all remaining vertical space above the carousel
                        if (canvasImages.isNotEmpty()) {
                            Text(
                                "Long press an image to move",
                                color = Color.Black,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally),
                                fontSize = 12.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.height(15.dp))
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                                .weight(1f)
                                .background(Color.Black)
                                .onGloballyPositioned { coords ->
                                    rootOriginInWindow = coords.positionInWindow()
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
                                // (no early return; instead, hide dragged item via alpha)
                                // Per-item gesture state
                                // Per-item gesture state with Compose state-backed vars
                                var frameOffset by remember(item.id) { mutableStateOf(item.offset) }
                                var userScale by remember(item.id) { mutableStateOf(item.userScale) }
                                var imageTranslation by remember(item.id) { mutableStateOf(item.imageTranslation) }
                                // Remembered position in window for this frame
                                var frameOriginInWindow by remember(item.id) { mutableStateOf(Offset.Zero) }

                                LaunchedEffect(item.offset) { frameOffset = item.offset }
                                LaunchedEffect(item.userScale) { userScale = item.userScale }
                                LaunchedEffect(item.imageTranslation) { imageTranslation = item.imageTranslation }

                                LaunchedEffect(frameOffset) {
                                    viewModel.updateOffset(item.id, offset = frameOffset)
                                }
                                LaunchedEffect(userScale) {
                                    viewModel.updateUserScale(item.id, userScale)
                                }
                                LaunchedEffect(imageTranslation) {
                                    viewModel.updateImageTranslation(item.id, imageTranslation)
                                }

                                // --- Frame sizing: match bitmap aspect, fit inside canvas, start smaller (no upscaling > 1:1) ---
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

                                // Content fills the frame at rest (Crop), user zoom is additional
                                val baseScale = 1f
                                val currentScale = (baseScale * userScale).coerceAtLeast(1f).coerceAtMost(8f)

                                // Clamp helper — ensures no blank space can appear at any scale
                                fun clampPanWithScale(p: Offset, scale: Float): Offset {
                                    val contentW = frameWpx * scale
                                    val contentH = frameHpx * scale
                                    val maxPanX = ((contentW - frameWpx) / 2f).coerceAtLeast(0f)
                                    val maxPanY = ((contentH - frameHpx) / 2f).coerceAtLeast(0f)
                                    return Offset(
                                        x = p.x.coerceIn(-maxPanX, maxPanX),
                                        y = p.y.coerceIn(-maxPanY, maxPanY)
                                    )
                                }

                                // Keep existing translation valid for the current scale
                                imageTranslation = clampPanWithScale(imageTranslation, currentScale)

                                // Frame box (aspect-correct), draggable by its border; inner image supports pinch-zoom/pan
                                Box(
                                    modifier = Modifier
                                        .size(width = frameWdp, height = frameHdp)
                                        .offset { IntOffset(frameOffset.x.roundToInt(), frameOffset.y.roundToInt()) }
                                        .onGloballyPositioned { coords ->
                                            frameOriginInWindow = coords.positionInWindow()
                                        }
                                        .graphicsLayer { alpha = if (draggingCanvasItemId == item.id) 0f else 1f }
                                        // Long-press drag to move image from canvas (like carousel)
                                        .pointerInput("canvasLongPressDrag-${item.id}") {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { start ->
                                                    // Show overlay while dragging; keep item in list to avoid cancelling gesture
                                                    draggingCanvasItemId = item.id
                                                    dragBitmap = item.bitmap
                                                    isDragging = true
                                                    // absolute pointer in window at gesture start
                                                    val startInWindow = frameOriginInWindow + start
                                                    dragOffset = startInWindow
                                                },
                                                onDrag = { change, _ ->
                                                    // absolute pointer in window during drag
                                                    dragOffset = frameOriginInWindow + change.position
                                                    change.consume()
                                                },
                                                onDragEnd = {
                                                    val bmp = dragBitmap
                                                    if (bmp != null && canvasBounds.contains(dragOffset)) {
                                                        val localX = dragOffset.x - canvasBounds.left
                                                        val localY = dragOffset.y - canvasBounds.top
                                                        val bmpW2 = bmp.width.toFloat()
                                                        val bmpH2 = bmp.height.toFloat()
                                                        val canvasW = canvasBounds.width.toFloat()
                                                        val canvasH = canvasBounds.height.toFloat()
                                                        val initialFitFraction = 0.6f
                                                        val scaleToFitCanvas = minOf(canvasW / bmpW2, canvasH / bmpH2, 1f) * initialFitFraction
                                                        val frameWpx2 = (bmpW2 * scaleToFitCanvas).coerceAtLeast(1f)
                                                        val frameHpx2 = (bmpH2 * scaleToFitCanvas).coerceAtLeast(1f)
                                                        val desiredTopLeftX = localX - frameWpx2 / 2f
                                                        val desiredTopLeftY = localY - frameHpx2 / 2f
                                                        val clampedX = desiredTopLeftX.coerceIn(0f, (canvasW - frameWpx2).coerceAtLeast(0f))
                                                        val clampedY = desiredTopLeftY.coerceIn(0f, (canvasH - frameHpx2).coerceAtLeast(0f))

                                                        // Update the existing item's offset
                                                        viewModel.updateOffset(item.id, Offset(clampedX, clampedY))

                                                        // Bring this item to front by moving it to the end of the list
                                                        val list = viewModel.canvasImages
                                                        if (list is MutableList<*>) {
                                                            @Suppress("UNCHECKED_CAST")
                                                            val mutable = list as MutableList<com.example.imagecarousel.presentation.models.CanvasImage>
                                                            val idx = mutable.indexOfFirst { it.id == item.id }
                                                            if (idx >= 0 && idx < mutable.size - 1) {
                                                                val moved = mutable.removeAt(idx)
                                                                mutable.add(moved)
                                                            }
                                                        }
                                                    }
                                                    // reset drag flags
                                                    draggingCanvasItemId = null
                                                    isDragging = false
                                                    dragBitmap = null
                                                },
                                                onDragCancel = {
                                                    draggingCanvasItemId = null
                                                    isDragging = false
                                                    dragBitmap = null
                                                }
                                            )
                                        }
                                        .clip(RoundedCornerShape(1.dp))
                                        .clipToBounds()
                                        .background(Color.Black)
                                        // Drag the whole frame by a 16dp border
                                        .pointerInput("frameDrag-${item.id}", draggingCanvasItemId) {
                                            if (draggingCanvasItemId != item.id) {
                                                var dragFrame = false
                                                detectDragGestures(
                                                    onDragStart = { start ->
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
                                                    onDragEnd = { dragFrame = false },
                                                    onDragCancel = { dragFrame = false }
                                                )
                                            }
                                        }
                                        // Pinch to zoom + pan the IMAGE inside the frame
                                        .pointerInput(item.id, draggingCanvasItemId) {
                                            if (draggingCanvasItemId != item.id) {
                                                detectTransformGestures { centroid, pan, zoom, _ ->
                                                    val oldUser = userScale
                                                    val newUser = (oldUser * zoom).coerceIn(1f, 8f)
                                                    val k = newUser / oldUser

                                                    val frameCenter = Offset(frameWpx / 2f, frameHpx / 2f)
                                                    val newTranslation = Offset(
                                                        x = k * imageTranslation.x - (k - 1f) * (centroid.x - frameCenter.x) + pan.x,
                                                        y = k * imageTranslation.y - (k - 1f) * (centroid.y - frameCenter.y) + pan.y
                                                    )

                                                    val newCurrentScale =
                                                        (baseScale * newUser).coerceAtLeast(1f).coerceAtMost(8f)
                                                    imageTranslation = clampPanWithScale(newTranslation, newCurrentScale)
                                                    userScale = newUser
                                                }
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = item.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop, // cover — no blank space at rest
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                transformOrigin = TransformOrigin(0.5f, 0.5f)

                                                val drawnW = frameWpx * currentScale
                                                val drawnH = frameHpx * currentScale
                                                val centerX = (frameWpx - drawnW) / 2f
                                                val centerY = (frameHpx - drawnH) / 2f
                                                translationX = centerX + imageTranslation.x
                                                translationY = centerY + imageTranslation.y
                                                scaleX = currentScale
                                                scaleY = currentScale
                                            }
                                    )
                                }
                            }
                        }

                        Text(
                            "Long press to drag image",
                            color = Color.Black,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .align(Alignment.CenterHorizontally),
                            fontSize = 12.sp
                        )

                        // Carousel
                        Carousel(
                            images = state.images,
                            onStartDrag = { bmp, startOffset ->
                                dragBitmap = bmp
                                dragOffset = startOffset
                                isDragging = true
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
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }

                    // Drag preview overlay (follows the finger)
                    if (isDragging && dragBitmap != null) {
                        val previewHeight = 96.dp
                        val aspect = dragBitmap!!.width.toFloat() / dragBitmap!!.height.toFloat()
                        val previewWidthDp = with(density) { (previewHeight.toPx(this) * aspect).dp }
                        Image(
                            bitmap = dragBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .offset {
                                    val previewWidthPx = previewWidthDp.toPx(density)
                                    val previewHeightPx = previewHeight.toPx(density)
                                    val localX = dragOffset.x - rootOriginInWindow.x
                                    val localY = dragOffset.y - rootOriginInWindow.y
                                    IntOffset(
                                        (localX - previewWidthPx / 2f).roundToInt(),
                                        (localY - previewHeightPx / 2f).roundToInt()
                                    )
                                }
                                .size(previewWidthDp, previewHeight)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }
            }
        }
    }
}
