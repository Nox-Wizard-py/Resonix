package com.noxwizard.resonix.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.extensions.currentMetadata
import com.noxwizard.resonix.extensions.getCurrentQueueIndex
import com.noxwizard.resonix.extensions.getQueueWindows
import com.noxwizard.resonix.extensions.metadata
import com.noxwizard.resonix.playback.MusicService.MusicBinder
import com.noxwizard.resonix.playback.queues.Queue
import com.noxwizard.resonix.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import com.noxwizard.resonix.ui.screens.PlaybackPermission
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player
    private val appContext = context.applicationContext
    private val connectionScope = scope

    private var _roomVolume: Float = 1f
    val roomVolume: Float get() = _roomVolume
    var isMuted: Boolean = false
    val isMutedFlow = MutableStateFlow(false)
    @Volatile private var isApplyingRemoteTrackChange = false

    fun setRoomVolume(value: Float) {
        if (blockIfGuest()) return
        _roomVolume = value
        player.volume = if (isMuted) 0f else _roomVolume
        ListenTogetherManager.updateGlobalVolume(value)
    }

    fun toggleMuteLocal() {
        isMuted = !isMuted
        isMutedFlow.value = isMuted
        player.volume = if (isMuted) 0f else _roomVolume
    }

    fun stopLocallyWithoutBroadcast() {
        isApplyingRemoteTrackChange = true
        service.clearAutomix()
        player.stop()
        player.clearMediaItems()
        isApplyingRemoteTrackChange = false
    }

    private fun blockIfGuest(): Boolean {
        if (!ListenTogetherManager.canControlPlayback()) {
            connectionScope.launch(Dispatchers.Main) {
                Toast.makeText(appContext, "Playback controlled by host", Toast.LENGTH_SHORT).show()
            }
            return true
        }
        return false
    }

    val playbackState = MutableStateFlow(player.playbackState)
    private val playWhenReady = MutableStateFlow(player.playWhenReady)
    val isPlaying =
        combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
            playWhenReady && playbackState != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            player.playWhenReady && player.playbackState != STATE_ENDED
        )
    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)
    val canControlFlow = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)
    val waitingForNetworkConnection = service.waitingForNetworkConnection

    val isWaitingForGuests = MutableStateFlow(false)
    private var guestWaitingForTrackId: String? = null
    private var hostWaitingForTrackId: String? = null
    private var hostIsReadyForTrack: String? = null
    private val readyGuests = mutableSetOf<String>()
    private val requiredGuests = mutableSetOf<String>()
    private var playTimeoutJob: kotlinx.coroutines.Job? = null

    init {
        player.addListener(this)

        playbackState.value = player.playbackState
        playWhenReady.value = player.playWhenReady
        mediaMetadata.value = player.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        shuffleModeEnabled.value = player.shuffleModeEnabled
        repeatMode.value = player.repeatMode

        connectionScope.launch {
            android.util.Log.d("PLAYER", "[TrackChange] Collector coroutine started")
            ListenTogetherManager.roomState.collect { roomState ->
                updateCanSkipPreviousAndNext()
                updateCanControl()
                // Apply remote room volume (host-set global volume)
                val remoteVolume = roomState?.globalVolume ?: 1f
                if (remoteVolume != _roomVolume) {
                    _roomVolume = remoteVolume
                    player.volume = if (isMuted) 0f else _roomVolume
                }
                if (isWaitingForGuests.value && roomState != null) {
                    val currentGuests = roomState.users.filter { it.role == "guest" }.map { it.userId }
                    requiredGuests.retainAll(currentGuests.toSet())
                    checkHostAndGuestsReady()
                }
                // Start / stop NTP clock probing with room lifecycle
                if (roomState != null) {
                    PlaybackSyncCoordinator.init(
                        coroutineScope = connectionScope,
                        playerProvider = { player as androidx.media3.exoplayer.ExoPlayer },
                        isPlayingProvider = { player.isPlaying },
                    )
                    if (!PlaybackSyncCoordinator.isSynced) PlaybackSyncCoordinator.startProbing()
                } else {
                    PlaybackSyncCoordinator.stop()
                }
            }
        }

        connectionScope.launch {
            android.util.Log.d("PLAYER", "[TrackChange] Collector coroutine started")
            SocketListenTogetherRepository.playbackEvents.collect { event ->
                android.util.Log.d("PLAYER", "[TrackChange] Received event: $event")
                if (event is PlaybackEvent.TrackChange) {
                    android.util.Log.d("PLAYER", "TrackChange triggered for trackId=${event.trackId} url=${event.url}")
                    if (!ListenTogetherManager.canControlPlayback()) {
                        val currentId = player.currentMediaItem?.mediaId
                        if (currentId != event.trackId) {
                            val resonixMetadata = com.noxwizard.resonix.models.MediaMetadata(
                                id = event.trackId,
                                title = event.title,
                                artists = listOf(
                                    com.noxwizard.resonix.models.MediaMetadata.Artist(
                                        id = null,
                                        name = event.artist
                                    )
                                ),
                                duration = -1,
                                thumbnailUrl = event.thumbnailUrl.ifEmpty { null }
                            )
                            val mediaItem = MediaItem.Builder()
                                .setMediaId(event.trackId)
                                .setUri(event.trackId) // Force local stream resolution via trackId
                                .setCustomCacheKey(event.trackId)
                                .setTag(resonixMetadata)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(event.title)
                                        .setArtist(event.artist)
                                        .setArtworkUri(if (event.thumbnailUrl.isNotEmpty()) android.net.Uri.parse(event.thumbnailUrl) else null)
                                        .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .build()
                                )
                                .build()

                            isApplyingRemoteTrackChange = true
                            withContext(Dispatchers.Main) {
                                guestWaitingForTrackId = event.trackId
                                isWaitingForGuests.value = true
                                player.playWhenReady = false

                                val queueSize = player.mediaItemCount
                                var targetIndex = -1
                                for (i in 0 until queueSize) {
                                    if (player.getMediaItemAt(i).mediaId == event.trackId) {
                                        targetIndex = i
                                        break
                                    }
                                }

                                if (targetIndex >= 0) {
                                    player.seekToDefaultPosition(targetIndex)
                                    player.prepare()
                                } else {
                                    player.stop()
                                    player.clearMediaItems()
                                    player.setMediaItem(mediaItem)
                                    player.prepare()
                                }
                                
                                if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
                                    guestWaitingForTrackId = null
                                    SocketListenTogetherRepository.sendTrackReady(event.trackId)
                                }
                            }
                            isApplyingRemoteTrackChange = false

                            android.util.Log.d("PLAYER", "Guest started playback for ${event.trackId}")
                        } else {
                            android.util.Log.d("PLAYER", "TrackChange skipped - same trackId already loaded")
                            isApplyingRemoteTrackChange = true
                            withContext(Dispatchers.Main) {
                                guestWaitingForTrackId = event.trackId
                                isWaitingForGuests.value = true
                                player.playWhenReady = false
                                player.prepare()
                                
                                if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
                                    guestWaitingForTrackId = null
                                    SocketListenTogetherRepository.sendTrackReady(event.trackId)
                                }
                            }
                            isApplyingRemoteTrackChange = false
                        }
                    } else {
                        android.util.Log.d("PLAYER", "TrackChange skipped - local user is host/control")
                    }
                } else if (event is PlaybackEvent.Seek) {
                    if (!ListenTogetherManager.canControlPlayback()) {
                        val corrected = PlaybackSyncCoordinator.correctedPosition(event.positionMs, event.timestamp)
                        withContext(Dispatchers.Main) {
                            player.seekTo(corrected)
                        }
                    }
                } else if (event is PlaybackEvent.PlayAt) {
                    if (!ListenTogetherManager.canControlPlayback()) {
                        val waitMs = PlaybackSyncCoordinator.estimatedWaitMs(event.startTime)
                        withContext(Dispatchers.Main) {
                            when {
                                waitMs > 50 -> {
                                    // Enough headroom — schedule future play
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        isWaitingForGuests.value = false
                                        playbackState.value = player.playbackState
                                        player.playWhenReady = true
                                    }, waitMs)
                                }
                                waitMs < -50 -> {
                                    // Already late — seek forward by the missed amount
                                    player.seekTo((-waitMs).coerceAtLeast(0L))
                                    isWaitingForGuests.value = false
                                    playbackState.value = player.playbackState
                                    player.playWhenReady = true
                                }
                                else -> {
                                    // Within ±50 ms tolerance — play immediately
                                    isWaitingForGuests.value = false
                                    playbackState.value = player.playbackState
                                    player.playWhenReady = true
                                }
                            }
                        }
                    }
                } else if (event is PlaybackEvent.PauseAt) {
                    if (!ListenTogetherManager.canControlPlayback()) {
                        val corrected = PlaybackSyncCoordinator.correctedPosition(event.positionMs, event.timestamp)
                        withContext(Dispatchers.Main) {
                            player.seekTo(corrected)
                            player.pause()
                        }
                    }
                } else if (event is PlaybackEvent.SyncUpdate) {
                    if (!ListenTogetherManager.canControlPlayback()) {
                        PlaybackSyncCoordinator.applyDriftCorrection(
                            expectedPositionMs = event.positionMs,
                            serverTimeToExecute = event.serverTimeToExecute,
                        )
                    }
                } else if (event is PlaybackEvent.SyncSnapshot) {
                    // SyncSnapshot is handled exclusively by MusicService which builds
                    // the MediaItem correctly with setCustomCacheKey + setUri(trackId).
                    // Do NOT duplicate here — it causes a "No media id" crash because
                    // the raw stream URL cannot be set as the URI for ResolvingDataSource.
                    android.util.Log.d("PLAYER", "[SyncSnapshot] delegated to MusicService handler — skipping PlayerConnection handling")
                } else if (event is PlaybackEvent.TrackReady) {
                    if (ListenTogetherManager.canControlPlayback()) {
                        if (event.trackId == hostIsReadyForTrack || event.trackId == hostWaitingForTrackId) {
                            readyGuests.add(event.userId)
                            checkHostAndGuestsReady()
                        }
                    }
                } else if (event is PlaybackEvent.Stop) {
                    withContext(Dispatchers.Main) {
                        playTimeoutJob?.cancel()
                        isWaitingForGuests.value = false
                        guestWaitingForTrackId = null
                        stopLocallyWithoutBroadcast()
                    }
                } else if (event is PlaybackEvent.QueueUpdate) {
                    if (!ListenTogetherManager.canControlPlayback() && event.queue.isNotEmpty()) {
                        val currentTrackId = player.currentMediaItem?.mediaId
                        val currentPos = player.currentPosition

                        val mediaItems = event.queue.map { item ->
                            val meta = com.noxwizard.resonix.models.MediaMetadata(
                                id = item.trackId,
                                title = item.title,
                                artists = listOf(
                                    com.noxwizard.resonix.models.MediaMetadata.Artist(
                                        id = null,
                                        name = item.artist
                                    )
                                ),
                                duration = -1,
                                thumbnailUrl = item.thumbnailUrl.ifEmpty { null }
                            )
                            MediaItem.Builder()
                                .setMediaId(item.trackId)
                                .setUri(item.trackId)
                                .setCustomCacheKey(item.trackId)
                                .setTag(meta)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(item.title)
                                        .setArtist(item.artist)
                                        .setArtworkUri(if (item.thumbnailUrl.isNotEmpty()) android.net.Uri.parse(item.thumbnailUrl) else null)
                                        .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                                        .build()
                                )
                                .build()
                        }

                        val targetIndex = event.queue.indexOfFirst { it.trackId == currentTrackId }
                            .takeIf { it >= 0 } ?: 0

                        withContext(Dispatchers.Main) {
                            isApplyingRemoteTrackChange = true
                            player.setMediaItems(mediaItems, targetIndex, currentPos)
                            if (guestWaitingForTrackId != null) {
                                player.prepare()
                            }
                            isApplyingRemoteTrackChange = false
                        }
                        android.util.Log.d("PLAYER", "QueueUpdate applied: ${event.queue.size} tracks, resuming at index=$targetIndex")
                    }
                }
            }
        }

        // Host: broadcast current position every 2.5 s so guests can drift-correct
        connectionScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(2500)
                val room = ListenTogetherManager.roomState.value ?: continue
                if (!ListenTogetherManager.canControlPlayback()) continue
                
                val (playing, pos) = withContext(Dispatchers.Main) {
                    player.isPlaying to player.currentPosition
                }
                
                if (!playing) continue
                SocketListenTogetherRepository.sendSyncUpdate(pos)
            }
        }
    }

    private fun checkHostAndGuestsReady() {
        if (hostIsReadyForTrack != null) {
            if (readyGuests.containsAll(requiredGuests)) {
                forcePlayTrack(hostIsReadyForTrack!!)
            }
        }
    }

    private fun forcePlayTrack(trackId: String) {
        if (isWaitingForGuests.value) {
            playTimeoutJob?.cancel()

            val startTime = System.currentTimeMillis() + 500
            SocketListenTogetherRepository.sendPlayAt(startTime)

            val delay = startTime - System.currentTimeMillis()
            if (delay > 0) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isWaitingForGuests.value = false
                    playbackState.value = player.playbackState
                    player.playWhenReady = true
                }, delay)
            } else {
                isWaitingForGuests.value = false
                playbackState.value = player.playbackState
                player.playWhenReady = true
            }
        }
    }

    private fun updateCanControl() {
        canControlFlow.value = ListenTogetherManager.canControlPlayback()
    }

    fun playQueue(queue: Queue) {
        if (blockIfGuest()) return
        service.playQueue(queue)
    }

    fun startRadioSeamlessly() {
        service.startRadioSeamlessly()
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        if (blockIfGuest()) return
        service.playNext(items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        if (blockIfGuest()) return
        service.addToQueue(items)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    fun seekToNext() {
        if (blockIfGuest()) return
        player.seekToNext()
        player.prepare()
        player.playWhenReady = true
        try {
            com.noxwizard.resonix.ui.screens.settings.DiscordPresenceManager.restart()
        } catch (_: Exception) {
        }
    }

    fun seekToPrevious() {
        if (blockIfGuest()) return
        player.seekToPrevious()
        player.prepare()
        player.playWhenReady = true
        try {
            com.noxwizard.resonix.ui.screens.settings.DiscordPresenceManager.restart()
        } catch (_: Exception) {
        }
    }

    fun togglePlayPause() {
        if (blockIfGuest()) return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun play() {
        if (blockIfGuest()) return
        player.play()
    }

    fun pause() {
        if (blockIfGuest()) return
        player.pause()
    }

    fun setShuffleModeEnabled(enabled: Boolean) {
        if (blockIfGuest()) return
        player.shuffleModeEnabled = enabled
    }

    fun toggleRepeatMode() {
        if (blockIfGuest()) return
        val newMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = newMode
    }

    fun setRepeatMode(mode: Int) {
        if (blockIfGuest()) return
        player.repeatMode = mode
    }

    fun seekTo(position: Long) {
        if (blockIfGuest()) return
        player.seekTo(position)
        ListenTogetherManager.broadcastSeek(position)
    }

    fun seekTo(windowIndex: Int, positionMs: Long) {
        if (blockIfGuest()) return
        player.seekTo(windowIndex, positionMs)
    }

    fun seekToDefaultPosition(windowIndex: Int) {
        if (blockIfGuest()) return
        player.seekToDefaultPosition(windowIndex)
    }

    fun setPlayWhenReady(playWhenReady: Boolean) {
        if (blockIfGuest()) return
        player.playWhenReady = playWhenReady
    }

    fun seekToPreviousMediaItem() {
        if (blockIfGuest()) return
        player.seekToPreviousMediaItem()
    }

    fun removeMediaItem(index: Int) {
        if (blockIfGuest()) return
        player.removeMediaItem(index)
    }

    fun addMediaItem(item: MediaItem) {
        if (blockIfGuest()) return
        player.addMediaItem(item)
    }

    fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        if (blockIfGuest()) return
        player.moveMediaItem(currentIndex, newIndex)
    }

    fun setShuffleOrder(shuffleOrder: DefaultShuffleOrder) {
        if (blockIfGuest()) return
        (player as? ExoPlayer)?.setShuffleOrder(shuffleOrder)
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (isWaitingForGuests.value) {
            playbackState.value = androidx.media3.common.Player.STATE_BUFFERING
        } else {
            playbackState.value = state
        }
        error.value = player.playerError

        val currentId = player.currentMediaItem?.mediaId ?: return
        if (state == androidx.media3.common.Player.STATE_READY) {
            if (!ListenTogetherManager.canControlPlayback()) {
                if (currentId == guestWaitingForTrackId) {
                    guestWaitingForTrackId = null
                    SocketListenTogetherRepository.sendTrackReady(currentId)
                }
            } else {
                if (currentId == hostWaitingForTrackId) {
                    hostWaitingForTrackId = null
                    hostIsReadyForTrack = currentId
                    checkHostAndGuestsReady()
                }
            }
        }
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        if (newPlayWhenReady && isWaitingForGuests.value) {
            player.playWhenReady = false
            return
        }
        
        playWhenReady.value = newPlayWhenReady
        if (!isApplyingRemoteTrackChange && ListenTogetherManager.roomState.value != null && ListenTogetherManager.canControlPlayback()) {
            if (!newPlayWhenReady) {
                if (!isWaitingForGuests.value && reason != androidx.media3.common.Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    SocketListenTogetherRepository.sendPauseAt(player.currentPosition)
                }
            } else {
                if (!isWaitingForGuests.value) {
                    val trackId = player.currentMediaItem?.mediaId ?: ""
                    ListenTogetherManager.broadcastPlay(trackId, player.currentPosition)
                }
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()

        if (!isApplyingRemoteTrackChange && ListenTogetherManager.roomState.value != null && ListenTogetherManager.canControlPlayback() && mediaItem != null) {
            val metadata = mediaItem.metadata
            if (metadata != null) {
                android.util.Log.d("PLAYER", "[TrackChange] Host broadcasting trackId=${metadata.id} url=${mediaItem.localConfiguration?.uri}")
                val fallbackThumbnail = mediaItem.mediaMetadata.artworkUri?.toString() ?: ""
                ListenTogetherManager.broadcastTrackChange(
                    trackId = metadata.id,
                    url = mediaItem.localConfiguration?.uri?.toString() ?: "",
                    title = metadata.title,
                    artist = metadata.artists.firstOrNull()?.name ?: "",
                    thumbnailUrl = metadata.thumbnailUrl ?: fallbackThumbnail,
                    startAt = 0 // Deprecated/ignored
                )

                val room = ListenTogetherManager.roomState.value
                val guests = room?.users?.filter { it.role == "guest" }?.map { it.userId } ?: emptyList()

                if (guests.isNotEmpty()) {
                    isWaitingForGuests.value = true
                    playbackState.value = androidx.media3.common.Player.STATE_BUFFERING
                    player.playWhenReady = false

                    hostWaitingForTrackId = metadata.id
                    hostIsReadyForTrack = null
                    readyGuests.clear()
                    requiredGuests.clear()
                    requiredGuests.addAll(guests)

                    if (player.playbackState == androidx.media3.common.Player.STATE_READY) {
                        hostWaitingForTrackId = null
                        hostIsReadyForTrack = metadata.id
                        checkHostAndGuestsReady()
                    }

                    playTimeoutJob?.cancel()
                    playTimeoutJob = connectionScope.launch {
                        kotlinx.coroutines.delay(10000)
                        if (isWaitingForGuests.value) {
                            val trackId = hostWaitingForTrackId ?: hostIsReadyForTrack
                            if (trackId != null) {
                                hostIsReadyForTrack = trackId
                                forcePlayTrack(trackId)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()

        // Broadcast queue snapshot to guests when host's queue changes
        if (!isApplyingRemoteTrackChange &&
            ListenTogetherManager.roomState.value != null &&
            ListenTogetherManager.canControlPlayback()
        ) {
            val sharedQueue = buildSharedQueue()
            // Prevent guests from wiping the room queue when they close their local player
            if (sharedQueue.isNotEmpty() || ListenTogetherManager.isHostOrSudo()) {
                ListenTogetherManager.broadcastQueueUpdate(sharedQueue)
            }
        }
    }

    private fun buildSharedQueue(): List<SharedQueueItem> {
        val count = player.mediaItemCount
        val items = mutableListOf<SharedQueueItem>()
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            val meta = item.metadata
            if (meta != null) {
                items.add(
                    SharedQueueItem(
                        trackId = item.mediaId,
                        url = item.localConfiguration?.uri?.toString() ?: "",
                        title = meta.title,
                        artist = meta.artists.firstOrNull()?.name ?: "",
                        thumbnailUrl = meta.thumbnailUrl ?: item.mediaMetadata.artworkUri?.toString() ?: ""
                    )
                )
            }
        }
        return items
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        val roomState = com.noxwizard.resonix.playback.ListenTogetherManager.roomState.value
        val canControl = if (roomState != null) {
            val user = roomState.users.find { it.userId == com.noxwizard.resonix.playback.ListenTogetherManager.localUserId }
            user != null && (user.isHost || user.hasTempControl || roomState.playbackPermission == com.noxwizard.resonix.ui.screens.PlaybackPermission.Everyone)
        } else true

        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = canControl && (player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            canSkipNext.value = canControl && (window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        player.removeListener(this)
    }
}



