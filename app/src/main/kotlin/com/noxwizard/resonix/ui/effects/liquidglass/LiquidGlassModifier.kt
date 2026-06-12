package com.noxwizard.resonix.ui.effects.liquidglass

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

fun Modifier.liquidGlass(
    backdropLayer: GraphicsLayer?,
    shape: CornerBasedShape, // Kept for compose Modifier.graphicsLayer bounds clipping
    luminanceAnimation: Float = 0.5f,
    interaction: GlassInteraction? = null
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }

    return composed {
        var localPosition by remember { mutableStateOf(Offset.Zero) }

        this
            .onGloballyPositioned { coordinates ->
                localPosition = coordinates.positionInWindow()
            }
            .then(
                if (interaction != null) {
                    Modifier.pointerInput(interaction) { interaction.detectPress(this) }
                } else Modifier
            )
            .graphicsLayer {
                this.shape = shape
                this.clip = true
                
                val press = interaction?.pressProgress ?: 0f
                val scale = androidx.compose.ui.util.lerp(1f, 1.04f, press)
                this.scaleX = scale
                this.scaleY = scale

                var currentEffect: android.graphics.RenderEffect? = null
                
                val l = (luminanceAnimation * 2f - 1f).let { kotlin.math.sign(it) * it * it }
                val blurRadius = (
                    if (l > 0f) {
                        androidx.compose.ui.util.lerp(8.dp.toPx(), 16.dp.toPx(), l)
                    } else {
                        androidx.compose.ui.util.lerp(8.dp.toPx(), 2.dp.toPx(), -l)
                    }
                ) + 2.dp.toPx() * press

                if (blurRadius > 0f) {
                    currentEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadius, blurRadius,
                        android.graphics.Shader.TileMode.DECAL
                    )
                }

                val colorFilter = colorControlsColorFilter(
                    brightness = 0.05f,
                    contrast = 1f,
                    saturation = 1.5f
                )
                val colorEffect = android.graphics.RenderEffect.createColorFilterEffect(colorFilter)
                currentEffect = if (currentEffect != null) {
                    android.graphics.RenderEffect.createChainEffect(colorEffect, currentEffect)
                } else colorEffect

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backdropLayer != null) {
                    val shader = android.graphics.RuntimeShader(RoundedRectRefractionShaderString)
                    
                    val refractionHeight = size.minDimension / 4f + 2.dp.toPx() * press
                    val refractionAmount = size.minDimension / 2f

                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setFloatUniform("offset", localPosition.x, localPosition.y)
                    
                    val cr = 32.dp.toPx() // Hardcoded corner radius fallback for now to match MiniPlayer
                    shader.setFloatUniform("cornerRadii", cr, cr, cr, cr) 

                    shader.setFloatUniform("refractionHeight", refractionHeight)
                    shader.setFloatUniform("refractionAmount", -refractionAmount)
                    shader.setFloatUniform("depthEffect", 0f)
                    
                    val refractionEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
                    currentEffect = if (currentEffect != null) {
                        android.graphics.RenderEffect.createChainEffect(refractionEffect, currentEffect)
                    } else refractionEffect
                }

                this.renderEffect = currentEffect?.asComposeRenderEffect()
            }
            .drawBehind {
                if (backdropLayer != null) {
                    translate(-localPosition.x, -localPosition.y) {
                        drawLayer(backdropLayer)
                    }
                }
                
                val darken = androidx.compose.ui.util.lerp(0.12f, 0.5f, ((luminanceAnimation - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = darken))

                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f * press),
                                androidx.compose.ui.graphics.Color.Transparent,
                            ),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
                    )
                }
            }
    }
}

private fun colorControlsColorFilter(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
): android.graphics.ColorFilter {
    val invSat = 1f - saturation
    val r = 0.213f * invSat
    val g = 0.715f * invSat
    val b = 0.072f * invSat

    val c = contrast
    val t = (0.5f - c * 0.5f + brightness) * 255f
    val s = saturation

    val cr = c * r
    val cg = c * g
    val cb = c * b
    val cs = c * s

    val floatArray = floatArrayOf(
        cr + cs, cg, cb, 0f, t,
        cr, cg + cs, cb, 0f, t,
        cr, cg, cb + cs, 0f, t,
        0f, 0f, 0f, 1f, 0f
    )
    val colorMatrix = android.graphics.ColorMatrix(floatArray)
    return android.graphics.ColorMatrixColorFilter(colorMatrix)
}
