package com.noxwizard.resonix.ui.component

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

// Aurora fallback palette — deep-space ambient tones, AMOLED friendly
private val AuroraFallback = listOf(
    Color(0xFF3A1A7B), // deep violet
    Color(0xFF0F3D7B), // deep navy
    Color(0xFF3A1A58), // dark magenta
    Color(0xFF0E4560), // dark teal
    Color(0xFF1A3A6B), // midnight blue
    Color(0xFF2A1A5A), // indigo
)

/**
 * Reusable cinematic aurora background composable.
 *
 * Architecture mirrors ArchiveTune's GLOW_ANIMATED style:
 * - Single [progress] float (0→1, 12s linear loop) drives all blob positions via sin() oscillation.
 *   This guarantees seamless looping with zero stutter at reversal points.
 * - [rotatedColorAt] cycles the palette over time so colors organically shift.
 * - [drawWithCache] caches all [Brush] objects — zero GC pressure per frame.
 * - 6 blobs with 3-stop radial gradients for rich cinematic depth.
 * - [Modifier.blur] on API 31+ for the soft atmospheric glow.
 */
@Composable
fun AuroraAnimatedBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val palette = when {
        colors.size >= 6 -> colors.take(6).mapIndexed { i, c -> c.soften(SOFTEN_LEVELS[i]) }
        colors.size >= 3 -> {
            val base = colors.map { it.soften(0.80f) }
            // Synthesise extra colors via hue-offset copies when palette is small
            base + base.take(6 - base.size).map { it.soften(0.65f) }
        }
        else -> AuroraFallback
    }

    val transition = rememberInfiniteTransition(label = "AuroraAnimation")

    // Single progress drives ALL animations — ensures seamless loop
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "auroraProgress",
    )

    // Rotate color indices over time so colors slowly cycle through palette
    fun rotatedColorAt(index: Int): Color {
        val size = palette.size
        val idx = index.toFloat() + progress * size
        val a = floor(idx).toInt() % size
        val b = (a + 1) % size
        val frac = idx - floor(idx)
        return lerp(palette.getOrElse(a) { Color.DarkGray }, palette.getOrElse(b) { Color.DarkGray }, frac)
    }

    // Sinusoidal oscillation — speed must be integer for seamless wrap at progress=1→0
    fun oscillate(min: Float, max: Float, phase: Float, speed: Float = 1f): Float {
        val v = sin(2f * PI.toFloat() * (progress * speed + phase)).toFloat()
        return min + (max - min) * ((v + 1f) * 0.5f)
    }

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        modifier.blur(radius = 48.dp)
    } else {
        modifier
    }

    Box(
        modifier = blurModifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val baseColor = Color(0xFF050505) // near-black AMOLED base

                // ── Compute colors (rotated) ──
                val c1 = rotatedColorAt(0)
                val c2 = rotatedColorAt(1)
                val c3 = rotatedColorAt(2)
                val c4 = rotatedColorAt(3)
                val c5 = rotatedColorAt(4)
                val c6 = rotatedColorAt(5)

                // ── Compute positions via oscillate (distinct phases → organic out-of-sync motion) ──
                val o1x = oscillate(0.0f, 1.0f, 0.00f)
                val o1y = oscillate(0.0f, 0.5f, 0.07f)
                val r1  = oscillate(0.8f, 1.6f, 0.12f)

                val o2x = oscillate(1.0f, 0.0f, 0.20f)
                val o2y = oscillate(0.5f, 1.0f, 0.25f)
                val r2  = oscillate(0.7f, 1.5f, 0.18f)

                val o3x = oscillate(0.2f, 0.8f, 0.33f)
                val o3y = oscillate(0.8f, 0.2f, 0.36f)
                val r3  = oscillate(0.6f, 1.4f, 0.29f)

                val o4x = oscillate(0.3f, 0.7f, 0.44f)
                val o4y = oscillate(0.2f, 0.8f, 0.41f)
                val r4  = oscillate(0.9f, 1.7f, 0.47f)

                val o5x = oscillate(0.4f, 0.6f, 0.55f)
                val o5y = oscillate(0.0f, 1.0f, 0.51f)
                val r5  = oscillate(0.7f, 1.5f, 0.58f)

                val o6x = oscillate(0.0f, 1.0f, 0.66f)
                val o6y = oscillate(0.5f, 0.7f, 0.62f)
                val r6  = oscillate(0.8f, 1.8f, 0.69f)

                // ── Build brushes once per cache invalidation (not per frame) ──
                val brush1 = Brush.radialGradient(
                    colors = listOf(c1.copy(alpha = 0.90f), c1.copy(alpha = 0.55f), Color.Transparent),
                    center = Offset(w * o1x, h * o1y), radius = w * r1,
                )
                val brush2 = Brush.radialGradient(
                    colors = listOf(c2.copy(alpha = 0.85f), c2.copy(alpha = 0.50f), Color.Transparent),
                    center = Offset(w * o2x, h * o2y), radius = w * r2,
                )
                val brush3 = Brush.radialGradient(
                    colors = listOf(c3.copy(alpha = 0.80f), c3.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(w * o3x, h * o3y), radius = w * r3,
                )
                val brush4 = Brush.radialGradient(
                    colors = listOf(c4.copy(alpha = 0.75f), c4.copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(w * o4x, h * o4y), radius = w * r4,
                )
                val brush5 = Brush.radialGradient(
                    colors = listOf(c5.copy(alpha = 0.70f), c5.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(w * o5x, h * o5y), radius = w * r5,
                )
                val brush6 = Brush.radialGradient(
                    colors = listOf(c6.copy(alpha = 0.65f), c6.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * o6x, h * o6y), radius = w * r6,
                )

                onDrawBehind {
                    drawRect(color = baseColor)
                    drawRect(brush = brush1)
                    drawRect(brush = brush2)
                    drawRect(brush = brush3)
                    drawRect(brush = brush4)
                    drawRect(brush = brush5)
                    drawRect(brush = brush6)
                }
            }
    )
}

/** Per-blob alpha soften levels — higher index = slightly dimmer for natural depth */
private val SOFTEN_LEVELS = floatArrayOf(0.90f, 0.85f, 0.80f, 0.75f, 0.70f, 0.65f)

/** Copies this color with its alpha adjusted — used to soften palette colors */
private fun Color.soften(alpha: Float): Color = this.copy(alpha = alpha.coerceIn(0f, 1f))
