package com.noxwizard.resonix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon

import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.MeshGradientBackground
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.noxwizard.resonix.viewmodels.LocalContentState
import com.noxwizard.resonix.viewmodels.OnlineContentState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.noxwizard.resonix.innertube.models.AlbumItem
import com.noxwizard.resonix.innertube.models.ArtistItem
import com.noxwizard.resonix.innertube.models.BrowseEndpoint
import com.noxwizard.resonix.innertube.models.PlaylistItem
import com.noxwizard.resonix.innertube.models.SongItem
import com.noxwizard.resonix.innertube.models.WatchEndpoint
import com.noxwizard.resonix.innertube.models.YTItem
import com.noxwizard.resonix.innertube.pages.HomePage
import com.noxwizard.resonix.innertube.utils.parseCookieString
import com.noxwizard.resonix.LocalDatabase
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.LocalPlayerConnection
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.CONTENT_TYPE_CHIPS
import com.noxwizard.resonix.constants.CONTENT_TYPE_GRID
import com.noxwizard.resonix.constants.CONTENT_TYPE_HEADER
import com.noxwizard.resonix.constants.CONTENT_TYPE_LIST
import com.noxwizard.resonix.constants.CONTENT_TYPE_SHIMMER
import com.noxwizard.resonix.constants.GridThumbnailHeight
import com.noxwizard.resonix.constants.InnerTubeCookieKey
import com.noxwizard.resonix.constants.ListItemHeight
import com.noxwizard.resonix.constants.ListThumbnailSize
import com.noxwizard.resonix.constants.ThumbnailCornerRadius
import com.noxwizard.resonix.constants.DisableBlurKey
import com.noxwizard.resonix.db.entities.Album
import com.noxwizard.resonix.db.entities.Artist
import com.noxwizard.resonix.db.entities.LocalItem
import com.noxwizard.resonix.db.entities.Playlist
import com.noxwizard.resonix.db.entities.Song
import com.noxwizard.resonix.extensions.togglePlayPause
import com.noxwizard.resonix.models.toMediaMetadata
import com.noxwizard.resonix.playback.queues.ListQueue
import com.noxwizard.resonix.playback.queues.LocalAlbumRadio
import com.noxwizard.resonix.playback.queues.YouTubeAlbumRadio
import com.noxwizard.resonix.playback.queues.YouTubeQueue
import com.noxwizard.resonix.ui.component.AlbumGridItem
import com.noxwizard.resonix.ui.component.ArtistGridItem
import com.noxwizard.resonix.ui.component.ChipsRow
import com.noxwizard.resonix.ui.component.HideOnScrollFAB
import com.noxwizard.resonix.ui.component.LocalBottomSheetPageState
import com.noxwizard.resonix.ui.component.LocalMenuState
import com.noxwizard.resonix.ui.component.NavigationTitle
import com.noxwizard.resonix.ui.component.SongGridItem
import com.noxwizard.resonix.ui.component.SongListItem
import com.noxwizard.resonix.ui.component.YouTubeGridItem
import com.noxwizard.resonix.ui.component.YouTubeListItem
import com.noxwizard.resonix.ui.component.shimmer.GridItemPlaceHolder
import com.noxwizard.resonix.ui.component.shimmer.ShimmerHost
import com.noxwizard.resonix.ui.component.shimmer.TextPlaceholder
import com.noxwizard.resonix.ui.menu.AlbumMenu
import com.noxwizard.resonix.ui.menu.ArtistMenu
import com.noxwizard.resonix.ui.menu.SongMenu
import com.noxwizard.resonix.ui.menu.YouTubeAlbumMenu
import com.noxwizard.resonix.ui.menu.YouTubeArtistMenu
import com.noxwizard.resonix.ui.menu.YouTubePlaylistMenu
import com.noxwizard.resonix.ui.menu.YouTubeSongMenu
import com.noxwizard.resonix.ui.utils.SnapLayoutInfoProvider
import com.noxwizard.resonix.utils.rememberPreference
import com.noxwizard.resonix.viewmodels.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val localContent by viewModel.localContentState.collectAsState()
    val onlineContent by viewModel.onlineContentState.collectAsState()

    val quickPicks = localContent.quickPicks
    val forgottenFavorites = localContent.forgottenFavorites
    val keepListening = localContent.keepListening
    val similarRecommendations = onlineContent.similarRecommendations
    val homePage = onlineContent.homePage
    val explorePage = onlineContent.explorePage
    val selectedChip = onlineContent.selectedChip

    // Aggregated lists computed in ViewModel — stable references, no composition-time allocation
    val allLocalItems by viewModel.allLocalItems.collectAsState()
    val allYtItems by viewModel.allYtItems.collectAsState()

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isMoodAndGenresLoading by remember {
        derivedStateOf { isLoading && explorePage?.moodAndGenres == null }
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val quickPicksLazyGridState = rememberLazyGridState()
    val forgottenFavoritesLazyGridState = rememberLazyGridState()

    val accountState by viewModel.accountState.collectAsState()
    val accountName = accountState.name
    val accountImageUrl = accountState.imageUrl
    val accountPlaylists = accountState.playlists
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    val currentContinuation = viewModel.onlineContentState.value.homePage?.continuation
                    viewModel.loadMoreYouTubeItems(currentContinuation)
                }
            }
    }

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    val localGridItem: @Composable (LocalItem) -> Unit =
        remember(
            mediaMetadata?.id,
            mediaMetadata?.album?.id,
            isPlaying,
            scope,
            navController,
            menuState,
            haptic,
            playerConnection
        ) {
            {
                when (it) {
                    is Song -> SongGridItem(
                        song = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (it.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(it.toMediaMetadata()),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.LongPress,
                                    )
                                    menuState.show {
                                        SongMenu(
                                            originalSong = it,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                        isActive = it.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                    )

                    is Album -> AlbumGridItem(
                        album = it,
                        isActive = it.id == mediaMetadata?.album?.id,
                        isPlaying = isPlaying,
                        coroutineScope = scope,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("album/${it.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        AlbumMenu(
                                            originalAlbum = it,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            )
                    )

                    is Artist -> ArtistGridItem(
                        artist = it,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    navController.navigate("artist/${it.id}")
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.LongPress,
                                    )
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = it,
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                    )

                    is Playlist -> {}
                }
            }
        }

    val ytGridItem: @Composable (YTItem) -> Unit =
        remember(
            mediaMetadata?.id,
            mediaMetadata?.album?.id,
            isPlaying,
            scope,
            navController,
            menuState,
            haptic,
            playerConnection
        ) {
            { item ->
                YouTubeGridItem(
                    item = item,
                    isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
                    isPlaying = isPlaying,
                    coroutineScope = scope,
                    thumbnailRatio = 1f,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                when (item) {
                                    is SongItem -> playerConnection.playQueue(
                                        YouTubeQueue(
                                            item.endpoint ?: WatchEndpoint(
                                                videoId = item.id
                                            ), item.toMediaMetadata()
                                        )
                                    )

                                    is AlbumItem -> navController.navigate("album/${item.id}")
                                    is ArtistItem -> navController.navigate("artist/${item.id}")
                                    is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    when (item) {
                                        is SongItem -> YouTubeSongMenu(
                                            song = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )

                                        is AlbumItem -> YouTubeAlbumMenu(
                                            albumItem = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss
                                        )

                                        is ArtistItem -> YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss
                                        )

                                        is PlaylistItem -> YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = scope,
                                            onDismiss = menuState::dismiss
                                        )
                                    }
                                }
                            }
                        )
                )
            }
        }

    LaunchedEffect(quickPicks) {
        quickPicksLazyGridState.scrollToItem(0)
    }

    LaunchedEffect(forgottenFavorites) {
        forgottenFavoritesLazyGridState.scrollToItem(0)
    }

    
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // M3E Mesh gradient background layer at the top
        if (!disableBlur) {
            MeshGradientBackground(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f) // Cover top 70% of screen
                    .align(Alignment.TopCenter)
                    .zIndex(-1f) // Place behind all content
            )
        }
        
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh
                )
        ) {
            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
            val quickPicksSnapLayoutInfoProvider = remember(quickPicksLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = quickPicksLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }
            val forgottenFavoritesSnapLayoutInfoProvider = remember(forgottenFavoritesLazyGridState) {
                SnapLayoutInfoProvider(
                    lazyGridState = forgottenFavoritesLazyGridState,
                    positionInLayout = { layoutSize, itemSize ->
                        (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                    }
                )
            }

            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
            ) {
            item(key = "chips_row", contentType = CONTENT_TYPE_CHIPS) {
                val chipPairs = remember(homePage?.chips) {
                    homePage?.chips?.map { it to it.title } ?: emptyList()
                }
                ChipsRow(
                    chips = chipPairs,
                    currentValue = selectedChip,
                    onValueUpdate = {
                        viewModel.toggleChip(it)
                    }
                )
            }

            quickPicks?.takeIf { it.isNotEmpty() }?.let { quickPicks ->
                QuickPicksSection(
                    quickPicks = quickPicks,
                    mediaMetadataId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    quickPicksLazyGridState = quickPicksLazyGridState,
                    quickPicksSnapLayoutInfoProvider = quickPicksSnapLayoutInfoProvider,
                    horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                    navController = navController,
                    haptic = haptic,
                    menuState = menuState,
                    playerConnection = playerConnection,
                )
            }

            keepListening?.takeIf { it.isNotEmpty() }?.let { keepListening ->
                KeepListeningSection(
                    keepListening = keepListening,
                    localGridItem = localGridItem,
                )
            }

            accountPlaylists?.takeIf { it.isNotEmpty() }?.let { accountPlaylists ->
                AccountPlaylistsSection(
                    accountPlaylists = accountPlaylists,
                    accountName = accountName,
                    accountImageUrl = url,
                    navController = navController,
                    ytGridItem = ytGridItem,
                )
            }

            forgottenFavorites?.takeIf { it.isNotEmpty() }?.let { forgottenFavorites ->
                ForgottenFavoritesSection(
                    forgottenFavorites = forgottenFavorites,
                    mediaMetadataId = mediaMetadata?.id,
                    isPlaying = isPlaying,
                    forgottenFavoritesLazyGridState = forgottenFavoritesLazyGridState,
                    forgottenFavoritesSnapLayoutInfoProvider = forgottenFavoritesSnapLayoutInfoProvider,
                    horizontalLazyGridItemWidth = horizontalLazyGridItemWidth,
                    navController = navController,
                    haptic = haptic,
                    menuState = menuState,
                    playerConnection = playerConnection,
                )
            }

            similarRecommendations?.takeIf { it.isNotEmpty() }?.let { similarRecommendations ->
                SimilarRecommendationsSection(
                    similarRecommendations = similarRecommendations,
                    navController = navController,
                    ytGridItem = ytGridItem,
                )
            }

            homePage?.let { homePage ->
                HomePageSections(
                    homePage = homePage,
                    navController = navController,
                    ytGridItem = ytGridItem,
                )
            }

            if (isLoading || homePage?.continuation != null && homePage?.sections?.isNotEmpty() == true) {
                ShimmerLoadingSection()
            }

            explorePage?.moodAndGenres?.let { moodAndGenres ->
                MoodAndGenresSection(
                    moodAndGenres = moodAndGenres,
                    navController = navController,
                )
            }

            if (isMoodAndGenresLoading) {
                MoodAndGenresShimmerSection()
            }
            }

            HideOnScrollFAB(
                visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
                lazyListState = lazylistState,
                icon = R.drawable.shuffle,
                onClick = {
                    val local = when {
                        allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                        allLocalItems.isNotEmpty() -> true
                        else -> false
                    }
                    scope.launch(Dispatchers.Main) {
                        if (local) {
                            when (val luckyItem = allLocalItems.random()) {
                                is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                is Album -> {
                                    val albumWithSongs = withContext(Dispatchers.IO) {
                                        database.albumWithSongs(luckyItem.id).first()
                                    }
                                    albumWithSongs?.let {
                                        playerConnection.playQueue(LocalAlbumRadio(it))
                                    }
                                }
                                is Artist -> {}
                                is Playlist -> {}
                            }
                        } else {
                            when (val luckyItem = allYtItems.random()) {
                                is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                                is ArtistItem -> luckyItem.radioEndpoint?.let {
                                    playerConnection.playQueue(YouTubeQueue(it))
                                }
                                is PlaylistItem -> luckyItem.playEndpoint?.let {
                                    playerConnection.playQueue(YouTubeQueue(it))
                                }
                            }
                        }
                    }
                }
            )

            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }
}


