package com.noxwizard.resonix.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {

    @Test
    fun `parseCookieString with empty string returns empty map`() {
        val cookie = ""
        val result = parseCookieString(cookie)
        assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun `parseCookieString with single cookie returns map with one entry`() {
        val cookie = "key=value"
        val result = parseCookieString(cookie)
        assertEquals(mapOf("key" to "value"), result)
    }

    @Test
    fun `parseCookieString with multiple cookies returns map with multiple entries`() {
        val cookie = "key1=value1; key2=value2; key3=value3"
        val result = parseCookieString(cookie)
        assertEquals(
            mapOf(
                "key1" to "value1",
                "key2" to "value2",
                "key3" to "value3"
            ),
            result
        )
    }

    @Test
    fun `parseCookieString ignores parts without equals sign`() {
        val cookie = "key1=value1; invalid_part; key2=value2"
        val result = parseCookieString(cookie)
        assertEquals(
            mapOf(
                "key1" to "value1",
                "key2" to "value2"
            ),
            result
        )
    }

    @Test
    fun `parseCookieString handles values with equals sign correctly`() {
        val cookie = "key=value=with=equals; key2=normal"
        val result = parseCookieString(cookie)
        assertEquals(
            mapOf(
                "key" to "value=with=equals",
                "key2" to "normal"
            ),
            result
        )
    }

    @Test
    fun `parseCookieString handles trailing semicolons and empty parts`() {
        val cookie = "key1=value1; ; key2=value2; "
        val result = parseCookieString(cookie)
        assertEquals(
            mapOf(
                "key1" to "value1",
                "key2" to "value2"
            ),
            result
        )
    }
}
