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
import com.noxwizard.resonix.constants.PaxsenixProviderOrderKey
import com.noxwizard.resonix.lyrics.LyricsProviderRegistry
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val PAX_PREFIX = "pax_"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsProviderPriorityScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // ── App-layer provider order ──────────────────────────────────────────────
    val (providerOrderString, onProviderOrderChange) = rememberPreference(
        key = LyricsProviderOrderKey,
        defaultValue = ""
    )
    val orderedProviders = remember(providerOrderString) {
        LyricsProviderRegistry.deserializeProviderOrder(providerOrderString).toMutableList()
    }

    // ── Paxsenix engine provider order ────────────────────────────────────────
    val (paxsenixOrderString, onPaxsenixOrderChange) = rememberPreference(
        key = PaxsenixProviderOrderKey,
        defaultValue = ""
    )
    val orderedPaxsenixProviders = remember(paxsenixOrderString) {
        LyricsProviderRegistry.deserializePaxsenixOrder(paxsenixOrderString).toMutableList()
    }

    var hasDragged by remember { mutableStateOf(false) }
    var hasDraggedPaxsenix by remember { mutableStateOf(false) }

    // Single LazyListState shared by the single LazyColumn.
    val lazyListState = rememberLazyListState()

    // Single reorderable state for the shared LazyColumn.
    // Dispatch to the correct list based on key prefix.
    val reorderableState = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey   = to.key   as? String ?: return@rememberReorderableLazyListState

        val fromIsPax = fromKey.startsWith(PAX_PREFIX)
        val toIsPax   = toKey.startsWith(PAX_PREFIX)

        // Only allow reordering within the same section
        if (fromIsPax != toIsPax) return@rememberReorderableLazyListState

        if (fromIsPax) {
            // Paxsenix section — strip prefix for list lookup
            val fromName = fromKey.removePrefix(PAX_PREFIX)
            val toName   = toKey.removePrefix(PAX_PREFIX)
            val fi = orderedPaxsenixProviders.indexOf(fromName)
            val ti = orderedPaxsenixProviders.indexOf(toName)
            if (fi != -1 && ti != -1) {
                orderedPaxsenixProviders.add(ti, orderedPaxsenixProviders.removeAt(fi))
                hasDraggedPaxsenix = true
            }
        } else {
            // App-layer section
            val fi = orderedProviders.indexOf(fromKey)
            val ti = orderedProviders.indexOf(toKey)
            if (fi != -1 && ti != -1) {
                orderedProviders.add(ti, orderedProviders.removeAt(fi))
                hasDragged = true
            }
        }
    }

    // Persist app-layer order when drag ends
    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && hasDragged) {
            onProviderOrderChange(LyricsProviderRegistry.serializeProviderOrder(orderedProviders))
            hasDragged = false
        }
        if (!reorderableState.isAnyItemDragging && hasDraggedPaxsenix) {
            onPaxsenixOrderChange(LyricsProviderRegistry.serializePaxsenixOrder(orderedPaxsenixProviders))
            hasDraggedPaxsenix = false
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

            // ── App Providers section ─────────────────────────────────────────
            item {
                Text(
                    text = "App Providers",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(orderedProviders, key = { _, item -> item }) { index, item ->
                ReorderableItem(
                    state = reorderableState,
                    key = item,
                ) {
                    ProviderDragRow(
                        name = item,
                        index = index,
                        total = orderedProviders.size,
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                }
            }

            // ── Paxsenix Engine Providers section ─────────────────────────────
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Paxsenix Engine Providers",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Text(
                    text = "Controls priority inside the Paxsenix lyrics engine.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }

            itemsIndexed(orderedPaxsenixProviders, key = { _, item -> "$PAX_PREFIX$item" }) { index, item ->
                ReorderableItem(
                    state = reorderableState,          // ← same state, same LazyColumn
                    key = "$PAX_PREFIX$item",
                ) {
                    ProviderDragRow(
                        name = item,
                        index = index,
                        total = orderedPaxsenixProviders.size,
                        dragHandleModifier = Modifier.draggableHandle(),
                    )
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ProviderDragRow(
    name: String,
    index: Int,
    total: Int,
    dragHandleModifier: Modifier = Modifier,
) {
    val shape = when {
        total == 1       -> RoundedCornerShape(24.dp)
        index == 0       -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        index == total - 1 -> RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        else             -> RoundedCornerShape(0.dp)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (index != total - 1) 1.dp else 0.dp),
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
                modifier = dragHandleModifier,
            ) {
                Icon(
                    painter = painterResource(R.drawable.drag_handle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
