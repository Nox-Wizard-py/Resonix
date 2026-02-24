package com.noxwizard.resonix.ui.theme

import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

data class ThemeSeedPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val neutral: Color,
)

object ThemeSeedPaletteCodec {

    private const val PREFIX = "seed:"

    fun encodeForPreference(palette: ThemeSeedPalette, name: String? = null): String {
        val sb = StringBuilder(PREFIX)
        sb.append(palette.primary.toArgb().toLong() and 0xFFFFFFFFL)
        sb.append(",")
        sb.append(palette.secondary.toArgb().toLong() and 0xFFFFFFFFL)
        sb.append(",")
        sb.append(palette.tertiary.toArgb().toLong() and 0xFFFFFFFFL)
        sb.append(",")
        sb.append(palette.neutral.toArgb().toLong() and 0xFFFFFFFFL)
        if (!name.isNullOrBlank()) {
            sb.append("|")
            sb.append(name)
        }
        return sb.toString()
    }

    fun decodeFromPreference(raw: String): ThemeSeedPalette? {
        if (!raw.startsWith(PREFIX)) return null
        return runCatching {
            val body = raw.removePrefix(PREFIX).substringBefore("|")
            val parts = body.split(",")
            if (parts.size != 4) return null
            ThemeSeedPalette(
                primary = Color(parts[0].toLong().toInt()),
                secondary = Color(parts[1].toLong().toInt()),
                tertiary = Color(parts[2].toLong().toInt()),
                neutral = Color(parts[3].toLong().toInt()),
            )
        }.getOrNull()
    }

    fun extractNameFromPreference(raw: String): String? {
        if (!raw.startsWith(PREFIX)) return null
        val idx = raw.indexOf('|')
        if (idx < 0) return null
        return raw.substring(idx + 1).takeIf { it.isNotBlank() }
    }

    fun encodeAsJson(palette: ThemeSeedPalette, name: String? = null): String {
        val json = JSONObject()
        json.put("primary", String.format("#%08X", palette.primary.toArgb()))
        json.put("secondary", String.format("#%08X", palette.secondary.toArgb()))
        json.put("tertiary", String.format("#%08X", palette.tertiary.toArgb()))
        json.put("neutral", String.format("#%08X", palette.neutral.toArgb()))
        if (!name.isNullOrBlank()) json.put("name", name)
        return json.toString(2)
    }

    fun decodeFromJson(text: String): ThemeSeedPalette? {
        return runCatching {
            val json = JSONObject(text)
            ThemeSeedPalette(
                primary = parseColor(json.getString("primary")),
                secondary = parseColor(json.getString("secondary")),
                tertiary = parseColor(json.getString("tertiary")),
                neutral = parseColor(json.getString("neutral")),
            )
        }.getOrNull()
    }

    private fun parseColor(hex: String): Color {
        val normalized = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        return Color(android.graphics.Color.parseColor(normalized))
    }
}
