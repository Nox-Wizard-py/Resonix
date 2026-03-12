package com.noxwizard.resonix.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.datastore.preferences.core.edit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.noxwizard.resonix.BuildConfig
import com.noxwizard.resonix.LocalDatabase
import com.noxwizard.resonix.LocalDownloadUtil
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.LocalPlayerConnection
import com.noxwizard.resonix.LocalSyncUtils
import com.noxwizard.resonix.MainActivity
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.AppBarHeight
import com.noxwizard.resonix.constants.DarkModeKey
import com.noxwizard.resonix.constants.DefaultOpenTabKey
import com.noxwizard.resonix.constants.DisableBlurKey
import com.noxwizard.resonix.constants.CustomThemeColorKey
import com.noxwizard.resonix.ui.theme.ThemeSeedPaletteCodec
import com.noxwizard.resonix.constants.UseSystemFontKey
import com.noxwizard.resonix.constants.DisableScreenshotKey
import com.noxwizard.resonix.constants.DynamicThemeKey
import com.noxwizard.resonix.constants.HasPressedStarKey
import com.noxwizard.resonix.constants.LaunchCountKey
import com.noxwizard.resonix.constants.MiniPlayerBottomSpacing
import com.noxwizard.resonix.constants.MiniPlayerHeight
import com.noxwizard.resonix.constants.NavigationBarAnimationSpec
import com.noxwizard.resonix.constants.NavigationBarHeight
import com.noxwizard.resonix.constants.PauseSearchHistoryKey
import com.noxwizard.resonix.constants.PureBlackKey
import com.noxwizard.resonix.constants.RemindAfterKey
import com.noxwizard.resonix.constants.SearchSource
import com.noxwizard.resonix.constants.SearchSourceKey
import com.noxwizard.resonix.constants.FrostedGlassNavBarKey
import com.noxwizard.resonix.constants.SlimNavBarHeight
import com.noxwizard.resonix.constants.SlimNavBarKey
import com.noxwizard.resonix.constants.UseNewMiniPlayerDesignKey
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.SearchHistory
import com.noxwizard.resonix.extensions.toEnum
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.innertube.models.WatchEndpoint
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playback.DownloadUtil
import com.noxwizard.resonix.playback.MusicService
import com.noxwizard.resonix.playback.PlayerConnection
import com.noxwizard.resonix.playback.queues.YouTubeQueue
import com.noxwizard.resonix.ui.component.AccountSettingsDialog
import com.noxwizard.resonix.ui.component.BottomSheetMenu
import com.noxwizard.resonix.ui.component.BottomSheetPage
import com.noxwizard.resonix.ui.component.LocalBottomSheetPageState
import com.noxwizard.resonix.ui.component.LocalMenuState
import com.noxwizard.resonix.ui.component.ResonixTopBar
import com.noxwizard.resonix.ui.component.StarDialog
import com.noxwizard.resonix.ui.component.UpdateSheet
import com.noxwizard.resonix.ui.utils.handleDeepLinkIntent
import com.noxwizard.resonix.ui.component.rememberBottomSheetState
import com.noxwizard.resonix.ui.component.shimmer.ShimmerTheme
import com.noxwizard.resonix.ui.menu.YouTubeSongMenu
import com.noxwizard.resonix.ui.screens.Screens
import com.noxwizard.resonix.ui.screens.navigationBuilder
import com.noxwizard.resonix.ui.screens.settings.DarkMode
import com.noxwizard.resonix.ui.screens.settings.NavigationTab
import com.noxwizard.resonix.ui.theme.ColorSaver
import com.noxwizard.resonix.ui.screens.settings.ThemePalettes
import com.noxwizard.resonix.ui.theme.DefaultThemeColor
import com.noxwizard.resonix.ui.theme.ResonixTheme
import com.noxwizard.resonix.ui.theme.extractThemeColor
import com.noxwizard.resonix.ui.utils.appBarScrollBehavior
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.ui.utils.resetHeightOffset
import com.noxwizard.resonix.utils.SyncUtils
import com.noxwizard.resonix.utils.Updater
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get
import com.noxwizard.resonix.utils.rememberEnumPreference
import com.noxwizard.resonix.utils.rememberPreference
import com.noxwizard.resonix.utils.reportException
import com.noxwizard.resonix.viewmodels.HomeViewModel
import com.valentinilk.shimmer.LocalShimmerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.time.Duration.Companion.days


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResonixApp(
    database: MusicDatabase,
    playerConnection: PlayerConnection?,
    downloadUtil: DownloadUtil,
    syncUtils: SyncUtils,
    latestVersionName: String,
    currentIntent: Intent?,
    onSetSystemBarAppearance: (Boolean) -> Unit,
    onSetWindowFlags: (Boolean) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
            Updater.getLatestVersionName().onSuccess {
                // We don't have update callback here to write back to main activity if it was tracking it.
                // But MainActivity passed latestVersionName. 
                // Actually MainActivity.latestVersionName was a state. 
                // If we want to update it, we need a callback.
                // Or ResonixApp manages it.
                // MainActivity managed it. Let's make ResonixApp manage it if possible.
                // But ResonixApp takes it as param.
                // Let's change ResonixApp to manage it locally or take a callback.
                // For now, I'll ignore the update check here since MainActivity does it? 
                // Wait, MainActivity had it in setContent. So I should move it here.
            }
        }
    }
    // Managing local state for version name to avoid callback hell if MainActivity doesn't need it.
    var localLatestVersionName by remember { mutableStateOf(latestVersionName) }
    
    // Update check logic
    LaunchedEffect(Unit) {
         if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
             Updater.getLatestVersionName().onSuccess {
                 localLatestVersionName = it
             }
         }
    }

    val bottomSheetPageState = remember { com.noxwizard.resonix.ui.component.BottomSheetPageState() }
    val menuState = remember { com.noxwizard.resonix.ui.component.MenuState() }
    val uriHandler = LocalUriHandler.current
    val releaseNotesState = remember { mutableStateOf<String?>(null) }

    val updateSheetContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        UpdateSheet(
            localLatestVersionName = localLatestVersionName,
            releaseNotesState = releaseNotesState,
            uriHandler = uriHandler
        )
    }

    LaunchedEffect(localLatestVersionName) {
        if (localLatestVersionName != BuildConfig.VERSION_NAME) {
            Updater.getLatestReleaseNotes().onSuccess {
                releaseNotesState.value = it
            }.onFailure {
                releaseNotesState.value = null
            }
            bottomSheetPageState.show(updateSheetContent)
        }
    }
    
    // Screenshot security
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        context.dataStore.data
            .map { it[DisableScreenshotKey] ?: false }
            .distinctUntilChanged()
            .collectLatest { disable ->
                 onSetWindowFlags(disable)
            }
    }


    val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme =
        remember(darkTheme, isSystemInDarkTheme) {
            if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
        }
    LaunchedEffect(useDarkTheme) {
        onSetSystemBarAppearance(useDarkTheme)
    }
    val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
    val pureBlack = pureBlackEnabled && useDarkTheme
    val useSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
    val customThemeColorId by rememberPreference(CustomThemeColorKey, defaultValue = ThemePalettes.Default.id)

    val paletteThemeColor = remember(customThemeColorId) {
        ThemeSeedPaletteCodec.decodeFromPreference(customThemeColorId)?.primary
            ?: ThemePalettes.findById(customThemeColorId)?.primary
            ?: DefaultThemeColor
    }

    var themeColor by rememberSaveable(stateSaver = ColorSaver) {
        mutableStateOf(DefaultThemeColor)
    }

    LaunchedEffect(playerConnection, enableDynamicTheme, isSystemInDarkTheme, paletteThemeColor) {
        val playerConnection = playerConnection
        if (!enableDynamicTheme || playerConnection == null) {
            themeColor = paletteThemeColor
            return@LaunchedEffect
        }
        playerConnection.service.currentMediaMetadata.collectLatest { song ->
            themeColor =
                if (song != null) {
                    withContext(Dispatchers.IO) {
                        val result =
                            context.imageLoader.execute(
                                ImageRequest
                                    .Builder(context)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false)
                                    .build(),
                            )
                        result.image?.toBitmap()?.extractThemeColor()
                            ?: DefaultThemeColor
                    }
                } else {
                    DefaultThemeColor
                }
        }
    }

    ResonixTheme(
        darkTheme = useDarkTheme,
        pureBlack = pureBlack,
        themeColor = themeColor,
        useSystemFont = useSystemFont,
    ) {
        BoxWithConstraints(
            modifier =
            Modifier
                .fillMaxSize()
                .background(
                    if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                )
        ) {
            val focusManager = LocalFocusManager.current
            val density = LocalDensity.current
            val windowsInsets = WindowInsets.systemBars
            val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
            val bottomInsetDp = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            val navController = rememberNavController()
            val homeViewModel: HomeViewModel = hiltViewModel()
            val accountState by homeViewModel.accountState.collectAsState()
            val accountImageUrl = accountState.imageUrl
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val (previousTab) = rememberSaveable { mutableStateOf("home") }

            val navigationItems = remember { Screens.MainScreens }
            val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
            val (frostedGlassNavBar) = rememberPreference(FrostedGlassNavBarKey, defaultValue = true)
            val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
            val defaultOpenTab =
                remember {
                    context.dataStore[DefaultOpenTabKey].toEnum(defaultValue = NavigationTab.HOME)
                }
            val tabOpenedFromShortcut =
                remember {
                    when (currentIntent?.action) {
                        MainActivity.ACTION_LIBRARY -> NavigationTab.LIBRARY
                        MainActivity.ACTION_SEARCH -> NavigationTab.SEARCH
                        else -> null
                    }
                }

            val topLevelScreens =
                listOf(
                    Screens.Home.route,
                    Screens.Search.route,
                    Screens.Library.route,
                    "settings",
                )

            val (query, onQueryChange) =
                rememberSaveable(stateSaver = TextFieldValue.Saver) {
                    mutableStateOf(TextFieldValue())
                }

            var active by rememberSaveable {
                mutableStateOf(false)
            }

            val onActiveChange: (Boolean) -> Unit = { newActive ->
                active = newActive
                if (!newActive) {
                    focusManager.clearFocus()
                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        onQueryChange(TextFieldValue())
                    }
                }
            }

            var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

            val searchBarFocusRequester = remember { FocusRequester() }
            
            val onSearch: (String) -> Unit = {
                if (it.isNotEmpty()) {
                    onActiveChange(false)
                    // URL Encoding handled inside OnlineSearchScreen in previous logic.
                    // But here we are at top level. 
                    navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                    if (context.dataStore[PauseSearchHistoryKey] != true) {
                        database.query {
                            insert(SearchHistory(query = it))
                        }
                    }
                }
            }

            var openSearchImmediately: Boolean by remember {
                mutableStateOf(currentIntent?.action == MainActivity.ACTION_SEARCH)
            }

            val shouldShowSearchBar =
                remember(active, navBackStackEntry) {
                    active ||
                            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                            navBackStackEntry?.destination?.route?.startsWith("search/") == true
                }

            val shouldShowNavigationBar =
                remember(navBackStackEntry, active) {
                    navBackStackEntry?.destination?.route == null ||
                            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } &&
                            !active
                }

            fun getNavPadding(): Dp {
                return if (shouldShowNavigationBar) {
                    if (slimNav) SlimNavBarHeight else NavigationBarHeight
                } else {
                    0.dp
                }
            }

            val navigationBarHeight by animateDpAsState(
                targetValue = if (shouldShowNavigationBar) NavigationBarHeight else 0.dp,
                animationSpec = NavigationBarAnimationSpec,
                label = "",
            )

            val playerBottomSheetState =
                rememberBottomSheetState(
                    dismissedBound = 0.dp,
                    collapsedBound = bottomInset + getNavPadding() + (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) + MiniPlayerHeight,
                    expandedBound = maxHeight,
                )

            val playerAwareWindowInsets =
                remember(
                    bottomInset,
                    shouldShowNavigationBar,
                    playerBottomSheetState.isDismissed,
                ) {
                    var bottom = bottomInset
                    if (shouldShowNavigationBar) bottom += NavigationBarHeight
                    if (!playerBottomSheetState.isDismissed) bottom += MiniPlayerHeight
                    windowsInsets
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                        .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                }

            appBarScrollBehavior(
                canScroll = {
                    navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                            (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                }
            )

            val searchBarScrollBehavior =
                appBarScrollBehavior(
                    canScroll = {
                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )
            val topAppBarScrollBehavior =
                appBarScrollBehavior(
                    canScroll = {
                        navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                    },
                )

            LaunchedEffect(navBackStackEntry) {
                if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                    val searchQuery =
                        withContext(Dispatchers.IO) {
                            val argQuery = navBackStackEntry?.arguments?.getString("query")!!
                            if (argQuery.contains("%")) {
                                URLDecoder.decode(argQuery, "UTF-8")
                            } else {
                                argQuery
                            }
                        }
                    onQueryChange(
                        TextFieldValue(
                            searchQuery,
                            TextRange(searchQuery.length)
                        )
                    )
                } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } || navBackStackEntry?.destination?.route in topLevelScreens) {
                    onQueryChange(TextFieldValue())
                    if (navBackStackEntry?.destination?.route != Screens.Home.route) {
                        searchBarScrollBehavior.state.resetHeightOffset()
                        topAppBarScrollBehavior.state.resetHeightOffset()
                    }
                }
            }
            LaunchedEffect(active) {
                if (active) {
                    searchBarScrollBehavior.state.resetHeightOffset()
                    topAppBarScrollBehavior.state.resetHeightOffset()
                    searchBarFocusRequester.requestFocus()
                }
            }

            LaunchedEffect(playerConnection) {
                val player = playerConnection?.player ?: return@LaunchedEffect
                if (player.currentMediaItem == null) {
                    if (!playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.dismiss()
                    }
                } else {
                    if (playerBottomSheetState.isDismissed) {
                        playerBottomSheetState.collapseSoft()
                    }
                }
            }

            DisposableEffect(playerConnection, playerBottomSheetState) {
                val player =
                    playerConnection?.player ?: return@DisposableEffect onDispose { }
                val listener =
                    object : Player.Listener {
                        override fun onMediaItemTransition(
                            mediaItem: MediaItem?,
                            reason: Int,
                        ) {
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                mediaItem != null &&
                                playerBottomSheetState.isDismissed
                            ) {
                                playerBottomSheetState.collapseSoft()
                            }
                        }
                    }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                }
            }

            var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(navBackStackEntry) {
                shouldShowTopBar =
                    !active && navBackStackEntry?.destination?.route in topLevelScreens && navBackStackEntry?.destination?.route != "settings"
            }

            var sharedSong: SongItem? by remember {
                mutableStateOf(null)
            }

            LaunchedEffect(currentIntent) {
                if (currentIntent != null) {
                    handleDeepLinkIntent(currentIntent, navController, context, playerConnection)
                }
            }

            var showStarDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val current = context.dataStore[LaunchCountKey] ?: 0
                    val newCount = current + 1
                    context.dataStore.edit { prefs ->
                        prefs[LaunchCountKey] = newCount
                    }
                }

                val shouldShow = withContext(Dispatchers.IO) {
                    val hasPressed = context.dataStore[HasPressedStarKey] ?: false
                    val remindAfter = context.dataStore[RemindAfterKey] ?: 3
                    !hasPressed && (context.dataStore[LaunchCountKey] ?: 0) >= remindAfter
                }

                if (shouldShow) {
                    delay(1000)
                    var waited = 0L
                    val waitStep = 500L
                    val maxWait = 30_000L // 30 seconds max
                    while (bottomSheetPageState.isVisible && waited < maxWait) {
                        delay(waitStep)
                        waited += waitStep
                    }

                    showStarDialog = true
                }
            }

            if (showStarDialog) {
                StarDialog(
                    onDismissRequest = { showStarDialog = false },
                    onStar = {
                        coroutineScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    context.dataStore.edit { prefs ->
                                        prefs[HasPressedStarKey] = true
                                        prefs[RemindAfterKey] = Int.MAX_VALUE
                                    }
                                }
                            } catch (e: Exception) {
                                reportException(e)
                            } finally {
                                showStarDialog = false
                            }
                        }
                    },
                    onLater = {
                        coroutineScope.launch {
                            try {
                                val launch = withContext(Dispatchers.IO) { context.dataStore[LaunchCountKey] ?: 0 }
                                withContext(Dispatchers.IO) {
                                    context.dataStore.edit { prefs ->
                                        prefs[RemindAfterKey] = launch + 10
                                    }
                                }
                            } catch (e: Exception) {
                                reportException(e)
                            } finally {
                                showStarDialog = false
                            }
                        }
                    }
                )
            }

            val (disableBlur) = rememberPreference(DisableBlurKey, false)

            var showAccountDialog by remember { mutableStateOf(false) }

            CompositionLocalProvider(
                LocalDatabase provides database,
                LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                LocalPlayerConnection provides playerConnection,
                LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                LocalDownloadUtil provides downloadUtil,
                LocalShimmerTheme provides ShimmerTheme,
                LocalSyncUtils provides syncUtils,
                com.noxwizard.resonix.ui.component.LocalBottomSheetPageState provides bottomSheetPageState,
                com.noxwizard.resonix.ui.component.LocalMenuState provides menuState,
            ) {
                Scaffold(
                    topBar = {
                        if (shouldShowTopBar) {
                            ResonixTopBar(
                                navController = navController,
                                navBackStackEntry = navBackStackEntry,
                                searchBarScrollBehavior = searchBarScrollBehavior,
                                topAppBarScrollBehavior = topAppBarScrollBehavior,
                                disableBlur = disableBlur,
                                pureBlack = pureBlack,
                                localLatestVersionName = localLatestVersionName,
                                accountImageUrl = accountImageUrl,
                                onShowAccountDialog = { showAccountDialog = true }
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = active || navBackStackEntry?.destination?.route?.startsWith("search/") == true,
                            enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 200))
                        ) {
                            ResonixSearchBar(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = onSearch,
                                active = active,
                                onActiveChange = onActiveChange,
                                searchSource = searchSource,
                                onSearchSourceChange = { searchSource = it },
                                navigationItems = navigationItems,
                                navBackStackEntry = navBackStackEntry,
                                navController = navController,
                                searchBarFocusRequester = searchBarFocusRequester,
                                pureBlack = pureBlack,
                                playerBottomSheetState = playerBottomSheetState,
                                database = database
                            )
                        }
                    },
                    bottomBar = {
                        val currentNavPadding = getNavPadding()

                        val navItemClick: (Screens, Boolean) -> Unit = { screen, isSelected ->
                            if (screen.route == Screens.Search.route) {
                                onActiveChange(true)
                            } else if (isSelected) {
                                navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                coroutineScope.launch {
                                    searchBarScrollBehavior.state.resetHeightOffset()
                                }
                            } else {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }

                        if (frostedGlassNavBar) {
                            FrostedGlassNavigationBar(
                                navController = navController,
                                navBackStackEntry = navBackStackEntry,
                                playerBottomSheetState = playerBottomSheetState,
                                navigationBarHeight = navigationBarHeight,
                                currentNavPadding = currentNavPadding,
                                bottomInset = bottomInset,
                                bottomInsetDp = bottomInsetDp,
                                pureBlack = pureBlack,
                                slimNav = slimNav,
                                navigationItems = navigationItems,
                                onItemClick = navItemClick,
                            )
                        } else {
                            ResonixBottomBar(
                                navController = navController,
                                navBackStackEntry = navBackStackEntry,
                                playerBottomSheetState = playerBottomSheetState,
                                navigationBarHeight = navigationBarHeight,
                                currentNavPadding = currentNavPadding,
                                bottomInset = bottomInset,
                                bottomInsetDp = bottomInsetDp,
                                pureBlack = pureBlack,
                                slimNav = slimNav,
                                navigationItems = navigationItems,
                                onItemClick = navItemClick,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                ) {
                    var transitionDirection =
                        AnimatedContentTransitionScope.SlideDirection.Left

                    if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                        if (navigationItems.fastAny { it.route == previousTab }) {
                            val curIndex = navigationItems.indexOf(
                                navigationItems.fastFirstOrNull {
                                    it.route == navBackStackEntry?.destination?.route
                                }
                            )

                            val prevIndex = navigationItems.indexOf(
                                navigationItems.fastFirstOrNull {
                                    it.route == previousTab
                                }
                            )

                            if (prevIndex > curIndex)
                                AnimatedContentTransitionScope.SlideDirection.Right.also {
                                    transitionDirection = it
                                }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                            NavigationTab.HOME -> Screens.Home
                            NavigationTab.LIBRARY -> Screens.Library
                            else -> Screens.Home
                        }.route,
                        enterTransition = {
                            if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                fadeIn(tween(250))
                            } else {
                                fadeIn(tween(250)) + slideInHorizontally { it / 2 }
                            }
                        },
                        exitTransition = {
                            if (initialState.destination.route in topLevelScreens && targetState.destination.route in topLevelScreens) {
                                fadeOut(tween(200))
                            } else {
                                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
                            }
                        },
                        popEnterTransition = {
                            if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                fadeIn(tween(250))
                            } else {
                                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
                            }
                        },
                        popExitTransition = {
                            if ((initialState.destination.route in topLevelScreens || initialState.destination.route?.startsWith("search/") == true) && targetState.destination.route in topLevelScreens) {
                                fadeOut(tween(200))
                            } else {
                                fadeOut(tween(200)) + slideOutHorizontally { it / 2 }
                            }
                        },
                        modifier = Modifier.nestedScroll(
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                navBackStackEntry?.destination?.route?.startsWith("search/") == true
                            ) {
                                searchBarScrollBehavior.nestedScrollConnection
                            } else {
                                topAppBarScrollBehavior.nestedScrollConnection
                            }
                        )
                    ) {
                        navigationBuilder(
                            navController,
                            topAppBarScrollBehavior,
                            localLatestVersionName
                        )
                    }
                }

                BottomSheetMenu(
                    state = LocalMenuState.current,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                BottomSheetPage(
                    state = LocalBottomSheetPageState.current,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                if (showAccountDialog) {
                    AccountSettingsDialog(
                        navController = navController,
                        onDismiss = { showAccountDialog = false },
                        latestVersionName = localLatestVersionName
                    )
                }

                sharedSong?.let { song ->
                    playerConnection?.let {
                        Dialog(
                            onDismissRequest = { sharedSong = null },
                            properties = DialogProperties(usePlatformDefaultWidth = false),
                        ) {
                            Surface(
                                modifier = Modifier.padding(24.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = AlertDialogDefaults.containerColor,
                                tonalElevation = AlertDialogDefaults.TonalElevation,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    YouTubeSongMenu(
                                        song = song,
                                        navController = navController,
                                        onDismiss = { sharedSong = null },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                if (shouldShowSearchBar && openSearchImmediately) {
                    onActiveChange(true)
                    try {
                        delay(100)
                        searchBarFocusRequester.requestFocus()
                    } catch (_: Exception) {
                    }
                    openSearchImmediately = false
                }
            }
        }
    }
}


