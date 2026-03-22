package com.noxwizard.resonix.betterlyrics

import org.junit.Assert.assertEquals
import org.junit.Test
import com.noxwizard.resonix.betterlyrics.TTMLParser.ParsedLine
import com.noxwizard.resonix.betterlyrics.TTMLParser.ParsedWord

class TTMLParserTest {

    @Test
    fun `toLRC should format simple lines correctly`() {
        val lines = listOf(
            ParsedLine(text = "Hello world", startTime = 12.345, words = emptyList()),
            ParsedLine(text = "This is a test", startTime = 65.0, words = emptyList())
        )

        val lrc = TTMLParser.toLRC(lines)

        val expected = """
            [00:12.34]Hello world
            [01:05.00]This is a test

        """.trimIndent()

        assertEquals(expected, lrc)
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
