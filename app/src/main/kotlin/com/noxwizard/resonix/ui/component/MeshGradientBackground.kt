package com.noxwizard.resonix.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier
) {
    // Read theme colors outside of draw lambda (composition-time read, not draw-time)
    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val color3 = MaterialTheme.colorScheme.tertiary
    val color4 = MaterialTheme.colorScheme.primaryContainer
    val color5 = MaterialTheme.colorScheme.secondaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.drawWithCache {
            val width = size.width
            val height = size.height

            // All brushes are cached and only recomputed when size changes
            val brush1 = Brush.radialGradient(
                colors = listOf(
                    color1.copy(alpha = 0.25f),
                    color1.copy(alpha = 0.15f),
                    color1.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(width * 0.15f, height * 0.1f),
                radius = width * 0.55f
            )

            val brush2 = Brush.radialGradient(
                colors = listOf(
                    color2.copy(alpha = 0.22f),
                    color2.copy(alpha = 0.12f),
                    color2.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(width * 0.85f, height * 0.2f),
                radius = width * 0.65f
            )

            val brush3 = Brush.radialGradient(
                colors = listOf(
                    color3.copy(alpha = 0.2f),
                    color3.copy(alpha = 0.1f),
                    color3.copy(alpha = 0.03f),
                    Color.Transparent
                ),
                center = Offset(width * 0.3f, height * 0.45f),
                radius = width * 0.6f
            )

            val brush4 = Brush.radialGradient(
                colors = listOf(
                    color4.copy(alpha = 0.18f),
                    color4.copy(alpha = 0.09f),
                    color4.copy(alpha = 0.02f),
                    Color.Transparent
                ),
                center = Offset(width * 0.7f, height * 0.5f),
                radius = width * 0.7f
            )

            val brush5 = Brush.radialGradient(
                colors = listOf(
                    color5.copy(alpha = 0.15f),
                    color5.copy(alpha = 0.07f),
                    color5.copy(alpha = 0.02f),
                    Color.Transparent
                ),
                center = Offset(width * 0.5f, height * 0.75f),
                radius = width * 0.8f
            )

            val overlayBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Transparent,
                    surfaceColor.copy(alpha = 0.3f),
                    surfaceColor.copy(alpha = 0.7f),
                    surfaceColor
                ),
                startY = height * 0.4f,
                endY = height
            )

            onDrawBehind {
                drawRect(brush = brush1)
                drawRect(brush = brush2)
                drawRect(brush = brush3)
                drawRect(brush = brush4)
                drawRect(brush = brush5)
                drawRect(brush = overlayBrush)
            }
        }
    )
}
