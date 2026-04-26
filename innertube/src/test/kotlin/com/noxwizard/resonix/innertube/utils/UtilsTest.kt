package com.noxwizard.resonix.innertube.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {

    @Test
    fun `isPrivateId returns true when string is exactly privately`() {
        assertTrue(isPrivateId("privately"))
    }

    @Test
    fun `isPrivateId returns true when string contains privately in the middle`() {
        assertTrue(isPrivateId("prefix_privately_suffix"))
    }

    @Test
    fun `isPrivateId returns true when string contains privately at the start`() {
        assertTrue(isPrivateId("privately_suffix"))
    }

    @Test
    fun `isPrivateId returns true when string contains privately at the end`() {
        assertTrue(isPrivateId("prefix_privately"))
    }

    @Test
    fun `isPrivateId returns false when string does not contain privately`() {
        assertFalse(isPrivateId("public_playlist"))
    }

    @Test
    fun `isPrivateId returns false when string contains partial match`() {
        assertFalse(isPrivateId("private_playlist"))
    }

    @Test
    fun `isPrivateId returns false for empty string`() {
        assertFalse(isPrivateId(""))
    }
}
