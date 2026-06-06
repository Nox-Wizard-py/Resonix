package com.noxwizard.resonix.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.InlineTextContent
import kotlin.math.sin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.activity.compose.BackHandler
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.view.WindowManager
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.noxwizard.resonix.LocalPlayerConnection
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.DarkModeKey
import com.noxwizard.resonix.constants.LyricsClickKey
import com.noxwizard.resonix.constants.LyricsRomanizeJapaneseKey
import com.noxwizard.resonix.constants.LyricsRomanizeKoreanKey
import com.noxwizard.resonix.constants.LyricsScrollKey
import com.noxwizard.resonix.constants.LyricsTextPositionKey
import com.noxwizard.resonix.constants.LyricsTextSizeKey
import com.noxwizard.resonix.constants.LyricsLineSpacingKey
import com.noxwizard.resonix.constants.LyricsStyle
import com.noxwizard.resonix.constants.LyricsStyleKey
import com.noxwizard.resonix.constants.PlayerBackgroundStyle
import com.noxwizard.resonix.constants.PlayerBackgroundStyleKey
import com.noxwizard.resonix.ui.lyrics.LyricsThemeSpec
import com.noxwizard.resonix.ui.lyrics.lyricsBlurEffect
import com.noxwizard.resonix.ui.lyrics.toThemeSpec
import com.noxwizard.resonix.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.noxwizard.resonix.lyrics.LyricsEntry
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
import com.noxwizard.resonix.lyrics.LyricsUtils.isChinese
import com.noxwizard.resonix.lyrics.LyricsUtils.isJapanese
import com.noxwizard.resonix.lyrics.LyricsUtils.isKorean
import com.noxwizard.resonix.lyrics.LyricsUtils.parseLyrics
import com.noxwizard.resonix.lyrics.LyricsUtils.romanizeJapanese
import com.noxwizard.resonix.lyrics.LyricsUtils.romanizeKorean
import com.noxwizard.resonix.ui.component.shimmer.ShimmerHost
import com.noxwizard.resonix.ui.component.shimmer.TextPlaceholder
import com.noxwizard.resonix.ui.menu.LyricsMenu
import com.noxwizard.resonix.ui.screens.settings.DarkMode
import com.noxwizard.resonix.ui.screens.settings.LyricsPosition
import com.noxwizard.resonix.ui.utils.fadingEdge
import com.noxwizard.resonix.ui.utils.smoothFadingEdge
import com.noxwizard.resonix.utils.ComposeToImage
import com.noxwizard.resonix.utils.rememberEnumPreference
import com.noxwizard.resonix.utils.rememberPreference
import com.noxwizard.resonix.lyrics.playback.LyricsDiagnosticsHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.Duration.Companion.seconds

private val AppleMusicEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
private val SmoothDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

@RequiresApi(Build.VERSION_CODES.M)
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@SuppressLint("UnusedBoxWithConstraintsScope", "StringFormatInvalid")
@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val landscapeOffset =
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val lyricsTextPosition by rememberEnumPreference(LyricsTextPositionKey, LyricsPosition.LEFT)
    val lyricsTextSize by rememberPreference(LyricsTextSizeKey, 26f)
    val lyricsLineSpacing by rememberPreference(LyricsLineSpacingKey, 1.3f)
    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val scrollLyrics by rememberPreference(LyricsScrollKey, true)
    val romanizeJapaneseLyrics by rememberPreference(LyricsRomanizeJapaneseKey, true)
    val romanizeKoreanLyrics by rememberPreference(LyricsRomanizeKoreanKey, true)
    val lyricsStyle by rememberEnumPreference(LyricsStyleKey, LyricsStyle.FLOW)
    val themeSpec = remember(lyricsStyle) { lyricsStyle.toThemeSpec() }
    val scope = rememberCoroutineScope()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = remember(lyricsEntity) { lyricsEntity?.lyrics?.trim() }

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    // Deserialize JSON-wrapped LyricsDocument stored in DB, or pass raw LRC through unchanged.
    val lyricsDocument: String? = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) null
        else if (lyrics.trimStart().startsWith("{")) {
            runCatching {
                val obj = org.json.JSONObject(lyrics)
                val rawText = obj.optString("rawText", "")
                val sanitized = LyricsPayloadSanitizer.sanitize(rawText)
                // TTML inside JSON envelope — convert to LRC
                if (sanitized.trimStart().startsWith("<tt") ||
                    sanitized.trimStart().startsWith("<?xml")) {
                    val parsedLines = com.noxwizard.resonix.betterlyrics.TTMLParser.parseTTML(sanitized)
                    @Suppress("DEPRECATION")
                    if (parsedLines.isEmpty()) null
                    else com.noxwizard.resonix.betterlyrics.TTMLParser.toLRC(parsedLines)
                } else {
                    sanitized
                }
            }.getOrNull()
        } else if (lyrics.trimStart().startsWith("<tt") ||
                   lyrics.trimStart().startsWith("<?xml")) {
            // Raw TTML stored directly (not wrapped in JSON) — convert to LRC
            runCatching {
                val parsedLines = com.noxwizard.resonix.betterlyrics.TTMLParser.parseTTML(lyrics)
                @Suppress("DEPRECATION")
                if (parsedLines.isEmpty()) null
                else com.noxwizard.resonix.betterlyrics.TTMLParser.toLRC(parsedLines)
            }.getOrNull()
        } else lyrics
    }

    val lines = remember(lyricsDocument, scope) {
        if (lyricsDocument == null) {
            emptyList()
        } else if (lyricsDocument.startsWith("[")) {
            val parsedLines = parseLyrics(lyricsDocument)
            parsedLines.map { entry ->
                val newEntry = LyricsEntry(entry.time, entry.text, entry.words)
                if (romanizeJapaneseLyrics) {
                    if (isJapanese(entry.text) && !isChinese(entry.text)) {
                        scope.launch {
                            newEntry.romanizedTextFlow.value = romanizeJapanese(entry.text)
                        }
                    }
                }
                if (romanizeKoreanLyrics) {
                    if (isKorean(entry.text)) {
                        scope.launch {
                            newEntry.romanizedTextFlow.value = romanizeKorean(entry.text)
                        }
                    }
                }
                newEntry
            }.let {
                listOf(LyricsEntry.HEAD_LYRICS_ENTRY) + it
            }
        } else {
            if (lyricsDocument.isBlank()) emptyList()
            else lyricsDocument.lines().mapIndexed { index, line ->
                val newEntry = LyricsEntry(index * 100L, line)
                if (romanizeJapaneseLyrics) {
                    if (isJapanese(line) && !isChinese(line)) {
                        scope.launch {
                            newEntry.romanizedTextFlow.value = romanizeJapanese(line)
                        }
                    }
                }
                if (romanizeKoreanLyrics) {
                    if (isKorean(line)) {
                        scope.launch {
                            newEntry.romanizedTextFlow.value = romanizeKorean(line)
                        }
                    }
                }
                newEntry
            }
        }
    }
    
    val syncLines = remember(lines) {
        val mapped = lines.map { entry ->
            com.noxwizard.resonix.paxsenix.models.LyricsLine(
                startMs = entry.time,
                endMs = -1L,
                text = entry.text,
                words = emptyList()
            )
        }
        com.noxwizard.resonix.lyrics.playback.LyricsPlaybackResolver.inferTimings(mapped)
    }
    val isSynced =
        remember(lyricsDocument) {
            !lyricsDocument.isNullOrEmpty() && lyricsDocument.startsWith("[")
        }

    val baseAccent = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }
    val themeAccent = themeSpec.themeAccentColor ?: baseAccent
    // Base text color remains neutral (white)
    val expressiveAccent = baseAccent
    val textColor = baseAccent

    var currentLineIndex by remember {
        mutableIntStateOf(-1)
    }
    var deferredCurrentLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var currentPlaybackPosition by remember {
        mutableLongStateOf(0L)
    }

    var previousLineIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    var lastPreviewTime by rememberSaveable {
        mutableLongStateOf(0L)
    }
    var isSeeking by remember {
        mutableStateOf(false)
    }

    var initialScrollDone by rememberSaveable {
        mutableStateOf(false)
    }

    var shouldScrollToFirstLine by rememberSaveable {
        mutableStateOf(true)
    }

    var isAppMinimized by rememberSaveable {
        mutableStateOf(false)
    }

    var showProgressDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var showColorPickerDialog by remember { mutableStateOf(false) }
    var previewBackgroundColor by remember { mutableStateOf(Color(0xFF242424)) }
    var previewTextColor by remember { mutableStateOf(Color.White) }
    var previewSecondaryTextColor by remember { mutableStateOf(Color.White.copy(alpha = 0.7f)) }

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedIndices = remember { mutableStateListOf<Int>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    var isAnimating by remember { mutableStateOf(false) }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedIndices.clear()
    }

    val maxSelectionLimit = 5

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast.makeText(
                context,
                context.getString(R.string.max_selection_limit, maxSelectionLimit),
                Toast.LENGTH_SHORT
            ).show()
            showMaxSelectionToast = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val visibleItemsInfo = lazyListState.layoutInfo.visibleItemsInfo
                val isCurrentLineVisible = visibleItemsInfo.any { it.index == currentLineIndex }
                if (isCurrentLineVisible) {
                    initialScrollDone = false
                }
                isAppMinimized = true
            } else if(event == Lifecycle.Event.ON_START) {
                isAppMinimized = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lines) {
        isSelectionModeActive = false
        selectedIndices.clear()
        // Clear stale diagnostics on every new lyrics load
        LyricsDiagnosticsHolder.clear()
    }

    // ── Toast when resolver selects a provider ────────────────────────────────
    LaunchedEffect(Unit) {
        var lastProvider = ""
        LyricsDiagnosticsHolder.state.collect { snap ->
            val prov = snap.providerName
            if (prov.isNotBlank() && prov != "—" && prov != lastProvider) {
                lastProvider = prov
                // Logcat (Task 7 — always logged)
                android.util.Log.i(
                    "LyricsResolver",
                    "[Resolver Selected] provider=$prov syncType=${snap.syncType} " +
                            "confidence=${"%.2f".format(snap.confidence)} lines=${snap.totalLines}"
                )
                // Toast (Task 8 — visible debug notification)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Lyrics: $prov | ${snap.syncType.name}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(lyricsDocument) {
        if (lyricsDocument.isNullOrEmpty() || !lyricsDocument.startsWith("[")) {
            currentLineIndex = -1
            return@LaunchedEffect
        }
        while (isActive) {
            delay(8)
            val sliderPosition = sliderPositionProvider()
            isSeeking = sliderPosition != null
            val position = sliderPosition ?: playerConnection.player.currentPosition
            currentPlaybackPosition = position
            
            val state = com.noxwizard.resonix.lyrics.playback.LyricsPlaybackResolver.resolve(
                lines = syncLines,
                currentPositionMs = position
            )
            currentLineIndex = state.activeLineIndex

            // ── Diagnostics update (same state as renderer) ───────────────────
            LyricsDiagnosticsHolder.updatePlayback(state, lines.size)
        }
    }

    var isManualScrolling by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(isSeeking, lastPreviewTime) {
        if (isSeeking) {
            lastPreviewTime = 0L
        } else if (lastPreviewTime != 0L) {
            delay(LyricsPreviewTime)
            if (!isManualScrolling) {
                lastPreviewTime = 0L
            } else {
            }
        }
    }

    LaunchedEffect(currentLineIndex, lastPreviewTime, initialScrollDone) {

        fun calculateOffset() = with(density) {
            if (currentLineIndex < 0 || currentLineIndex >= lines.size) return@with 0
            val currentItem = lines[currentLineIndex]
            val totalNewLines = currentItem.text.count { it == '\n' }

            val dpValue = if (landscapeOffset) 16.dp else 20.dp
            dpValue.toPx().toInt() * totalNewLines
        }

        if (!isSynced) return@LaunchedEffect

        suspend fun performSmoothPageScroll(targetIndex: Int, duration: Int = 600, isSeek: Boolean = false) {
            if (isAnimating) return

            isAnimating = true

            try {
                val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                if (itemInfo != null) {
                    val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                    val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)
                    val itemCenter = itemInfo.offset + itemInfo.size / 2
                    val offset = itemCenter - center

                    if (abs(offset) > 5) {
                        lazyListState.animateScrollBy(
                            value = offset.toFloat(),
                            animationSpec = if (isSeek) {
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessVeryLow
                                )
                            }
                        )
                    }
                } else {
                    val firstVisibleIndex = lazyListState.firstVisibleItemIndex
                    val distance = abs(targetIndex - firstVisibleIndex)

                    if (distance > 10) {
                        lazyListState.scrollToItem(targetIndex)
                        delay(16)
                        val newItemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                        if (newItemInfo != null) {
                            val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                            val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)
                            val itemCenter = newItemInfo.offset + newItemInfo.size / 2
                            val finalOffset = itemCenter - center
                            if (abs(finalOffset) > 5) {
                                lazyListState.animateScrollBy(
                                    value = finalOffset.toFloat(),
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            }
                        }
                    } else {
                        lazyListState.animateScrollToItem(targetIndex)
                    }
                }
            } finally {
                isAnimating = false
            }
        }

        if((currentLineIndex == 0 && shouldScrollToFirstLine) || !initialScrollDone) {
            shouldScrollToFirstLine = false
            val initialCenterIndex = kotlin.math.max(0, currentLineIndex)
            performSmoothPageScroll(initialCenterIndex, duration = 500)
            if(!isAppMinimized) {
                initialScrollDone = true
            }
        } else if (currentLineIndex != -1) {
            deferredCurrentLineIndex = currentLineIndex
            if (isSeeking) {
                val seekCenterIndex = kotlin.math.max(0, currentLineIndex)
                performSmoothPageScroll(seekCenterIndex, duration = 300, isSeek = true)
            } else if ((lastPreviewTime == 0L || currentLineIndex != previousLineIndex) && scrollLyrics && !isManualScrolling) {
                if (currentLineIndex != previousLineIndex) {
                    val centerTargetIndex = kotlin.math.max(0, currentLineIndex)
                    performSmoothPageScroll(centerTargetIndex, duration = 600)
                }
            }
        }
        if(currentLineIndex > 0) {
            shouldScrollToFirstLine = true
        }
        previousLineIndex = currentLineIndex
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
    ) {

        if (lyrics == LYRICS_NOT_FOUND) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(0.5f)
                )
            }
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "prism")
            val prismPhaseState = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "prismPhase"
            )

            LazyColumn(
            state = lazyListState,
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Top)
                .add(WindowInsets(top = maxHeight / 2, bottom = maxHeight / 2))
                .asPaddingValues(),
            modifier = Modifier
                .smoothFadingEdge(vertical = 72.dp)
                .nestedScroll(remember {
                    object : NestedScrollConnection {
                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                                lastPreviewTime = System.currentTimeMillis()
                                isManualScrolling = true
                            }
                            return super.onPostScroll(consumed, available, source)
                        }

                        override suspend fun onPostFling(
                            consumed: Velocity,
                            available: Velocity
                        ): Velocity {
                            if (!isSelectionModeActive) {
                                lastPreviewTime = System.currentTimeMillis()
                                isManualScrolling = true
                            }
                            return super.onPostFling(consumed, available)
                        }
                    }
                })
        ) {
            val displayedCurrentLineIndex =
                if (isSeeking || isSelectionModeActive) deferredCurrentLineIndex else currentLineIndex

            if (lyrics == null) {
                item {
                    ShimmerHost {
                        repeat(10) {
                            Box(
                                contentAlignment = when (lyricsTextPosition) {
                                    LyricsPosition.LEFT -> Alignment.CenterStart
                                    LyricsPosition.CENTER -> Alignment.Center
                                    LyricsPosition.RIGHT -> Alignment.CenterEnd
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 4.dp)
                            ) {
                                TextPlaceholder()
                            }
                        }
                    }
                }
            } else {
                // Hoist anticipation progress so any line can read it without per-item nesting.
                val hoistedAnticipationProgress: Float = if (themeSpec.useColorAnticipation && displayedCurrentLineIndex >= 0) {
                    val lineStartMs  = lines.getOrNull(displayedCurrentLineIndex)?.time ?: 0L
                    val lineEndMs    = lines.getOrNull(displayedCurrentLineIndex + 1)?.time ?: (lineStartMs + 3000L)
                    val lineDuration = (lineEndMs - lineStartMs).coerceAtLeast(1L)
                    val rawProgress  = (currentPlaybackPosition - lineStartMs).toFloat() / lineDuration
                    val threshold    = themeSpec.anticipationThreshold
                    if (rawProgress >= threshold) ((rawProgress - threshold) / (1f - threshold)).coerceIn(0f, 1f) else 0f
                } else 0f

                itemsIndexed(
                    items = lines,
                    key = { index, _ -> index }
                ) { index, item ->
                    val isSelected = selectedIndices.contains(index)

                    // ── FLOW theme: spec-driven opacity hierarchy ─────────────────────────
                    // Previous lines: previousLineAlpha + blur
                    // Active line: activeLineAlpha (no blur)
                    // Future lines: futureAlphaFalloff (no blur)
                    val isPreviousLine = isSynced && index < displayedCurrentLineIndex
                    val isFutureLine   = isSynced && index > displayedCurrentLineIndex
                    val isActiveLineForAnimation = index == displayedCurrentLineIndex && isSynced
                    val isNextLineForAnimation   = index == displayedCurrentLineIndex + 1 && isSynced
                    val prismPhase = prismPhaseState.value

                    // ── Prism spectrum color (Purple→Pink→Amber) ──────────────────────
                    fun spectrumColorAt(phase: Float): Color = when {
                        phase < 1f -> androidx.compose.ui.graphics.lerp(Color(0xFF8E44AD), Color(0xFFE75480), phase)
                        phase < 2f -> androidx.compose.ui.graphics.lerp(Color(0xFFE75480), Color(0xFFE79C3C), phase - 1f)
                        else       -> androidx.compose.ui.graphics.lerp(Color(0xFFE79C3C), Color(0xFF8E44AD), phase - 2f)
                    }
                    val currentSpectrumColor = spectrumColorAt(prismPhase)

                    val anticipationProgress = hoistedAnticipationProgress

                    // ── Depth transfer: per-item scale Animatable with overshoot ────────
                    val depthScaleAnimatable = remember { androidx.compose.animation.core.Animatable(if (isActiveLineForAnimation) 1.0f else 0.92f) }
                    LaunchedEffect(isActiveLineForAnimation) {
                        if (themeSpec.useDepthTransfer) {
                            if (isActiveLineForAnimation) {
                                // Incoming: 0.92 → 1.05 → 1.00
                                depthScaleAnimatable.animateTo(1.05f, tween(280, easing = FastOutSlowInEasing))
                                depthScaleAnimatable.animateTo(1.00f, tween(220, easing = FastOutSlowInEasing))
                            } else {
                                // Outgoing: retreat to 0.92
                                depthScaleAnimatable.animateTo(0.92f, tween(350, easing = FastOutSlowInEasing))
                            }
                        }
                    }

                    // ── White fill sweep animation (left → right) ────────────────────────
                    val whiteFillAnimatable = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(isActiveLineForAnimation) {
                        if (isActiveLineForAnimation) {
                            whiteFillAnimatable.snapTo(0f)
                            // Delay briefly to let spectrum color linger, then sweep white in
                            kotlinx.coroutines.delay(300)
                            whiteFillAnimatable.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(1200, easing = FastOutSlowInEasing)
                            )
                        } else {
                            whiteFillAnimatable.snapTo(0f)
                        }
                    }
                    val whiteFillProgress = whiteFillAnimatable.value

                    // ── Resolve final colors ─────────────────────────────────────────────
                    // Next line anticipation: lerp from neutral → spectrum (no black dip)
                    val anticipatedNextLineColor = if (themeSpec.useColorAnticipation && isNextLineForAnimation && anticipationProgress > 0f) {
                        val neutralColor = expressiveAccent.copy(alpha = 0.7f)
                        androidx.compose.ui.graphics.lerp(neutralColor, currentSpectrumColor, anticipationProgress)
                    } else expressiveAccent.copy(alpha = 0.7f)

                    // Active line brush: white sweeps in from left, spectrum color on the right
                    val activeLineBrush: androidx.compose.ui.graphics.Brush? = if (isActiveLineForAnimation && themeSpec.useAnimatedSpectrumColors) {
                        val edge = whiteFillProgress.coerceIn(0.001f, 0.999f)
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f          to Color.White,
                                edge        to Color.White,
                                edge        to currentSpectrumColor,
                                1f          to currentSpectrumColor
                            )
                        )
                    } else null

                    // Legacy scalar color (used by word-synced path which has its own span logic)
                    val activeSpectrumColor = when {
                        themeSpec.useAnimatedSpectrumColors && isActiveLineForAnimation ->
                            androidx.compose.ui.graphics.lerp(currentSpectrumColor, Color.White, whiteFillProgress)
                        themeSpec.useColorAnticipation && isNextLineForAnimation && anticipationProgress > 0f ->
                            anticipatedNextLineColor
                        else -> expressiveAccent
                    }

                    // White glow on active line, spectrum-tinted glow on next line
                    val activeGlowRadius = when {
                        isActiveLineForAnimation -> themeSpec.activeLineGlowRadiusPx
                        isNextLineForAnimation && anticipationProgress > 0f ->
                            themeSpec.activeLineGlowRadiusPx * anticipationProgress * 0.45f
                        else -> 0f
                    }
                    val activeGlowColor = when {
                        isActiveLineForAnimation -> Color.White.copy(alpha = 0.40f)
                        isNextLineForAnimation && anticipationProgress > 0f ->
                            currentSpectrumColor.copy(alpha = anticipationProgress * 0.30f)
                        else -> Color.Transparent
                    }

                    val lineYOffset = 0f // Depth transfer via scale; no vertical float

                    val targetAlpha = when {
                        !isSynced || (isSelectionModeActive && isSelected) -> 1f
                        index == displayedCurrentLineIndex -> themeSpec.activeLineAlpha
                        isPreviousLine -> themeSpec.previousLineAlpha
                        else -> { // future lines
                            val futureDistance = index - displayedCurrentLineIndex
                            themeSpec.futureAlphaFalloff.getOrElse(futureDistance - 1) {
                                themeSpec.futureAlphaFalloff.lastOrNull() ?: 0.15f
                            }
                        }
                    }

                    val animatedAlpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = tween(
                            durationMillis = if (isPreviousLine) (themeSpec.previousAlphaDurationMs ?: themeSpec.transitionDurationMs) else themeSpec.transitionDurationMs,
                            delayMillis = if (isPreviousLine) themeSpec.transitionDelayMs else 0,
                            easing = themeSpec.transitionEasing
                        ),
                        label = "lyricAlpha"
                    )

                    val targetScale = when {
                        !isSynced || index == displayedCurrentLineIndex -> themeSpec.activeLineScale
                        else -> {
                            val scaleDistance = abs(index - displayedCurrentLineIndex)
                            themeSpec.inactiveScaleFalloff.getOrElse(scaleDistance - 1) {
                                themeSpec.inactiveScaleFalloff.lastOrNull() ?: 0.92f
                            }
                        }
                    }

                    val animatedScale by animateFloatAsState(
                        targetValue = targetScale,
                        animationSpec = if (themeSpec.useSpringForScale) {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        } else {
                            tween(
                                durationMillis = themeSpec.scaleDurationMs ?: themeSpec.transitionDurationMs,
                                delayMillis = if (isPreviousLine) themeSpec.transitionDelayMs else 0,
                                easing = themeSpec.transitionEasing
                            )
                        },
                        label = "lyricScale"
                    )

                    // Blur: ONLY previous lines per Spotifont CSS spec
                    val targetBlurRadius = when {
                        !isSynced -> 0f
                        isPreviousLine && themeSpec.blurPreviousLines -> themeSpec.blurRadiusPx
                        isFutureLine && themeSpec.blurFutureLines -> {
                            val distance = index - displayedCurrentLineIndex
                            themeSpec.futureBlurFalloffPx.getOrElse(distance - 1) {
                                themeSpec.futureBlurFalloffPx.lastOrNull() ?: themeSpec.blurRadiusPx
                            }
                        }
                        else -> 0f
                    }

                    val animatedBlurRadius by animateFloatAsState(
                        targetValue = targetBlurRadius,
                        animationSpec = tween(
                            durationMillis = if (isPreviousLine) (themeSpec.previousBlurDurationMs ?: themeSpec.transitionDurationMs) else themeSpec.transitionDurationMs,
                            delayMillis = if (isPreviousLine) themeSpec.transitionDelayMs else 0,
                            easing = themeSpec.transitionEasing
                        ),
                        label = "lyricBlur"
                    )

                    val itemModifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            enabled = true,
                            onClick = {
                                if (isSelectionModeActive) {
                                    if (isSelected) {
                                        selectedIndices.remove(index)
                                        if (selectedIndices.isEmpty()) {
                                            isSelectionModeActive = false
                                        }
                                    } else {
                                        if (selectedIndices.size < maxSelectionLimit) {
                                            selectedIndices.add(index)
                                        } else {
                                            showMaxSelectionToast = true
                                        }
                                    }
                                } else if (isSynced && changeLyrics) {
                                    playerConnection.seekTo(item.time)
                                    scope.launch {
                                        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                        if (itemInfo != null) {
                                            val viewportHeight = lazyListState.layoutInfo.viewportEndOffset - lazyListState.layoutInfo.viewportStartOffset
                                            val center = lazyListState.layoutInfo.viewportStartOffset + (viewportHeight / 2)
                                            val itemCenter = itemInfo.offset + itemInfo.size / 2
                                            val offset = itemCenter - center

                                            if (abs(offset) > 10) {
                                                lazyListState.animateScrollBy(
                                                    value = offset.toFloat(),
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                                        stiffness = Spring.StiffnessVeryLow
                                                    )
                                                )
                                            }
                                        } else {
                                            lazyListState.animateScrollToItem(index)
                                        }
                                    }
                                    lastPreviewTime = 0L
                                }
                            },
                            onLongClick = {
                                if (!isSelectionModeActive) {
                                    isSelectionModeActive = true
                                    selectedIndices.add(index)
                                } else if (!isSelected && selectedIndices.size < maxSelectionLimit) {
                                    selectedIndices.add(index)
                                } else if (!isSelected) {
                                    showMaxSelectionToast = true
                                }
                            }
                        )
                        .background(
                            color = if (isSelected && isSelectionModeActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(
                            horizontal = 24.dp,
                            vertical = (8 + (lyricsTextSize - 24f) * 0.6f).dp
                        )
                        .alpha(animatedAlpha)
                        .graphicsLayer {
                            val scale = if (themeSpec.useDepthTransfer) depthScaleAnimatable.value else animatedScale
                            scaleX = scale
                            scaleY = scale
                            translationY = lineYOffset
                            transformOrigin = when (lyricsTextPosition) {
                                LyricsPosition.LEFT -> androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                                LyricsPosition.RIGHT -> androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                                LyricsPosition.CENTER -> androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                            }
                        }
                        .lyricsBlurEffect(animatedBlurRadius)

                    Column(
                        modifier = itemModifier,
                        horizontalAlignment = when (lyricsTextPosition) {
                            LyricsPosition.LEFT -> Alignment.Start
                            LyricsPosition.CENTER -> Alignment.CenterHorizontally
                            LyricsPosition.RIGHT -> Alignment.End
                        }
                    ) {
                        val isActiveLine = index == displayedCurrentLineIndex && isSynced
                        val isNextLine = index == displayedCurrentLineIndex + 1 && isSynced
                        
                        val lineColor = when {
                            isActiveLine -> expressiveAccent
                            themeSpec.tintNextLineWithAccent && isNextLine -> themeAccent.copy(alpha = 0.9f)
                            else -> expressiveAccent.copy(alpha = 0.7f)
                        }
                        val alignment = when (lyricsTextPosition) {
                            LyricsPosition.LEFT   -> TextAlign.Left
                            LyricsPosition.CENTER -> TextAlign.Center
                            LyricsPosition.RIGHT  -> TextAlign.Right
                        }

                        val hasWordTimings = item.words?.isNotEmpty() == true
                        // Use theme spec line height instead of raw lyricsLineSpacing
                        val scaledTextSize = lyricsTextSize * themeSpec.fontSizeMultiplier
                        val specLineHeight = (scaledTextSize * themeSpec.lineHeightMultiplier).sp

                        if (hasWordTimings && item.words != null) {

                            val styledText = buildAnnotatedString {
                                item.words.forEachIndexed { wordIndex, word ->
                                    val wordStartMs = (word.startTime * 1000).toLong()
                                    val wordEndMs = (word.endTime * 1000).toLong()
                                    val wordDuration = wordEndMs - wordStartMs

                                    val isWordActive = isActiveLine && currentPlaybackPosition >= wordStartMs && currentPlaybackPosition <= wordEndMs
                                    val hasWordPassed = isActiveLine && currentPlaybackPosition > wordEndMs

                                    val transitionProgress = when {
                                        !isActiveLine -> 0f
                                        hasWordPassed -> 1f
                                        isWordActive && wordDuration > 0 -> {
                                            val elapsed = currentPlaybackPosition - wordStartMs
                                            val linear = (elapsed.toFloat() / wordDuration).coerceIn(0f, 1f)
                                            linear * linear * (3f - 2f * linear)
                                        }
                                        else -> 0f
                                    }

                                    val wordColor = when {
                                        !isActiveLine -> {
                                            if (themeSpec.tintNextLineWithAccent && isNextLine) {
                                                themeAccent.copy(alpha = 0.9f)
                                            } else {
                                                expressiveAccent.copy(alpha = 0.7f)
                                            }
                                        }
                                        hasWordPassed -> expressiveAccent.copy(alpha = themeSpec.passedWordAlpha)
                                        isWordActive -> {
                                            if (themeSpec.activeLineProgressFill) {
                                                androidx.compose.ui.graphics.lerp(
                                                    themeAccent.copy(alpha = 0.9f),
                                                    expressiveAccent.copy(alpha = themeSpec.passedWordAlpha),
                                                    transitionProgress
                                                )
                                            } else {
                                                expressiveAccent.copy(alpha = 0.5f + (0.5f * transitionProgress))
                                            }
                                        }
                                        else -> {
                                            if (themeSpec.activeLineProgressFill) {
                                                themeAccent.copy(alpha = 0.9f)
                                            } else {
                                                expressiveAccent.copy(alpha = themeSpec.futureWordAlpha)
                                            }
                                        }
                                    }

                                    val wordWeight = when {
                                        !isActiveLine -> themeSpec.inactiveFontWeight
                                        hasWordPassed -> themeSpec.activeFontWeight
                                        isWordActive  -> FontWeight.ExtraBold
                                        else          -> FontWeight.Medium
                                    }

                                    // Per-word glow via SpanStyle shadow — only on active word.
                                    // Shadow(color, offset=zero, blurRadius) produces soft bloom
                                    // without affecting layout or requiring separate composable nodes.
                                    val wordShadow: Shadow? = if (themeSpec.glowActiveWord && isWordActive && isActiveLine) {
                                        Shadow(
                                            color = if (themeSpec.useAccentForWordGlow) themeAccent.copy(alpha = themeSpec.activeWordGlowAlpha)
                                                    else Color.White.copy(alpha = themeSpec.activeWordGlowAlpha),
                                            offset = Offset.Zero,
                                            blurRadius = themeSpec.wordGlowRadiusPx
                                        )
                                    } else null

                                    val wordBaselineShift = if (themeSpec.wordLiftEffect && isActiveLine) {
                                        if (hasWordPassed) null
                                        else if (isWordActive) androidx.compose.ui.text.style.BaselineShift(-0.15f * (1f - transitionProgress))
                                        else androidx.compose.ui.text.style.BaselineShift(-0.15f)
                                    } else null

                                    withStyle(
                                        style = SpanStyle(
                                            color = wordColor,
                                            fontWeight = wordWeight,
                                            shadow = wordShadow,
                                            baselineShift = wordBaselineShift
                                        )
                                    ) {
                                        append(word.text)
                                    }

                                    if (wordIndex < item.words.size - 1) {
                                        append(" ")
                                    }
                                }
                            }

                            Text(
                                text = styledText,
                                fontFamily = themeSpec.fontFamily,
                                fontSize = scaledTextSize.sp,
                                textAlign = alignment,
                                lineHeight = specLineHeight,
                                style = if (activeGlowRadius > 0f) {
                                    androidx.compose.ui.text.TextStyle(
                                        shadow = Shadow(
                                            color = activeGlowColor,
                                            offset = Offset.Zero,
                                            blurRadius = activeGlowRadius
                                        )
                                    )
                                } else androidx.compose.ui.text.TextStyle.Default
                            )
                        } else {
                            val popInScale = remember { androidx.compose.animation.core.Animatable(1f) }

                            LaunchedEffect(isActiveLine) {
                                if (isActiveLine) {
                                    popInScale.snapTo(0.96f)
                                    popInScale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }

                            Text(
                                text = item.text,
                                fontFamily = themeSpec.fontFamily,
                                fontSize = scaledTextSize.sp,
                                color = if (!isActiveLine && !(isNextLineForAnimation && anticipationProgress > 0f)) lineColor else Color.Unspecified,
                                textAlign = alignment,
                                fontWeight = if (isActiveLine) themeSpec.activeFontWeight else themeSpec.inactiveFontWeight,
                                lineHeight = specLineHeight,
                                style = androidx.compose.ui.text.TextStyle(
                                    brush = when {
                                        isActiveLine && activeLineBrush != null -> activeLineBrush
                                        isNextLineForAnimation && anticipationProgress > 0f ->
                                            Brush.horizontalGradient(listOf(anticipatedNextLineColor, anticipatedNextLineColor))
                                        else -> null
                                    },
                                    shadow = if (activeGlowRadius > 0f) Shadow(
                                        color = activeGlowColor,
                                        offset = Offset.Zero,
                                        blurRadius = activeGlowRadius
                                    ) else null
                                ),
                                modifier = if (isActiveLine) Modifier.graphicsLayer {
                                    scaleX = popInScale.value
                                    scaleY = popInScale.value
                                } else Modifier
                            )
                        }
                        if (romanizeJapaneseLyrics || romanizeKoreanLyrics) {

                            val romanizedText by item.romanizedTextFlow.collectAsState()
                            val romanizedFontSize = 16.sp
                            romanizedText?.let { romanized ->

                                val romanizedModifier = Modifier
                                    .padding(top = 2.dp)
                                    .then(
                                        if (themeSpec.romanizedBackgroundAlpha > 0f) {
                                            Modifier
                                                .padding(top = 4.dp)
                                                .background(
                                                    color = themeAccent.copy(alpha = themeSpec.romanizedBackgroundAlpha),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = themeAccent.copy(alpha = themeSpec.romanizedBackgroundAlpha * 2f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                        } else Modifier
                                    )

                                if (hasWordTimings && item.words != null && isActiveLine) {

                                    val romanizedWords = romanized.split(" ")
                                    val mainWords = item.words

                                    val romanizedStyledText = buildAnnotatedString {
                                        romanizedWords.forEachIndexed { romIndex, romWord ->

                                            val wordIndex = (romIndex.toFloat() / romanizedWords.size * mainWords.size).toInt().coerceIn(0, mainWords.size - 1)
                                            val word = mainWords.getOrNull(wordIndex)

                                            if (word != null) {
                                                val wordStartMs = (word.startTime * 1000).toLong()
                                                val wordEndMs = (word.endTime * 1000).toLong()
                                                val isWordActive = currentPlaybackPosition in wordStartMs..wordEndMs
                                                val hasWordPassed = currentPlaybackPosition > wordEndMs

                                                val romColor = when {
                                                    !isActiveLine -> expressiveAccent.copy(alpha = themeSpec.romanizedTextAlpha)
                                                    isWordActive -> expressiveAccent.copy(alpha = (themeSpec.romanizedTextAlpha + 0.3f).coerceAtMost(1f))
                                                    hasWordPassed -> expressiveAccent.copy(alpha = (themeSpec.romanizedTextAlpha + 0.2f).coerceAtMost(1f))
                                                    else -> expressiveAccent.copy(alpha = (themeSpec.romanizedTextAlpha - 0.1f).coerceAtLeast(0.2f))
                                                }

                                                withStyle(
                                                    style = SpanStyle(
                                                        color = romColor,
                                                        fontWeight = if (isWordActive) FontWeight.Medium else FontWeight.Normal
                                                    )
                                                ) {
                                                    append(romWord)
                                                }
                                            } else {
                                                withStyle(
                                                    style = SpanStyle(
                                                        color = expressiveAccent.copy(alpha = themeSpec.romanizedTextAlpha),
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                ) {
                                                    append(romWord)
                                                }
                                            }

                                            if (romIndex < romanizedWords.size - 1) {
                                                append(" ")
                                            }
                                        }
                                    }

                                    Text(
                                        text = romanizedStyledText,
                                        fontFamily = themeSpec.fontFamily,
                                        fontStyle = themeSpec.romanizedFontStyle,
                                        fontSize = romanizedFontSize,
                                        textAlign = when (lyricsTextPosition) {
                                            LyricsPosition.LEFT -> TextAlign.Left
                                            LyricsPosition.CENTER -> TextAlign.Center
                                            LyricsPosition.RIGHT -> TextAlign.Right
                                        },
                                        modifier = romanizedModifier
                                    )
                                } else {

                                    Text(
                                        text = romanized,
                                        fontFamily = themeSpec.fontFamily,
                                        fontStyle = themeSpec.romanizedFontStyle,
                                        fontSize = romanizedFontSize,
                                        color = expressiveAccent.copy(alpha = if (isActiveLine) (themeSpec.romanizedTextAlpha + 0.1f).coerceAtMost(1f) else themeSpec.romanizedTextAlpha),
                                        textAlign = when (lyricsTextPosition) {
                                            LyricsPosition.LEFT -> TextAlign.Left
                                            LyricsPosition.CENTER -> TextAlign.Center
                                            LyricsPosition.RIGHT -> TextAlign.Right
                                        },
                                        fontWeight = FontWeight.Normal,
                                        modifier = romanizedModifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isManualScrolling && scrollLyrics && !isSelectionModeActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable {
                            isManualScrolling = false
                            lastPreviewTime = 0L
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.resume_autoscroll),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (isSelectionModeActive) {
            mediaMetadata?.let { metadata ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp), 
                    contentAlignment = Alignment.Center
                ) {

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(48.dp) 
                                .background(
                                    color = Color.Black.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    isSelectionModeActive = false
                                    selectedIndices.clear()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.close),
                                contentDescription = stringResource(R.string.cancel),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .background(
                                    color = if (selectedIndices.isNotEmpty()) 
                                        Color.White.copy(alpha = 0.9f) 
                                    else 
                                        Color.White.copy(alpha = 0.5f), 
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable(enabled = selectedIndices.isNotEmpty()) {
                                    if (selectedIndices.isNotEmpty()) {
                                        val sortedIndices = selectedIndices.sorted()
                                        val selectedLyricsText = sortedIndices
                                            .mapNotNull { lines.getOrNull(it)?.text }
                                            .joinToString("\n")

                                        if (selectedLyricsText.isNotBlank()) {
                                            shareDialogData = Triple(
                                                selectedLyricsText,
                                                metadata.title,
                                                metadata.artists.joinToString { it.name }
                                            )
                                            showShareDialog = true
                                        }
                                        isSelectionModeActive = false
                                        selectedIndices.clear()
                                    }
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.share),
                                contentDescription = stringResource(R.string.share_selected),
                                tint = Color.Black, 
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.share),
                                color = Color.Black, 
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

    }

    if (showProgressDialog) {
        BasicAlertDialog(onDismissRequest = {  }) {
            Card( 
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.padding(32.dp)) {
                    Text(
                        text = stringResource(R.string.generating_image) + "\n" + stringResource(R.string.please_wait),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showShareDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!! 
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    val songLink = "https://music.youtube.com/watch?v=${mediaMetadata?.id}"

                                    putExtra(Intent.EXTRA_TEXT, "\"$lyricsText\"\n\n$songTitle - $artists\n$songLink")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_lyrics)))
                                showShareDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share), 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {

                                shareDialogData = Triple(lyricsText, songTitle, artists)
                                showColorPickerDialog = true
                                showShareDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share), 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable { showShareDialog = false }
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showColorPickerDialog && shareDialogData != null) {
        val (lyricsText, songTitle, artists) = shareDialogData!!
        val coverUrl = mediaMetadata?.thumbnailUrl
        val paletteColors = remember { mutableStateListOf<Color>() }

        val previewCardWidth = configuration.screenWidthDp.dp * 0.90f
        val previewPadding = 20.dp * 2
        val previewBoxPadding = 28.dp * 2
        val previewAvailableWidth = previewCardWidth - previewPadding - previewBoxPadding
        val previewBoxHeight = 340.dp
        val headerFooterEstimate = (48.dp + 14.dp + 16.dp + 20.dp + 8.dp + 28.dp * 2)
        val previewAvailableHeight = previewBoxHeight - headerFooterEstimate

        val textStyleForMeasurement = TextStyle(
            color = previewTextColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        val textMeasurer = rememberTextMeasurer()

        rememberAdjustedFontSize(
            text = lyricsText,
            maxWidth = previewAvailableWidth,
            maxHeight = previewAvailableHeight,
            density = density,
            initialFontSize = 50.sp,
            minFontSize = 22.sp,
            style = textStyleForMeasurement,
            textMeasurer = textMeasurer
        )

        LaunchedEffect(coverUrl) {
            if (coverUrl != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val loader = ImageLoader(context)
                        val req = ImageRequest.Builder(context).data(coverUrl).allowHardware(false).build()
                        val result = loader.execute(req)
                        val bmp = result.image?.toBitmap()
                        if (bmp != null) {
                            val palette = Palette.from(bmp).generate()
                            val swatches = palette.swatches.sortedByDescending { it.population }
                            val colors = swatches.map { Color(it.rgb) }
                                .filter { color ->
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                    hsv[1] > 0.2f
                                }
                            paletteColors.clear()
                            paletteColors.addAll(colors.take(5))
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        BasicAlertDialog(onDismissRequest = { showColorPickerDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.customize_colors),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .padding(8.dp)
                    ) {
                        LyricsImageCard(
                            lyricText = lyricsText,
                            mediaMetadata = mediaMetadata ?: return@Box,
                            backgroundColor = previewBackgroundColor,
                            textColor = previewTextColor,
                            secondaryTextColor = previewSecondaryTextColor
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(text = stringResource(id = R.string.background_color), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        (paletteColors + listOf(Color(0xFF242424), Color(0xFF121212), Color.White, Color.Black, Color(0xFFF5F5F5))).distinct().take(8).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, shape = RoundedCornerShape(8.dp))
                                    .clickable { previewBackgroundColor = color }
                                    .border(2.dp, if (previewBackgroundColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    Text(text = stringResource(id = R.string.text_color), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        (paletteColors + listOf(Color.White, Color.Black, Color(0xFF1DB954))).distinct().take(8).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, shape = RoundedCornerShape(8.dp))
                                    .clickable { previewTextColor = color }
                                    .border(2.dp, if (previewTextColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    Text(text = stringResource(id = R.string.secondary_text_color), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        (paletteColors.map { it.copy(alpha = 0.7f) } + listOf(Color.White.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.7f), Color(0xFF1DB954))).distinct().take(8).forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(color, shape = RoundedCornerShape(8.dp))
                                    .clickable { previewSecondaryTextColor = color }
                                    .border(2.dp, if (previewSecondaryTextColor == color) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            showColorPickerDialog = false
                            showProgressDialog = true
                            scope.launch {
                                try {
                                    val screenWidth = configuration.screenWidthDp
                                    val screenHeight = configuration.screenHeightDp

                                    val image = ComposeToImage.createLyricsImage(
                                        context = context,
                                        coverArtUrl = coverUrl,
                                        songTitle = songTitle,
                                        artistName = artists,
                                        lyrics = lyricsText,
                                        width = (screenWidth * density.density).toInt(),
                                        height = (screenHeight * density.density).toInt(),
                                        backgroundColor = previewBackgroundColor.toArgb(),
                                        textColor = previewTextColor.toArgb(),
                                        secondaryTextColor = previewSecondaryTextColor.toArgb(),
                                    )
                                    val timestamp = System.currentTimeMillis()
                                    val filename = "lyrics_$timestamp"
                                    val uri = ComposeToImage.saveBitmapAsFile(context, image, filename)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Lyrics"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to create image: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    showProgressDialog = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(id = R.string.share))
                    }
                }
            }
        }
    }
}
}



private const val Resonix_AUTO_SCROLL_DURATION = 1500L 
private const val Resonix_INITIAL_SCROLL_DURATION = 1000L 
private const val Resonix_SEEK_DURATION = 800L 
private const val Resonix_FAST_SEEK_DURATION = 600L 

val LyricsPreviewTime = 2.seconds


