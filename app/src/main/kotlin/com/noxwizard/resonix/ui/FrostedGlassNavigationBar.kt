package com.noxwizard.resonix.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import com.noxwizard.resonix.ui.component.ListDialog
import com.noxwizard.resonix.ui.component.MenuState
import com.noxwizard.resonix.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.noxwizard.resonix.constants.NavigationBarHeight
import com.noxwizard.resonix.ui.effects.liquidglass.liquidGlass
import com.noxwizard.resonix.ui.component.BottomSheetState
import com.noxwizard.resonix.ui.player.BottomSheetPlayer
import com.noxwizard.resonix.ui.screens.Screens
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState

private val NavBarHeight = 56.dp
private val NavBarHorizontalMargin = 92.dp
private val NavBarBottomMargin = 16.dp
private val NavBarCornerRadius = 32.dp
private val IconSize = 24.dp
private val IndicatorCornerRadius = 20.dp
private val IndicatorVerticalPadding = 6.dp
private val IndicatorHorizontalPadding = 4.dp

@Composable
fun FrostedGlassNavigationBar(
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
    onItemClick: (Screens, Boolean) -> Unit,
    onItemLongClick: (Screens) -> Unit = {},
    isUtilityFabExpanded: Boolean,
    onUtilityFabExpandedChange: (Boolean) -> Unit,
    menuState: MenuState
) {
    val isDarkTheme = if (pureBlack) true
    else !MaterialTheme.colorScheme.background.luminance().let { it > 0.5f }

    val selectedIndex = navigationItems.indexOfFirst { screen ->
        navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true
    }.coerceAtLeast(0)

    val itemCount = navigationItems.size

    // Animated indicator fraction: slides to selectedIndex
    val indicatorFraction by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(
            durationMillis = 250,
            easing = FastOutSlowInEasing,
        ),
        label = "indicatorSlide",
    )

    // --- Glass colors ---
    val glassBg = if (isDarkTheme)
        Color.Black.copy(alpha = 0.15f)
    else
        Color.White.copy(alpha = 0.20f)

    val indicatorHighColor = if (isDarkTheme) Color.White.copy(alpha = 0.25f)
    else Color.Black.copy(alpha = 0.12f)

    val indicatorLowColor = if (isDarkTheme) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.04f)

    val specularColor = if (isDarkTheme) Color.White.copy(alpha = 0.20f)
    else Color.White.copy(alpha = 0.80f)

    val innerReflectionColor = if (isDarkTheme) Color.White.copy(alpha = 0.06f)
    else Color.White.copy(alpha = 0.40f)

    val borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.10f)
    else Color.Black.copy(alpha = 0.08f)

    val shadowColor = if (isDarkTheme) Color.Black.copy(alpha = 0.50f)
    else Color.Black.copy(alpha = 0.12f)
    
    val backdropLayer = com.noxwizard.resonix.ui.effects.liquidglass.LocalBackdropGraphicsLayer.current

    Box {
        BottomSheetPlayer(
            state = playerBottomSheetState,
            navController = navController,
            pureBlack = pureBlack,
        )

        // Floating pill container
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = NavBarHorizontalMargin,
                    end = NavBarHorizontalMargin,
                    bottom = NavBarBottomMargin,
                )
                .offset {
                    if (navigationBarHeight == 0.dp) {
                        IntOffset(
                            x = 0,
                            y = (bottomInset + NavigationBarHeight).roundToPx(),
                        )
                    } else {
                        val slideOffset =
                            (bottomInset + NavigationBarHeight) *
                                    playerBottomSheetState.progress.coerceIn(0f, 1f)
                        val hideOffset =
                            (bottomInset + NavigationBarHeight) *
                                    (1 - navigationBarHeight / NavigationBarHeight)
                        IntOffset(
                            x = 0,
                            y = (slideOffset + hideOffset).roundToPx(),
                        )
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NavBarHeight)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(NavBarCornerRadius),
                        ambientColor = shadowColor,
                        spotColor = shadowColor,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Glass Background
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(NavBarCornerRadius))
                        .liquidGlass(
                            backdropLayer = backdropLayer,
                            shape = RoundedCornerShape(NavBarCornerRadius),
                            luminanceAnimation = 0.5f,
                            interaction = null,
                            isDarkTheme = isDarkTheme
                        )
                        // Inner glass reflection line at top
                        .drawBehind {
                            // Top specular highlight
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        innerReflectionColor,
                                        innerReflectionColor,
                                        Color.Transparent,
                                    ),
                                    startX = size.width * 0.15f,
                                    endX = size.width * 0.85f,
                                ),
                                topLeft = Offset(size.width * 0.15f, 1.dp.toPx()),
                                size = Size(size.width * 0.70f, 1.5f.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx()),
                            )
                            // Subtle border
                            drawRoundRect(
                                color = borderColor,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = CornerRadius(NavBarCornerRadius.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 0.5f.dp.toPx()
                                ),
                            )
                        }
                )
            Box(
                modifier = Modifier
                    .height(NavBarHeight)
                    .fillMaxWidth()
            ) {
                // Sliding pill indicator (drawn behind icons)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            if (itemCount == 0) return@drawBehind
                            val slotWidth = size.width / itemCount
                            val indicatorX =
                                indicatorFraction * slotWidth + IndicatorHorizontalPadding.toPx()
                            val indicatorW =
                                slotWidth - IndicatorHorizontalPadding.toPx() * 2f
                            val indicatorY = IndicatorVerticalPadding.toPx()
                            val indicatorH =
                                size.height - IndicatorVerticalPadding.toPx() * 2f
                            val cr = CornerRadius(IndicatorCornerRadius.toPx())

                            // Glass gradient fill
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(indicatorHighColor, indicatorLowColor),
                                ),
                                topLeft = Offset(indicatorX, indicatorY),
                                size = Size(indicatorW, indicatorH),
                                cornerRadius = cr,
                            )

                            // Top specular line on indicator
                            val specStartX = indicatorX + indicatorW * 0.15f
                            val specWidth = indicatorW * 0.70f
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        specularColor,
                                        Color.Transparent,
                                    ),
                                    startX = specStartX,
                                    endX = specStartX + specWidth,
                                ),
                                topLeft = Offset(specStartX, indicatorY + 1.5f.dp.toPx()),
                                size = Size(specWidth, 1.dp.toPx()),
                                cornerRadius = CornerRadius(0.5f.dp.toPx()),
                            )

                            // Thin border around indicator (dark theme)
                            if (isDarkTheme) {
                                drawRoundRect(
                                    color = borderColor,
                                    topLeft = Offset(indicatorX, indicatorY),
                                    size = Size(indicatorW, indicatorH),
                                    cornerRadius = cr,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 0.5f.dp.toPx()
                                    ),
                                )
                            }
                        }
                )

                // Navigation items
                Row(
                    modifier = Modifier
                        .matchParentSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    navigationItems.forEachIndexed { index, screen ->
                        val isSelected = index == selectedIndex
                        CompactNavItem(
                            screen = screen,
                            isSelected = isSelected,
                            isDarkTheme = isDarkTheme,
                            onSelect = { onItemClick(screen, isSelected) },
                            onLongSelect = { onItemLongClick(screen) },
                        )
                    }
                }
            }
        }
        }

        // Bottom inset fill
        val baseBg = if (pureBlack) Color.Black else Color.Transparent
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RowScope.CompactNavItem(
    screen: Screens,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = tween(
            durationMillis = 250,
            easing = FastOutSlowInEasing,
        ),
        label = "iconScale",
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutSlowInEasing,
        ),
        label = "iconAlpha",
    )

    val iconTint = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .weight(1f)
            .height(NavBarHeight)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
                onLongClick = onLongSelect
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                id = if (isSelected) screen.iconIdActive else screen.iconIdInactive,
            ),
            contentDescription = stringResource(screen.titleId),
            modifier = Modifier
                .size(IconSize)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = iconAlpha
                },
            tint = iconTint,
        )
    }
}



@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GlassUtilityFab(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    glassBg: Color,
    shadowColor: Color,
    borderColor: Color,
    innerReflectionColor: Color,
    menuState: MenuState,
    navController: NavHostController,
    modifier: Modifier = Modifier,
    useGlassTheme: Boolean = true
) {
    val backdropLayer = com.noxwizard.resonix.ui.effects.liquidglass.LocalBackdropGraphicsLayer.current
    val iconRotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "utility_fab_icon_rotation",
    )

    // Child animation states — computed here so graphicsLayer lambdas can capture them
    val recogStiffness = Spring.StiffnessMediumLow
    val syncStiffness  = Spring.StiffnessMediumLow + 50f

    val menuTransY by animateDpAsState(
        targetValue = if (isExpanded) 0.dp else 24.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "menu_transY",
    )
    val recogAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "recog_alpha",
    )
    val syncAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sync_alpha",
    )

    // Outer anchor: wrapContentSize so nothing clips children, but children
    // placed in a 0-size overflow box so they don't shift the FAB position.
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {

        // Zero-layout-footprint overflow container — renders children above
        // the FAB without affecting the parent's measured size at all.
        Box(
            modifier = Modifier
                .size(0.dp)
                .wrapContentSize(unbounded = true)
                .padding(end = 28.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .padding(bottom = 230.dp)
                    .offset(x = (-70).dp, y = menuTransY),
            ) {
                if (syncAlpha > 0.01f) {
                    ChildFabItem(
                        icon = R.drawable.sync,
                        label = "Resonance",
                        onClick = {
                            onExpandedChange(false)
                            navController.navigate("listen_together")
                        },
                        onLongClick = {
                            onExpandedChange(false)
                            menuState.show {
                                ListDialog(onDismiss = menuState::dismiss) {
                                    item {
                                        ListItem(
                                            headlineContent = { Text("Invite users") },
                                            leadingContent = { Icon(painterResource(R.drawable.share), contentDescription = null) },
                                            modifier = Modifier.clickable { menuState.dismiss() }
                                        )
                                    }
                                    item {
                                        ListItem(
                                            headlineContent = { Text("Copy session code") },
                                            leadingContent = { Icon(painterResource(R.drawable.link), contentDescription = null) },
                                            modifier = Modifier.clickable { menuState.dismiss() }
                                        )
                                    }
                                    item {
                                        ListItem(
                                            headlineContent = { Text("Leave session", color = MaterialTheme.colorScheme.error) },
                                            leadingContent = { Icon(painterResource(R.drawable.logout), contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            modifier = Modifier.clickable { menuState.dismiss() }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = syncAlpha.coerceIn(0f, 1f)
                                scaleY = syncAlpha.coerceIn(0f, 1f)
                                alpha = syncAlpha
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                            },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (recogAlpha > 0.01f) {
                    ChildFabItem(
                        icon = R.drawable.mic_24,
                        label = "Music Recognition",
                        onClick = {
                            onExpandedChange(false)
                            navController.navigate("recognition")
                        },
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = recogAlpha.coerceIn(0f, 1f)
                                scaleY = recogAlpha.coerceIn(0f, 1f)
                                alpha = recogAlpha
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                            },
                    )
                }
            }
        }

        // Main anchor FAB — always at BottomEnd, never moves
        if (useGlassTheme) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape,
                        ambientColor = shadowColor,
                        spotColor = shadowColor,
                    )
                    .clip(CircleShape)
                    .clickable { onExpandedChange(!isExpanded) },
                contentAlignment = Alignment.Center,
            ) {
                // Glass background layer
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .liquidGlass(
                            backdropLayer = backdropLayer,
                            shape = CircleShape,
                            luminanceAnimation = 0.5f,
                            interaction = null,
                            isDarkTheme = isDarkTheme
                        )
                        .drawBehind {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, innerReflectionColor, innerReflectionColor, Color.Transparent),
                                    startX = size.width * 0.15f,
                                    endX = size.width * 0.85f,
                                ),
                                topLeft = Offset(size.width * 0.15f, 1.dp.toPx()),
                                size = Size(size.width * 0.70f, 1.5f.dp.toPx()),
                                cornerRadius = CornerRadius(1.dp.toPx()),
                            )
                            drawRoundRect(
                                color = borderColor,
                                topLeft = Offset.Zero,
                                size = size,
                                cornerRadius = CornerRadius(size.width / 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5f.dp.toPx()),
                            )
                        }
                )

                // Foreground content
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                    tint = if (isDarkTheme) Color.White else Color.Black,
                )
            }
        } else {
            androidx.compose.material3.FloatingActionButton(
                onClick = { onExpandedChange(!isExpanded) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation },
                )
            }
        }
    }
}

@Composable
private fun ChildFabItem(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = if (onLongClick != null) {
                Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() },
                    )
                }
            } else Modifier,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.size(10.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
