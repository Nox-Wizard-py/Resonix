package com.noxwizard.resonix.betterlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.noxwizard.resonix.betterlyrics.TTMLParser.ParsedLine
import com.noxwizard.resonix.betterlyrics.TTMLParser.ParsedWord


class TTMLParserTest {

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

        val secondWord = firstLine.words[1]
        assertEquals("World", secondWord.text)
        assertEquals(1.5, secondWord.startTime, 0.001)
        assertEquals(2.0, secondWord.endTime, 0.001)

        val secondLine = lines[1]
        assertEquals("Test", secondLine.text)
        assertEquals(2.5, secondLine.startTime, 0.001)
        assertEquals(1, secondLine.words.size)
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
        val firstLine = lines[0]
        assertEquals("Only text line without spans", firstLine.text)
        assertEquals(83.456, firstLine.startTime, 0.001)
        assertEquals(0, firstLine.words.size)
    }

    @Test
    fun testParseInvalidXML() {
        val ttml = "<invalid xml"
        val lines = TTMLParser.parseTTML(ttml)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun testParseEmptyAttributes() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <!-- p missing begin -->
                        <p>Should be skipped</p>
                        <p begin="0:00.000">
                            <!-- span missing begin -->
                            <span end="0:01.000">SkippedWordStart</span>
                            <!-- span missing end -->
                            <span begin="0:01.000">SkippedWordEnd</span>
                            <!-- valid span -->
                            <span begin="0:02.000" end="0:03.000">Valid</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val lines = TTMLParser.parseTTML(ttml)
        assertEquals(1, lines.size)
        val line = lines[0]

        // words without valid begin and end are appended to text but not added to words list
        assertEquals("SkippedWordStart SkippedWordEnd Valid", line.text)
        assertEquals(1, line.words.size)

        val validWord = line.words[0]
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
        val line = lines[0]
        assertEquals("Valid", line.text)
        assertEquals(1, line.words.size)
        val validWord = line.words[0]
        assertEquals("Valid", validWord.text)
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
    fun `toLRC should format lines with words correctly`() {
        val lines = listOf(
            ParsedLine(
                text = "Hello world",
                startTime = 12.345,
                words = listOf(
                    ParsedWord(text = "Hello", startTime = 12.345, endTime = 12.500),
                    ParsedWord(text = "world", startTime = 12.500, endTime = 13.000)
                )
            )
        )

        val lrc = TTMLParser.toLRC(lines)

        val expected = """
            [00:12.34]Hello world
            <Hello:12.345:12.5|world:12.5:13.0>

        """.trimIndent()

        assertEquals(expected, lrc)
    }

    @Test
    fun `toLRC should handle zero start time`() {
        val lines = listOf(
            ParsedLine(text = "First line", startTime = 0.0, words = emptyList())
        )

        val lrc = TTMLParser.toLRC(lines)

        val expected = """
            [00:00.00]First line

        """.trimIndent()

        assertEquals(expected, lrc)
    }

    @Test
    fun `toLRC should format large timestamps correctly`() {
        val lines = listOf(
            // 1 hour, 5 minutes, 3 seconds, 450 milliseconds = 3903.450 seconds
            ParsedLine(text = "Over an hour", startTime = 3903.450, words = emptyList())
        )

        val lrc = TTMLParser.toLRC(lines)

        // 3903.450 seconds = 65 minutes, 3 seconds, 45 centiseconds
        val expected = """
            [65:03.45]Over an hour

        """.trimIndent()

        assertEquals(expected, lrc)
    }

    @Test
    fun `toLRC should handle empty lines`() {
        val lines = emptyList<ParsedLine>()

        val lrc = TTMLParser.toLRC(lines)

        val expected = ""

        assertEquals(expected, lrc)
    }
}
