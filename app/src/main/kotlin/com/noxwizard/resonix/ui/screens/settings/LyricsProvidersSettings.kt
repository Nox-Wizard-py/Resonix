package com.noxwizard.resonix.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.noxwizard.resonix.LocalPlayerAwareWindowInsets
import com.noxwizard.resonix.R
import com.noxwizard.resonix.constants.EnableBetterLyricsKey
import com.noxwizard.resonix.constants.EnableBetterLyricsMusixmatchKey
import com.noxwizard.resonix.constants.EnableKugouKey
import com.noxwizard.resonix.constants.EnableLrcLibKey
import com.noxwizard.resonix.constants.EnablePaxsenixAppleMusicKey
import com.noxwizard.resonix.constants.EnablePaxsenixMusixmatchKey
import com.noxwizard.resonix.constants.EnablePaxsenixSpotifyKey
import com.noxwizard.resonix.constants.EnableSimpMusicKey
import com.noxwizard.resonix.ui.component.IconButton
import com.noxwizard.resonix.ui.utils.backToMain
import com.noxwizard.resonix.utils.rememberPreference

// ─── Data model ──────────────────────────────────────────────────────────────

data class LyricsProviderUiModel(
    val id: String,
    val displayName: String,
    val category: String,           // e.g. "PREMIUM_WORD_SYNC"
    val capabilities: List<String>, // badge labels, e.g. ["WORD_SYNC", "PREMIUM"]
    val reliability: String,        // e.g. "98%"
    val averageLatency: String,     // e.g. "420ms"
    val supportsWordSync: Boolean,
    val sourceType: String,         // e.g. "Musixmatch"
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit,
)

// ─── Entry point ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LyricsProvidersSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // ── Preferences ──────────────────────────────────────────────────────────
    val (enablePaxMusixmatch, onPaxMusixmatch) = rememberPreference(EnablePaxsenixMusixmatchKey, true)
    val (enablePaxSpotify, onPaxSpotify) = rememberPreference(EnablePaxsenixSpotifyKey, true)
    val (enablePaxApple, onPaxApple) = rememberPreference(EnablePaxsenixAppleMusicKey, true)
    val (enableBLMusixmatch, onBLMusixmatch) = rememberPreference(EnableBetterLyricsMusixmatchKey, true)
    val (enableBetterLyrics, onBetterLyrics) = rememberPreference(EnableBetterLyricsKey, true)
    val (enableLrclib, onLrclib) = rememberPreference(EnableLrcLibKey, true)
    val (enableKugou, onKugou) = rememberPreference(EnableKugouKey, true)
    val (enableSimpMusic, onSimpMusic) = rememberPreference(EnableSimpMusicKey, true)

    // ── Provider model lists ─────────────────────────────────────────────────
    val premiumProviders = remember(
        enablePaxMusixmatch, enablePaxSpotify, enablePaxApple, enableBLMusixmatch
    ) {
        listOf(
            LyricsProviderUiModel(
                id = "paxsenix_musixmatch",
                displayName = "Paxsenix : Musixmatch",
                category = "PREMIUM_WORD_SYNC",
                capabilities = listOf("WORD_SYNC", "PREMIUM"),
                reliability = "98%",
                averageLatency = "420ms",
                supportsWordSync = true,
                sourceType = "Musixmatch",
                enabled = enablePaxMusixmatch,
                onToggle = onPaxMusixmatch,
            ),
            LyricsProviderUiModel(
                id = "paxsenix_spotify",
                displayName = "Paxsenix : Spotify",
                category = "PREMIUM_WORD_SYNC",
                capabilities = listOf("WORD_SYNC", "TTML"),
                reliability = "96%",
                averageLatency = "380ms",
                supportsWordSync = true,
                sourceType = "Spotify",
                enabled = enablePaxSpotify,
                onToggle = onPaxSpotify,
            ),
            LyricsProviderUiModel(
                id = "paxsenix_apple",
                displayName = "Paxsenix : Apple Music",
                category = "PREMIUM_LINE_SYNC",
                capabilities = listOf("LINE_SYNC", "APPLE"),
                reliability = "94%",
                averageLatency = "510ms",
                supportsWordSync = false,
                sourceType = "Apple Music",
                enabled = enablePaxApple,
                onToggle = onPaxApple,
            ),
            LyricsProviderUiModel(
                id = "betterlyrics_musixmatch",
                displayName = "BetterLyrics : Musixmatch",
                category = "PREMIUM_WORD_SYNC",
                capabilities = listOf("WORD_SYNC", "ENHANCED"),
                reliability = "95%",
                averageLatency = "460ms",
                supportsWordSync = true,
                sourceType = "Musixmatch",
                enabled = enableBLMusixmatch,
                onToggle = onBLMusixmatch,
            ),
        )
    }

    val standardProviders = remember(
        enableBetterLyrics, enableLrclib, enableKugou, enableSimpMusic
    ) {
        listOf(
            LyricsProviderUiModel(
                id = "betterlyrics",
                displayName = "BetterLyrics",
                category = "PREMIUM_WORD_SYNC",
                capabilities = listOf("WORD_SYNC"),
                reliability = "92%",
                averageLatency = "600ms",
                supportsWordSync = true,
                sourceType = "BetterLyrics",
                enabled = enableBetterLyrics,
                onToggle = onBetterLyrics,
            ),
            LyricsProviderUiModel(
                id = "lrclib",
                displayName = "LRCLib",
                category = "STANDARD_SYNC",
                capabilities = listOf("COMMUNITY", "LRC"),
                reliability = "89%",
                averageLatency = "350ms",
                supportsWordSync = false,
                sourceType = "LRCLib",
                enabled = enableLrclib,
                onToggle = onLrclib,
            ),
            LyricsProviderUiModel(
                id = "kugou",
                displayName = "KuGou",
                category = "STANDARD_SYNC",
                capabilities = listOf("CHINESE", "SYNC"),
                reliability = "91%",
                averageLatency = "480ms",
                supportsWordSync = false,
                sourceType = "KuGou",
                enabled = enableKugou,
                onToggle = onKugou,
            ),
            LyricsProviderUiModel(
                id = "simpmusic",
                displayName = "SimpMusic",
                category = "FALLBACK",
                capabilities = listOf("FALLBACK"),
                reliability = "85%",
                averageLatency = "700ms",
                supportsWordSync = false,
                sourceType = "SimpMusic",
                enabled = enableSimpMusic,
                onToggle = onSimpMusic,
            ),
        )
    }

    val preferredProvider = remember(premiumProviders, standardProviders) {
        (premiumProviders + standardProviders).firstOrNull { it.enabled }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Lyrics Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Provider Control",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Preferred provider card ───────────────────────────────────────
            PreferredProviderCard(provider = preferredProvider)

            Spacer(Modifier.height(20.dp))

            // ── Premium Sync section ──────────────────────────────────────────
            ProviderSection(
                title = "Premium Sync",
                subtitle = "Word-level & TTML providers",
                providers = premiumProviders,
            )

            Spacer(Modifier.height(16.dp))

            // ── Standard Providers section ────────────────────────────────────
            ProviderSection(
                title = "Standard Providers",
                subtitle = "Community & regional sources",
                providers = standardProviders,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── PreferredProviderCard ────────────────────────────────────────────────────

@Composable
fun PreferredProviderCard(provider: LyricsProviderUiModel?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Preferred Provider",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.height(10.dp))

            if (provider != null) {
                Text(
                    provider.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    provider.capabilities.forEach { badge ->
                        ProviderCapabilityBadge(label = badge)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Category: ${provider.category.replace('_', ' ')}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "All providers disabled",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── ProviderSection ─────────────────────────────────────────────────────────

@Composable
fun ProviderSection(
    title: String,
    subtitle: String,
    providers: List<LyricsProviderUiModel>,
) {
    Column {
        // Section header
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.75f),
            )
        }

        // Card wrapping all rows
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            Column {
                providers.forEachIndexed { index, provider ->
                    ProviderCard(provider = provider)
                    if (index < providers.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                        )
                    }
                }
            }
        }
    }
}

// ─── ProviderCard ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderCard(provider: LyricsProviderUiModel) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
    ) {
        // ── Main row ──────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (provider.enabled)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .alpha(if (provider.enabled) 1f else 0.4f),
                    tint = if (provider.enabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(14.dp))

            // Name + badges
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.alpha(if (provider.enabled) 1f else 0.5f),
                )
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    provider.capabilities.forEach { badge ->
                        ProviderCapabilityBadge(
                            label = badge,
                            dimmed = !provider.enabled,
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // Toggle
            Switch(
                checked = provider.enabled,
                onCheckedChange = provider.onToggle,
                thumbContent = {
                    Icon(
                        painter = painterResource(
                            if (provider.enabled) R.drawable.check else R.drawable.close
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            )
        }

        // ── Expanded details ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ProviderExpandedDetails(provider = provider)
        }
    }
}

// ─── ProviderCapabilityBadge ─────────────────────────────────────────────────

@Composable
fun ProviderCapabilityBadge(
    label: String,
    dimmed: Boolean = false,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (dimmed) 0.05f else 0.08f
                )
            )
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                alpha = if (dimmed) 0.4f else 0.7f
            ),
            letterSpacing = 0.4.sp,
        )
    }
}

// ─── ProviderExpandedDetails ─────────────────────────────────────────────────

@Composable
fun ProviderExpandedDetails(provider: LyricsProviderUiModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProviderDetailRow(
            label = "Sync",
            value = if (provider.supportsWordSync) "WORD_SYNC" else "LINE_SYNC",
        )
        ProviderDetailRow(label = "Reliability", value = provider.reliability)
        ProviderDetailRow(label = "Source", value = provider.sourceType)
        ProviderDetailRow(label = "Average Resolve", value = provider.averageLatency)
        ProviderDetailRow(
            label = "Cache",
            value = "Compatible",
        )
    }
}

@Composable
private fun ProviderDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
