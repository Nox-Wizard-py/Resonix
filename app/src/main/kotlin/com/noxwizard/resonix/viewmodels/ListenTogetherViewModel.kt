package com.noxwizard.resonix.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.db.entities.Song
import com.noxwizard.resonix.playback.ListenTogetherManager
import com.noxwizard.resonix.playback.PlaybackEvent
import com.noxwizard.resonix.playback.PlayerConnection
import com.noxwizard.resonix.playback.RoomEvent
import com.noxwizard.resonix.playback.SocketListenTogetherRepository
import com.noxwizard.resonix.ui.screens.PlaybackPermission
import com.noxwizard.resonix.ui.screens.RoomUser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoomUiState(
    val roomCode: String,
    val hostId: String,
    val connectedUsers: List<RoomUser>,
    val currentTrack: Song?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val globalVolume: Float,
    val timingOffsetMs: Int,
    val playbackPermission: PlaybackPermission,
    val isLocalUserHost: Boolean,
    val rtt: String = "24ms",
    val offset: String = "+0ms",
    val ntpSynced: String = "Yes"
)

/**
 * ViewModel for the Listen Together room.
 *
 * Observes [SocketListenTogetherRepository] for real-time room state changes
 * including presence (room_updated) and playback commands (playback_sync).
 *
 * PlayerConnection cannot be Hilt-injected (it's a CompositionLocal).
 * The screen must call [bindPlayerConnection] once it has access to a non-null
 * PlayerConnection, after which playback sync events will function correctly.
 */
@HiltViewModel
class ListenTogetherViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val roomState = ListenTogetherManager.roomState
    private var playerConnection: PlayerConnection? = null

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val playerListener = object : androidx.media3.common.Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isApplyingRemoteEvent) return
            if (!ListenTogetherManager.canControlPlayback()) return

            val pc = playerConnection ?: return
            val position = pc.player.currentPosition
            
            if (isPlaying) {
                ListenTogetherManager.broadcastPlay(pc.player.currentMediaItem?.mediaId ?: "", position)
            } else {
                ListenTogetherManager.broadcastPause(position)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: androidx.media3.common.Player.PositionInfo,
            newPosition: androidx.media3.common.Player.PositionInfo,
            reason: Int
        ) {
            if (isApplyingRemoteEvent) return
            if (!ListenTogetherManager.canControlPlayback()) return

            if (reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK || 
                reason == androidx.media3.common.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                ListenTogetherManager.broadcastSeek(newPosition.positionMs)
            }
        }
        
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            if (isApplyingRemoteEvent) return
            if (!ListenTogetherManager.canControlPlayback()) return
            
            val pc = playerConnection ?: return
            if (pc.player.isPlaying) {
                ListenTogetherManager.broadcastPlay(mediaItem?.mediaId ?: "", pc.player.currentPosition)
            }
        }
    }

    private var isApplyingRemoteEvent = false

    // Navigation events for UI to react to (room closed, kicked)
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>(replay = 0)
    val navigationEvent = _navigationEvent.asSharedFlow()

    // Snackbar events for username duplicate notifications
    private val _snackbarEvent = MutableSharedFlow<String>(replay = 0)
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    sealed class NavigationEvent {
        object RoomClosed : NavigationEvent()
        object Kicked : NavigationEvent()
        object RoomNotFound : NavigationEvent()
    }

    /** Called from the composable once LocalPlayerConnection.current is available. */
    fun bindPlayerConnection(pc: PlayerConnection) {
        if (playerConnection == pc) return
        playerConnection?.player?.removeListener(playerListener)
        playerConnection = pc
        pc.player.addListener(playerListener)
    }

    // Map the socket room state to UI state
    val uiState: StateFlow<RoomUiState?> = roomState.combine(
        MutableStateFlow(Unit)
    ) { state, _ ->
        if (state == null) return@combine null
        val localId = ListenTogetherManager.localUserId
        val isLocalHost = state.users.find { it.userId == localId }?.isHost == true

        RoomUiState(
            roomCode = state.roomCode,
            hostId = state.hostId,
            connectedUsers = state.users.map { u ->
                RoomUser(
                    userId = u.userId,
                    username = u.username.ifEmpty { "Unknown" },
                    isHost = u.isHost,
                    hasTempControl = u.hasTempControl,
                    isLocalUser = u.userId == localId
                )
            },
            currentTrack = null,
            isPlaying = false,
            currentPositionMs = 0L,
            globalVolume = state.globalVolume,
            timingOffsetMs = state.timingOffsetMs,
            playbackPermission = state.playbackPermission,
            isLocalUserHost = isLocalHost,
            offset = "${state.timingOffsetMs}ms"
        ).also {
            android.util.Log.d("ROOM_SYNC", "Room: ${state.roomCode} Users: ${state.users.size}")
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    init {
        // Observe local room events (room closed, kicked) from manager
        viewModelScope.launch {
            ListenTogetherManager.events.collect { event ->
                handleRoomEvent(event)
            }
        }

        // Observe real-time playback events from the socket repo (guest-side)
        viewModelScope.launch {
            SocketListenTogetherRepository.playbackEvents.collect { event ->
                handlePlaybackEvent(event)
            }
        }

        // Auto-unmute when local user gains sudo (hasTempControl transitions false -> true)
        viewModelScope.launch {
            var previousCanControl = false
            ListenTogetherManager.roomState.collect { state ->
                val localId = ListenTogetherManager.localUserId
                val user = state?.users?.find { it.userId == localId }
                val nowCanControl = user != null &&
                    (user.isHost || user.hasTempControl ||
                     state.playbackPermission == PlaybackPermission.Everyone)
                if (nowCanControl && !previousCanControl) {
                    // Guest was just elevated — restore full volume
                    playerConnection?.player?.volume = 1f
                    android.util.Log.d("AUTH_UI", "[ViewModel] sudo granted → player unmuted")
                }
                previousCanControl = nowCanControl
            }
        }
    }

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.RoomClosed -> {
                playerConnection?.player?.pause()
                viewModelScope.launch { _navigationEvent.emit(NavigationEvent.RoomClosed) }
            }
            is RoomEvent.Kicked -> {
                if (event.userId == ListenTogetherManager.localUserId) {
                    playerConnection?.player?.pause()
                    viewModelScope.launch { _navigationEvent.emit(NavigationEvent.Kicked) }
                }
            }
            is RoomEvent.RoomNotFound -> {
                viewModelScope.launch { _navigationEvent.emit(NavigationEvent.RoomNotFound) }
            }
            is RoomEvent.HostTransferred -> {
                if (event.newHostId == ListenTogetherManager.localUserId) {
                    com.noxwizard.resonix.ui.screens.ListenTogetherSessionManager.isHost = true
                }
            }
            else -> Unit
        }
    }

    private fun handlePlaybackEvent(event: PlaybackEvent) {
        val pc = playerConnection ?: return
        
        // Prevent mirroring our own actions if we are controlling playback
        // Wait, actually, if a guest with "temp control" plays, the host should mirror it.
        // So we only discard IF we were the one who sent this exact event.
        // The sender is excluded on the backend, so if we receive an event, it's from someone else.
        
        val timingOffset = SocketListenTogetherRepository.roomState.value?.timingOffsetMs?.toLong() ?: 0L
        
        isApplyingRemoteEvent = true
        when (event) {
            is PlaybackEvent.Play -> {
                // To play a specific track if we aren't on it (basic implementation)
                if (pc.player.currentMediaItem?.mediaId != event.trackId && event.trackId.isNotEmpty()) {
                    // Real app should lookup song and play, but for now we just play what we have
                }
                pc.player.seekTo(event.positionMs + timingOffset)
                pc.player.play()
            }
            is PlaybackEvent.Pause -> {
                pc.player.pause()
                pc.player.seekTo(event.positionMs + timingOffset)
            }
            is PlaybackEvent.Seek -> {
                pc.player.seekTo(event.positionMs + timingOffset)
            }
        }
        isApplyingRemoteEvent = false
    }

    override fun onCleared() {
        super.onCleared()
        playerConnection?.player?.removeListener(playerListener)
    }

    private val canControl: Boolean
        get() {
            val room = uiState.value ?: return false
            val currentUser = room.connectedUsers.find { it.userId == ListenTogetherManager.localUserId } ?: return false
            return when (room.playbackPermission) {
                PlaybackPermission.Everyone -> true
                PlaybackPermission.HostOnly -> currentUser.isHost || currentUser.hasTempControl
            }
        }

    fun play() {
        if (!canControl) return
        val pc = playerConnection ?: return
        val position = pc.player.currentPosition
        ListenTogetherManager.broadcastPlay(pc.player.currentMediaItem?.mediaId ?: "", position)
        pc.player.play()
    }

    fun pause() {
        if (!canControl) return
        val pc = playerConnection ?: return
        val position = pc.player.currentPosition
        ListenTogetherManager.broadcastPause(position)
        pc.player.pause()
    }

    fun seek(positionMs: Long) {
        if (!canControl) return
        val pc = playerConnection ?: return
        ListenTogetherManager.broadcastSeek(positionMs)
        pc.player.seekTo(positionMs)
    }

    /** Only called from the Leave Room button — NEVER from BackHandler. */
    fun leaveRoom() {
        ListenTogetherManager.leaveRoom()
        ListenTogetherManager.clearSession(context)
    }

    fun transferHost(newHostId: String) = ListenTogetherManager.transferHost(newHostId)
    fun grantSudo(userId: String)= ListenTogetherManager.grantSudo(userId)
    fun kickUser(userId: String) = ListenTogetherManager.kickUser(userId)
    fun setPlaybackPermission(mode: String) = ListenTogetherManager.setPlaybackPermission(mode)
    fun updateGlobalVolume(volume: Float) = ListenTogetherManager.updateGlobalVolume(volume)
    fun updateTimingNudge(nudgeMs: Int) = ListenTogetherManager.updateTimingNudge(nudgeMs)
}
