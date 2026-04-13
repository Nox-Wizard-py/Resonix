package com.noxwizard.resonix.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.noxwizard.resonix.BuildConfig
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.AppBarHeight
import com.noxwizard.resonix.ui.screens.Screens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResonixTopBar(
    navController: NavController,
    navBackStackEntry: NavBackStackEntry?,
    searchBarScrollBehavior: TopAppBarScrollBehavior,
    topAppBarScrollBehavior: TopAppBarScrollBehavior,
    disableBlur: Boolean,
    pureBlack: Boolean,
    localLatestVersionName: String,
    accountImageUrl: String?,
    onShowAccountDialog: () -> Unit
) {
    val shouldShowBlurBackground = remember(navBackStackEntry) {
        navBackStackEntry?.destination?.route == Screens.Home.route ||
                navBackStackEntry?.destination?.route == Screens.Library.route
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val currentScrollBehavior =
        if (shouldShowBlurBackground) searchBarScrollBehavior else topAppBarScrollBehavior

    Box(
        modifier = Modifier.offset {
            IntOffset(
                x = 0,
                y = currentScrollBehavior.state.heightOffset.toInt()
            )
        }
    ) {
        if (shouldShowBlurBackground) {
            if (disableBlur) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppBarHeight + with(LocalDensity.current) {
                            WindowInsets.systemBars.getTop(LocalDensity.current).toDp()
                        })
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    surfaceColor.copy(alpha = 0.95f),
                                    surfaceColor.copy(alpha = 0.85f),
                                    surfaceColor.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AppBarHeight + with(LocalDensity.current) {
                            WindowInsets.systemBars.getTop(LocalDensity.current).toDp()
                        })
                ) {
                    // Semi-transparent surface — no GPU BlurEffect needed
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(surfaceColor.copy(alpha = 0.7f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.10f),
                                        Color.White.copy(alpha = 0.03f),
                                        Color.Transparent
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        surfaceColor.copy(alpha = 0.3f),
                                        surfaceColor.copy(alpha = 0.1f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // app icon
                    Image(
                        painter = painterResource(R.drawable.about_appbar),
                        contentDescription = null,
                        modifier = Modifier
                            .size(55.dp)
                            .padding(end = 3.dp),
                        contentScale = ContentScale.Fit
                    )

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            actions = {
                IconButton(onClick = { navController.navigate("history") }) {
                    Icon(
                        painter = painterResource(R.drawable.music_history_24),
                        contentDescription = stringResource(R.string.history)
                    )
                }
                IconButton(onClick = { navController.navigate("stats") }) {
                    Icon(
                        painter = painterResource(R.drawable.analytics_24),
                        contentDescription = stringResource(R.string.stats)
                    )
                }
                IconButton(onClick = { navController.navigate("new_release") }) {
                    Icon(
                        painter = painterResource(R.drawable.app_badging_24),
                        contentDescription = stringResource(R.string.new_release_albums)
                    )
                }
                IconButton(onClick = { onShowAccountDialog() }) {
                    BadgedBox(badge = {
                        if (localLatestVersionName != BuildConfig.VERSION_NAME) {
                            Badge()
                        }
                    }) {
                        if (accountImageUrl != null) {
                            AsyncImage(
                                model = accountImageUrl,
                                contentDescription = stringResource(R.string.account),
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.identity_platform_24),
                                contentDescription = stringResource(R.string.account),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            scrollBehavior = currentScrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (shouldShowBlurBackground) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                scrolledContainerColor = if (shouldShowBlurBackground) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
