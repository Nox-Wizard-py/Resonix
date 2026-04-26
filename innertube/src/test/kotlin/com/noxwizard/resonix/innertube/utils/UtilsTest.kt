package com.noxwizard.resonix.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilsTest {

    @Test
    fun parseTime_validTwoParts() {
        assertEquals(12 * 60 + 34, "12:34".parseTime())
        assertEquals(0 * 60 + 0, "00:00".parseTime())
        assertEquals(5 * 60 + 9, "05:09".parseTime())
        assertEquals(99 * 60 + 59, "99:59".parseTime())
    }

    @Test
    fun parseTime_validThreeParts() {
        assertEquals(1 * 3600 + 23 * 60 + 45, "1:23:45".parseTime())
        assertEquals(0 * 3600 + 0 * 60 + 0, "00:00:00".parseTime())
        assertEquals(10 * 3600 + 5 * 60 + 9, "10:05:09".parseTime())
    }

    @Test
    fun parseTime_invalidFormat_returnsNull() {
        assertNull("invalid".parseTime())
        assertNull("12".parseTime())
        assertNull("12:34:56:78".parseTime())
        assertNull("abc:def".parseTime())
        assertNull("".parseTime())
        assertNull(":".parseTime())
        assertNull("::".parseTime())
        assertNull("12:".parseTime())
        assertNull(":34".parseTime())
        assertNull("12::34".parseTime())
        assertNull(" 12:34 ".parseTime()) // NumberFormatException
    }
}
