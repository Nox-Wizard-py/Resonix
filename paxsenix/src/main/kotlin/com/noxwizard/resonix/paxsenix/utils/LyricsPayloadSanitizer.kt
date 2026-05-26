package com.noxwizard.resonix.paxsenix.utils

import org.json.JSONObject

/**
 * Sanitizes raw provider response strings before parsing.
 *
 * Handles:
 * 1. JSON-wrapped LRC: `{"rawText":"[00:00.40]..."}` → extracts inner string
 * 2. Escaped newlines: literal `\n` → real newline
 * 3. JSON envelope artifacts stripped before handing to LRC/TTML parser
 */
object LyricsPayloadSanitizer {

    fun sanitize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return trimmed

        // 1. Detect JSON envelope
        val unwrapped = if (trimmed.startsWith("{")) {
            runCatching {
                val obj = JSONObject(trimmed)
                val extracted = when {
                    obj.has("rawText") -> obj.getString("rawText")
                    obj.has("lyrics") -> obj.getString("lyrics")
                    obj.has("lrc") -> obj.getString("lrc")
                    obj.has("syncedLyrics") -> obj.getString("syncedLyrics")
                    else -> null
                }
                extracted?.takeIf { it.isNotBlank() } ?: trimmed
            }.getOrDefault(trimmed)
        } else {
            trimmed
        }

        // 2. Unescape literal \n sequences
        val unescaped = unwrapped.replace("\\n", "\n").replace("\\r", "")

        // 3. Strip any residual JSON artifacts (e.g. trailing `}` from partial wraps)
        return unescaped.trim()
    }
}
