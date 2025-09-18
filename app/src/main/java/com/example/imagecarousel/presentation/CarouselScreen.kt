package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
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
import java.util.UUID
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import com.example.imagecarousel.domain.Image
import kotlin.math.roundToInt


data class PlacedImage(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    var offset: Offset = Offset.Zero, // position within the canvas (top-left based)
    var scale: Float = 1f            // maintain aspect ratio by uniform scaling
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarouselScreen(modifier: Modifier = Modifier) {

    val viewModel: CarouselViewModel = hiltViewModel<CarouselViewModel>()

    val state by viewModel.uiState.collectAsState()

    var canvasBounds by remember { mutableStateOf(IntRect(0, 0, 0, 0)) }

    // State: the items placed on the canvas
    val placed = remember { mutableStateListOf<PlacedImage>() }

    // State for cross-composable drag from carousel
    var isDragging by remember { mutableStateOf(false) }
    var dragBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) } // in root/screen coords

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
                            // Render all placed image; each is draggable & pinch-zoomable
                            placed.forEach { item ->
                                var itemOffset by remember(item.id) { mutableStateOf(item.offset) }
                                var itemScale by remember(item.id) { mutableStateOf(item.scale) }

                                // Keep backing data updated (optional, if you need persistence)
                                LaunchedEffect(itemOffset, itemScale) {
                                    item.offset = itemOffset
                                    item.scale = itemScale
                                }

                                // Gesture: transform (pan inside canvas + pinch to zoom)
                                Box(
                                    modifier = Modifier
                                        .wrapContentSize()
                                        .offset {
                                            IntOffset(
                                                x = itemOffset.x.roundToInt(),
                                                y = itemOffset.y.roundToInt()
                                            )
                                        }
                                        .pointerInput(item.id) {
                                            detectTransformGestures { _, pan, zoom, _ ->
                                                // Pan: move within canvas (clamp roughly to bounds)
                                                val newOffset = itemOffset + pan
                                                // Simple clamping to keep image roughly inside canvas:
                                                // (We don't know rendered size; a simple safe margin helps)
                                                val marginPx = with(density) { 24.dp.toPx() }
                                                val minX = -marginPx
                                                val minY = -marginPx
                                                val maxX = (canvasBounds.width - marginPx)
                                                val maxY = (canvasBounds.height - marginPx)
                                                itemOffset = Offset(
                                                    x = newOffset.x.coerceIn(minX, maxX),
                                                    y = newOffset.y.coerceIn(minY, maxY)
                                                )

                                                // Zoom (uniform scale keeps aspect ratio)
                                                val newScale = (itemScale * zoom).coerceIn(0.3f, 5f)
                                                itemScale = newScale
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = item.bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = itemScale
                                                scaleY = itemScale
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
                                    placed += PlacedImage(
                                        bitmap = bmp,
                                        offset = Offset(localX, localY)
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