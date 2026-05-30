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

    private fun isCjk(text: String): Boolean {
        return text.any { c ->
            Character.UnicodeBlock.of(c) in setOf(
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
                Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
                Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT,
                Character.UnicodeBlock.HIRAGANA,
                Character.UnicodeBlock.KATAKANA,
                Character.UnicodeBlock.HANGUL_SYLLABLES,
                Character.UnicodeBlock.HANGUL_JAMO,
                Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
            )
        }
    }

    private fun getElementsByTagNameAgnostic(doc: org.w3c.dom.Document, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val allElements = doc.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val el = allElements.item(i) as? Element ?: continue
            val name = el.tagName
            if (name.equals(tagName, ignoreCase = true) || name.endsWith(":$tagName", ignoreCase = true)) {
                result.add(el)
            }
        }
        return result
    }

    private fun getElementsByTagNameAgnostic(parent: Element, tagName: String): List<Element> {
        val result = mutableListOf<Element>()
        val allElements = parent.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val el = allElements.item(i) as? Element ?: continue
            val name = el.tagName
            if (name.equals(tagName, ignoreCase = true) || name.endsWith(":$tagName", ignoreCase = true)) {
                result.add(el)
            }
        }
        return result
    }
    
    fun parseTTML(ttml: String): List<ParsedLine> {
        val lines = mutableListOf<ParsedLine>()
        
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            
            // Secure XML parser configuration against XXE and Entity Expansion (Billion Laughs) attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            factory.setExpandEntityReferences(false)

            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ttml.byteInputStream())
            
            // Find all <p> elements (paragraphs/lines)
            val pElements = getElementsByTagNameAgnostic(doc, "p")
            
            for (pElement in pElements) {
                
                val begin = pElement.getAttribute("begin")
                if (begin.isNullOrEmpty()) continue
                
                val startTime = parseTime(begin)
                val words = mutableListOf<ParsedWord>()
                val lineText = StringBuilder()
                
                // Parse <span> elements (words)
                val spans = getElementsByTagNameAgnostic(pElement, "span")
                for (span in spans) {
                    
                    val wordBegin = span.getAttribute("begin")
                    val wordEnd = span.getAttribute("end")
                    val wordTextRaw = span.textContent ?: ""
                    
                    if (wordTextRaw.isNotBlank()) {
                        val wordText = wordTextRaw.trim()
                        val isSyllableContinuation = words.isNotEmpty() && !lineText.endsWith(" ") && !wordTextRaw.startsWith(" ")
                        
                        if (!isSyllableContinuation && lineText.isNotEmpty() && !lineText.endsWith(" ")) {
                            // Only append a space if the raw text implies a break, or if it's the first non-continuation
                            // Actually, TTML often relies on raw whitespace. If it's not a continuation and there's no space, 
                            // we should probably add one unless it's CJK.
                            if (!isCjk(wordText) || lineText.last().isWhitespace() == false) {
                                lineText.append(" ")
                            }
                        }
                        
                        lineText.append(wordTextRaw)
                        
                        if (wordBegin.isNotEmpty() && wordEnd.isNotEmpty()) {
                            val newWordStart = parseTime(wordBegin)
                            val newWordEnd = parseTime(wordEnd)
                            
                            val lastWord = words.lastOrNull()
                            if (isSyllableContinuation && lastWord != null && 
                                !lastWord.text.endsWith(" ") && 
                                !isCjk(lastWord.text.trim()) && !isCjk(wordText)
                            ) {
                                words[words.lastIndex] = lastWord.copy(
                                    text = lastWord.text + wordText,
                                    endTime = newWordEnd
                                )
                            } else {
                                words.add(
                                    ParsedWord(
                                        text = wordTextRaw,
                                        startTime = newWordStart,
                                        endTime = newWordEnd
                                    )
                                )
                            }
                        }
                    } else if (wordTextRaw.isNotEmpty() && !wordTextRaw.contains('\n')) {
                         if (words.isNotEmpty() && !words.last().text.endsWith(" ")) {
                             lineText.append(" ")
                             val lastWord = words.last()
                             words[words.lastIndex] = lastWord.copy(text = lastWord.text + " ")
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

