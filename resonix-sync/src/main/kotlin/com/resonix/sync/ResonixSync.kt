package com.resonix.sync

import android.content.Context
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer
import com.resonix.sync.scheduler.PlaybackScheduler
import com.resonix.sync.session.SessionState
import com.resonix.sync.session.SyncSession
import kotlinx.coroutines.flow.StateFlow

/**
 * **ResonixSync** — public façade for the resonix-sync module.
 *
 * This object is the single entry-point for host and peer clients. It owns the
 * [SyncSession] and [PlaybackScheduler] lifecycle and exposes a simple,
 * Kotlin-idiomatic API with no callbacks or raw threads.
 *
 * ## Typical host usage
 * ```kotlin
 * ResonixSync.init(context, serverUrl = "wss://sync.resonix.app/ws")
 * ResonixSync.attachPlayer(exoPlayer)
 * val code = ResonixSync.createRoom()          // share 'code' with peers
 * ResonixSync.startPeriodicResync()
 * ResonixSync.play(positionMs = 0L)
 * // … later …
 * ResonixSync.destroy()
 * ```
 *
 * ## Typical peer usage
 * ```kotlin
 * ResonixSync.init(context, serverUrl = "wss://sync.resonix.app/ws")
 * ResonixSync.attachPlayer(exoPlayer)
 * ResonixSync.joinRoom(code)
 * ResonixSync.startPeriodicResync()
 * // ExoPlayer will play/pause automatically on ScheduledPlay/Pause commands
 * // … later …
 * ResonixSync.destroy()
 * ```
 *
 * ## Threading
 * All network and timing logic runs on [kotlinx.coroutines.Dispatchers.IO].
 * ExoPlayer calls are dispatched to the main thread automatically.
 *
 * ## Constraints
 * - [init] must be called before any other method.
 * - [attachPlayer] must be called before [play], [pause], or [seek] are meaningful.
 * - [destroy] must be called (e.g. in `onDestroy`) to release resources.
 */
object ResonixSync {

    private const val TAG = "ResonixSync"

    /** Placeholder audio source used when the host does not differentiate sources. */
    private const val DEFAULT_AUDIO_SOURCE = "resonix_current"

    @Volatile
    private var session: SyncSession? = null

    @Volatile
    private var scheduler: PlaybackScheduler? = null

    // ── Observable state ──────────────────────────────────────────────────────

    /**
     * Observable [SessionState] stream. Collect this in your ViewModel or UI layer.
     *
     * Emits [SessionState.Idle] before [init], [SessionState.Connecting] during NTP
     * calibration, [SessionState.Synced] when ready, and [SessionState.Drifted] when
     * periodic resync detects significant clock drift.
     *
     * Returns null if [init] has not been called yet.
     */
    val sessionState: StateFlow<SessionState>?
        get() = session?.state

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Initialize the sync module. Must be the first call.
     *
     * Idempotent: calling [init] again after [destroy] is safe.
     *
     * @param context   Application [Context] (used for [ConnectivityManager]).
     * @param serverUrl Full WebSocket server URL, e.g. `"wss://sync.resonix.app/ws"`.
     *                  Must NOT contain trailing slashes or query params.
     */
    fun init(context: Context, serverUrl: String) {
        if (session != null) {
            Log.w(TAG, "init() called when session already active — call destroy() first")
            return
        }
        val sched = PlaybackScheduler()
        scheduler = sched
        session = SyncSession(
            context = context.applicationContext,
            serverUrl = serverUrl,
            scheduler = sched,
        )
        Log.d(TAG, "ResonixSync initialized (serverUrl=$serverUrl)")
    }

    /**
     * Attach the [ExoPlayer] instance that receives play/pause/seek commands.
     *
     * Safe to call multiple times if the player is recreated (e.g. after orientation
     * change). The previous reference is silently replaced.
     *
     * @param player The active [ExoPlayer] instance.
     */
    fun attachPlayer(player: ExoPlayer) {
        requireScheduler().attachPlayer(player)
    }

    // ── Room management ───────────────────────────────────────────────────────

    /**
     * Create a new sync room and return its 6-character alphanumeric room code.
     *
     * This client becomes the **host** and is the only one allowed to call
     * [play], [pause], and [seek]. Share the returned code out-of-band with peers.
     *
     * @return 6-character uppercase alphanumeric room code.
     */
    fun createRoom(): String = requireSession().createRoom()

    /**
     * Join an existing room identified by [code].
     *
     * This client becomes a **peer** and will automatically receive and execute
     * scheduled play/pause commands from the host via [PlaybackScheduler].
     *
     * @param code 6-character room code obtained from the host.
     */
    fun joinRoom(code: String) {
        requireSession().joinRoom(code)
    }

    // ── Playback commands (host only) ─────────────────────────────────────────

    /**
     * Send a play command to the server at the given [positionMs].
     *
     * Only the host's call is forwarded to the server. Peer calls are silently dropped.
     *
     * @param positionMs Playback start position in milliseconds.
     * @param audioSource Optional audio source identifier (defaults to [DEFAULT_AUDIO_SOURCE]).
     */
    fun play(positionMs: Long, audioSource: String = DEFAULT_AUDIO_SOURCE) {
        requireSession().play(positionMs, audioSource)
    }

    /**
     * Send a pause command to the server.
     *
     * Only the host's call is forwarded. Peer calls are silently dropped.
     *
     * @param positionMs  Current playback position at the time of pause (milliseconds).
     * @param audioSource Optional audio source identifier.
     */
    fun pause(positionMs: Long = 0L, audioSource: String = DEFAULT_AUDIO_SOURCE) {
        requireSession().pause(positionMs, audioSource)
    }

    /**
     * Send a seek command to the server.
     *
     * The server will broadcast a [SyncMessage.ScheduledPlay] with the new position to
     * all peers, causing their [PlaybackScheduler] to fire a seekTo + play.
     *
     * Only the host's call is forwarded. Peer calls are silently dropped.
     *
     * @param positionMs  Target seek position in milliseconds.
     * @param audioSource Optional audio source identifier.
     */
    fun seek(positionMs: Long, audioSource: String = DEFAULT_AUDIO_SOURCE) {
        requireSession().seek(positionMs, audioSource)
    }

    // ── Resync ────────────────────────────────────────────────────────────────

    /**
     * Start the periodic NTP resync loop.
     *
     * Runs a full NTP probe cycle every [intervalMs] milliseconds. If the new offset
     * deviates from the current one by more than 5 ms, [sessionState] transitions to
     * [SessionState.Drifted] and the offset is corrected automatically.
     *
     * A resync is also triggered immediately on any network-change event (e.g.
     * Wi-Fi handoff, mobile data reconnect).
     *
     * @param intervalMs Resync interval in milliseconds. Default: 30,000 ms.
     */
    fun startPeriodicResync(intervalMs: Long = 30_000L) {
        requireSession().startPeriodicResync(intervalMs)
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Destroy the active session and release all resources.
     *
     * Cancels all coroutines, closes the WebSocket, and unregisters the network
     * connectivity callback. Must be called in your Activity/Fragment `onDestroy`.
     *
     * After calling destroy, [init] can be called again to start a new session.
     */
    fun destroy() {
        session?.destroy()
        session = null
        scheduler = null
        Log.d(TAG, "ResonixSync destroyed")
    }

    // ── Guard helpers ─────────────────────────────────────────────────────────

    private fun requireSession(): SyncSession =
        session ?: error("ResonixSync.init() must be called before using the session API")

    private fun requireScheduler(): PlaybackScheduler =
        scheduler ?: error("ResonixSync.init() must be called before attachPlayer()")
}
