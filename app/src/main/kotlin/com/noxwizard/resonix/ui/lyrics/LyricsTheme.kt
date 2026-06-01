package com.noxwizard.resonix.ui.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.font.FontWeight
import com.noxwizard.resonix.constants.LyricsStyle

/**
 * Declarative visual specification for a lyrics theme.
 *
 * All style-specific logic lives here. The renderer reads this spec
 * and applies it uniformly — no scattered if/when on LyricsStyle.
 */
data class LyricsThemeSpec(
    // ── Opacity hierarchy ──────────────────────────────────────────────────
    /** Alpha of the currently active line (1.0 = fully opaque). */
    val activeLineAlpha: Float = 1.0f,
    /** Alpha of lines BEFORE the active line (previous lines). */
    val previousLineAlpha: Float = 0.5f,
    /** Alpha of lines AFTER the active line (future lines). */
    val futureLineAlpha: Float = 0.65f,
    /** Per-distance alpha falloff for future lines (index 0 = distance 1). */
    val futureAlphaFalloff: List<Float> = listOf(0.65f, 0.45f, 0.30f),

    // ── Blur ───────────────────────────────────────────────────────────────
    /** Apply Gaussian blur to previous lines. Spotifont signature. */
    val blurPreviousLines: Boolean = false,
    /** Blur radius in pixels for previous lines. */
    val blurRadiusPx: Float = 18f,
    /** Apply blur to future lines. False for Flow/Spotifont. */
    val blurFutureLines: Boolean = false,

    // ── Scale ──────────────────────────────────────────────────────────────
    /** Scale factor for the active line. 1.0 = no scale change. */
    val activeLineScale: Float = 1.0f,
    /** Scale factor per-distance falloff (index 0 = distance 1). */
    val inactiveScaleFalloff: List<Float> = listOf(0.97f, 0.94f, 0.92f),

    // ── Typography ─────────────────────────────────────────────────────────
    /** Font weight for the active line. */
    val activeFontWeight: FontWeight = FontWeight.Bold,
    /** Font weight for inactive lines. */
    val inactiveFontWeight: FontWeight = FontWeight.Bold,
    /** Line height multiplier (relative to fontSize). */
    val lineHeightMultiplier: Float = 1.3f,

    // ── Word sync ──────────────────────────────────────────────────────────
    /** Enable soft glow on the currently active word. */
    val glowActiveWord: Boolean = false,
    /** Glow radius in px when glowActiveWord = true. */
    val wordGlowRadiusPx: Float = 16f,
    /** Alpha of future (not-yet-sung) words within the active line. */
    val futureWordAlpha: Float = 0.35f,
    /** Alpha of past (already-sung) words within the active line. */
    val passedWordAlpha: Float = 1.0f,

    // ── Transitions ────────────────────────────────────────────────────────
    /** Duration (ms) for opacity/blur transitions on inactive lines. */
    val transitionDurationMs: Int = 500,
    /** Delay (ms) before blur/opacity transition starts. CSS: 0.3s delay. */
    val transitionDelayMs: Int = 300,
    /** Easing for blur/alpha transitions. */
    val transitionEasing: Easing = FastOutSlowInEasing,
)

// ─────────────────────────────────────────────────────────────────────────────
// Extension: maps each LyricsStyle to its canonical spec.
// Add new styles here when they're ready; renderer stays untouched.
// ─────────────────────────────────────────────────────────────────────────────

private val FlowEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

fun LyricsStyle.toThemeSpec(): LyricsThemeSpec = when (this) {

    LyricsStyle.FLOW -> LyricsThemeSpec(
        // Opacity — matches CSS `opacity: 0.5` on previous lines
        activeLineAlpha = 1.0f,
        previousLineAlpha = 0.5f,
        futureLineAlpha = 0.65f,
        futureAlphaFalloff = listOf(0.65f, 0.45f, 0.30f),

        // Blur — CSS: `filter: blur(.5rem)` ≈ 8dp → ~20–24px at 2.5x density
        // Only on PREVIOUS lines. Future lines explicitly excluded (CSS ~div rule).
        blurPreviousLines = true,
        blurRadiusPx = 20f,
        blurFutureLines = false,

        // Scale — subtle, Spotify doesn't resize aggressively
        activeLineScale = 1.0f,
        inactiveScaleFalloff = listOf(0.97f, 0.94f, 0.92f),

        // Typography — SpotifyMixUITitle is a variable display font, heavy weight
        activeFontWeight = FontWeight.ExtraBold,
        inactiveFontWeight = FontWeight.Bold,
        lineHeightMultiplier = 1.25f,  // CSS: --blyrics-line-height: 1.25

        // Word glow — soft bloom on active word
        glowActiveWord = true,
        wordGlowRadiusPx = 14f,
        futureWordAlpha = 0.35f,
        passedWordAlpha = 1.0f,

        // Transitions — CSS: `transition: 0.5s 0.3s`
        transitionDurationMs = 500,
        transitionDelayMs = 300,
        transitionEasing = FlowEasing,
    )

    // Placeholder specs — fall back to Flow visuals until implemented.
    LyricsStyle.VELVET -> LyricsStyle.FLOW.toThemeSpec()
    LyricsStyle.HALO   -> LyricsStyle.FLOW.toThemeSpec()
    LyricsStyle.AURORA -> LyricsStyle.FLOW.toThemeSpec()
}
