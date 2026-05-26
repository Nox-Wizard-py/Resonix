package com.noxwizard.resonix.lyrics.engine

import android.util.Log
import com.noxwizard.resonix.models.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsPreloadManager @Inject constructor(
    private val unifiedLyricsEngine: UnifiedLyricsEngine,
    private val lyricsCacheManager: LyricsCacheManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preloadJob: Job? = null
    private val preloadedIds = mutableSetOf<String>()

    /**
     * Called whenever the playback queue changes.
     * Evaluates the next [tracks] and preloads their lyrics.
     */
    fun onUpcomingTracksChanged(tracks: List<MediaMetadata>) {
        preloadJob?.cancel()
        
        preloadJob = scope.launch {
            for (track in tracks.take(3)) {
                if (preloadedIds.contains(track.id)) continue
                
                // Check cache first
                val cached = lyricsCacheManager.getLyrics(track.id)
                if (cached != null) {
                    preloadedIds.add(track.id)
                    continue
                }

                try {
                    Log.d("LyricsPreloadManager", "Preloading lyrics for: ${track.title}")
                    val document = unifiedLyricsEngine.resolveLyrics(track)
                    if (document != null) {
                        lyricsCacheManager.putLyrics(track.id, document)
                    } else {
                        lyricsCacheManager.markNotFound(track.id)
                    }
                    preloadedIds.add(track.id)
                } catch (e: Exception) {
                    Log.e("LyricsPreloadManager", "Failed to preload lyrics for ${track.title}", e)
                }
            }
        }
    }
    
    fun clearSession() {
        preloadedIds.clear()
        preloadJob?.cancel()
    }
}
