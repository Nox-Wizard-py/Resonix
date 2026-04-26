package com.noxwizard.resonix.db.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistEntityTest {

    @Test
    fun testGenerateArtistId() {
        val id1 = ArtistEntity.generateArtistId()
        val id2 = ArtistEntity.generateArtistId()

        // Verify the prefix
        assertTrue("ID should start with 'LA'", id1.startsWith("LA"))
        assertTrue("ID should start with 'LA'", id2.startsWith("LA"))

        // Verify the total length ("LA" + 8 random alphanumeric characters = 10)
        assertEquals("ID length should be 10", 10, id1.length)
        assertEquals("ID length should be 10", 10, id2.length)

        // Verify it contains only alphanumeric characters (after LA)
        assertTrue("ID should only contain letters and digits", id1.substring(2).all { it.isLetterOrDigit() })
        assertTrue("ID should only contain letters and digits", id2.substring(2).all { it.isLetterOrDigit() })

        // Verify randomness (extremely low probability of collision)
        assertNotEquals("Successive IDs should be different", id1, id2)
    }
}
