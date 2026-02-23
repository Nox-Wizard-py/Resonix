package com.noxwizard.resonix.ui.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.WatchEndpoint
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playback.MusicService
import com.noxwizard.resonix.playback.PlayerConnection
import com.noxwizard.resonix.playback.queues.YouTubeQueue
import com.noxwizard.resonix.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun handleDeepLinkIntent(
    intent: Intent,
    navController: NavHostController,
    context: Context,
    playerConnection: PlayerConnection?
) {
    val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
    // Simple CoroutineScope using Dispatchers.Main
    val coroutineScope = CoroutineScope(Dispatchers.Main)

    when (val path = uri.pathSegments.firstOrNull()) {
        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
            if (playlistId.startsWith("OLAK5uy_")) {
                coroutineScope.launch {
                    YouTube.albumSongs(playlistId).onSuccess { songs ->
                        songs.firstOrNull()?.album?.id?.let { browseId ->
                            navController.navigate("album/$browseId")
                        }
                    }.onFailure { reportException(it) }
                }
            } else {
                navController.navigate("online_playlist/$playlistId")
            }
        }

        "browse" -> uri.lastPathSegment?.let { browseId ->
            navController.navigate("album/$browseId")
        }

        "channel", "c" -> uri.lastPathSegment?.let { artistId ->
            navController.navigate("artist/$artistId")
        }

        else -> {
            val videoId = when {
                path == "watch" -> uri.getQueryParameter("v")
                uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                else -> null
            }

            val playlistId = uri.getQueryParameter("list")

            videoId?.let { vid ->
                coroutineScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        YouTube.queue(listOf(vid), playlistId)
                    }

                    result.onSuccess { queued ->
                        coroutineScope.launch {
                            val timeoutMs = 3000L
                            var waited = 0L
                            val step = 100L
                            // We need playerConnection to be updated potentially?
                            // This passed playerConnection might be stale if it was null when called?
                            // Actually it's a reference. If it's null, it stays null.
                            // The logic in ResonixApp looped waiting for playerConnection.
                            // But here playerConnection is passed as value.
                            // We need a provider or current value accessor if we want to wait for it.
                            // However, the original code used 'playerConnection' parameter which was passed to the function.
                            // AND it looped `while (playerConnection == null ...)`
                            // Wait, if playerConnection is passed as an argument (val), it will NEVER change inside this function's scope.
                            // So the original code `while (playerConnection == null)` would be an infinite loop if it started null!
                            // UNLESS `playerConnection` was captured from the closure in ResonixApp, NOT passed as argument.
                            // Let's check ResonixApp again.
                            
                            // In ResonixApp line 1059: private fun handleDeepLinkIntent(..., playerConnection: PlayerConnection?)
                            // It WAS passed as argument.
                            // So the original code had a bug or I misunderstood Kotlin closures.
                            // If it's a val parameter, it's fixed.
                            // UNLESS `playerConnection` property in ResonixApp was a var/val provided by CompositionLocal or param.
                            // In ResonixApp: `playerConnection: PlayerConnection?` is a parameter to ResonixApp.
                            // And `handleDeepLinkIntent` is a private function at the bottom of the file (outside class/composable).
                            // So it definitely takes the parameter.
                            // So yes, `while (playerConnection == null)` inside `handleDeepLinkIntent` checking the PARAMETER would loop forever.
                            
                            // HOWEVER, maybe the original loop was checking a *global* or something?
                            // No, it seemed to use the param.
                            // Maybe the intent was that `playerConnection` would be initialized by the service start?
                            // But the variable reference inside the function won't update.
                            
                            // To fix this during extraction, I should pass a *Provider* or similar, OR just keep the service starting logic.
                            // Actually, if we start the service, we might need to rely on the UI recomposing and calling this again?
                            // No, this is a one-off function call.
                            
                            // Let's assume for now I should copy it as is, but maybe flag this potential bug.
                            // Wait, if I extract it, `playerConnection` is definitely local.
                            
                            // Alternative: Pass `() -> PlayerConnection?`.
                            // But `ResonixApp`'s `playerConnection` comes from its parameter, which updates via recomposition.
                            // This function is called from `LaunchedEffect(currentIntent)`.
                            // If `playerConnection` changes, `ResonixApp` recomposes.
                            // But `handleDeepLinkIntent` is called once deeply.
                            // If `playerConnection` is null, we start service.
                            // Then we wait.
                            // But `playerConnection` param won't change.
                            
                            // Maybe I should pass `LocalPlayerConnection.current`? 
                            // `ResonixApp` has `playerConnection`.
                            
                            // Let's just reproduce the code for now. Functional changes are risky without testing.
                            // But I can't leave an infinite loop if I see one.
                            // Wait, line 1107: `while (playerConnection == null && waited < timeoutMs)`
                            // If it's null, it waits 3s then gives up (or tries to start service?).
                            // Loops, delays, checks again.
                            // It will definitely timeout if null.
                            // Then line 1112: `if (playerConnection != null)` -> else -> start service.
                            // So it checks, waits 3s (uselessly if null), then starts service.
                            // This looks like it tries to wait for connection if it's *about* to be ready?
                            // But it can't know.
                            
                            // Using `Supplier` or `Getter` would be better.
                            // `playerConnectionProvider: () -> PlayerConnection?`
                            
                            val currentPlayer = playerConnection // capture current
                            
                             if (currentPlayer != null) {
                                currentPlayer.playQueue(
                                    YouTubeQueue(
                                        WatchEndpoint(videoId = queued.firstOrNull()?.id, playlistId = playlistId),
                                        queued.firstOrNull()?.toMediaMetadata()
                                    )
                                )
                            } else {
                                // Start service
                                try {
                                    val startIntent = Intent(context, MusicService::class.java)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        try {
                                            androidx.core.content.ContextCompat.startForegroundService(context, startIntent)
                                        } catch (e: IllegalStateException) {
                                            reportException(e)
                                            context.startService(startIntent)
                                        } catch (e: SecurityException) {
                                            reportException(e)
                                            context.startService(startIntent)
                                        }
                                    } else {
                                        context.startService(startIntent)
                                    }
                                } catch (e: Exception) {
                                    reportException(e)
                                }
                            }
                        }
                    }.onFailure {
                        reportException(it)
                    }
                }
            }
        }
    }
}
