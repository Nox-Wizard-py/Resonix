package com.resonix.sync.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OkHttp WebSocket wrapper with:
 * - Automatic exponential-backoff reconnection
 * - A [SharedFlow] of parsed [SyncMessage]s for consumption
 * - [ConnectivityManager] network callback to trigger immediate re-sync on network changes
 *
 * All outgoing messages are serialized to JSON via kotlinx.serialization. Incoming
 * messages are deserialized by inspecting the top-level `"type"` field and delegating
 * to the correct [SyncMessage] subtype.
 *
 * @param context    Application context (needed for [ConnectivityManager]).
 * @param serverUrl  WebSocket server URL (e.g. `"wss://sync.resonix.app/ws"`).
 * @param onResync   Callback invoked when a network change is detected. The caller should
 *                   re-run [NtpEngine.measure] and update the session clock offset.
 */
internal class SyncWebSocket(
    context: Context,
    private val serverUrl: String,
    private val onResync: () -> Unit,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    private val _incoming = MutableSharedFlow<SyncMessage>(extraBufferCapacity = 64)

    /**
     * Hot flow of all incoming [SyncMessage]s decoded from the server.
     * Messages are emitted on [Dispatchers.IO]. Collectors must handle
     * their own threading if UI work is required.
     */
    val incoming: SharedFlow<SyncMessage> = _incoming.asSharedFlow()

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // WebSocket: keep alive indefinitely
        .build()

    private var activeSocket: WebSocket? = null
    private val isDestroyed = AtomicBoolean(false)

    // ── Connectivity monitor ──────────────────────────────────────────────────

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available — triggering re-sync")
            onResync()
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        connect()
    }

    // ── Connection lifecycle ─────────────────────────────────────────────────

    private fun connect(delayMs: Long = 0L) {
        if (isDestroyed.get()) return
        scope.launch {
            if (delayMs > 0) delay(delayMs)
            if (isDestroyed.get()) return@launch

            val request = Request.Builder().url(serverUrl).build()
            activeSocket = client.newWebSocket(request, listener)
            Log.d(TAG, "Connecting to $serverUrl")
        }
    }

    // ── OkHttp listener ──────────────────────────────────────────────────────

    private var reconnectAttempt = 0

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
            Log.d(TAG, "WebSocket open")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val parsed = parseMessage(text)
            scope.launch { _incoming.emit(parsed) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isDestroyed.get()) return
            val backoffMs = minOf(30_000L, 500L * (1L shl reconnectAttempt.coerceAtMost(6)))
            reconnectAttempt++
            Log.w(TAG, "WebSocket failure: ${t.message}. Retrying in ${backoffMs}ms (attempt $reconnectAttempt)")
            connect(backoffMs)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isDestroyed.get()) return
            Log.d(TAG, "WebSocket closed: $code $reason. Reconnecting…")
            connect(1_000L)
        }
    }

    // ── Outgoing ─────────────────────────────────────────────────────────────

    /**
     * Serialize and send a [SyncMessage] to the server.
     *
     * Does nothing if the socket is not currently open.
     */
    fun send(message: SyncMessage) {
        val socket = activeSocket ?: return
        val text = serializeMessage(message)
        if (text != null) {
            socket.send(text)
        }
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    private fun serializeMessage(message: SyncMessage): String? = try {
        when (message) {
            is SyncMessage.NtpProbe -> json.encodeToString(SyncMessage.NtpProbe.serializer(), message)
            is SyncMessage.Play -> json.encodeToString(SyncMessage.Play.serializer(), message)
            is SyncMessage.Pause -> json.encodeToString(SyncMessage.Pause.serializer(), message)
            is SyncMessage.Seek -> json.encodeToString(SyncMessage.Seek.serializer(), message)
            is SyncMessage.Join -> json.encodeToString(SyncMessage.Join.serializer(), message)
            is SyncMessage.RequestSync -> """{"type":"SYNC"}"""
            else -> {
                Log.w(TAG, "Attempted to send non-outgoing message type: ${message::class.simpleName}")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to serialize message: ${e.message}")
        null
    }

    private fun parseMessage(raw: String): SyncMessage = try {
        val obj = json.parseToJsonElement(raw) as? JsonObject
        when (obj?.get("type")?.jsonPrimitive?.content) {
            "NTP_RESPONSE" -> json.decodeFromString(SyncMessage.NtpResponse.serializer(), raw)
            "ROOM_STATE" -> json.decodeFromString(SyncMessage.RoomState.serializer(), raw)
            "SCHEDULED_ACTION" -> parseScheduledAction(obj, raw)
            else -> SyncMessage.Unknown(raw)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse message: ${e.message} — raw: $raw")
        SyncMessage.Unknown(raw)
    }

    private fun parseScheduledAction(obj: JsonObject?, raw: String): SyncMessage {
        val innerType = (obj?.get("scheduledAction") as? JsonObject)
            ?.get("type")?.jsonPrimitive?.content
        return when (innerType) {
            "PLAY" -> json.decodeFromString(SyncMessage.ScheduledPlay.serializer(), extractScheduledActionPayload(obj, raw, "SCHEDULED_PLAY"))
            "PAUSE" -> json.decodeFromString(SyncMessage.ScheduledPause.serializer(), extractScheduledActionPayload(obj, raw, "SCHEDULED_PAUSE"))
            else -> SyncMessage.Unknown(raw)
        }
    }

    /**
     * Flatten a BeatSync `SCHEDULED_ACTION` envelope into a top-level [SyncMessage] JSON
     * that our serializer can decode: merges `serverTimeToExecute` with the inner action.
     *
     * Validates that all required fields are present and non-empty before constructing
     * the flattened JSON. Throws [IllegalArgumentException] with a descriptive message
     * on any missing field so that the outer try-catch can log and return [raw] for
     * graceful [SyncMessage.Unknown] handling.
     */
    private fun extractScheduledActionPayload(obj: JsonObject?, raw: String, forcedType: String): String {
        return try {
            val serverTime = obj?.get("serverTimeToExecute")?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing serverTimeToExecute")
            val inner = obj.get("scheduledAction") as? JsonObject
                ?: throw IllegalArgumentException("Missing scheduledAction object")
            val trackTime = inner["trackTimeSeconds"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing trackTimeSeconds")
            val audioSource = inner["audioSource"]?.jsonPrimitive?.content
                ?: throw IllegalArgumentException("Missing audioSource")
            """{"type":"$forcedType","serverTimeToExecute":$serverTime,"trackTimeSeconds":$trackTime,"audioSource":"$audioSource"}"""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flatten scheduled action payload: ${e.message} — raw: $raw")
            raw // return raw so Unknown() handles it gracefully upstream
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Close the WebSocket connection and unregister connectivity callbacks.
     * Must be called when the owning [SyncSession] is destroyed.
     */
    fun destroy() {
        if (isDestroyed.compareAndSet(false, true)) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            activeSocket?.close(1000, "destroy")
            activeSocket = null
            client.dispatcher.executorService.shutdown()
            Log.d(TAG, "SyncWebSocket destroyed")
        }
    }

    companion object {
        private const val TAG = "SyncWebSocket"
    }
}
