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
            ListenTogetherManager.roomState.collect { roomState ->
                updateCanSkipPreviousAndNext()
                updateCanControl()
                // Apply remote room volume (host-set global volume)
                val remoteVolume = roomState?.globalVolume ?: 1f
                if (remoteVolume != _roomVolume) {
                    _roomVolume = remoteVolume
                    player.volume = if (isMuted) 0f else _roomVolume
                }
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
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
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



