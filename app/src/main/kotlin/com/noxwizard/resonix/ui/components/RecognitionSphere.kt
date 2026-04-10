package com.noxwizard.resonix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.*
import kotlinx.coroutines.isActive

// ─────────────────────────────────────────────────────────────
//  State enum
// ─────────────────────────────────────────────────────────────
enum class SphereState { IDLE, ACTIVE, RECOGNIZING }

/**
 * @param ox/oy/oz  original unit-sphere position
 * @param phaseA    unique random phase for orbital drift (latitude drift)
 * @param phaseB    unique random phase for longitude drift
 * @param phaseW    unique random phase for shimmer wave offset
 */
class ParticleNode(
    val ox: Float, val oy: Float, val oz: Float,
    val phaseA: Float,
    val phaseB: Float,
    val phaseW: Float
) {
    var sx: Float = 0f
    var sy: Float = 0f
    var sz: Float = 0f
    var rOff: Float = 1f
    // animated position on disk (unit sphere) blended in for ACTIVE
    var ax: Float = ox
    var ay: Float = oy
    var az: Float = oz
}

// ─────────────────────────────────────────────────────────────
//  Component
// ─────────────────────────────────────────────────────────────
@Composable
fun RecognitionSphere(
    state: SphereState,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = when (state) {
            SphereState.IDLE -> 1.0f
            SphereState.ACTIVE -> 1.05f
            SphereState.RECOGNIZING -> 1.15f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 50f),
        label = "scale"
    )

    val targetSpeed = when (state) {
        SphereState.IDLE -> 0.4f
        SphereState.ACTIVE -> 1.5f
        SphereState.RECOGNIZING -> 3.0f
    }

    val speed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(1500, easing = LinearOutSlowInEasing),
        label = "speed"
    )

    // Blend weight: 0 = idle/recognizing logic, 1 = full listening particle animation
    val listeningBlend by animateFloatAsState(
        targetValue = if (state == SphereState.ACTIVE) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "listeningBlend"
    )

    val pulseStrength by animateFloatAsState(
        targetValue = if (state == SphereState.ACTIVE) 1f else 0f,
        animationSpec = tween(800),
        label = "pulse"
    )

    val recognizingPulse by animateFloatAsState(
        targetValue = if (state == SphereState.RECOGNIZING) 1f else 0f,
        animationSpec = tween(1000),
        label = "recognizingPulse"
    )

    val animatedFront by animateColorAsState(
        targetValue = when (state) {
            SphereState.IDLE -> Color(0xFF00E5FF)
            SphereState.ACTIVE -> Color(0xFF66FFFF)
            SphereState.RECOGNIZING -> Color(0xFF00E5FF)
        },
        animationSpec = tween(1000), label = "frontColor"
    )

    val animatedBack by animateColorAsState(
        targetValue = when (state) {
            SphereState.IDLE -> Color(0xFF0022AA)
            SphereState.ACTIVE -> Color(0xFF0055EE)
            SphereState.RECOGNIZING -> Color(0xFF3300AA)
        },
        animationSpec = tween(1000), label = "backColor"
    )

    val animatedCore by animateColorAsState(
        targetValue = when (state) {
            SphereState.IDLE -> Color(0xFF0066FF)
            SphereState.ACTIVE -> Color(0xFF00AAFF)
            SphereState.RECOGNIZING -> Color(0xFFFF00FF)
        },
        animationSpec = tween(1000), label = "coreColor"
    )

    // Highlight accent for ACTIVE shimmer (bright cyan-white)
    val shimmerAccent = Color(0xFFAAFFFF)

    var time by remember { mutableFloatStateOf(0f) }
    var rotationY by remember { mutableFloatStateOf(0f) }
    var rotationX by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = withFrameNanos { it }
        while (isActive) {
            val currentTime = withFrameNanos { it }
            val dt = (currentTime - lastTime) / 1e9f
            lastTime = currentTime

            time += dt
            rotationY += dt * speed
            rotationX += dt * speed * 0.4f
        }
    }

    val nodesCount = 500
    val particles = remember {
        val phi = Math.PI * (3.0 - sqrt(5.0))
        Array(nodesCount) { i ->
            val y = 1f - (i / (nodesCount - 1f)) * 2f
            val rAtY = sqrt(max(0.0, 1.0 - y * y))
            val theta = phi * i
            // Deterministic pseudo-random phases from index
            val pA = ((i * 1.61803f) % 1f) * (2f * PI.toFloat())
            val pB = ((i * 2.71828f) % 1f) * (2f * PI.toFloat())
            val pW = ((i * 3.14159f) % 1f) * (2f * PI.toFloat())
            ParticleNode(
                ox = (cos(theta) * rAtY).toFloat(),
                oy = y,
                oz = (sin(theta) * rAtY).toFloat(),
                phaseA = pA,
                phaseB = pB,
                phaseW = pW
            )
        }
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val baseRadius = min(w, h) * 0.38f * scale

            val minS = size.minDimension * 0.003f
            val maxS = size.minDimension * 0.015f

            // Global sphere rotation matrix
            val cosY = cos(rotationY)
            val sinY = sin(rotationY)
            val cosX = cos(rotationX)
            val sinX = sin(rotationX)

            // Global breath (only for IDLE carrying into ACTIVE naturally)
            val activeBreath = sin(time * 6f) * 0.02f * pulseStrength
            val recognizingBreath = sin(time * 8f) * 0.04f * recognizingPulse
            val totalBreath = activeBreath + recognizingBreath

            // ── LISTENING state: per-particle animated position on sphere ──
            // Each particle drifts slightly along its great-circle, driven by
            // unique phase offsets and a shared wave-band clock.
            // Drift amplitude (angular, radians): small enough to look orbital, not teleport.
            val driftAmp = 0.18f * listeningBlend   // max ~10 degrees of drift

            // Traveling wave band: a sinusoidal brightness envelope sweeping in latitude (oy)
            // Wave periods: two overlapping waves for complexity
            val waveSpeed1 = 2.2f
            val waveSpeed2 = 3.7f
            val waveFreq1 = 4.0f  // cycles per sphere height
            val waveFreq2 = 6.5f

            for (i in particles.indices) {
                val p = particles[i]

                // ── Per-particle orbital micro-drift (ACTIVE only) ─────────
                // Drift the particle along its local latitude circle using
                // a unique phase, producing independent continuous motion.
                val driftLon = sin(time * 0.9f + p.phaseA) * driftAmp
                val driftLat = sin(time * 0.6f + p.phaseB) * driftAmp * 0.4f

                // Rotate original position by drift angles around Y (longitude) then X (latitude)
                val cosDL = cos(driftLon); val sinDL = sin(driftLon)
                val cosDX = cos(driftLat); val sinDX = sin(driftLat)

                // Apply drift rotation to original unit position
                val dx1 = p.ox * cosDL - p.oz * sinDL
                val dz1 = p.ox * sinDL + p.oz * cosDL
                val dy2 = p.oy * cosDX - dz1 * sinDX
                val dz2 = p.oy * sinDX + dz1 * cosDX
                val dx2 = dx1

                // Blend between original and drifted position
                val bx = lerp(p.ox, dx2, listeningBlend)
                val by_ = lerp(p.oy, dy2, listeningBlend)
                val bz = lerp(p.oz, dz2, listeningBlend)

                // ── Apply sphere rotation matrix to blended position ───────
                val x1 = bx * cosY - bz * sinY
                val z1 = bx * sinY + bz * cosY
                val y2 = by_ * cosX - z1 * sinX
                val z2 = by_ * sinX + z1 * cosX
                val x2 = x1

                // ── Radial offset (recognizing wave / breath) ─────────────
                var rOff = 1f + totalBreath
                if (recognizingPulse > 0f) {
                    val wave = sin(y2 * 10f - time * 15f)
                    rOff += wave * 0.1f * recognizingPulse
                }

                val currentR = baseRadius * rOff
                val persp = 2.5f / (3.5f - z2)

                p.sx = cx + x2 * persp * currentR
                p.sy = cy + y2 * persp * currentR
                p.sz = z2
                p.rOff = rOff

                // Store blended world-Y for wave shimmer (access in draw loop)
                // We abuse ax to store the blended world-y value for shimmer
                p.ax = by_   // latitude used for shimmer wave
            }

            particles.sortBy { it.sz }

            // ── Draw ──────────────────────────────────────────────────────

            // Outer bloom glow
            val bloomR = baseRadius * 2.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedCore.copy(alpha = 0.25f),
                        animatedCore.copy(alpha = 0.05f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = bloomR
                ),
                center = Offset(cx, cy),
                radius = bloomR
            )

            var coreDrawn = false

            for (i in particles.indices) {
                val p = particles[i]

                // Draw central glow core when we cross z=0 (front hemisphere starts)
                if (!coreDrawn && p.sz >= 0f) {
                    val coreR = baseRadius * 0.85f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animatedCore.copy(alpha = 0.9f),
                                animatedCore.copy(alpha = 0.4f),
                                animatedCore.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = coreR * 1.1f
                        ),
                        center = Offset(cx, cy),
                        radius = coreR * 1.1f
                    )
                    coreDrawn = true
                }

                val zDepth = ((p.sz + 1f) / 2f).coerceIn(0f, 1f)
                val baseNodeR = lerp(minS, maxS, zDepth)
                val baseAlpha = lerp(0.15f, 1.0f, zDepth)
                val baseC = colorLerp(animatedBack, animatedFront, zDepth)

                // ── LISTENING shimmer overlay ──────────────────────────────
                // Two overlapping traveling wave bands sweep across latitudes.
                // Each particle gets brightness pulsed by how much wave energy
                // passes through its stored world-latitude (p.ax).
                val shimmerBoost: Float
                val shimmerAlpha: Float
                val drawRadius: Float
                if (listeningBlend > 0f) {
                    val lat = p.ax  // stored world-Y (latitude, -1..1)
                    // Unique per-particle shimmer phase offsets the wave envelope individually
                    val w1 = sin(lat * waveFreq1 * PI.toFloat() - time * waveSpeed1 + p.phaseW)
                    val w2 = sin(lat * waveFreq2 * PI.toFloat() - time * waveSpeed2 + p.phaseW * 0.7f)
                    // Combine waves, normalize to 0..1
                    val waveCombined = ((w1 * 0.6f + w2 * 0.4f) + 1f) * 0.5f
                    shimmerBoost = waveCombined * listeningBlend

                    // Alpha increases where shimmer is high (brightness pulse)
                    shimmerAlpha = lerp(baseAlpha, minOf(baseAlpha * 2.2f, 1.0f), shimmerBoost * 0.8f)
                    // Node radius also slightly grows at shimmer peaks
                    drawRadius = lerp(baseNodeR, baseNodeR * 1.6f, shimmerBoost * 0.5f * listeningBlend)
                } else {
                    shimmerBoost = 0f
                    shimmerAlpha = baseAlpha
                    drawRadius = baseNodeR
                }

                // Color: blend base toward shimmer accent proportional to shimmer boost
                val colorWithShimmer = if (listeningBlend > 0f && shimmerBoost > 0.3f) {
                    colorLerp(baseC, shimmerAccent, (shimmerBoost - 0.3f) / 0.7f * listeningBlend)
                } else baseC

                // ── RECOGNIZING wave highlight (untouched) ─────────────────
                val divisor = 0.1f * recognizingPulse
                val peakHighlight = if (divisor > 0.001f) {
                    val wavePart = p.rOff - (1f + totalBreath)
                    (wavePart / divisor).coerceIn(0f, 1f) * recognizingPulse
                } else 0f

                val finalC = if (peakHighlight > 0f) {
                    colorLerp(colorWithShimmer, Color(0xFFFF00FF), peakHighlight)
                } else colorWithShimmer

                drawCircle(
                    color = finalC.copy(alpha = shimmerAlpha),
                    radius = drawRadius,
                    center = Offset(p.sx, p.sy)
                )
            }

            if (!coreDrawn) {
                val coreR = baseRadius * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedCore.copy(alpha = 0.9f),
                            animatedCore.copy(alpha = 0.4f),
                            animatedCore.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = coreR * 1.1f
                    ),
                    center = Offset(cx, cy),
                    radius = coreR * 1.1f
                )
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)
private fun colorLerp(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)
