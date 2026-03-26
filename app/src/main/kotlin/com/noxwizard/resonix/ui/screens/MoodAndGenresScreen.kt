package com.noxwizard.resonix.ui.screens

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.ripple
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.component.NavigationTitle
import com.noxwizard.resonix.ui.component.shimmer.ListItemPlaceHolder
import com.noxwizard.resonix.ui.component.shimmer.ShimmerHost
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.viewmodels.MoodAndGenresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val localConfiguration = LocalConfiguration.current
    val itemsPerRow = if (localConfiguration.orientation == ORIENTATION_LANDSCAPE) 3 else 2

    val moodAndGenresList by viewModel.moodAndGenres.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        if (moodAndGenresList == null) {
            item {
                ShimmerHost {
                    repeat(8) {
                        ListItemPlaceHolder()
                    }
                }
            }
        }

        moodAndGenresList?.forEach { moodAndGenres ->
            item {
                NavigationTitle(
                    title = moodAndGenres.title,
                )

                Column(
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    moodAndGenres.items.chunked(itemsPerRow).forEach { row ->
                        Row {
                            row.forEach {
                                MoodAndGenresButton(
                                    title = it.title,
                                    onClick = {
                                        navController.navigate("youtube_browse/${it.endpoint.browseId}?params=${it.endpoint.params}")
                                    },
                                    modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(6.dp),
                                )
                            }

                            repeat(itemsPerRow - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.mood_and_genres)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
fun MoodAndGenresButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
        modifier
            .height(MoodAndGenresButtonHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

val MoodAndGenresButtonHeight = 48.dp

// ─── Category Color Maps ──────────────────────────────────────────

val moodColorMap: Map<String, Color> = mapOf(
    "Chill" to Color(0xFF2EC4B6),
    "Commute" to Color(0xFF3A86FF),
    "Energize" to Color(0xFF70E000),
    "Feel Good" to Color(0xFFF77F00),
    "Focus" to Color(0xFFFFC857),
    "Gaming" to Color(0xFFE63946),
    "Party" to Color(0xFFFF006E),
    "Romance" to Color(0xFFC77DFF),
    "Sad" to Color(0xFF5C6BC0),
    "Sleep" to Color(0xFF1D3557),
    "Workout" to Color(0xFFFF5400)
)

val genreColorMap: Map<String, Color> = mapOf(
    "African" to Color(0xFFF3722C),
    "Bengali" to Color(0xFF2A9D8F),
    "Arabic" to Color(0xFFC9A227),
    "Bhojpuri" to Color(0xFFFFB703),
    "Carnatic Classical" to Color(0xFF6A4C93),
    "Classical" to Color(0xFF9D7ED6),
    "Country & Americana" to Color(0xFF8D5524),
    "Dance & Electronic" to Color(0xFF00F5D4),
    "Decades" to Color(0xFF9B5DE5),
    "Desi Hip-Hop" to Color(0xFFD62828),
    "Devotional" to Color(0xFFFF9933),
    "Family" to Color(0xFFF4A261),
    "Folk & Acoustic" to Color(0xFF588157),
    "Ghazal / Sufi" to Color(0xFF7B2CBF),
    "Gujarati" to Color(0xFFF4C430),
    "Hindi" to Color(0xFFD00000),
    "Haryanvi" to Color(0xFFC05746),
    "Hindustani Classical" to Color(0xFF3A0CA3),
    "Hip-Hop" to Color(0xFFB5179E),
    "Indian Indie" to Color(0xFF8338EC),
    "Indian Pop" to Color(0xFFFF4D6D),
    "Indie & Alternative" to Color(0xFF386641),
    "J-Pop" to Color(0xFFFF7EB6),
    "Jazz" to Color(0xFF264653),
    "K-Pop" to Color(0xFFFF5DA2),
    "Kannada" to Color(0xFF4361EE),
    "Latin" to Color(0xFFE63946),
    "Malayalam" to Color(0xFF2A9D8F),
    "Marathi" to Color(0xFFE85D04),
    "Metal" to Color(0xFF495057),
    "Monsoon" to Color(0xFF4CC9F0),
    "Punjabi" to Color(0xFFFFB703),
    "Reggae & Caribbean" to Color(0xFF2B9348),
    "Tamil" to Color(0xFF800020),
    "Pop" to Color(0xFF7209B7),
    "R&B & Soul" to Color(0xFF6F42C1),
    "Rock" to Color(0xFFD00000),
    "Telugu" to Color(0xFFE63946)
)

// ─── Category Card ────────────────────────────────────────────────

@Composable
fun CategoryCard(
    title: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 0.dp,
        animationSpec = tween(100),
        label = "elevation"
    )

    val bgAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.25f else 0.13f,
        animationSpec = tween(250),
        label = "bgAlpha"
    )
    val barWidth by animateDpAsState(
        targetValue = if (isSelected) 9.dp else 6.dp,
        animationSpec = tween(250),
        label = "barWidth"
    )

    Box(
        modifier = modifier
            .height(MoodAndGenresButtonHeight)
            .scale(scale)
            .shadow(elevation, RoundedCornerShape(10.dp), clip = false)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                onClick = onClick
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            // Label
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
            )
        }
    }
}

