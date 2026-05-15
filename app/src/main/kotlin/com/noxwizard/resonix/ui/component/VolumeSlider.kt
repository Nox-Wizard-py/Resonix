package com.noxwizard.resonix.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import com.noxwizard.resonix.constants.SliderEmojiKey
import com.noxwizard.resonix.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSlider(
    progressProvider: () -> Float,
    onProgressChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
    trackHeight: Dp = 10.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val haptic = LocalHapticFeedback.current

    val active = isPressed || isDragged

    // Subtle haptic when starting interaction
    LaunchedEffect(active) {
        if (active) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth
        val dotSpacingDp = 8.dp
        val dotsCount = (widthDp / dotSpacingDp).toInt()
        
        val currentValue = progressProvider()
        var lastHapticIndex by remember { mutableIntStateOf(-1) }

        LaunchedEffect(currentValue, active) {
            if (active) {
                val currentDotIndex = (currentValue * dotsCount).toInt()
                if (lastHapticIndex != -1 && lastHapticIndex != currentDotIndex) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastHapticIndex = currentDotIndex
            } else {
                lastHapticIndex = -1
            }
        }

        val (sliderEmoji) = rememberPreference(SliderEmojiKey, defaultValue = "")
        val hasEmoji = sliderEmoji.isNotEmpty()

        Slider(
            value = currentValue,
            onValueChange = onProgressChange,
            valueRange = 0f..1f,
            interactionSource = interactionSource,
            thumb = {
                val scale by animateFloatAsState(
                    targetValue = if (active) 1.25f else 1f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing), label = "thumbScale"
                )

                Box(
                    modifier = Modifier.scale(scale),
                    contentAlignment = Alignment.Center
                ) {
                    if (sliderEmoji.isNotEmpty()) {
                        Text(
                            text = sliderEmoji,
                            fontSize = 20.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(activeColor)
                        )
                    }
                }
            },
            track = { sliderState ->
                VolumeSliderTrack(
                    sliderState = sliderState,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    trackHeight = trackHeight,
                    hasEmoji = hasEmoji
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    hasEmoji: Boolean = false
) {
    val fraction = sliderState.value
    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        val trackWidth = size.width
        val trackHeightPx = size.height
        val cornerRadius = CornerRadius(trackHeightPx / 2, trackHeightPx / 2)
        val activeWidth = trackWidth * fraction
        
        val gapWidth = if (hasEmoji) 4.dp.toPx() else 0f
        val emojiHalfWidth = if (hasEmoji) 10.dp.toPx() else 0f
        val gapTotalHalf = emojiHalfWidth + gapWidth
        
        val activeTrackEnd = (activeWidth - gapTotalHalf).coerceAtLeast(0f)
        val inactiveTrackStart = (activeWidth + gapTotalHalf).coerceAtMost(trackWidth)

        // 1. Draw inactive track capsule starting AFTER the gap
        if (inactiveTrackStart < trackWidth) {
            drawRoundRect(
                color = inactiveColor.copy(alpha = 0.15f),
                topLeft = Offset(inactiveTrackStart, 0f),
                size = Size(trackWidth - inactiveTrackStart, trackHeightPx),
                cornerRadius = cornerRadius
            )
        }
        
        // 2. Draw dotted inactive track, clipped so it never appears inside the active region or gap
        val dotRadius = 1.5.dp.toPx()
        val spacing = 8.dp.toPx()
        val dotsCount = (trackWidth / spacing).toInt()
        
        clipRect(left = inactiveTrackStart, top = 0f, right = trackWidth, bottom = trackHeightPx) {
            for (i in 0..dotsCount) {
                val cx = i * spacing
                drawCircle(
                    color = inactiveColor,
                    radius = dotRadius,
                    center = Offset(cx, trackHeightPx / 2)
                )
            }
        }
        
        // 3. Draw solid active track ending BEFORE the gap
        if (activeTrackEnd > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(0f, 0f),
                size = Size(activeTrackEnd.coerceAtLeast(trackHeightPx), trackHeightPx),
                cornerRadius = cornerRadius
            )
        }
    }
}
