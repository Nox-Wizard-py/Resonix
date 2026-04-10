package com.noxwizard.resonix.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Random
import kotlin.math.abs

/**
 * A waveform slider that draws deterministic, randomized vertical bars.
 * 
 * @param seed A unique identifier (e.g. song ID hash) to seed the random heights.
 *             This guarantees the vertical bar pattern is consistent for the same song.
 */
@Composable
fun VerticalWaveformSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    seed: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
    trackHeight: Dp = 60.dp,
    barWidth: Dp = 3.dp,
    barGap: Dp = 2.dp,
    thumbWidth: Dp = 4.dp,
    thumbHeight: Dp = trackHeight,
) {
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range == 0f) 0f
    else ((value - valueRange.start) / range).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val currentFraction = if (isDragging) dragFraction else fraction

    // Use a derived state to memoize the random heights so they don't rebuild every frame
    val randomHeights: FloatArray = remember(seed) {
        val random = Random(seed.toLong())
        // Generate up to exactly 1000 bars. (Width 5dp total = ~5000px, which fits even large tablets).
        FloatArray(1000) {
            // Generates a multiplier between 0.1f and 1.0f
            0.1f + random.nextFloat() * 0.9f
        }
    }

    // TWEAK: Continuous breathing animation
    val infiniteTransition = rememberInfiniteTransition()
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(modifier = modifier.fillMaxWidth().height(trackHeight)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + f * range)
                        onValueChangeFinished?.invoke()
                    }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + dragFraction * range)
                        },
                        onDragEnd = {
                            isDragging = false
                            onValueChangeFinished?.invoke()
                        },
                        onDragCancel = {
                            isDragging = false
                            onValueChangeFinished?.invoke()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            // Update drag fraction
                            dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + dragFraction * range)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            
            val barWidthPx = barWidth.toPx()
            val barGapPx = barGap.toPx()
            val thumbWidthPx = thumbWidth.toPx()
            val thumbHeightPx = thumbHeight.toPx()
            val totalBarWidth = barWidthPx + barGapPx
            
            // Limit drawing beyond max width
            val numberOfBars = (w / totalBarWidth).toInt()
            
            // TWEAK: Decrease the length of the vertical bars slightly.
            val maxBarHeight = h * 0.6f 
            
            val splitX = currentFraction * w
            
            // For the breathing animation, define an active window radius around the thumb
            val breathingRadiusPx = w * 0.15f // 15% of track width
            
            var currentX = 0f
            
            for (i in 0 until numberOfBars) {
                // Determine base height
                val heightMultiplier = randomHeights[i % randomHeights.size]
                var barHeightPx = maxBarHeight * heightMultiplier
                
                // TWEAK: Subtle breathing animation near the thumb
                val centerX = currentX + barWidthPx / 2f
                val distanceToThumb = abs(centerX - splitX)
                
                if (distanceToThumb < breathingRadiusPx) {
                    // Gaussian-like bump near the thumb
                    val baseBumpFactor = 1f - (distanceToThumb / breathingRadiusPx)
                    // The bump pulses continuously between 0.0x and 1.0x of the full potential bump based on the breathing phase
                    val pulsedBump = baseBumpFactor * breathingPhase
                    // Increase height up to 40% near the thumb
                    barHeightPx += (maxBarHeight * 0.4f * pulsedBump)
                }
                
                val color = if (centerX <= splitX) {
                    activeColor
                } else {
                    inactiveColor
                }
                
                // Draw rounded vertical bar
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x = currentX, y = centerY - barHeightPx / 2f),
                    size = Size(width = barWidthPx, height = barHeightPx),
                    cornerRadius = CornerRadius(barWidthPx / 2f, barWidthPx / 2f)
                )
                
                currentX += totalBarWidth
            }
            
            // TWEAK: Draw the vertical seek handle (thumb)
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(x = splitX - thumbWidthPx / 2f, y = centerY - thumbHeightPx / 2f),
                size = Size(width = thumbWidthPx, height = thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }
    }
}
