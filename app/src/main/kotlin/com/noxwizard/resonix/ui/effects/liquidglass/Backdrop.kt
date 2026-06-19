package com.noxwizard.resonix.ui.effects.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer

/**
 * Provides the background layer for liquid glass effects to capture and refract.
 */
val LocalBackdropGraphicsLayer: ProvidableCompositionLocal<GraphicsLayer?> = staticCompositionLocalOf { null }

/**
 * Wrap the root of your application or the content you want to blur with this.
 * It captures the content into a GraphicsLayer so that frosted glass elements
 * drawn on top of it can sample the background.
 */
@Composable
fun Modifier.provideBackdropLayer(layer: GraphicsLayer): Modifier {
    return this.drawWithContent {
        // Force Offscreen compositing before recording.
        // This rasterizes all child RenderNodes (including hardware-accelerated
        // text glyphs) into a flat bitmap. Without this, text nodes bypass
        // the RenderEffect blur pipeline when the layer is sampled by glass elements.
        layer.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        layer.record {
            this@drawWithContent.drawContent()
        }
        drawContent()
    }
}
