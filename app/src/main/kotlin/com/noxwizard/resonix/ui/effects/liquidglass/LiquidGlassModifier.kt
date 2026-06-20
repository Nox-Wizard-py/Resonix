package com.noxwizard.resonix.ui.effects.liquidglass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow

val LocalLayerBackdrop: ProvidableCompositionLocal<LayerBackdrop?> = staticCompositionLocalOf { null }

fun Modifier.liquidGlass(
    backdropLayer: LayerBackdrop?,
    shape: CornerBasedShape,
    luminanceAnimation: Float = 0.5f,
    interaction: GlassInteraction? = null,
    isDarkTheme: Boolean = true
): Modifier = composed {
    val press = interaction?.pressProgress ?: 0f
    
    val l = (luminanceAnimation * 2f - 1f).let { kotlin.math.sign(it) * it * it }
    val blurRadius = (
        if (l > 0f) {
            androidx.compose.ui.util.lerp(8f, 16f, l)
        } else {
            androidx.compose.ui.util.lerp(8f, 2f, -l)
        }
    ) + 2f * press
    
    val highlightStyle = if (isDarkTheme) {
        HighlightStyle.Default(color = Color.White.copy(alpha = 0.5f))
    } else {
        HighlightStyle.Default(color = Color.White.copy(alpha = 0.8f))
    }

    val darken = androidx.compose.ui.util.lerp(0.12f, 0.5f, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
    
    this
        .then(
            if (interaction != null) {
                Modifier.pointerInput(interaction) { interaction.detectPress(this) }
            } else Modifier
        )
        .drawBackdrop(
            backdrop = backdropLayer ?: com.kyant.backdrop.backdrops.emptyBackdrop(),
            shape = { shape },
            effects = {
                if (blurRadius > 0f) blur(blurRadius.dp.toPx())
                colorControls(brightness = 0.05f, contrast = 1f, saturation = 1.5f)
                lens(refractionHeight = size.minDimension / 4f + 2.dp.toPx() * press, refractionAmount = size.minDimension / 2f)
            },
            highlight = { Highlight(style = highlightStyle) },
            shadow = { null },
            innerShadow = { null },
            onDrawSurface = {
                drawRect(Color.Black.copy(alpha = darken))
                if (press > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f * press),
                                Color.Transparent,
                            ),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            }
        )
}

