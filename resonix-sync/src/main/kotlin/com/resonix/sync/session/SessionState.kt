package com.resonix.sync.session

/**
 * Lifecycle state of a [SyncSession].
 *
 * State machine:
 * ```
 * Idle ──► Connecting ──► Synced
 *                    ╰──► Drifted (when periodic resync detects > 5ms drift)
 * ```
 */
sealed class SessionState {

    /** No active session. Initial state after [ResonixSync.init] before any room is joined. */
    object Idle : SessionState()

    /** Actively connecting to the server and running the NTP probe cycle. */
    object Connecting : SessionState()

    /**
     * Connected and clock-offset established. Playback commands can now be scheduled.
     *
     * @property offsetMs The current NTP clock offset in milliseconds.
     *                    Add to local time to get estimated server time.
     */
    data class Synced(val offsetMs: Long) : SessionState()

    /**
     * Clock drift detected during a periodic resync. The sync module automatically
     * updates its internal offset, but exposes this state so the host UI can show
     * a warning banner or log the event.
     *
     * @property driftMs Magnitude of the detected drift in milliseconds.
     */
    data class Drifted(val driftMs: Long) : SessionState()
}
