package com.noxwizard.resonix.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.noxwizard.resonix.ui.component.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.MiniPlayerHeight
import com.noxwizard.resonix.constants.PauseSearchHistoryKey
import com.noxwizard.resonix.constants.SearchSource
import com.noxwizard.resonix.ui.screens.Screens
import com.noxwizard.resonix.db.MusicDatabase
import com.noxwizard.resonix.db.entities.SearchHistory
import com.noxwizard.resonix.ui.component.BottomSheetState
import com.noxwizard.resonix.ui.component.TopSearch
import com.noxwizard.resonix.ui.screens.search.LocalSearchScreen
import com.noxwizard.resonix.ui.screens.search.OnlineSearchScreen
import com.noxwizard.resonix.utils.dataStore
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResonixSearchBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    searchSource: SearchSource,
    onSearchSourceChange: (SearchSource) -> Unit,
    navigationItems: List<Screens>,
    navBackStackEntry: NavBackStackEntry?,
    navController: NavHostController,
    searchBarFocusRequester: FocusRequester,
    pureBlack: Boolean,
    playerBottomSheetState: BottomSheetState,
    database: MusicDatabase,
    modifier: Modifier = Modifier
) {
    TopSearch(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        active = active,
        onActiveChange = onActiveChange,
        placeholder = {
            Text(
                text = stringResource(
                    when (searchSource) {
                        SearchSource.LOCAL -> R.string.search_library
                        SearchSource.ONLINE -> R.string.search_yt_music
                    }
                ),
            )
        },
        leadingIcon = {
            IconButton(
                onClick = {
                    when {
                        active -> onActiveChange(false)
                        !navigationItems.any { screen -> screen.route == navBackStackEntry?.destination?.route } -> {
                            navController.navigateUp()
                        }

                        else -> onActiveChange(true)
                    }
                },
                onLongClick = {
                    when {
                        active -> {}
                        !navigationItems.any { screen -> screen.route == navBackStackEntry?.destination?.route } -> {
                            // Back handling if needed, or just let navigateUp work
                            navController.popBackStack(navController.graph.startDestinationId, false)
                        }
                        else -> {}
                    }
                },
            ) {
                Icon(
                    painterResource(
                        if (active ||
                            !navigationItems.any { screen -> screen.route == navBackStackEntry?.destination?.route }
                        ) {
                            R.drawable.arrow_back
                        } else {
                            R.drawable.search
                        },
                    ),
                    contentDescription = null,
                )
            }
        },
        trailingIcon = {
            Row {
                if (active) {
                    if (query.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onQueryChange(
                                    TextFieldValue(
                                        ""
                                    )
                                )
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onSearchSourceChange(
                                if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                            )
                        },
                    ) {
                        Icon(
                            painter = painterResource(
                                when (searchSource) {
                                    SearchSource.LOCAL -> R.drawable.library_music
                                    SearchSource.ONLINE -> R.drawable.language
                                },
                            ),
                            contentDescription = null,
                        )
                    }
                }
            }
        },
        modifier = modifier
            .focusRequester(searchBarFocusRequester),
        focusRequester = searchBarFocusRequester,
        colors = if (pureBlack && active) {
            SearchBarDefaults.colors(
                containerColor = Color.Black,
                dividerColor = Color.DarkGray,
                inputFieldColors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.Gray,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )
        } else {
            SearchBarDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        }
    ) {
        Crossfade(
            targetState = searchSource,
            label = "",
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp)
                .navigationBarsPadding(),
        ) { searchSource ->
            val context = androidx.compose.ui.platform.LocalContext.current
            when (searchSource) {
                SearchSource.LOCAL ->
                    LocalSearchScreen(
                        query = query.text,
                        navController = navController,
                        onDismiss = { onActiveChange(false) },
                        pureBlack = pureBlack,
                    )

                SearchSource.ONLINE ->
                    OnlineSearchScreen(
                        query = query.text,
                        onQueryChange = onQueryChange,
                        navController = navController,
                        onSearch = {
                            navController.navigate(
                                "search/${URLEncoder.encode(it, "UTF-8")}"
                            )
                            // Note: onSearch lambda in MainActivity handled history insertion.
                            // We need to pass that responsibility or replicate it.
                            // Here we are inside the Screen, so replicating is fine given dependencies.
                            // But cleaner would be to use the passed `onSearch` maybe?
                            // Actually `onSearch` param to ResonixSearchBar is what MainActivity passes.
                            // The OnlineSearchScreen needs to call that or do it itself.
                            // In MainActivity:
                            /*
                              onSearch = { ... logic to navigate and save history ... }
                            */
                            // So we should just call onSearch(it) here!
                            // But wait, the original code had inline navigation inside OnlineSearchScreen onSearch callback.
                            // Let's defer to the parent's onSearch if possible, or replicate.
                            // The passed onSearch handles history saving and navigation.
                            onSearch(it) 
                        },
                        onDismiss = { onActiveChange(false) },
                        pureBlack = pureBlack
                    )
            }
        }
    }
}
