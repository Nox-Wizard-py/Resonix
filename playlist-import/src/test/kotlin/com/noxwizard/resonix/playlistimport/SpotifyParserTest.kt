package com.noxwizard.resonix.playlistimport

import org.junit.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import java.io.File

class SpotifyParserTest {

    @Test
    fun `parseTracksFromHtml extracts tracks correctly from live snapshot`() {
        // Load the real HTML screenshot we saved
        val htmlFile = File("../spotify_real.html")
        if (!htmlFile.exists()) {
             println("WARNING: spotify_real.html not found, skipping test. This test is intended for manual verification environment.")
             return
        }
        val html = htmlFile.readText()

        // We need to access the private parseTracksFromHtml
        // Since we can't easily change visibility without a separate step,
        // let's use reflection to invoke it for this verification test.
        val parser = SpotifyParser
        val method = parser.javaClass.getDeclaredMethod("parseTracksFromHtml", String::class.java)
        method.isAccessible = true
        
        @Suppress("UNCHECKED_CAST")
        val tracks = method.invoke(parser, html) as List<ParsedTrack>
        
        println("Found ${tracks.size} tracks in snapshot")
        
        // Assertions
        assertTrue(tracks.isNotEmpty(), "Should find tracks in the snapshot")
        
        // Check for specific known tracks from the snapshot we saw earlier
        // "What You Saying" by "Lil Uzi Vert" was in the previous inspection output
        val hasLilUzi = tracks.any { it.title == "What You Saying" && it.artist.contains("Lil Uzi Vert") }
        assertTrue(hasLilUzi, "Should find 'What You Saying' by 'Lil Uzi Vert'")
        
        // Check for "4 Raws" by "EsDeeKid"
        val hasEsDeeKid = tracks.any { it.title == "4 Raws" && it.artist.contains("EsDeeKid") }
        assertTrue(hasEsDeeKid, "Should find '4 Raws' by 'EsDeeKid'")
    }
    @Test
    fun `extractTracksArrayStandard parses standard API response correctly`() {
        val parser = SpotifyParser
        
        // precise standard API response format (simplified)
        val jsonString = """
            {
                "href": "https://api.spotify.com/v1/playlists/id/tracks",
                "items": [
                    {
                        "track": {
                            "name": "Standard Song",
                            "duration_ms": 200000,
                            "album": { "name": "Standard Album" },
                            "artists": [ { "name": "Standard Artist" } ]
                        }
                    },
                     {
                        "track": {
                            "name": "Second Song",
                            "duration_ms": 180000,
                            "album": { "name": "Album 2" },
                            "artists": [ { "name": "Artist A" }, { "name": "Artist B" } ]
                        }
                    }
                ]
            }
        """.trimIndent()
        
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val data = json.parseToJsonElement(jsonString)
        
        // Reflection to call private extractTracksArrayStandard
        val extractMethod = parser.javaClass.getDeclaredMethod("extractTracksArrayStandard", kotlinx.serialization.json.JsonElement::class.java)
        extractMethod.isAccessible = true
        val tracksArray = extractMethod.invoke(parser, data) as kotlinx.serialization.json.JsonArray
        
        // Reflection to call private parseTracksFromJsonArray
        val parseMethod = parser.javaClass.getDeclaredMethod("parseTracksFromJsonArray", kotlinx.serialization.json.JsonArray::class.java)
        parseMethod.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val tracks = parseMethod.invoke(parser, tracksArray) as List<ParsedTrack>
        
        assertEquals(2, tracks.size)
        assertEquals("Standard Song", tracks[0].title)
        assertEquals("Standard Artist", tracks[0].artist)
        assertEquals("Standard Album", tracks[0].album)
        
        assertEquals("Second Song", tracks[1].title)
        assertEquals("Artist A, Artist B", tracks[1].artist)
    }
}
