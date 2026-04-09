package com.noxwizard.resonix.lyrics

import com.noxwizard.resonix.betterlyrics.BetterLyrics
import com.noxwizard.resonix.betterlyrics.TTMLParser
import com.noxwizard.resonix.kugou.KuGou
import com.noxwizard.resonix.lrclib.LrcLib
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.Locale

data class NormalizedLyricsResult(
    val title: String,
    val artist: String,
    val album: String?,
    val lyricsSnippetMatch: String?,
    val sourceProvider: String,
    val confidenceScore: Double,
    val youtubeSearchQuery: String,
)

object ParallelLyricsSearcher {

    suspend fun searchAllProviders(query: String): NormalizedLyricsResult? = coroutineScope {
        val snippet = query.trim()
        if (snippet.length < 5) return@coroutineScope null

        val results = awaitAll(
            async { searchLrcLib(snippet) },
            async { searchKugou(snippet) },
            async { searchSimpMusic(snippet) },
            async { searchBetterLyrics(snippet) },
            async { searchLyricsPlus(snippet) },
        ).flatten()

        if (results.isEmpty()) return@coroutineScope null

        // 1. Group and find duplicates
        val groupedByQuery = results.groupBy { 
            it.youtubeSearchQuery.lowercase(Locale.getDefault()) 
        }

        // 2. Score results
        val scoredResults = results.map { res ->
            val duplicateCount = groupedByQuery[res.youtubeSearchQuery.lowercase(Locale.getDefault())]?.size ?: 1
            
            // Weights logic defined by user
            val lyricMatchScore = calculateSnippetMatchScore(res.lyricsSnippetMatch ?: "", snippet)
            val titleSimScore = if (res.title.isNotEmpty()) 1.0 else 0.0 // Hard to know what they meant vs title
            val artistConfScore = if (res.artist.isNotEmpty()) 1.0 else 0.0
            
            // Scaled components
            val lyricWeight = lyricMatchScore * 0.50
            val duplicateWeight = (minOf(duplicateCount, 4) / 4.0) * 0.25
            val titleWeight = titleSimScore * 0.15
            val artistWeight = artistConfScore * 0.10

            var finalScore = lyricWeight + duplicateWeight + titleWeight + artistWeight
            
            // Significant boost for cross-provider agreement
            if (duplicateCount > 1) {
                finalScore += 0.20
            }

            res.copy(confidenceScore = minOf(finalScore, 1.0))
        }

        // Return best match
        scoredResults.maxByOrNull { it.confidenceScore }
    }

    private suspend fun searchLrcLib(query: String): List<NormalizedLyricsResult> = runCatching {
        val tracks = LrcLib.queryLyrics(artist = "", title = query)
        tracks.mapNotNull { track ->
            if (track.trackName.isBlank()) return@mapNotNull null
            NormalizedLyricsResult(
                title = track.trackName,
                artist = track.artistName,
                album = track.albumName,
                lyricsSnippetMatch = track.syncedLyrics ?: track.plainLyrics,
                sourceProvider = "LRCLIB",
                confidenceScore = 0.0,
                youtubeSearchQuery = "${track.trackName} ${track.artistName}"
            )
        }
    }.getOrElse { emptyList() }

    private suspend fun searchKugou(query: String): List<NormalizedLyricsResult> = runCatching {
        val keyword = KuGou.generateKeyword(query, "")
        val response = KuGou.searchSongs(keyword)
        response.data.info.mapNotNull { song ->
            NormalizedLyricsResult(
                title = song.songname ?: "",
                artist = song.singername ?: "",
                album = song.album_name,
                lyricsSnippetMatch = null,
                sourceProvider = "Kugou",
                confidenceScore = 0.0,
                youtubeSearchQuery = "${song.songname} ${song.singername}"
            )
        }
    }.getOrElse { emptyList() }

    private suspend fun searchBetterLyrics(query: String): List<NormalizedLyricsResult> = runCatching {
        val ttmlResponse = BetterLyrics.fetchTTML(artist = "", title = query) ?: return emptyList()
        val parsed = TTMLParser.parseTTML(ttmlResponse)
        val lrc = TTMLParser.toLRC(parsed)
        listOf(
            NormalizedLyricsResult(
                title = query, // BetterLyrics doesn't return metadata
                artist = "Unknown",
                album = null,
                lyricsSnippetMatch = lrc,
                sourceProvider = "BetterLyrics",
                confidenceScore = 0.0,
                youtubeSearchQuery = query
            )
        )
    }.getOrElse { emptyList() }

    private suspend fun searchSimpMusic(query: String): List<NormalizedLyricsResult> = runCatching {
        // SimpMusic only takes a videoId, so we do a quick Innertube text search to get a videoId
        val sr = com.noxwizard.resonix.innertube.YouTube.searchSummary(query).getOrNull() ?: return emptyList()
        val topSong = sr.summaries
            .firstOrNull { summary -> summary.items.any { it is com.noxwizard.resonix.innertube.models.SongItem } }
            ?.items
            ?.filterIsInstance<com.noxwizard.resonix.innertube.models.SongItem>()
            ?.firstOrNull() ?: return emptyList()

        val lyricsResp = com.noxwizard.resonix.lyrics.simpmusic.SimpMusicLyrics.getLyrics(topSong.id, 0).getOrNull()
        if (!lyricsResp.isNullOrEmpty()) {
            listOf(
                NormalizedLyricsResult(
                    title = topSong.title,
                    artist = topSong.artists.joinToString { it.name },
                    album = topSong.album?.name,
                    lyricsSnippetMatch = lyricsResp,
                    sourceProvider = "SimpMusic",
                    confidenceScore = 0.0,
                    youtubeSearchQuery = "${topSong.title} ${topSong.artists.joinToString { it.name }}"
                )
            )
        } else emptyList()
    }.getOrElse { emptyList() }

    private suspend fun searchLyricsPlus(query: String): List<NormalizedLyricsResult> = runCatching {
        val resp = LyricsPlusProvider.fetchLyrics(title = query, artist = "", duration = -1)
        val lyricsText = resp?.lyrics?.joinToString(" ") { it.text }

        if (!lyricsText.isNullOrEmpty()) {
            listOf(
                NormalizedLyricsResult(
                    title = query,
                    artist = "Unknown",
                    album = null,
                    lyricsSnippetMatch = lyricsText,
                    sourceProvider = "LyricsPlus",
                    confidenceScore = 0.0,
                    youtubeSearchQuery = query
                )
            )
        } else emptyList()
    }.getOrElse { emptyList() }

    private fun calculateSnippetMatchScore(fullLyrics: String, snippet: String): Double {
        if (fullLyrics.isBlank()) return 0.0
        val normalizedFull = fullLyrics.lowercase(Locale.getDefault()).replace("[^a-z0-9]".toRegex(), " ")
        val normalizedSnippet = snippet.lowercase(Locale.getDefault()).replace("[^a-z0-9]".toRegex(), " ")
        
        val snippetWords = normalizedSnippet.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (snippetWords.isEmpty()) return 0.0

        var matchCount = 0
        snippetWords.forEach { word ->
            if (normalizedFull.contains(word)) matchCount++
        }
        
        return matchCount.toDouble() / snippetWords.size
    }
}
