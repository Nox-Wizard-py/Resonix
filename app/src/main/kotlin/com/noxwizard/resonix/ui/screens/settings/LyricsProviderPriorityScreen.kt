package com.noxwizard.resonix.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.LyricsProviderOrderKey
import com.noxwizard.resonix.lyrics.LyricsProviderRegistry
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderPriorityScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (providerOrderString, onProviderOrderChange) = rememberPreference(
        key = LyricsProviderOrderKey,
        defaultValue = ""
    )
    val orderedProviders = remember(providerOrderString) {
        LyricsProviderRegistry.deserializeProviderOrder(providerOrderString).toMutableList()
    }

    var hasDragged by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val fromProvider = from.key as? String ?: return@rememberReorderableLazyListState
        val toProvider = to.key as? String ?: return@rememberReorderableLazyListState

        val fromIndex = orderedProviders.indexOf(fromProvider)
        val toIndex = orderedProviders.indexOf(toProvider)

        if (fromIndex != -1 && toIndex != -1) {
            val moved = orderedProviders.removeAt(fromIndex)
            orderedProviders.add(toIndex, moved)
            hasDragged = true
        }
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasDragged) {
            onProviderOrderChange(LyricsProviderRegistry.serializeProviderOrder(orderedProviders))
            hasDragged = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lyrics provider priority") },
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
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Drag to reorder providers by preference. Higher position means higher priority.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            itemsIndexed(orderedProviders, key = { _, item -> item }) { index, item ->
                ReorderableItem(
                    state = reorderableState,
                    key = item,
                ) {
                    val shape = when {
                        orderedProviders.size == 1 -> RoundedCornerShape(24.dp)
                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        index == orderedProviders.size - 1 -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        else -> RoundedCornerShape(0.dp)
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index != orderedProviders.size - 1) 1.dp else 0.dp),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { },
                                modifier = Modifier.draggableHandle(),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.drag_handle),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
