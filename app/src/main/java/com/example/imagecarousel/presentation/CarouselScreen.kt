package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import com.example.imagecarousel.domain.Image
import kotlin.math.roundToInt
import com.example.imagecarousel.presentation.models.CanvasImage


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

    LaunchedEffect(Unit) {
        viewModel.loadImages()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Image Carousel") }) }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .aspectRatio(1f) // square
                                .clip(RoundedCornerShape(16.dp))
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
                            // Render all placed images; each is draggable & pinch-zoomable within a fixed frame
                            canvasImages.forEach { item ->
                                // Local state for per-item gesture
                                var frameOffset by remember(item.id) { mutableStateOf(item.offset) }     // where the FRAME sits on the canvas
                                var userScale by remember(item.id) { mutableStateOf(item.userScale) }    // user zoom (>= 1f)
                                var imageTranslation by remember(item.id) { mutableStateOf(Offset.Zero) } // pan inside the frame, relative to center

                                // Persist back to the backing data class (optional persistence)
                                LaunchedEffect(frameOffset, userScale, imageTranslation) {
                                    item.offset = frameOffset
                                    item.userScale = userScale
                                }

                                // Frame should match the image's aspect and fit inside the canvas (no upscaling beyond 1:1)
                                val densityLocal = LocalDensity.current
                                val bmpW = item.bitmap.width.toFloat()
                                val bmpH = item.bitmap.height.toFloat()
                                val canvasW = canvasBounds.width.toFloat()
                                val canvasH = canvasBounds.height.toFloat()
                                val scaleToFitCanvas = minOf(canvasW / bmpW, canvasH / bmpH, 1f)
                                val frameWpx = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
                                val frameHpx = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)
                                val frameWdp = with(densityLocal) { frameWpx.toDp() }
                                val frameHdp = with(densityLocal) { frameHpx.toDp() }

                                // Base scale: content fills the frame (due to ContentScale.Crop)
                                val baseScale = 1f

                                // Current scale of the drawn content
                                val currentScale = (baseScale * userScale).coerceAtLeast(1f).coerceAtMost(8f)

                                // Clamp helper that depends on the *given* scale so we can clamp during gesture updates
                                fun clampPanWithScale(p: Offset, scale: Float): Offset {
                                    val contentW = frameWpx * scale
                                    val contentH = frameHpx * scale
                                    val maxPanX = maxOf(0f, (contentW - frameWpx) / 2f)
                                    val maxPanY = maxOf(0f, (contentH - frameHpx) / 2f)
                                    return Offset(
                                        x = p.x.coerceIn(-maxPanX, maxPanX),
                                        y = p.y.coerceIn(-maxPanY, maxPanY)
                                    )
                                }

                                // Actively clamp any existing translation using the *current* scale
                                imageTranslation = clampPanWithScale(imageTranslation, currentScale)

                                // Gesture for zoom/pan inside the fixed frame
                                Box(
                                    modifier = Modifier
                                        .size(frameWdp, frameHdp) // frame matches image aspect, fits in canvas
                                        .offset {
                                            IntOffset(frameOffset.x.roundToInt(), frameOffset.y.roundToInt())
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black)
                                        .pointerInput("frameDrag-${item.id}") {
                                            var dragFrame = false
                                            detectDragGestures(
                                                onDragStart = { start ->
                                                    // Start is in frame-local coords (0..frameWpx, 0..frameHpx)
                                                    val borderPx = with(densityLocal) { 16.dp.toPx() }
                                                    dragFrame = start.x <= borderPx || start.y <= borderPx ||
                                                                start.x >= frameWpx - borderPx || start.y >= frameHpx - borderPx
                                                },
                                                onDrag = { change, dragAmount ->
                                                    if (dragFrame) {
                                                        val new = frameOffset + dragAmount
                                                        val maxX = (canvasW - frameWpx).coerceAtLeast(0f)
                                                        val maxY = (canvasH - frameHpx).coerceAtLeast(0f)
                                                        frameOffset = Offset(new.x.coerceIn(0f, maxX), new.y.coerceIn(0f, maxY))
                                                        change.consume()
                                                    }
                                                },
                                                onDragEnd = { dragFrame = false },
                                                onDragCancel = { dragFrame = false }
                                            )
                                        }
                                        .pointerInput(item.id) {
                                            detectTransformGestures { centroid, pan, zoom, _ ->
                                                // Compute proposed new user scale
                                                val oldUser = userScale
                                                val newUser = (oldUser * zoom).coerceIn(1f, 8f) // don't go below fit-to-frame
                                                val k = newUser / oldUser

                                                // Frame center in local (frame) coords
                                                val frameCenter = Offset(frameWpx / 2f, frameHpx / 2f)

                                                // Adjust imageTranslation so the point under the fingers (centroid) stays put during scaling
                                                // T' = k*T - (k - 1) * (a - frameCenter) + pan
                                                val newTranslation = Offset(
                                                    x = k * imageTranslation.x - (k - 1f) * (centroid.x - frameCenter.x) + pan.x,
                                                    y = k * imageTranslation.y - (k - 1f) * (centroid.y - frameCenter.y) + pan.y
                                                )

                                                val newCurrentScale = (baseScale * newUser).coerceAtLeast(1f).coerceAtMost(8f)
                                                imageTranslation = clampPanWithScale(newTranslation, newCurrentScale)
                                                userScale = newUser
                                            }
                                        }
                                ) {
                                    // Draw the image centered; apply current scale and inner pan.
                                    Image(
                                        bitmap = item.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                // Scale the filled content and keep it centered; pan within bounds
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

                        Carousel(
                            image = state.images,
                            onStartDrag = { bmp, startOffset ->
                                dragBitmap = bmp
                                dragOffset = startOffset
                                isDragging = true
                            },
                            onDrag = { _, dragBy ->
                                dragOffset += dragBy
                            },
                            onEndDrag = {
                                val bmp = dragBitmap
                                if (bmp != null && canvasBounds.contains(dragOffset)) {
                                    // Convert drop point from screen coords to canvas-local coords
                                    val localX = dragOffset.x - canvasBounds.left
                                    val localY = dragOffset.y - canvasBounds.top
                                    val bmpW = bmp.width.toFloat()
                                    val bmpH = bmp.height.toFloat()
                                    val canvasW = canvasBounds.width.toFloat()
                                    val canvasH = canvasBounds.height.toFloat()
                                    val scaleToFitCanvas = minOf(canvasW / bmpW, canvasH / bmpH, 1f)
                                    val frameWpx = (bmpW * scaleToFitCanvas).coerceAtLeast(1f)
                                    val frameHpx = (bmpH * scaleToFitCanvas).coerceAtLeast(1f)
                                    val clampedX = (localX).coerceIn(0f, (canvasW - frameWpx).coerceAtLeast(0f))
                                    val clampedY = (localY).coerceIn(0f, (canvasH - frameHpx).coerceAtLeast(0f))
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
                        Image(
                            bitmap = dragBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .offset {
                                    // Center the preview a bit above finger
                                    IntOffset(
                                        (dragOffset.x - 48.dp.toPx(density)).roundToInt(),
                                        (dragOffset.y - 48.dp.toPx(density)).roundToInt()
                                    )
                                }
                                .size(96.dp)
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
    onDrag: (bitmap: Bitmap, dragDelta: Offset) -> Unit,
    onEndDrag: () -> Unit,
    modifier: Modifier = Modifier
) {
    // We’ll translate touch points into window coordinates for cross-composable hit-testing.
    var lastPointerInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 12.dp)
        ) {
            items(image) { bmp ->
                // Each thumbnail can initiate a drag
                var itemOriginInWindow by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(96.dp)
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
                                onDrag = { change, dragAmount ->
                                    lastPointerInWindow = itemOriginInWindow + change.position
                                    onDrag(bmp.bitmap!!, Offset(dragAmount.x, dragAmount.y))
                                },
                                onDragEnd = {
                                    onEndDrag()
                                },
                                onDragCancel = {
                                    onEndDrag()
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = bmp.bitmap!!.asImageBitmap(),
                        contentDescription = null,
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