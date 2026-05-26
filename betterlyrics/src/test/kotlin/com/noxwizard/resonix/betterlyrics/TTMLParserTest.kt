package com.noxwizard.resonix.betterlyrics

import com.noxwizard.resonix.betterlyrics.TTMLParser.ParsedLine
import com.noxwizard.resonix.betterlyrics.TTMLParser.ParsedWord
import com.noxwizard.resonix.betterlyrics.models.SyncType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TTMLParserTest {

    // ─── Existing tests (preserved) ──────────────────────────────────────────

    @Test
    fun testParseValidTTML() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="0:00.000">
                            <span begin="0:00.000" end="0:01.500">Hello</span>
                            <span begin="0:01.500" end="0:02.000">World</span>
                        </p>
                        <p begin="0:02.500">
                            <span begin="0:02.500" end="0:03.000">Test</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(2, lines.size)
        val firstLine = lines[0]
        assertEquals("Hello World", firstLine.text)
        assertEquals(0.0, firstLine.startTime, 0.001)
        assertEquals(2, firstLine.words.size)
        val firstWord = firstLine.words[0]
        assertEquals("Hello", firstWord.text)
        assertEquals(0.0, firstWord.startTime, 0.001)
        assertEquals(1.5, firstWord.endTime, 0.001)
        val secondLine = lines[1]
        assertEquals("Test", secondLine.text)
        assertEquals(2.5, secondLine.startTime, 0.001)
    }

    @Test
    fun testParseNoSpans() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="1:23.456">Only text line without spans</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(1, lines.size)
        assertEquals("Only text line without spans", lines[0].text)
        assertEquals(83.456, lines[0].startTime, 0.001)
        assertEquals(0, lines[0].words.size)
    }

    @Test
    fun testParseInvalidXML() {
        val lines = TTMLParser.parseTTML("<invalid xml")
        assertTrue(lines.isEmpty())
    }

    @Test
    fun testParseEmptyAttributes() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p>Should be skipped</p>
                        <p begin="0:00.000">
                            <span end="0:01.000">SkippedWordStart</span>
                            <span begin="0:01.000">SkippedWordEnd</span>
                            <span begin="0:02.000" end="0:03.000">Valid</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(1, lines.size)
        assertEquals("SkippedWordStart SkippedWordEnd Valid", lines[0].text)
        assertEquals(1, lines[0].words.size)
        val validWord = lines[0].words[0]
        assertEquals("Valid", validWord.text)
        assertEquals(2.0, validWord.startTime, 0.001)
        assertEquals(3.0, validWord.endTime, 0.001)
    }

    @Test
    fun testParseMissingWordsIfSpanEmpty() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="0:00.000">
                            <span begin="0:00.000" end="0:01.000">  </span>
                            <span begin="0:01.000" end="0:02.000">Valid</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(1, lines.size)
        assertEquals("Valid", lines[0].text)
    }

    @Test
    fun testParseTimeFormats() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="1:05.500">MM:SS.mmm format</p>
                        <p begin="1:02:03.456">HH:MM:SS.mmm format</p>
                        <p begin="42.123">Seconds format</p>
                        <p begin="invalid">Invalid format</p>
                        <p begin="0:00:00:00">Four parts format</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(5, lines.size)
        assertEquals(65.5, lines[0].startTime, 0.001)
        assertEquals(3723.456, lines[1].startTime, 0.001)
        assertEquals(42.123, lines[2].startTime, 0.001)
        assertEquals(0.0, lines[3].startTime, 0.001)
        assertEquals(0.0, lines[4].startTime, 0.001)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `toLRC should format lines with words correctly`() {
        val lines = listOf(
            ParsedLine(
                text = "Hello world",
                startTime = 12.345,
                words = listOf(
                    ParsedWord(text = "Hello", startTime = 12.345, endTime = 12.500),
                    ParsedWord(text = "world", startTime = 12.500, endTime = 13.000),
                ),
            ),
        )
        val lrc = TTMLParser.toLRC(lines)
        val expected = """
            [00:12.34]Hello world
            <Hello:12.345:12.5|world:12.5:13.0>

        """.trimIndent()
        assertEquals(expected, lrc)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `toLRC should handle zero start time`() {
        val lines = listOf(ParsedLine(text = "First line", startTime = 0.0, words = emptyList()))
        val lrc = TTMLParser.toLRC(lines)
        val expected = """
            [00:00.00]First line

        """.trimIndent()
        assertEquals(expected, lrc)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `toLRC should format large timestamps correctly`() {
        val lines = listOf(ParsedLine(text = "Over an hour", startTime = 3903.450, words = emptyList()))
        val lrc = TTMLParser.toLRC(lines)
        val expected = """
            [65:03.45]Over an hour

        """.trimIndent()
        assertEquals(expected, lrc)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `toLRC should handle empty lines`() {
        assertEquals("", TTMLParser.toLRC(emptyList()))
    }

    // ─── New structured document tests ───────────────────────────────────────

    @Test
    fun `parseTTMLDocument preserves word timing`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="0:01.000">
                        <span begin="0:01.000" end="0:01.500">Hello</span>
                        <span begin="0:01.500" end="0:02.000">World</span>
                    </p>
                </div></body>
            </tt>
        """.trimIndent()
        val doc = TTMLParser.parseTTMLDocument(ttml)
        val line = doc.lines.first { !it.isInstrumental }
        assertTrue(line.hasWordSync)
        assertEquals(2, line.words.size)
        assertEquals("Hello", line.words[0].word)
        assertEquals(1_000L, line.words[0].startTimeMs)
        assertEquals(1_500L, line.words[0].endTimeMs)
        assertEquals("World", line.words[1].word)
    }

    @Test
    fun `parseTTMLDocument sets correct syncType with word timing`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="0:00.000">
                        <span begin="0:00.000" end="0:01.000">Word</span>
                    </p>
                </div></body>
            </tt>
        """.trimIndent()
        val doc = TTMLParser.parseTTMLDocument(ttml)
        assertEquals(SyncType.WORD_SYNCED, doc.syncType)
        assertTrue(doc.hasWordSync)
    }

    @Test
    fun `parseTTMLDocument sets LINE_SYNCED when no word timing`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="0:05.000">No word spans here</p>
                </div></body>
            </tt>
        """.trimIndent()
        val doc = TTMLParser.parseTTMLDocument(ttml)
        assertEquals(SyncType.LINE_SYNCED, doc.syncType)
        assertFalse(doc.hasWordSync)
    }

    @Test
    fun `parseTTMLDocument infers line endTimeMs from next line`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="0:05.000">First</p>
                    <p begin="0:10.000">Second</p>
                </div></body>
            </tt>
        """.trimIndent()
        val doc = TTMLParser.parseTTMLDocument(ttml)
        val plain = doc.lines.filter { !it.isInstrumental }
        // First line end should equal second line start
        assertEquals(10_000L, plain[0].endTimeMs)
    }

    @Test
    fun `parseTTMLDocument detects instrumental gap`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="0:01.000">Verse</p>
                    <p begin="0:20.000">Chorus</p>
                </div></body>
            </tt>
        """.trimIndent()
        val doc = TTMLParser.parseTTMLDocument(ttml)
        assertTrue(doc.hasInstrumentalGaps)
        assertTrue(doc.lines.any { it.isInstrumental })
    }

    @Test
    fun `parseTTMLDocument returns empty document on malformed TTML`() {
        val doc = TTMLParser.parseTTMLDocument("<broken xml")
        assertTrue(doc.isEmpty)
        assertEquals(SyncType.UNSYNCED, doc.syncType)
    }

    @Test
    fun `parseTTMLDocument reports correct provider name`() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div>
                    <p begin="0:01.000">Line</p>
                </div></body>
            </tt>
        """.trimIndent()
        val doc = TTMLParser.parseTTMLDocument(ttml)
        assertEquals("BetterLyrics TTML", doc.provider.providerName)
    }
}
