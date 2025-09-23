package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.example.imagecarousel.R
import com.example.imagecarousel.domain.Image
import kotlin.math.roundToInt

@Composable
fun Carousel(
    images: List<Image>,
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
                .height(height = dimensionResource(R.dimen.carousel_height).value.dp)
                .padding(horizontal = dimensionResource(R.dimen.spacer_height))
        ) {
            items(images) { image ->
                var itemOriginInWindow by remember { mutableStateOf(Offset.Zero) }
                val thumbHeight = dimensionResource(R.dimen.carousel_thumb_height).value.dp
                val aspect = (image.bitmap?.width ?: 1).toFloat() / (image.bitmap?.height ?: 1).toFloat()
                Box(
                    modifier = Modifier
                        .padding(end = dimensionResource(R.dimen.spacer_height))
                        .height(thumbHeight)
                        .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                        .background(Color.White)
                        .onGloballyPositioned { coords ->
                            itemOriginInWindow = coords.positionInWindow()
                        }
                        .pointerInput(image) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { start ->
                                    lastPointerInWindow = itemOriginInWindow + start
                                    onStartDrag(image.bitmap!!, lastPointerInWindow)
                                },
                                onDrag = { change, _ ->
                                    lastPointerInWindow = itemOriginInWindow + change.position
                                    onDrag(image.bitmap!!, lastPointerInWindow)
                                },
                                onDragEnd = { onEndDrag() },
                                onDragCancel = { onEndDrag() }
                            )
                        }
                ) {
                    Image(
                        bitmap = image.bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

fun IntRect.contains(point: Offset): Boolean {
    val x = point.x.roundToInt()
    val y = point.y.roundToInt()
    return x in left..right && y in top..bottom
}

fun androidx.compose.ui.unit.Dp.toPx(density: androidx.compose.ui.unit.Density): Float =
    with(density) { this@toPx.toPx() }