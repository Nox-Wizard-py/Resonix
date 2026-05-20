package com.noxwizard.resonix.ui.player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.exoplayer.ExoPlayer
import com.noxwizard.resonix.R
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// 1. Device model
// ─────────────────────────────────────────────────────────────────────────────

enum class OutputType {
    PHONE_SPEAKER,
    PHONE_EARPIECE,
    WIRED_HEADSET,
    BLUETOOTH_A2DP,
    BLUETOOTH_SCO,
    USB_AUDIO,
    UNKNOWN,
}

data class MediaOutputDevice(
    val id: String,
    val name: String,
    val type: OutputType,
    val audioDeviceInfo: AudioDeviceInfo?,
    val isPersonalBluetoothAudio: Boolean = false,
    val isWiredPersonalAudio: Boolean = false,
)

data class MediaOutputState(
    val availableDevices: List<MediaOutputDevice> = emptyList(),
    val activeDeviceId: String? = null,
    val activeDeviceLabel: String = "Phone speaker",
    val activeIconType: MediaOutputIconType = MediaOutputIconType.SPEAKER
)

enum class MediaOutputIconType {
    SPEAKER,
    EARPIECE,
    EARBUD,
    BLUETOOTH,
    CAST
}

fun getMediaOutputIconType(device: MediaOutputDevice?): MediaOutputIconType {
    if (device == null) return MediaOutputIconType.SPEAKER
    
    return when (device.type) {
        OutputType.PHONE_SPEAKER   -> MediaOutputIconType.SPEAKER
        OutputType.PHONE_EARPIECE  -> MediaOutputIconType.EARPIECE
        OutputType.WIRED_HEADSET   -> MediaOutputIconType.EARBUD
        OutputType.USB_AUDIO       -> {
            if (device.isWiredPersonalAudio) MediaOutputIconType.EARBUD else MediaOutputIconType.EARBUD
        }
        OutputType.BLUETOOTH_A2DP,
        OutputType.BLUETOOTH_SCO   -> {
            if (device.isPersonalBluetoothAudio) MediaOutputIconType.EARBUD
            else MediaOutputIconType.BLUETOOTH
        }
        OutputType.UNKNOWN         -> {
            if (device.isWiredPersonalAudio) MediaOutputIconType.EARBUD else MediaOutputIconType.SPEAKER
        }
    }
}

@DrawableRes
fun MediaOutputIconType.iconRes(): Int = when (this) {
    MediaOutputIconType.SPEAKER   -> R.drawable.speaker
    MediaOutputIconType.EARPIECE  -> R.drawable.earpiece
    MediaOutputIconType.EARBUD    -> R.drawable.earbuds
    MediaOutputIconType.BLUETOOTH -> R.drawable.bluetooth_connected
    MediaOutputIconType.CAST      -> R.drawable.speaker
}

@DrawableRes
fun MediaOutputDevice.iconRes(): Int = getMediaOutputIconType(this).iconRes()

fun isBluetoothPersonalAudioDevice(
    name: String?,
    bluetoothClass: android.bluetooth.BluetoothClass?
): Boolean {
    val lowerName = name?.lowercase()?.trim()?.replace(Regex("\\s+"), " ") ?: ""
    val genericKeywords = listOf(
        "speaker", "soundbar", "partybox", "boombox", "flip", "charge",
        "go 3", "go 4", "stone", "soundcore motion", "car", "laptop", "pc", "phone", "tv"
    )
    if (genericKeywords.any { it in lowerName }) return false

    val personalKeywords = listOf(
        "buds", "bud", "earbud", "earbuds", "tws", "airpods", "airdopes",
        "earphone", "earphones", "headphone", "headphones", "headset",
        "neckband", "freebuds", "galaxy buds", "nothing ear", "oneplus buds",
        "realme buds", "oppo enco", "boat", "boat nirvana", "nirvana", "zenith",
        "rockerz", "sony wf", "sony wh", "xm4", "xm5", "quietcomfort", "qc",
        "beats", "jbl tune", "soundcore liberty"
    )
    if (personalKeywords.any { it in lowerName }) return true

    if (bluetoothClass != null) {
        val deviceClass = bluetoothClass.deviceClass
        if (deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
        ) {
            return true
        }
        if (deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO ||
            deviceClass == android.bluetooth.BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
            deviceClass == android.bluetooth.BluetoothClass.Device.COMPUTER_LAPTOP ||
            deviceClass == android.bluetooth.BluetoothClass.Device.PHONE_SMART
        ) {
            return false
        }
    }
    return false
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AudioManager device discovery
// ─────────────────────────────────────────────────────────────────────────────

private fun String.normalizeDeviceName(): String {
    return this.lowercase().trim().replace(Regex("\\s+"), " ")
}

private fun AudioDeviceInfo.routePriority(): Int = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 1
    26 -> 2 // TYPE_BLE_HEADSET
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 3
    27 -> 4 // TYPE_BLE_SPEAKER
    30 -> 5 // TYPE_BLE_BROADCAST
    else -> 6
}

fun isWiredPersonalAudio(name: String, type: Int): Boolean {
    if (type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || type == AudioDeviceInfo.TYPE_WIRED_HEADSET || type == AudioDeviceInfo.TYPE_USB_HEADSET) return true
    val lower = name.lowercase()
    return "h2w" in lower || "headset" in lower || "wired headset" in lower || "wired headphone" in lower || "wired earphone" in lower
}

@SuppressLint("MissingPermission")
fun buildDeviceList(context: Context): List<MediaOutputDevice> {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val rawDevices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

    val resolvedList = rawDevices.mapNotNull { dev ->
        if (dev.type == AudioDeviceInfo.TYPE_TELEPHONY || dev.type == AudioDeviceInfo.TYPE_FM) return@mapNotNull null
        val (rawName, btClass) = dev.resolveLabelAndClass(context, am)
        if (rawName.isBlank()) return@mapNotNull null
        Triple(dev, rawName, btClass)
    }

    val grouped = resolvedList.groupBy { (dev, rawName, _) ->
        val type = dev.toOutputType()
        if (type == OutputType.PHONE_SPEAKER || type == OutputType.PHONE_EARPIECE) {
            "${type.name}:$rawName"
        } else {
            rawName.normalizeDeviceName()
        }
    }

    val result = mutableListOf<MediaOutputDevice>()
    for ((groupKey, items) in grouped) {
        val bestItem = items.minByOrNull { it.first.routePriority() } ?: continue
        val (bestDev, rawName, btClass) = bestItem
        val outputType = bestDev.toOutputType()
        
        val isPersonalBt = if (outputType == OutputType.BLUETOOTH_A2DP || outputType == OutputType.BLUETOOTH_SCO) {
            isBluetoothPersonalAudioDevice(rawName, btClass)
        } else false

        val isWiredPersonal = isWiredPersonalAudio(rawName, bestDev.type)

        val finalId = when (outputType) {
            OutputType.PHONE_SPEAKER -> "phone_speaker"
            OutputType.PHONE_EARPIECE -> "phone_earpiece"
            OutputType.BLUETOOTH_A2DP, OutputType.BLUETOOTH_SCO -> rawName.normalizeDeviceName()
            else -> {
                if ("h2w" in rawName.lowercase()) "wired_h2w"
                else "${bestDev.id}:${outputType.name}"
            }
        }

        result += MediaOutputDevice(
            id = finalId,
            name = rawName,
            type = outputType,
            audioDeviceInfo = bestDev,
            isPersonalBluetoothAudio = isPersonalBt,
            isWiredPersonalAudio = isWiredPersonal,
        )
    }

    return result.sortedWith(compareBy { it.type.sortOrder() })
}

private fun OutputType.sortOrder() = when (this) {
    OutputType.BLUETOOTH_A2DP  -> 0
    OutputType.BLUETOOTH_SCO   -> 1
    OutputType.WIRED_HEADSET   -> 2
    OutputType.USB_AUDIO       -> 3
    OutputType.PHONE_SPEAKER   -> 4
    OutputType.PHONE_EARPIECE  -> 5
    OutputType.UNKNOWN         -> 6
}

private fun AudioDeviceInfo.toOutputType() = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputType.PHONE_SPEAKER
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE     -> OutputType.PHONE_EARPIECE
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES     -> OutputType.WIRED_HEADSET
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    26, // TYPE_BLE_HEADSET
    27, // TYPE_BLE_SPEAKER
    30  // TYPE_BLE_BROADCAST
                                              -> OutputType.BLUETOOTH_A2DP
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO        -> OutputType.BLUETOOTH_SCO
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY        -> OutputType.USB_AUDIO
    else                                      -> OutputType.UNKNOWN
}

@SuppressLint("MissingPermission")
private fun AudioDeviceInfo.resolveLabelAndClass(context: Context, am: AudioManager): Pair<String, android.bluetooth.BluetoothClass?> {
    if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        type == 26 || type == 27 || type == 30) {
        val hasBtPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        if (hasBtPerm) {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                val bonded = adapter?.bondedDevices
                if (!bonded.isNullOrEmpty()) {
                    val productName = productName?.toString() ?: ""
                    for (btDev in bonded) {
                        val btName = btDev.name ?: continue
                        if (productName.isNotBlank() &&
                            (btName.contains(productName, true) || productName.contains(btName, true))
                        ) return btName to btDev.bluetoothClass
                    }
                    // Fallback: first bonded device while BT is active
                    if (am.isBluetoothA2dpOn || am.isBluetoothScoOn) {
                        val fallback = bonded.firstOrNull { it.name != null }
                        if (fallback != null) return fallback.name!! to fallback.bluetoothClass
                    }
                }
            } catch (_: SecurityException) {}
        }
        val product = productName?.toString()
        return (if (!product.isNullOrBlank()) product else "Bluetooth Device") to null
    }

    val name = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Phone speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE      -> "Phone earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES      -> {
            val p = productName?.toString()
            if (!p.isNullOrBlank()) p else "Wired headphones"
        }
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY         -> {
            val p = productName?.toString()
            if (!p.isNullOrBlank()) p else "USB audio"
        }
        else -> productName?.toString() ?: ""
    }
    return name to null
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. Guess which device is actually playing (heuristic — no API gives this for
//    media streams below API 31). Used ONLY when user hasn't selected a device
//    yet or as post-routing confirmation.
// ─────────────────────────────────────────────────────────────────────────────

private fun guessActiveType(am: AudioManager): OutputType = when {
    am.isBluetoothA2dpOn -> OutputType.BLUETOOTH_A2DP
    am.isBluetoothScoOn  -> OutputType.BLUETOOTH_SCO
    am.isWiredHeadsetOn  -> OutputType.WIRED_HEADSET
    else                 -> OutputType.PHONE_SPEAKER
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Controller — holds Compose state, owns routing + selection logic
// ─────────────────────────────────────────────────────────────────────────────

@Stable
class MediaOutputController(
    private val context: Context,
    private val player: ExoPlayer,
) {
    var state by mutableStateOf(MediaOutputState())
        private set

    private var manualSelectionId: String? = null
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevicesAndRoute()
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevicesAndRoute()
        }
    }

    fun start() {
        am.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        refreshDevicesAndRoute()
    }

    fun stop() {
        am.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    private fun refreshDevicesAndRoute() {
        val freshDevices = buildDeviceList(context)
        
        val manual = manualSelectionId?.let { id -> freshDevices.find { it.id == id } }
        
        val activeDev = manual 
            ?: freshDevices.firstOrNull { it.isPersonalBluetoothAudio }
            ?: freshDevices.firstOrNull { it.isWiredPersonalAudio }
            ?: freshDevices.firstOrNull { it.type == guessActiveType(am) && it.type != OutputType.PHONE_EARPIECE }
            ?: freshDevices.firstOrNull { it.type == OutputType.PHONE_SPEAKER }

        if (manualSelectionId != null && manual == null) {
            manualSelectionId = null
        }

        routeToDevice(activeDev, manual != null)

        state = MediaOutputState(
            availableDevices = freshDevices,
            activeDeviceId = activeDev?.id,
            activeDeviceLabel = activeDev?.name ?: "Phone speaker",
            activeIconType = getMediaOutputIconType(activeDev)
        )
    }

    fun selectDevice(device: MediaOutputDevice) {
        manualSelectionId = device.id
        refreshDevicesAndRoute()
    }

    private fun routeToDevice(device: MediaOutputDevice?, isManual: Boolean) {
        try {
            val devInfo = device?.audioDeviceInfo
            val routeType = if (isManual) "manually selected" else "auto-selected"
            Log.d("ResonixMediaOutput", "Routing to $routeType device:")
            Log.d("ResonixMediaOutput", "  device id: ${device?.id}")
            Log.d("ResonixMediaOutput", "  device type: ${device?.type}")
            Log.d("ResonixMediaOutput", "  productName: ${devInfo?.productName}")
            Log.d("ResonixMediaOutput", "  resolved label: ${device?.name}")
            Log.d("ResonixMediaOutput", "  icon type: ${getMediaOutputIconType(device)}")
            Log.d("ResonixMediaOutput", "  isBluetoothPersonalAudio: ${device?.isPersonalBluetoothAudio}")
            Log.d("ResonixMediaOutput", "  isWiredPersonalAudio: ${device?.isWiredPersonalAudio}")
            Log.d("ResonixMediaOutput", "  activeDeviceId: ${device?.id}")

            if (devInfo == null) {
                Log.d("ResonixMediaOutput", "  -> Calling player.setPreferredAudioDevice(null)")
                player.setPreferredAudioDevice(null)
            } else {
                Log.d("ResonixMediaOutput", "  -> Calling player.setPreferredAudioDevice(devInfo)")
                player.setPreferredAudioDevice(devInfo)
            }
        } catch (e: Exception) {
            Log.e("ResonixMediaOutput", "Error routing to device", e)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Composable entry points
// ─────────────────────────────────────────────────────────────────────────────

/** Creates and remembers a [MediaOutputController], refreshing it periodically. */
@Composable
fun rememberMediaOutputController(player: ExoPlayer): MediaOutputController {
    val context = LocalContext.current
    val controller = remember(player) { MediaOutputController(context, player) }

    DisposableEffect(controller) {
        controller.start()
        onDispose {
            controller.stop()
        }
    }

    return controller
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. MediaOutputSelectorSheet composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedAudioVisualizer(modifier: Modifier = Modifier, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )

    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar1).clip(RoundedCornerShape(50)).background(color))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar2).clip(RoundedCornerShape(50)).background(color))
        Box(modifier = Modifier.width(3.dp).fillMaxHeight(bar3).clip(RoundedCornerShape(50)).background(color))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaOutputSelectorSheet(
    controller: MediaOutputController,
    TextBackgroundColor: Color, // Kept for API compatibility, though not used for container background anymore
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        scrimColor = Color.Black.copy(alpha = 0.4f),
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Play on",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(Modifier.height(16.dp))

            val devices = controller.state.availableDevices
            if (devices.isEmpty()) {
                Text(
                    text = "No output devices detected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn {
                    items(items = devices, key = { it.id }) { device ->
                        val isSelected = device.id == controller.state.activeDeviceId
                        
                        val cardBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        val iconBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        val activeColor = MaterialTheme.colorScheme.onSurface
                        
                        val subtitleText = when {
                            isSelected -> "Connected"
                            device.isPersonalBluetoothAudio -> "Bluetooth earbuds"
                            device.isWiredPersonalAudio -> "Wired headset"
                            device.type == OutputType.BLUETOOTH_A2DP || device.type == OutputType.BLUETOOTH_SCO -> "Bluetooth device"
                            device.type == OutputType.PHONE_SPEAKER -> "Phone speaker"
                            else -> "Audio output"
                        }
                        
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.97f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ), label = "cardScale"
                        )
                        
                        androidx.compose.material3.Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                                .scale(scale)
                                .clip(RoundedCornerShape(24.dp))
                                .clickable(
                                    interactionSource = interactionSource, 
                                    indication = LocalIndication.current
                                ) {
                                    controller.selectDevice(device)
                                    onDismiss()
                                },
                            color = cardBgColor,
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(iconBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(getMediaOutputIconType(device).iconRes()),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                        tint = activeColor
                                    )
                                }
                                
                                Spacer(Modifier.width(16.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                        color = activeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = subtitleText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = activeColor.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                if (isSelected) {
                                    AnimatedAudioVisualizer(
                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
