package com.noxwizard.resonix.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.noxwizard.resonix.LocalDatabase
import com.noxwizard.resonix.R
import com.noxwizard.resonix.extensions.toMediaItem
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.PlaylistItem
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.innertube.models.WatchEndpoint
import com.noxwizard.resonix.innertube.models.YTItem
import com.noxwizard.resonix.innertube.utils.completed
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playback.PlayerConnection
import com.noxwizard.resonix.playback.queues.YouTubeQueue
import com.noxwizard.resonix.ui.component.shimmer.ShimmerHost
import com.noxwizard.resonix.ui.component.shimmer.TextPlaceholder
import com.noxwizard.resonix.ui.menu.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CommunityPlaylistCard(
    item: YTItem,
    navController: NavController,
    playerConnection: PlayerConnection,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var songs by remember { mutableStateOf<List<SongItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val database = LocalDatabase.current
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    // AddToPlaylistDialog handles inserting songs and mapping to playlists.
    // onGetSong fetches the full playlist songs, inserts them into DB, then returns their IDs.
    // The dialog itself (not us) calls database.addSongToPlaylist for each selected playlist.
    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = { _ ->
            val resolvedSongs = withContext(Dispatchers.IO) {
                YouTube.playlist(item.id).completed().getOrNull()?.songs.orEmpty()
                    .map { it.toMediaMetadata() }
            }
            database.transaction {
                resolvedSongs.forEach { song -> insert(song) }
            }
            resolvedSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    LaunchedEffect(item.id) {
        if (item is PlaylistItem) {
            val result = withContext(Dispatchers.IO) {
                YouTube.playlist(item.id)
            }
            result.onSuccess { page ->
                songs = page.songs.take(3)
            }
            isLoading = false
        } else {
            isLoading = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (item is PlaylistItem) {
                    navController.navigate("online_playlist/${item.id}")
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.thumbnail)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val playlistItem = item as? PlaylistItem
                    val authorName = playlistItem?.author?.name
                    val songCount = playlistItem?.songCountText

                    if (authorName != null) {
                        Text(
                            text = authorName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (songCount != null) {
                        Text(
                            text = songCount,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Song list preview
            if (isLoading) {
                ShimmerHost {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Gray)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    TextPlaceholder(height = 14.dp, modifier = Modifier.width(120.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    TextPlaceholder(height = 12.dp, modifier = Modifier.width(80.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                songs?.takeIf { it.isNotEmpty() }?.let { validSongs ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        validSongs.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                song.toMediaMetadata()
                                            )
                                        )
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(song.thumbnail)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artists.joinToString { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val playlistItem = item as? PlaylistItem
                if (playlistItem != null) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            playlistItem.playEndpoint?.let { endpoint ->
                                playerConnection.playQueue(YouTubeQueue(endpoint))
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(painterResource(R.drawable.play), contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.IconButton(
                        onClick = {
                            playlistItem.radioEndpoint?.let { endpoint ->
                                playerConnection.playQueue(YouTubeQueue(endpoint))
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(painterResource(R.drawable.radio), contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.IconButton(
                        onClick = { showChoosePlaylistDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(painterResource(R.drawable.playlist_add), contentDescription = null)
                    }
                }
            }
        }
    }
}
