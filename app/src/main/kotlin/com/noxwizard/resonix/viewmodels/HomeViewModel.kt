package com.noxwizard.resonix.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noxwizard.resonix.innertube.YouTube
import com.noxwizard.resonix.innertube.models.PlaylistItem
import com.noxwizard.resonix.innertube.models.WatchEndpoint
import com.noxwizard.resonix.innertube.models.YTItem
import com.noxwizard.resonix.innertube.models.filterExplicit
import com.noxwizard.resonix.innertube.pages.ExplorePage
import com.noxwizard.resonix.innertube.pages.HomePage
import com.noxwizard.resonix.innertube.utils.completed
import com.noxwizard.resonix.constants.HideExplicitKey
import com.noxwizard.resonix.constants.InnerTubeCookieKey
import com.noxwizard.resonix.constants.QuickPicks
import com.noxwizard.resonix.constants.QuickPicksKey
import com.noxwizard.resonix.constants.YtmSyncKey
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.*
import com.noxwizard.resonix.extensions.toEnum
import com.noxwizard.resonix.models.SimilarRecommendation
import com.noxwizard.resonix.utils.dataStore
import com.noxwizard.resonix.utils.get
import com.noxwizard.resonix.utils.SyncUtils
import com.noxwizard.resonix.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject
import com.noxwizard.resonix.di.IoDispatcher

/**
 * Groups account-related UI state to reduce recomposition triggers.
 * When any account field changes, composables reading this single state
 * recompose once instead of up to 3 times.
 */
data class AccountState(
    val name: String = "Guest",
    val imageUrl: String? = null,
    val playlists: List<PlaylistItem>? = null,
)

/**
 * Groups local DB content. Updates together when DB loads.
 */
data class LocalContentState(
    val quickPicks: List<Song>? = null,
    val forgottenFavorites: List<Song>? = null,
    val keepListening: List<LocalItem>? = null,
)

/**
 * Groups network content. Updates together when API loads.
 */
data class OnlineContentState(
    val similarRecommendations: List<SimilarRecommendation>? = null,
    val homePage: HomePage? = null,
    val explorePage: ExplorePage? = null,
    val selectedChip: HomePage.Chip? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    // Local Content State
    private val _quickPicks = MutableStateFlow<List<Song>?>(null)
    private val _forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    private val _keepListening = MutableStateFlow<List<LocalItem>?>(null)

    val localContentState: StateFlow<LocalContentState> = combine(
        _quickPicks, _forgottenFavorites, _keepListening
    ) { quickPicks, forgottenFavorites, keepListening ->
        LocalContentState(quickPicks, forgottenFavorites, keepListening)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalContentState())

    // Online Content State
    private val _similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    private val _homePage = MutableStateFlow<HomePage?>(null)
    private val _explorePage = MutableStateFlow<ExplorePage?>(null)
    private val _selectedChip = MutableStateFlow<HomePage.Chip?>(null)

    val onlineContentState: StateFlow<OnlineContentState> = combine(
        _similarRecommendations, _homePage, _explorePage, _selectedChip
    ) { similarRecommendations, homePage, explorePage, selectedChip ->
        OnlineContentState(similarRecommendations, homePage, explorePage, selectedChip)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, OnlineContentState())

    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    // Account state — grouped to reduce recomposition triggers (3 flows → 1)
    private val _accountName = MutableStateFlow("Guest")
    private val _accountImageUrl = MutableStateFlow<String?>(null)
    private val _accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)

    val accountState: StateFlow<AccountState> = combine(
        _accountName, _accountImageUrl, _accountPlaylists
    ) { name, imageUrl, playlists ->
        AccountState(name, imageUrl, playlists)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountState())

    // Pre-computed aggregate lists — derived in ViewModel to avoid composition-time allocation.
    // Previously computed via remember() in HomeScreen, causing GC pressure on every recomposition.
    val allLocalItems: StateFlow<List<LocalItem>> = combine(
        _quickPicks, _forgottenFavorites, _keepListening
    ) { quickPicks, forgottenFavorites, keepListening ->
        (quickPicks.orEmpty() + forgottenFavorites.orEmpty() + keepListening.orEmpty())
            .filter { it is Song || it is Album }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allYtItems: StateFlow<List<YTItem>> = combine(
        _similarRecommendations, _homePage
    ) { similarRecommendations, homePage ->
        similarRecommendations?.flatMap { it.items }.orEmpty() +
                homePage?.sections?.flatMap { it.items }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Track if initial load has completed (survives config changes via Hilt ViewModel scope)
    private var isInitialized = false

    // Track if we're currently processing account data
    private var isProcessingAccountData = false

    private suspend fun getQuickPicks(){
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> _quickPicks.value = database.quickPicks().first().shuffled().take(20)
            QuickPicks.LAST_LISTEN -> songLoad()
        }
    }

    /**
     * Progressive parallel loading: each section loads independently via supervisorScope.
     * If one section fails, others still succeed. UI shows sections as they arrive.
     */
    private suspend fun load() {
        if (isInitialized) return
        isInitialized = true // Mark as initialized immediately to prevent parallel triggers
        isLoading.value = true
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        supervisorScope {
            // Section 1: Quick Picks (local DB — fast)
            launch {
                try {
                    getQuickPicks()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            // Section 2: Forgotten Favorites (local DB — fast)
            launch {
                try {
                    _forgottenFavorites.value = database.forgottenFavorites().first().shuffled().take(20)
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            // Section 3: Keep Listening (local DB — fast)
            launch {
                try {
                    val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                    val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                        .first().shuffled().take(10)

                    val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                        .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)

                    val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
                        .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                        .shuffled().take(5)

                    _keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            // Section 4: Similar Recommendations (network — slow, most expensive)
            launch {
                try {
                    val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                    val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
                        .filter { it.artist.isYouTubeArtist }
                        .shuffled().take(3)
                        .mapNotNull {
                            val items = mutableListOf<YTItem>()
                            YouTube.artist(it.id).onSuccess { page ->
                                items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                                items += page.sections.lastOrNull()?.items.orEmpty()
                            }
                            SimilarRecommendation(
                                title = it,
                                items = items.filterExplicit(hideExplicit).shuffled().ifEmpty { return@mapNotNull null }
                            )
                        }

                    val songRecommendations = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
                        .filter { it.album != null }
                        .shuffled().take(2)
                        .mapNotNull { song ->
                            val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                                ?: return@mapNotNull null
                            val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                            SimilarRecommendation(
                                title = song,
                                items = (page.songs.shuffled().take(8) +
                                        page.albums.shuffled().take(4) +
                                        page.artists.shuffled().take(4) +
                                        page.playlists.shuffled().take(4))
                                    .filterExplicit(hideExplicit)
                                    .shuffled()
                                    .ifEmpty { return@mapNotNull null }
                            )
                        }

                    _similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            // Section 5: YouTube Home Page (network)
            launch {
                try {
                    YouTube.home().onSuccess { page ->
                        _homePage.value = page.copy(
                            sections = page.sections.map { section ->
                                section.copy(items = section.items.filterExplicit(hideExplicit))
                            }
                        )
                    }.onFailure {
                        reportException(it)
                    }
                } catch (e: Exception) {
                    reportException(e)
                }
            }

            // Section 6: Explore Page (network)
            launch {
                try {
                    YouTube.explore().onSuccess { page ->
                        val artists: MutableMap<Int, String> = mutableMapOf()
                        val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                        database.allArtistsByPlayTime().first().let { list ->
                            var favIndex = 0
                            for ((artistsIndex, artist) in list.withIndex()) {
                                artists[artistsIndex] = artist.id
                                if (artist.artist.bookmarkedAt != null) {
                                    favouriteArtists[favIndex] = artist.id
                                    favIndex++
                                }
                            }
                        }
                        _explorePage.value = page.copy(
                            newReleaseAlbums = page.newReleaseAlbums
                                .sortedBy { album ->
                                    val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                    val firstArtistKey = artistIds.firstNotNullOfOrNull { artistId ->
                                        if (artistId in favouriteArtists.values) {
                                            favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                        } else {
                                            artists.entries.firstOrNull { it.value == artistId }?.key
                                        }
                                    } ?: Int.MAX_VALUE
                                    firstArtistKey
                                }.filterExplicit(hideExplicit)
                        )
                    }.onFailure {
                        reportException(it)
                    }
                } catch (e: Exception) {
                    reportException(e)
                }
            }
        }

        isLoading.value = false
        // isInitialized = true // Moved to start
    }


    private suspend fun songLoad() {
        val song = database.events().first().firstOrNull()?.song
        if (song != null) {
            if (database.hasRelatedSongs(song.id)) {
                val relatedSongs = database.getRelatedSongs(song.id).first().shuffled().take(20)
                _quickPicks.value = relatedSongs
            }
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        _isLoadingMore.value = true

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        viewModelScope.launch(ioDispatcher) {
            val nextSections = YouTube.home(continuation).getOrNull()
            if (nextSections == null) {
                _isLoadingMore.value = false
                return@launch
            }

            _homePage.update { current ->
                if (current == null) return@update nextSections

                // Append new sections, but ensure we don't duplicate if the same request ran twice
                // (though the flag should prevent this, existing content check is safer)
                val currentSectionTitles = current.sections.map { it.title }.toSet()
                val uniqueNewSections = nextSections.sections.filter {
                    it.title !in currentSectionTitles
                }

                // Optimization: Only process (filter/copy) the NEW sections.
                // Re-processing current.sections is wasteful O(N^2) and causes GC thrashing on large feeds.
                val processedNewSections = uniqueNewSections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit))
                }

                nextSections.copy(
                    chips = current.chips,
                    sections = current.sections + processedNewSections
                )
            }
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == _selectedChip.value && previousHomePage.value != null) {
            _homePage.value = previousHomePage.value
            previousHomePage.value = null
            _selectedChip.value = null
            return
        }

        if (_selectedChip.value == null) {
            previousHomePage.value = _homePage.value
        }

        viewModelScope.launch(ioDispatcher) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch

            _homePage.value = nextSections.copy(
                chips = _homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit))
                }
            )
            _selectedChip.value = chip
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(ioDispatcher) {
            isRefreshing.value = true
            isInitialized = false  // Allow reload on explicit refresh
            load()
            isRefreshing.value = false
        }
    }

    fun refreshAccountData() {
        viewModelScope.launch(ioDispatcher) {
            if (isProcessingAccountData) return@launch

            isProcessingAccountData = true
            try {
                val cookie = context.dataStore.get(InnerTubeCookieKey, "")
                if (cookie.isNotEmpty()) {
                    _accountName.value = "Guest"
                    _accountImageUrl.value = null
                    _accountPlaylists.value = null

                    YouTube.cookie = cookie

                    YouTube.accountInfo().onSuccess { info ->
                        _accountName.value = info.name
                        _accountImageUrl.value = info.thumbnailUrl
                    }.onFailure {
                        reportException(it)
                    }

                    viewModelScope.launch(ioDispatcher) {
                        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                            val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                            _accountPlaylists.value = lists
                        }.onFailure {
                            reportException(it)
                        }
                    }
                } else {
                    _accountName.value = "Guest"
                    _accountImageUrl.value = null
                    _accountPlaylists.value = null
                }
            } finally {
                isProcessingAccountData = false
            }
        }
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            // Wait for cookie to be available, then load
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .first()

            load()

            // Sync runs after load completes, in parallel it won't block the UI
            val isSyncEnabled = context.dataStore.data
                .map { it[YtmSyncKey] ?: true }
                .distinctUntilChanged()
                .first()

            if (isSyncEnabled) {
                try {
                    syncUtils.syncLikedSongs()
                    syncUtils.syncLibrarySongs()
                    syncUtils.syncSavedPlaylists()
                    syncUtils.syncLikedAlbums()
                    syncUtils.syncArtistsSubscriptions()
                    syncUtils.syncAutoSyncPlaylists()
                } catch (e: Exception) {
                    timber.log.Timber.e(e, "Error during sync")
                }
            }
        }

        // Listen for cookie changes AFTER initial emission (drop(1) skips the first value)
        // No delay hacks — react directly to actual changes
        viewModelScope.launch(ioDispatcher) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .drop(1)  // Skip the initial emission, we handle that in load()
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect

                    isProcessingAccountData = true
                    try {
                        if (cookie != null && cookie.isNotEmpty()) {
                            _accountName.value = "Guest"
                            _accountImageUrl.value = null
                            _accountPlaylists.value = null

                            YouTube.cookie = cookie

                            YouTube.accountInfo().onSuccess { info ->
                                _accountName.value = info.name
                                _accountImageUrl.value = info.thumbnailUrl
                            }.onFailure {
                                reportException(it)
                            }

                            viewModelScope.launch(ioDispatcher) {
                                YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                                    val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                                    _accountPlaylists.value = lists
                                }.onFailure {
                                    reportException(it)
                                }
                            }
                        } else {
                            _accountName.value = "Guest"
                            _accountImageUrl.value = null
                            _accountPlaylists.value = null
                        }
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }
    }
}
