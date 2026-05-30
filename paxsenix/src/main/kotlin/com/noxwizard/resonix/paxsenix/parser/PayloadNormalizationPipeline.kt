package com.noxwizard.resonix.paxsenix.parser

import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer

/**
 * Mandatory payload normalization stage that runs on every provider's raw response
 * BEFORE it is parsed into a [LyricsLine] list.
 *
 * Responsibilities:
 * - Detect and route raw payloads by type: JSON → sanitizer, TTML/XML → flag passthrough,
 *   LRC → repair + parse
 * - Strip BOM (U+FEFF)
 * - Fix escaped newlines (\\n → real newlines)
 * - Repair malformed LRC timestamps (common provider bugs)
 * - Return a clean string ready for the appropriate parser
 *
 * All providers should call [normalize] on their raw response before constructing
 * a [com.noxwizard.resonix.paxsenix.models.LyricsDocument].
 *
 * The renderer NEVER sees raw provider payloads.
 */
object PayloadNormalizationPipeline {

    private val BOM = "\uFEFF"
    private val ESCAPED_NEWLINE = Regex("""\\n""")
    private val ESCAPED_CARRIAGE = Regex("""\\r""")

    // Detects malformed LRC timestamps like [1:23.45] → [01:23.45]
    private val MALFORMED_TIMESTAMP_PATTERN = Regex("""^\[(\d):(\d{2}\.\d{2,3})]""")
    private val MALFORMED_TIMESTAMP_FIX = Regex("""^\[(\d):""")

    /**
     * Normalizes a raw provider payload into a clean string.
     *
     * Routing:
     * - Starts with `{` → unwrapped via [LyricsPayloadSanitizer]
     * - Starts with `<tt` or `<?xml` → returned as-is (TTML, handled by TTMLParser)
     * - Otherwise treated as LRC → BOM stripped, escape sequences fixed, timestamps repaired
     *
     * @return Normalized string ready for downstream parsing, or null if payload is empty/invalid.
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null

        val stripped = raw.removePrefix(BOM).trim()

        return when {
            // JSON-wrapped rawText payload
            stripped.startsWith("{") -> {
                val sanitized = LyricsPayloadSanitizer.sanitize(stripped)
                if (sanitized.isBlank()) null else sanitized
            }

            // Raw TTML / XML — pass through intact for TTMLParser
            stripped.startsWith("<tt") || stripped.startsWith("<?xml") -> stripped

            // LRC or plain text — apply repairs
            else -> repairLrc(stripped)
        }
    }

    /**
     * Repairs common LRC payload issues:
     * - Escaped newlines (\\n) → real newlines
     * - Single-digit minute in timestamps [1:23.45] → [01:23.45]
     * - Unicode BOM stripped (already done above, but double-check per-line)
     * - Empty text lines from malformed providers preserved as instrumental markers
     */
    fun repairLrc(lrc: String): String {
        var repaired = lrc
            .replace(ESCAPED_NEWLINE, "\n")
            .replace(ESCAPED_CARRIAGE, "")

        // Fix single-digit minute timestamps
        repaired = repaired.lines().joinToString("\n") { line ->
            if (MALFORMED_TIMESTAMP_PATTERN.containsMatchIn(line)) {
                MALFORMED_TIMESTAMP_FIX.replace(line) { "[0${it.groupValues[1]}:" }
            } else {
                line
            }
        }

        return repaired.trim()
    }

    /**
     * Determines the likely payload type without full parsing.
     */
    fun detectType(raw: String): PayloadType {
        val trimmed = raw.removePrefix(BOM).trimStart()
        return when {
            trimmed.startsWith("{") -> PayloadType.JSON
            trimmed.startsWith("<tt") || trimmed.startsWith("<?xml") -> PayloadType.TTML
            trimmed.contains(Regex("""^\[\d{1,2}:\d{2}\.\d{2,3}]""", RegexOption.MULTILINE)) -> PayloadType.LRC
            else -> PayloadType.PLAIN_TEXT
        }
    }

    enum class PayloadType { JSON, TTML, LRC, PLAIN_TEXT }
}
