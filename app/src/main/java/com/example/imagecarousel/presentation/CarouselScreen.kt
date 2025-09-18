package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.lazy.items
import com.example.imagecarousel.domain.Image
import com.example.imagecarousel.presentation.models.CanvasImage
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselScreen(modifier: Modifier = Modifier) {

    val viewModel: CarouselViewModel = hiltViewModel<CarouselViewModel>()
    val state by viewModel.uiState.collectAsState()

    var canvasBounds by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }
    val canvasImages = remember { mutableStateListOf<CanvasImage>() }

    // State for cross-composable drag from carousel
    var isDragging by remember { mutableStateOf(false) }
    var dragBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val density = LocalDensity.current

    LaunchedEffect(Unit) { viewModel.loadImages() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Carousel") },
                modifier = Modifier.height(48.dp) // compact, below status bar by default
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFFFFFFFF))
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
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Canvas fills all remaining vertical space above the carousel
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp)
                                .weight(1f)
                                .background(Color(0xFF111315))
                                .onGloballyPositioned { layoutCoordinates ->
                                    val pos = layoutCoordinates.positionInWindow()
                                    val size = layoutCoordinates.size
                                    canvasBounds = IntRect(
                                        pos.x.roundToInt(),
                                        pos.y.roundToInt(),
                                        (pos.x + size.width).roundToInt(),
                                        (pos.y + size.height).roundToInt()
                                    )
                                }
                        ) {
                            // Render all placed images
                            canvasImages.forEach { item ->
                                // Per-item gesture state
                                var frameOffset by remember(item.id) { mutableStateOf(item.offset) }   // frame top-left on canvas
                                var userScale by remember(item.id) { mutableStateOf(item.userScale) }  // ≥ 1f
                                var imageTranslation by remember(item.id) { mutableStateOf(Offset.Zero) } // pan within frame

                                // Persist back to model
                                LaunchedEffect(frameOffset, userScale, imageTranslation) {
                                    item.offset = frameOffset
                                    item.userScale = userScale
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
                                        .clip(RoundedCornerShape(1.dp))
                                        .clipToBounds()
                                        .background(Color.Black)
                                        // Drag the whole frame by a 16dp border
                                        .pointerInput("frameDrag-${item.id}") {
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
                                        // Pinch to zoom + pan the IMAGE inside the frame
                                        .pointerInput(item.id) {
                                            detectTransformGestures { centroid, pan, zoom, _ ->
                                                val oldUser = userScale
                                                val newUser = (oldUser * zoom).coerceIn(1f, 8f)
                                                val k = newUser / oldUser

                                                // keep the point under fingers steady
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
                                ) {
                                    // Draw the image to fill the frame; apply user scale + pan
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

                        // Carousel
                        Carousel(
                            image = state.images,
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
                                    canvasImages += CanvasImage(
                                        bitmap = bmp,
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
                                    // Preview slightly above finger; offset uses half of preview size
                                    IntOffset(
                                        (dragOffset.x - (previewWidthDp.toPx(density) / 2f)).roundToInt(),
                                        (dragOffset.y - (previewHeight.toPx(density) / 2f)).roundToInt()
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

@Composable
private fun Carousel(
    image: List<Image>,
    onStartDrag: (bitmap: Bitmap, startOffsetInWindow: Offset) -> Unit,
    onDrag: (bitmap: Bitmap, pointerInWindow: Offset) -> Unit,
    onEndDrag: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastPointerInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 12.dp)
        ) {
            items(image) { bmp ->
                var itemOriginInWindow by remember { mutableStateOf(Offset.Zero) }
                val thumbHeight = 96.dp
                val aspect = (bmp.bitmap?.width ?: 1).toFloat() / (bmp.bitmap?.height ?: 1).toFloat()
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .height(thumbHeight)
                        .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF22262B))
                        .onGloballyPositioned { coords ->
                            itemOriginInWindow = coords.positionInWindow()
                        }
                        .pointerInput(bmp) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    lastPointerInWindow = itemOriginInWindow + start
                                    onStartDrag(bmp.bitmap!!, lastPointerInWindow)
                                },
                                onDrag = { change, _ ->
                                    lastPointerInWindow = itemOriginInWindow + change.position
                                    onDrag(bmp.bitmap!!, lastPointerInWindow)
                                },
                                onDragEnd = { onEndDrag() },
                                onDragCancel = { onEndDrag() }
                            )
                        }
                ) {
                    Image(
                        bitmap = bmp.bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun IntRect.contains(point: Offset): Boolean {
    val x = point.x.roundToInt()
    val y = point.y.roundToInt()
    return x in left..right && y in top..bottom
}

private fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density): Float =
    with(density) { this@toPx.toPx() }
