package com.noxwizard.resonix.ui.effects.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

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
        layer.record {
            this@drawWithContent.drawContent()
        }
        drawContent()
    }
}
