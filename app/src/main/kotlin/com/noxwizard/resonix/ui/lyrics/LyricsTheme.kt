package com.noxwizard.resonix.ui.lyrics

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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
    /** Use the expressive accent color for the active word's glow instead of white. */
    val useAccentForWordGlow: Boolean = false,
    /** Base alpha for the active word glow. */
    val activeWordGlowAlpha: Float = 0.75f,
    /** Theme-specific accent color override. If null, the app's default accent is used. */
    val themeAccentColor: Color? = null,
    /** Whether the line immediately after the active line should be tinted with the accent color. */
    val tintNextLineWithAccent: Boolean = false,
    /** Whether the active line should use a left-to-right progress fill animation. */
    val activeLineProgressFill: Boolean = false,

    // ── Romanized / Translated ─────────────────────────────────────────────
    /** Alpha for the romanized text. */
    val romanizedTextAlpha: Float = 0.5f,
    /** Background container alpha for romanized text. 0.0f = no background. */
    val romanizedBackgroundAlpha: Float = 0.0f,
    /** Alpha for the translated text. */
    val translatedTextAlpha: Float = 0.5f,

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
        // Opacity — CSS `opacity: 0.5` on previous lines
        activeLineAlpha = 1.0f,
        previousLineAlpha = 0.5f,
        futureLineAlpha = 0.65f,
        futureAlphaFalloff = listOf(0.65f, 0.42f, 0.28f),

        // Blur — CSS `filter: blur(.5rem)` ≈ 8px logical.
        // 14f at high density produces a natural depth without smearing too aggressively.
        blurPreviousLines = true,
        blurRadiusPx = 14f,
        blurFutureLines = false,

        // Scale — minimal, Spotify doesn't resize lines
        activeLineScale = 1.0f,
        inactiveScaleFalloff = listOf(0.97f, 0.94f, 0.92f),

        // Typography — SpotifyMixUITitle is ExtraBold display; active line is dominant
        activeFontWeight = FontWeight.ExtraBold,
        inactiveFontWeight = FontWeight.Bold,
        lineHeightMultiplier = 1.25f,  // CSS: --blyrics-line-height: 1.25

        // Word glow — soft white Shadow on active word via SpanStyle.
        // Radius 10f: subtle bloom, not a neon halo.
        glowActiveWord = true,
        wordGlowRadiusPx = 10f,
        futureWordAlpha = 0.45f,   // dim but readable — Spotify shows upcoming words faintly
        passedWordAlpha = 1.0f,
        useAccentForWordGlow = false,
        activeWordGlowAlpha = 0.75f,

        // Romanized / Translated
        romanizedTextAlpha = 0.5f,
        romanizedBackgroundAlpha = 0.0f,
        translatedTextAlpha = 0.5f,
        themeAccentColor = null,

        // Transitions — CSS: `transition: 0.5s 0.3s`
        transitionDurationMs = 500,
        transitionDelayMs = 300,
        transitionEasing = FlowEasing,
    )

    LyricsStyle.VELVET -> LyricsThemeSpec(
        // Opacity
        activeLineAlpha = 1.0f,
        previousLineAlpha = 0.7f,
        futureLineAlpha = 0.7f,
        futureAlphaFalloff = listOf(0.7f, 0.6f, 0.5f),

        // Blur — No blur in Velvet
        blurPreviousLines = false,
        blurRadiusPx = 0f,
        blurFutureLines = false,

        // Scale — 1.05 for active, no shrinking for inactive
        activeLineScale = 1.05f,
        inactiveScaleFalloff = listOf(1.0f),

        // Typography — Softer than Flow
        activeFontWeight = FontWeight.SemiBold,
        inactiveFontWeight = FontWeight.Medium,
        lineHeightMultiplier = 1.35f,

        // Word glow — Gentle color emphasis, soft pulse using expressive accent
        glowActiveWord = true,
        wordGlowRadiusPx = 10f,
        futureWordAlpha = 0.6f, // softer contrast
        passedWordAlpha = 0.8f,
        useAccentForWordGlow = true,
        activeWordGlowAlpha = 0.4f, // soft pulse
        tintNextLineWithAccent = true,
        activeLineProgressFill = true,

        // Romanized / Translated — Special container styling for romanized
        romanizedTextAlpha = 0.7f,
        romanizedBackgroundAlpha = 0.05f,
        translatedTextAlpha = 0.7f,
        themeAccentColor = Color(0xFFD49E9B),

        // Transitions
        transitionDurationMs = 500,
        transitionDelayMs = 0,
        transitionEasing = FlowEasing,
    )

    // Placeholder specs — fall back to Flow visuals until implemented.
    LyricsStyle.HALO   -> LyricsStyle.FLOW.toThemeSpec()
    LyricsStyle.AURORA -> LyricsStyle.FLOW.toThemeSpec()
}
