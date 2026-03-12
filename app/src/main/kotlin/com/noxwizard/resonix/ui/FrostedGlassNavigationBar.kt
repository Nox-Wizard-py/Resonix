package com.noxwizard.resonix.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.noxwizard.resonix.constants.NavigationBarHeight
import com.noxwizard.resonix.ui.component.BottomSheetState3
import com.noxwizard.resonix.ui.player.BottomSheetPlayer
import com.noxwizard.resonix.ui.screens.Screens
import io.github.fletchmckee.liquid.liquid
import io.github.fletchmckee.liquid.rememberLiquidState

private val NavBarHeight = 56.dp
private val NavBarHorizontalMargin = 72.dp
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
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.50f)
    else
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)

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
                }
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(NavBarCornerRadius),
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                )
                .clip(RoundedCornerShape(NavBarCornerRadius))
                .background(glassBg)
                .liquid(rememberLiquidState()) {
                    shape = RoundedCornerShape(NavBarCornerRadius)
                    frost = if (isDarkTheme) 32.dp else 28.dp
                    curve = if (isDarkTheme) 0.40f else 0.50f
                    refraction = if (isDarkTheme) 0.06f else 0.10f
                    dispersion = if (isDarkTheme) 0.15f else 0.22f
                    saturation = if (isDarkTheme) 0.70f else 0.90f
                    contrast = if (isDarkTheme) 1.9f else 1.2f
                }
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
                },
            contentAlignment = Alignment.Center,
        ) {
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
                        )
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

@Composable
private fun RowScope.CompactNavItem(
    screen: Screens,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onSelect: () -> Unit,
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { onSelect() }
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
