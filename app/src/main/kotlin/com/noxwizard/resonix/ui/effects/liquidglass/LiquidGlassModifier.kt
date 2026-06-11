package com.noxwizard.resonix.ui.effects.liquidglass

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
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

    return this.graphicsLayer {
        this.shape = shape
        this.clip = true
        
        var currentEffect: RenderEffect? = null
        
        // Stage 1: Blur
        if (blurRadius > 0f) {
            currentEffect = RenderEffect.createBlurEffect(
                blurRadius, blurRadius,
                android.graphics.Shader.TileMode.DECAL
            )
        }
        
        // Stage 2: Vibrancy
        if (vibrancy) {
            val vibrantFilter = colorControlsColorFilter(saturation = 1.5f)
            val colorEffect = RenderEffect.createColorFilterEffect(vibrantFilter)
            currentEffect = if (currentEffect != null) {
                RenderEffect.createChainEffect(colorEffect, currentEffect)
            } else {
                colorEffect
            }
        }
        
        // Stage 4: Lens Refraction
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && refractionHeight > 0f && refractionAmount > 0f && backdropLayer != null) {
            // Need a RuntimeShaderEffect that uses backdropLayer as 'content'
            // This will be implemented using the shader strings.
        }
        
        this.renderEffect = currentEffect
    }
}

private fun colorControlsColorFilter(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f
): ColorFilter {
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

    val colorMatrix = ColorMatrix(
        floatArrayOf(
            cr + cs, cg, cb, 0f, t,
            cr, cg + cs, cb, 0f, t,
            cr, cg, cb + cs, 0f, t,
            0f, 0f, 0f, 1f, 0f
        )
    )
    return ColorMatrixColorFilter(colorMatrix)
}
