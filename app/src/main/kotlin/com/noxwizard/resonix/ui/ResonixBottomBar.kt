package com.noxwizard.resonix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.noxwizard.resonix.constants.NavigationBarHeight
import com.noxwizard.resonix.ui.component.BottomSheetState
import com.noxwizard.resonix.ui.player.BottomSheetPlayer
import com.noxwizard.resonix.ui.screens.Screens

@Composable
fun ResonixBottomBar(
    navController: NavHostController,
    navBackStackEntry: NavBackStackEntry?,
    playerBottomSheetState: BottomSheetState,
    navigationBarHeight: Dp,
    currentNavPadding: Dp,
    bottomInset: Dp,
    bottomInsetDp: Dp,
    pureBlack: Boolean,
    slimNav: Boolean,

    navigationItems: List<Screens>,
    onItemClick: (Screens, Boolean) -> Unit
) {
    Box {
        BottomSheetPlayer(
            state = playerBottomSheetState,
            navController = navController,
            pureBlack = pureBlack,

        )
        NavigationBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(bottomInset + currentNavPadding)
                .offset {
                    if (navigationBarHeight == 0.dp) {
                        IntOffset(
                            x = 0,
                            y = (bottomInset + NavigationBarHeight).roundToPx(),
                        )
                    } else {
                        val slideOffset =
                            (bottomInset + NavigationBarHeight) *
                                    playerBottomSheetState.progress.coerceIn(
                                        0f,
                                        1f,
                                    )
                        val hideOffset =
                            (bottomInset + NavigationBarHeight) * (1 - navigationBarHeight / NavigationBarHeight)
                        IntOffset(
                            x = 0,
                            y = (slideOffset + hideOffset).roundToPx(),
                        )
                    }
                },
            containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            navigationItems.fastForEach { screen ->
                val isSelected =
                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                NavigationBarItem(
                    selected = isSelected,
                    icon = {
                        Icon(
                            painter = painterResource(
                                id = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                            ),
                            contentDescription = null,
                        )
                    },
                    label = {
                        if (!slimNav) {
                            Text(
                                text = stringResource(screen.titleId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                    },
                    onClick = {
                        onItemClick(screen, isSelected)
                    },
                )
            }
        }
        val baseBg = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        val insetBg = if (playerBottomSheetState.progress > 0f) Color.Transparent else baseBg

        Box(
            modifier = Modifier
                .background(insetBg)
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(bottomInsetDp)
        )
    }
}
