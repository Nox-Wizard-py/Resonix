package com.noxwizard.resonix.db.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistEntityTest {

    @Test
    fun `generatePlaylistId should start with LP prefix`() {
        val id = PlaylistEntity.generatePlaylistId()
        assertTrue("ID should start with 'LP'", id.startsWith("LP"))
    }

    @Test
    fun `generatePlaylistId should have a total length of 10 characters`() {
        val id = PlaylistEntity.generatePlaylistId()
        assertEquals("ID length should be 10 characters", 10, id.length)
    }

    @Test
    fun `generatePlaylistId should only contain alphabetic characters after the prefix`() {
        val id = PlaylistEntity.generatePlaylistId()
        val randomPart = id.substring(2)
        assertTrue("The random part should only contain alphabetic characters", randomPart.all { it.isLetter() })
    }

    @Test
    fun `generatePlaylistId should generate unique ids`() {
        val ids = mutableSetOf<String>()
        val count = 1000
        for (i in 0 until count) {
            ids.add(PlaylistEntity.generatePlaylistId())
        }
        assertEquals("All generated IDs should be unique", count, ids.size)
    }
}
