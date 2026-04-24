package com.resonix.sync.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Sealed hierarchy of all WebSocket messages.
 *
 * Ported from BeatSync's WSRequest / WSUnicast / WSBroadcast Zod schemas
 * (packages/shared/types/). Each subtype maps to exactly one `"type"` discriminator
 * field so that kotlinx.serialization's polymorphic JSON can round-trip them correctly.
 *
 * ## Client → Server messages (outgoing)
 * - [NtpProbe]   — NTP coded-probe request (index 0 or 1)
 * - [Play]       — Trigger playback at a given track position
 * - [Pause]      — Pause playback
 * - [Seek]       — Seek to a specific position
 * - [Join]       — Join or create a room
 * - [RequestSync]— Late-join sync request
 *
 * ## Server → Client messages (incoming)
 * - [NtpResponse]    — Server echo with t1/t2 timestamps
 * - [RoomState]      — Full room state snapshot
 * - [ScheduledPlay]  — Server-time-stamped play command
 * - [ScheduledPause] — Server-time-stamped pause command
 */
@Serializable
sealed class SyncMessage {

    // ─── Client → Server ─────────────────────────────────────────────────────

    /**
     * One probe in a coded Huygens pair (index 0 or 1).
     *
     * @property t0             Client send timestamp (epoch ms).
     * @property probeGroupId   Shared ID for both probes in a pair.
     * @property probeGroupIndex 0 = first probe, 1 = second probe.
     */
    @Serializable
    @SerialName("NTP_REQUEST")
    data class NtpProbe(
        val t0: Long,
        val probeGroupId: Int,
        val probeGroupIndex: Int,
        val clientRtt: Long? = null,
        val clientCompensationMs: Long? = null,
        val clientNudgeMs: Long? = null,
    ) : SyncMessage()

    /**
     * Request the server to start or resume playback at [trackTimeSeconds].
     *
     * @property trackTimeSeconds Playback position in seconds (matches BeatSync's `trackTimeSeconds`).
     * @property audioSource     Opaque URL / ID of the audio source.
     */
    @Serializable
    @SerialName("PLAY")
    data class Play(
        val trackTimeSeconds: Double,
        val audioSource: String,
    ) : SyncMessage()

    /**
     * Request the server to pause playback.
     *
     * @property trackTimeSeconds Current playback position at pause time (seconds).
     * @property audioSource      Opaque URL / ID of the audio source.
     */
    @Serializable
    @SerialName("PAUSE")
    data class Pause(
        val trackTimeSeconds: Double,
        val audioSource: String,
    ) : SyncMessage()

    /**
     * Request the server to seek to [trackTimeSeconds].
     * The server will broadcast a [ScheduledPlay] with the new position.
     *
     * @property trackTimeSeconds Target seek position in seconds.
     * @property audioSource      Opaque URL / ID of the audio source.
     */
    @Serializable
    @SerialName("SEEK")
    data class Seek(
        val trackTimeSeconds: Double,
        val audioSource: String,
    ) : SyncMessage()

    /**
     * Join a room or implicitly create one if the code is new.
     *
     * @property roomCode Alphanumeric room identifier.
     * @property clientId Stable per-device client UUID.
     */
    @Serializable
    @SerialName("JOIN")
    data class Join(
        val roomCode: String,
        val clientId: String,
    ) : SyncMessage()

    /**
     * Late-join sync request. Server responds with a full [RoomState].
     */
    @Serializable
    @SerialName("SYNC")
    object RequestSync : SyncMessage()

    // ─── Server → Client ─────────────────────────────────────────────────────

    /**
     * NTP response echoed back by the server.
     *
     * @property t0             Echoed client send timestamp.
     * @property t1             Server receive timestamp (epoch ms).
     * @property t2             Server send timestamp (epoch ms).
     * @property probeGroupId   Echoed from request.
     * @property probeGroupIndex Echoed from request.
     */
    @Serializable
    @SerialName("NTP_RESPONSE")
    data class NtpResponse(
        val t0: Long,
        val t1: Long,
        val t2: Long,
        val probeGroupId: Int,
        val probeGroupIndex: Int,
        val clientRtt: Long? = null,
    ) : SyncMessage()

    /**
     * Full room state snapshot — sent on join/sync.
     *
     * @property roomCode     The room identifier.
     * @property hostClientId Client ID of the current room host/admin.
     * @property isPlaying    Whether the room is currently playing.
     * @property positionMs   Current playback position in milliseconds.
     */
    @Serializable
    @SerialName("ROOM_STATE")
    data class RoomState(
        val roomCode: String,
        val hostClientId: String,
        val isPlaying: Boolean,
        val positionMs: Long,
    ) : SyncMessage()

    /**
     * Server-scheduled play command. The client must execute playback at exactly
     * [serverTimeToExecute] (server epoch ms), adjusted by the local NTP offset.
     *
     * @property serverTimeToExecute  Target server epoch timestamp in ms.
     * @property trackTimeSeconds     Playback position to start from.
     * @property audioSource          Opaque URL / ID of the audio source.
     */
    @Serializable
    @SerialName("SCHEDULED_PLAY")
    data class ScheduledPlay(
        val serverTimeToExecute: Long,
        val trackTimeSeconds: Double,
        val audioSource: String,
    ) : SyncMessage()

    /**
     * Server-scheduled pause command.
     *
     * @property serverTimeToExecute Target server epoch timestamp in ms.
     * @property trackTimeSeconds    Playback position at which to pause.
     * @property audioSource         Opaque URL / ID of the audio source.
     */
    @Serializable
    @SerialName("SCHEDULED_PAUSE")
    data class ScheduledPause(
        val serverTimeToExecute: Long,
        val trackTimeSeconds: Double,
        val audioSource: String,
    ) : SyncMessage()

    /**
     * Unrecognised or malformed server message — used as a safe fallback.
     *
     * @property raw The raw JSON string for debugging.
     */
    data class Unknown(val raw: String) : SyncMessage()
}
