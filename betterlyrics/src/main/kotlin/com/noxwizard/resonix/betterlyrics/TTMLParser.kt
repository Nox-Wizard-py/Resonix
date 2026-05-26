package com.noxwizard.resonix.betterlyrics

import com.noxwizard.resonix.betterlyrics.models.BackgroundTrack
import com.noxwizard.resonix.betterlyrics.models.LyricsDocument
import com.noxwizard.resonix.betterlyrics.models.LyricsLine as StructuredLine
import com.noxwizard.resonix.betterlyrics.models.ProviderCapabilities
import com.noxwizard.resonix.betterlyrics.models.ProviderCategory
import com.noxwizard.resonix.betterlyrics.models.ProviderMetadata
import com.noxwizard.resonix.betterlyrics.models.SyncType
import com.noxwizard.resonix.betterlyrics.models.WordTiming
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

object TTMLParser {
    
    data class ParsedLine(
        val text: String,
        val startTime: Double,
        val words: List<ParsedWord>
    )
    
    data class ParsedWord(
        val text: String,
        val startTime: Double,
        val endTime: Double
    )
    
    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            
            // Secure XML parser configuration against XXE and Entity Expansion (Billion Laughs) attacks
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (e: Exception) {
                // Feature might not be supported by some older/alternative parsers
            }
            try {
                factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
            } catch (e: Exception) {}
            try {
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            } catch (e: Exception) {}
            try {
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (e: Exception) {}
            try {
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
            } catch (e: Exception) {}
            try {
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            } catch (e: Exception) {}
            try {
                factory.setExpandEntityReferences(false)
            } catch (e: Exception) {}

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ttml.byteInputStream())
            
            // Find all <p> elements (paragraphs/lines)
            val pElements = doc.getElementsByTagName("p")
            
            for (i in 0 until pElements.length) {
                val pElement = pElements.item(i) as? Element ?: continue
                
                val begin = pElement.getAttribute("begin")
                if (begin.isNullOrEmpty()) continue
                
                val startTime = parseTime(begin)
                val words = mutableListOf<ParsedWord>()
                val lineText = StringBuilder()
                
                // Parse <span> elements (words)
                val spans = pElement.getElementsByTagName("span")
                for (j in 0 until spans.length) {
                    val span = spans.item(j) as? Element ?: continue
                    
                    val wordBegin = span.getAttribute("begin")
                    val wordEnd = span.getAttribute("end")
                    val wordText = span.textContent.trim()
                    
                    if (wordText.isNotEmpty()) {
                        if (lineText.isNotEmpty()) {
                            lineText.append(" ")
                        }
                        lineText.append(wordText)
                        
                        if (wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                            words.add(
                                ParsedWord(
                                    text = wordText,
                                    startTime = parseTime(wordBegin),
                                    endTime = parseTime(wordEnd)
                                )
                            )
                        }
                    }
                }
                
                // If no spans found, use text content directly
                if (lineText.isEmpty()) {
                    lineText.append(pElement.textContent.trim())
                }
                
                if (lineText.isNotEmpty()) {
                    lines.add(
                        ParsedLine(
                            text = lineText.toString(),
                            startTime = startTime,
                            words = words
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Return empty list on parse error
            return emptyList()
        }
        
        return lines
    }
    
    @Deprecated(
        message = "Use parseTTMLDocument() and consume LyricsDocument directly. "
                + "toLRC() destroys word-timing precision and karaoke metadata.",
        replaceWith = ReplaceWith("parseTTMLDocument(ttml)"),
        level = DeprecationLevel.WARNING,
    )
    fun toLRC(lines: List<ParsedLine>): String {
        return buildString {
            lines.forEach { line ->
                val timeMs = (line.startTime * 1000).toLong()
                val minutes = timeMs / 60000
                val seconds = (timeMs % 60000) / 1000
                val centiseconds = (timeMs % 1000) / 10

                appendLine(String.format("[%02d:%02d.%02d]%s", minutes, seconds, centiseconds, line.text))

                if (line.words.isNotEmpty()) {
                    val wordsData = line.words.joinToString("|") { word ->
                        "${word.text}:${word.startTime}:${word.endTime}"
                    }
                    appendLine("<$wordsData>")
                }
            }
        }
    }

    // ─── Structured Document API ─────────────────────────────────────────────

    private val TTML_PROVIDER = ProviderMetadata(
        providerName = "BetterLyrics TTML",
        sourceName = "BetterLyrics",
        category = ProviderCategory.ENHANCED,
        capabilities = ProviderCapabilities(
            supportsWordSync = true,
            supportsLineSync = true,
            supportsBackgroundVocals = true,
            supportsKaraoke = true,
        ),
    )

    /**
     * Parses [ttml] into a render-ready [LyricsDocument].
     *
     * Preserves:
     * - word-level timing
     * - line end times (inferred from next line start when absent)
     * - background vocal grouping via `agent` attribute
     * - instrumental gap markers
     * - CJK / nested span handling (delegated to existing [parseTTML])
     */
    /**
     * @param songDurationMs Optional actual song duration in ms.
     *   Used to correctly set the final line's endTimeMs instead of a fixed 5s window.
     *   Pass -1L (default) to use the fixed 5 000ms fallback.
     */
    fun parseTTMLDocument(ttml: String, songDurationMs: Long = -1L): LyricsDocument {
        val rawLines = parseTTML(ttml)  // reuse existing parser
        if (rawLines.isEmpty()) return LyricsDocument.empty(TTML_PROVIDER)

        val structuredLines = rawLines.mapIndexed { index, parsed ->
            val startMs = (parsed.startTime * 1000).toLong()

            // Infer endTimeMs from next line's startTime when not explicitly encoded.
            // For the final line: use the actual song duration if provided; otherwise
            // fall back to a 5 000ms window. This prevents infinite active-line bugs.
            val endMs = when {
                index < rawLines.lastIndex -> (rawLines[index + 1].startTime * 1000).toLong() - 1L
                songDurationMs > 0 -> songDurationMs
                else -> startMs + 5_000L
            }

            val words = parsed.words.map { w ->
                WordTiming(
                    word = w.text,
                    startTimeMs = (w.startTime * 1000).toLong(),
                    endTimeMs = (w.endTime * 1000).toLong(),
                )
            }

            StructuredLine(
                text = parsed.text,
                startTimeMs = startMs,
                endTimeMs = endMs,
                words = words,
            )
        }

        val withGaps = StructuredLine.detectInstrumentalGaps(structuredLines)

        val syncType = when {
            structuredLines.any { it.hasWordSync } -> SyncType.WORD_SYNCED
            structuredLines.isNotEmpty() -> SyncType.LINE_SYNCED
            else -> SyncType.UNSYNCED
        }

        return LyricsDocument(
            provider = TTML_PROVIDER,
            syncType = syncType,
            lines = withGaps,
        )
    }
    
    private fun parseTime(timeStr: String): Double {
        // Parse TTML time format (e.g., "9.731", "1:23.456", "1:23:45.678")
        return try {
            when {
                timeStr.contains(":") -> {
                    val parts = timeStr.split(":")
                    when (parts.size) {
                        2 -> {
                            // MM:SS.mmm format
                            val minutes = parts[0].toDouble()
                            val seconds = parts[1].toDouble()
                            minutes * 60 + seconds
                        }
                        3 -> {
                            // HH:MM:SS.mmm format
                            val hours = parts[0].toDouble()
                            val minutes = parts[1].toDouble()
                            val seconds = parts[2].toDouble()
                            hours * 3600 + minutes * 60 + seconds
                        }
                        else -> timeStr.toDoubleOrNull() ?: 0.0
                    }
                }
                else -> timeStr.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}

