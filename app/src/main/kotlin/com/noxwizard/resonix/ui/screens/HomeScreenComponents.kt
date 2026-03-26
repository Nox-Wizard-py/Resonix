package com.noxwizard.resonix.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.CONTENT_TYPE_GRID
import com.noxwizard.resonix.constants.CONTENT_TYPE_HEADER
import com.noxwizard.resonix.constants.CONTENT_TYPE_LIST
import com.noxwizard.resonix.constants.CONTENT_TYPE_SHIMMER
import com.noxwizard.resonix.constants.GridThumbnailHeight
import com.noxwizard.resonix.constants.ListItemHeight
import com.noxwizard.resonix.constants.ListThumbnailSize
import com.noxwizard.resonix.constants.ThumbnailCornerRadius
import com.noxwizard.resonix.db.entities.Album
import com.noxwizard.resonix.db.entities.Artist
import com.noxwizard.resonix.db.entities.LocalItem
import com.noxwizard.resonix.db.entities.Playlist
import com.noxwizard.resonix.db.entities.Song
import com.noxwizard.resonix.innertube.models.PlaylistItem
import com.noxwizard.resonix.innertube.models.YTItem
import com.noxwizard.resonix.innertube.pages.HomePage
import com.noxwizard.resonix.innertube.pages.MoodAndGenres
import com.noxwizard.resonix.models.SimilarRecommendation
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.LocalMenuState
import com.noxwizard.resonix.ui.component.NavigationTitle
import com.noxwizard.resonix.ui.component.SongListItem
import com.noxwizard.resonix.ui.component.shimmer.GridItemPlaceHolder
import com.noxwizard.resonix.ui.component.shimmer.ShimmerHost
import com.noxwizard.resonix.ui.component.shimmer.TextPlaceholder
import com.noxwizard.resonix.ui.menu.SongMenu
import com.noxwizard.resonix.ui.utils.SnapLayoutInfoProvider
import com.noxwizard.resonix.extensions.togglePlayPause
import com.noxwizard.resonix.models.toMediaMetadata
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlin.math.min

/**
 * Extracted HomeScreen sections as LazyListScope extension functions.
 * Each function creates its own recomposition scope, so when state changes
 * (e.g. isPlaying, mediaMetadata), only the affected section recomposes —
 * not the entire 900+ line LazyColumn DSL body.
 *
 * This mirrors Resonix's HomeScreenComponents.kt pattern.
 */

// ─── Quick Picks Section ──────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.QuickPicksSection(
    quickPicks: List<Song>,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    quickPicksLazyGridState: LazyGridState,
    quickPicksSnapLayoutInfoProvider: SnapLayoutInfoProvider,
    horizontalLazyGridItemWidth: Dp,
    navController: NavController,
    haptic: HapticFeedback,
    menuState: com.noxwizard.resonix.ui.component.MenuState,
    playerConnection: com.noxwizard.resonix.playback.PlayerConnection,
) {
    item(key = "quick_picks_title", contentType = CONTENT_TYPE_HEADER) {
        NavigationTitle(
            title = stringResource(R.string.quick_picks),
            modifier = Modifier.animateItem()
        )
    }

    item(key = "quick_picks_grid", contentType = CONTENT_TYPE_GRID) {
        LazyHorizontalGrid(
            state = quickPicksLazyGridState,
            rows = GridCells.Fixed(4),
            flingBehavior = rememberSnapFlingBehavior(quickPicksSnapLayoutInfoProvider),
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
            modifier = Modifier
                .fillMaxWidth()
                .height(ListItemHeight * 4)
        ) {
            items(
                items = quickPicks.distinctBy { it.id },
                key = { it.id }
            ) { song ->
                SongListItem(
                    song = song,
                    showInLibraryIcon = true,
                    isActive = song.id == mediaMetadataId,
                    isPlaying = isPlaying,
                    isSwipeable = false,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .width(horizontalLazyGridItemWidth)
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadataId) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        com.noxwizard.resonix.playback.queues.YouTubeQueue.radio(song.toMediaMetadata())
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                )
            }
        }
    }
}

// ─── Keep Listening Section ───────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.KeepListeningSection(
    keepListening: List<LocalItem>,
    localGridItem: @Composable (LocalItem) -> Unit,
) {
    item(key = "keep_listening_title", contentType = CONTENT_TYPE_HEADER) {
        NavigationTitle(
            title = stringResource(R.string.keep_listening),
            modifier = Modifier.animateItem()
        )
    }

    item(key = "keep_listening_grid", contentType = CONTENT_TYPE_GRID) {
        val rows = if (keepListening.size > 6) 2 else 1
        LazyHorizontalGrid(
            state = rememberLazyGridState(),
            rows = GridCells.Fixed(rows),
            modifier = Modifier
                .fillMaxWidth()
                .height((GridThumbnailHeight + with(LocalDensity.current) {
                    MaterialTheme.typography.bodyLarge.lineHeight.toDp() * 2 +
                            MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 2
                }) * rows)
        ) {
            items(items = keepListening, key = { it.id }) {
                localGridItem(it)
            }
        }
    }
}

// ─── Account Playlists Section ────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.AccountPlaylistsSection(
    accountPlaylists: List<PlaylistItem>,
    accountName: String,
    accountImageUrl: String?,
    navController: NavController,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    item(key = "account_playlists_title", contentType = CONTENT_TYPE_HEADER) {
        NavigationTitle(
            label = stringResource(R.string.your_youtube_playlists),
            title = accountName,
            thumbnail = {
                if (accountImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(accountImageUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(accountImageUrl)
                            .crossfade(true)
                            .build(),
                        placeholder = painterResource(id = R.drawable.person),
                        error = painterResource(id = R.drawable.person),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.person),
                        contentDescription = null,
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                }
            },
            onClick = {
                navController.navigate("account")
            },
            modifier = Modifier.animateItem()
        )
    }

    item(key = "account_playlists_list", contentType = CONTENT_TYPE_LIST) {
        LazyRow(
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        ) {
            items(
                items = accountPlaylists,
                key = { it.id },
            ) { item ->
                ytGridItem(item)
            }
        }
    }
}

// ─── Forgotten Favorites Section ──────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.ForgottenFavoritesSection(
    forgottenFavorites: List<Song>,
    mediaMetadataId: String?,
    isPlaying: Boolean,
    forgottenFavoritesLazyGridState: LazyGridState,
    forgottenFavoritesSnapLayoutInfoProvider: SnapLayoutInfoProvider,
    horizontalLazyGridItemWidth: Dp,
    navController: NavController,
    haptic: HapticFeedback,
    menuState: com.noxwizard.resonix.ui.component.MenuState,
    playerConnection: com.noxwizard.resonix.playback.PlayerConnection,
) {
    item(key = "forgotten_favorites_title", contentType = CONTENT_TYPE_HEADER) {
        NavigationTitle(
            title = stringResource(R.string.forgotten_favorites),
            modifier = Modifier.animateItem()
        )
    }

    item(key = "forgotten_favorites_grid", contentType = CONTENT_TYPE_GRID) {
        val rows = min(4, forgottenFavorites.size)
        LazyHorizontalGrid(
            state = forgottenFavoritesLazyGridState,
            rows = GridCells.Fixed(rows),
            flingBehavior = rememberSnapFlingBehavior(
                forgottenFavoritesSnapLayoutInfoProvider
            ),
            contentPadding = WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
            modifier = Modifier
                .fillMaxWidth()
                .height(ListItemHeight * rows)
        ) {
            items(
                items = forgottenFavorites.distinctBy { it.id },
                key = { it.id }
            ) { song ->
                SongListItem(
                    song = song,
                    showInLibraryIcon = true,
                    isActive = song.id == mediaMetadataId,
                    isPlaying = isPlaying,
                    isSwipeable = false,
                    trailingContent = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .width(horizontalLazyGridItemWidth)
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadataId) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        com.noxwizard.resonix.playback.queues.YouTubeQueue.radio(song.toMediaMetadata())
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss
                                    )
                                }
                            }
                        )
                )
            }
        }
    }
}

// ─── Similar Recommendations Section ──────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.SimilarRecommendationsSection(
    similarRecommendations: List<SimilarRecommendation>,
    navController: NavController,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    similarRecommendations.forEach {
        item(key = "header_${it.title.title}", contentType = CONTENT_TYPE_HEADER) {
            NavigationTitle(
                label = stringResource(R.string.similar_to),
                title = it.title.title,
                thumbnail = it.title.thumbnailUrl?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (it.title is Artist) CircleShape else RoundedCornerShape(
                                ThumbnailCornerRadius
                            )
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape)
                        )
                    }
                },
                onClick = {
                    when (it.title) {
                        is Song -> navController.navigate("album/${it.title.album!!.id}")
                        is Album -> navController.navigate("album/${it.title.id}")
                        is Artist -> navController.navigate("artist/${it.title.id}")
                        is Playlist -> {}
                    }
                },
                modifier = Modifier.animateItem()
            )
        }

        item(key = "list_${it.title.title}", contentType = CONTENT_TYPE_LIST) {
            LazyRow(
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
            ) {
                items(it.items, key = { item -> item.id }) { item ->
                    ytGridItem(item)
                }
            }
        }
    }
}

// ─── Home Page Sections (YouTube content) ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.HomePageSections(
    homePage: HomePage,
    navController: NavController,
    ytGridItem: @Composable (YTItem) -> Unit,
) {
    homePage.sections.forEachIndexed { index, section ->
        item(key = "header_${section.title}_$index", contentType = CONTENT_TYPE_HEADER) {
            NavigationTitle(
                title = section.title,
                label = section.label,
                thumbnail = section.thumbnail?.let { thumbnailUrl ->
                    {
                        val shape =
                            if (section.endpoint?.isArtistEndpoint == true) CircleShape else RoundedCornerShape(
                                ThumbnailCornerRadius
                            )
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(shape)
                        )
                    }
                },
                onClick = section.endpoint?.browseId?.let { browseId ->
                    {
                        if (browseId == "FEmusic_moods_and_genres")
                            navController.navigate("mood_and_genres")
                        else
                            navController.navigate("browse/$browseId")
                    }
                },
                modifier = Modifier.animateItem()
            )
        }

        item(key = "list_${section.title}_$index", contentType = CONTENT_TYPE_LIST) {
            LazyRow(
                contentPadding = WindowInsets.systemBars
                    .only(WindowInsetsSides.Horizontal)
                    .asPaddingValues(),
            ) {
                items(section.items, key = { item -> "${item.id}_${index}" }) { item ->
                    ytGridItem(item)
                }
            }
        }
    }
}

// ─── Shimmer Loading Section ──────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.ShimmerLoadingSection() {
    item(key = "shimmer_loading", contentType = CONTENT_TYPE_SHIMMER) {
        ShimmerHost(
            modifier = Modifier.animateItem()
        ) {
            TextPlaceholder(
                height = 36.dp,
                modifier = Modifier
                    .padding(12.dp)
                    .width(250.dp),
            )
            LazyRow {
                items(4) {
                    GridItemPlaceHolder()
                }
            }
        }
    }
}

// ─── Mood Section ──────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.MoodSection(
    items: List<MoodAndGenres.Item>,
    navController: NavController,
    enableDynamicTheme: Boolean,
    selectedCategoryTitle: String?,
    onCategorySelect: (String, Color) -> Unit,
) {
    val moodItems = items.filter { moodColorMap.containsKey(it.title) }
    if (moodItems.isEmpty()) return

    item(key = "mood_title", contentType = CONTENT_TYPE_HEADER) {
        NavigationTitle(
            title = "Mood",
            modifier = Modifier.animateItem()
        )
    }

    item(key = "mood_grid", contentType = CONTENT_TYPE_GRID) {
        val rows = moodItems.chunked(2)
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { row ->
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { moodItem ->
                        val accent = moodColorMap[moodItem.title] ?: MaterialTheme.colorScheme.primary
                        CategoryCard(
                            title = moodItem.title,
                            accentColor = accent,
                            isSelected = selectedCategoryTitle == moodItem.title,
                            onClick = {
                                if (enableDynamicTheme) onCategorySelect(moodItem.title, accent)
                                navController.navigate("youtube_browse/${moodItem.endpoint.browseId}?params=${moodItem.endpoint.params}")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ─── Genre Section ─────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.GenreSection(
    items: List<MoodAndGenres.Item>,
    navController: NavController,
    enableDynamicTheme: Boolean,
    selectedCategoryTitle: String?,
    onCategorySelect: (String, Color) -> Unit,
) {
    val genreItems = items.filter { genreColorMap.containsKey(it.title) }
    if (genreItems.isEmpty()) return

    val pages = genreItems.chunked(10)

    item(key = "genre_title", contentType = CONTENT_TYPE_HEADER) {
        val pagerState = rememberPagerState(pageCount = { pages.size })
        val coroutineScope = rememberCoroutineScope()
        
        NavigationTitle(
            title = "Genres →",
            onClick = {
                coroutineScope.launch {
                    val nextPage = (pagerState.currentPage + 1) % pages.size
                    pagerState.animateScrollToPage(nextPage)
                }
            },
            modifier = Modifier.animateItem()
        )

        androidx.compose.foundation.layout.Column {
            Box(modifier = Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 48.dp),
                    pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val pageItems = pages[page]
                    val rows = pageItems.chunked(2)
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((MoodAndGenresButtonHeight * 5) + 24.dp)
                            .padding(horizontal = 6.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                    ) {
                        rows.forEach { row ->
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { genreItem ->
                                    val accent = genreColorMap[genreItem.title] ?: MaterialTheme.colorScheme.primary
                                    CategoryCard(
                                        title = genreItem.title,
                                        accentColor = accent,
                                        isSelected = selectedCategoryTitle == genreItem.title,
                                        onClick = {
                                            if (enableDynamicTheme) onCategorySelect(genreItem.title, accent)
                                            navController.navigate("youtube_browse/${genreItem.endpoint.browseId}?params=${genreItem.endpoint.params}")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) {
                                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Right-edge gradient fade
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(48.dp)
                        .fillMaxHeight()
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
            }

            // Page Indicator
            androidx.compose.foundation.layout.Row(
                Modifier
                    .height(24.dp)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { iteration ->
                    val isSelected = pagerState.currentPage == iteration
                    val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    val width by androidx.compose.animation.core.animateDpAsState(if (isSelected) 16.dp else 6.dp, label = "indicator")
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(width = width, height = 6.dp)
                    )
                }
            }
        }
    }
}

// ─── Mood & Genres Shimmer Section ────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.MoodAndGenresShimmerSection() {
    item(key = "mood_shimmer", contentType = CONTENT_TYPE_SHIMMER) {
        ShimmerHost(
            modifier = Modifier.animateItem()
        ) {
            TextPlaceholder(
                height = 36.dp,
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 12.dp)
                    .width(250.dp),
            )

            repeat(4) {
                androidx.compose.foundation.layout.Row {
                    repeat(2) {
                        TextPlaceholder(
                            height = MoodAndGenresButtonHeight,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .width(200.dp)
                        )
                    }
                }
            }
        }
    }
}
