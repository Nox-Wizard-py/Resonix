package com.noxwizard.resonix.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Samsung One UI 6 style waveform slider.
 * Final behaviors: Thick base track, wave only on played portion,
 * dynamic amplitude collapse when paused/dragging, and optimized allocations.
 */
@Composable
fun WaveformSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f),
    thumbColor: Color = activeColor, // Kept for API compatibility
    trackHeight: Dp = 60.dp,
) {
    val range = valueRange.endInclusive - valueRange.start
    val fraction = if (range == 0f) 0f
    else ((value - valueRange.start) / range).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val currentFraction = if (isDragging) dragFraction else fraction

    // Phase animation for water flow
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val phase by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    // Amplitude multiplier explicitly driving 0 state when paused or dragging
    val targetMultiplier = if (isPlaying && !isDragging) 1f else 0f
    val animatedMultiplier by animateFloatAsState(
        targetValue = targetMultiplier,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing), // Smooth collapse
        label = "amplitudeAnim"
    )

    // Reusable Paths to avoid object allocations in onDraw
    val maskPath = remember { Path() }
    val rectPath = remember { Path() }
    val pillPath = remember { Path() }
    val mainWavePath = remember { Path() }
    val phantomWavePath = remember { Path() }

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
                            dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + dragFraction * range)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2f
            val splitX = currentFraction * w

            val baseH = 9.dp.toPx() // Base thick track height 8-10dp
            val baseCornerRadius = CornerRadius(baseH / 2f, baseH / 2f)
            val topOfBase = centerY - baseH / 2f
            
            // Dynamic breathing amplitude based on base track height
            val baseAmplitude = 16.dp.toPx() // Increased vertical width scale
            val breathingFactor = 0.85f + 0.15f * kotlin.math.sin(phase)
            val ampPx = baseAmplitude * breathingFactor * animatedMultiplier
            
            // --- 1. Draw Inactive Thick Base Track ---
            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, topOfBase),
                size = Size(w, baseH),
                cornerRadius = baseCornerRadius
            )

            // --- 2. Draw Active Base Track & Wave (Merged via Mask) ---
            if (splitX > 0f) {
                // Prepare the seamless UI Mask: Combines the precise pill shape + the open top ceiling for waves
                // Note: The ceiling mask MUST start from x = (baseH / 2) to protect the left-most corner radius rounding visually
                rectPath.reset()
                rectPath.addRect(androidx.compose.ui.geometry.Rect(baseH / 2f, -1000f, w, centerY))

                pillPath.reset()
                pillPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 0f, 
                        top = topOfBase, 
                        right = w, 
                        bottom = topOfBase + baseH,
                        cornerRadius = baseCornerRadius
                    )
                )

                maskPath.reset()
                maskPath.op(rectPath, pillPath, androidx.compose.ui.graphics.PathOperation.Union)

                // Only calculate wave arcs if mathematically visible
                if (ampPx > 0.1f) {
                    val frequency = 6f * PI.toFloat() // exactly 3 sine waves across the screen
                    val step = 4f // Optimized pixel step gap

                    // A. Calculate Phantom Wave (Background Liquid Depth)
                    phantomWavePath.reset()
                    phantomWavePath.moveTo(0f, topOfBase)
                    var px = 0f
                    val phantomPhase = phase - 1.2f // Phase shift for trailing effect
                    val phantomAmp = ampPx * 0.7f // Slightly lower height variance
                    
                    while (px <= w + step) {
                        val currentX = px.coerceAtMost(w)
                        // Symmetric Smoothstep: Eases up from origin, and eases down flat before hitting the knob (splitX limit)
                        val originEase = smoothstep(0f, w * 0.15f, currentX)
                        val knobEase = 1f - smoothstep(splitX - (w * 0.15f), splitX, currentX)
                        val progressFactor = originEase * knobEase
                        
                        val normalizedSin = kotlin.math.sin((currentX / w * frequency) + phantomPhase) * 0.5f + 0.5f
                        val waveHeight = normalizedSin * phantomAmp * progressFactor
                        phantomWavePath.lineTo(currentX, topOfBase - waveHeight)
                        if (currentX == w) break
                        px += step
                    }
                    phantomWavePath.lineTo(w, topOfBase + baseH + 10f) // Drop deep to guarantee base saturation
                    phantomWavePath.lineTo(0f, topOfBase + baseH + 10f)
                    phantomWavePath.close()

                    // B. Calculate Main Wave (Foreground Body)
                    mainWavePath.reset()
                    mainWavePath.moveTo(0f, topOfBase)
                    var mx = 0f
                    
                    while (mx <= w + step) {
                        val currentX = mx.coerceAtMost(w)
                        val originEase = smoothstep(0f, w * 0.15f, currentX)
                        val knobEase = 1f - smoothstep(splitX - (w * 0.15f), splitX, currentX)
                        val progressFactor = originEase * knobEase
                        
                        val normalizedSin = kotlin.math.sin((currentX / w * frequency) + phase) * 0.5f + 0.5f
                        val waveHeight = normalizedSin * ampPx * progressFactor
                        mainWavePath.lineTo(currentX, topOfBase - waveHeight)
                        if (currentX == w) break
                        mx += step
                    }
                    mainWavePath.lineTo(w, topOfBase + baseH + 10f)
                    mainWavePath.lineTo(0f, topOfBase + baseH + 10f)
                    mainWavePath.close()
                } else {
                    // C. Fallback Flat Paths if completely paused/collapsed
                    phantomWavePath.reset()
                    phantomWavePath.addRect(androidx.compose.ui.geometry.Rect(0f, topOfBase, w, topOfBase + baseH + 10f))
                    
                    mainWavePath.reset()
                    mainWavePath.addRect(androidx.compose.ui.geometry.Rect(0f, topOfBase, w, topOfBase + baseH + 10f))
                }

                val activeBrush = Brush.horizontalGradient(
                    colors = listOf(
                        activeColor.copy(alpha = 0.7f),
                        activeColor
                    ),
                    startX = 0f,
                    endX = splitX.coerceAtLeast(1f) // Ensure valid gradient range
                )

                // Draw the waves constrained perfectly by the hardware Pill Mask clipping
                clipPath(maskPath) {
                    clipRect(left = 0f, right = splitX) {
                        // Background phantom layer for authentic One UI 6 depth
                        drawPath(
                            path = phantomWavePath,
                            color = activeColor.copy(alpha =  0.35f),
                            style = Fill
                        )

                        // Foreground primary layer
                        drawPath(
                            path = mainWavePath,
                            brush = activeBrush,
                            style = Fill
                        )
                    }
                }
            }

            // --- 3. Circular Thumb Indicator ---
            val thumbOuterRadius = 11.dp.toPx()
            val thumbInnerRadius = 8.dp.toPx()
            
            // Knob stays horizontally tracking perfectly along the CENTER baseline
            val thumbY = centerY 

            // Outer circle (white)
            drawCircle(
                color = Color.White,
                radius = thumbOuterRadius,
                center = Offset(splitX, thumbY)
            )

            // Inner circle (theme color)
            drawCircle(
                color = activeColor,
                radius = thumbInnerRadius,
                center = Offset(splitX, thumbY)
            )
        }
    }
}

// Math interpolator to ensure continuous path bridging with base coordinates
private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
