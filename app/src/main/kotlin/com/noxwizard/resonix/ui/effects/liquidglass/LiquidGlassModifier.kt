package com.noxwizard.resonix.ui.effects.liquidglass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection

fun Modifier.liquidGlass(
    backdropLayer: GraphicsLayer?,
    shape: CornerBasedShape,
    blurRadius: Float = 0f,
    vibrancy: Boolean = false,
    refractionHeight: Float = 0f,
    refractionAmount: Float = 0f,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return this
    }

    // Incremental Implementation Stages
    // Stage 1: Blur only
    // Stage 2: Blur + Vibrancy
    // Stage 3: Blur + Vibrancy + Highlights
    // Stage 4: Full Lens Refraction

    return composed {
        var localPosition by remember { mutableStateOf(Offset.Zero) }
        
        val shader by remember(refractionHeight, refractionAmount) {
            mutableStateOf(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && refractionHeight > 0f && refractionAmount > 0f) {
                    android.graphics.RuntimeShader(RoundedRectRefractionShaderString)
                } else null
            )
        }

        this
            .onGloballyPositioned { coordinates ->
                localPosition = coordinates.positionInWindow()
            }
            .graphicsLayer {
                this.shape = shape
                this.clip = true
                
                var currentEffect: android.graphics.RenderEffect? = null
                
                // Stage 1: Blur
                if (blurRadius > 0f) {
                    currentEffect = android.graphics.RenderEffect.createBlurEffect(
                        blurRadius, blurRadius,
                        android.graphics.Shader.TileMode.DECAL
                    )
                }
                
                // Stage 2: Vibrancy
                if (vibrancy) {
                    val vibrantFilter = colorControlsColorFilter(saturation = 1.5f)
                    val colorEffect = android.graphics.RenderEffect.createColorFilterEffect(vibrantFilter)
                    currentEffect = if (currentEffect != null) {
                        android.graphics.RenderEffect.createChainEffect(colorEffect, currentEffect)
                    } else {
                        colorEffect
                    }
                }
                
                // Stage 4: Lens Refraction
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && backdropLayer != null) {
                    shader?.setFloatUniform("size", size.width, size.height)
                    shader?.setFloatUniform("offset", localPosition.x, localPosition.y)
                    shader?.setFloatUniform("cornerRadii", 32.dp.toPx(), 32.dp.toPx(), 32.dp.toPx(), 32.dp.toPx()) // Hardcoded for MiniPlayer
                    shader?.setFloatUniform("refractionHeight", refractionHeight)
                    shader?.setFloatUniform("refractionAmount", refractionAmount)
                    shader?.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
                    
                    val refractionEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader!!, "content")
                    currentEffect = if (currentEffect != null) {
                        android.graphics.RenderEffect.createChainEffect(refractionEffect, currentEffect)
                    } else {
                        refractionEffect
                    }
                }
                
                this.renderEffect = currentEffect?.asComposeRenderEffect()
            }
            .drawBehind {
                if (backdropLayer != null) {
                    translate(-localPosition.x, -localPosition.y) {
                        drawLayer(backdropLayer)
                    }
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
