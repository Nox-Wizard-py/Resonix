package com.noxwizard.resonix.lastfm.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class MD5Test {

    @Test
    fun testMD5Hash() {
        val testStrings = listOf(
            "",
            "a",
            "abc",
            "message digest",
            "abcdefghijklmnopqrstuvwxyz",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789",
            "12345678901234567890123456789012345678901234567890123456789012345678901234567890",
            "Last.fm API signature test with some random characters !@#$%^&*()_+{}|:\"<>?~`-=[]\\;',./",
            "UTF-8 test with accents: áéíóú ñ and special symbols: € £"
        )

        for (testString in testStrings) {
            val bytes = testString.toByteArray(Charsets.UTF_8)

            val expectedHash = MessageDigest.getInstance("MD5").digest(bytes)
            val actualHash = MD5.hash(bytes)

            val expectedHex = expectedHash.joinToString("") { "%02x".format(it) }
            val actualHex = actualHash.joinToString("") { "%02x".format(it) }

            assertEquals("MD5 hash mismatch for input: '$testString'", expectedHex, actualHex)
        }
    }
}
