package com.noxwizard.resonix.lyrics

/**
 * Registry of all active lyrics providers in priority order.
 *
 * Two-tier registry:
 *
 * **App-layer providers** (resolved by UnifiedLyricsEngine directly):
 *   BetterLyrics, LRCLib, KuGou, SimpMusic
 *
 * **Paxsenix engine providers** (resolved by PaxsenixLyricsEngine):
 *   MusixMatch (Paxsenix), Spotify (Paxsenix), BetterLyrics (Paxsenix),
 *   Apple Music (Paxsenix), NetEase (Paxsenix), KuGou (Paxsenix), YouTube (Paxsenix)
 */
object LyricsProviderRegistry {
    // ── App-layer providers (have their own isEnabled(Context) gate) ──────────
    private val appProviderMap = mapOf(
        "BetterLyrics" to BetterLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "Kugou" to KuGouLyricsProvider,
        "SimpMusic" to SimpMusicLyricsProvider,
    )

    // ── Paxsenix engine provider canonical names (default priority order) ─────
    val paxsenixProviderNames = listOf(
        "MusixMatch",
        "Spotify",
        "BetterLyrics",
        "PaxsenixAppleMusic",
        "PaxsenixNetEase",
        "PaxsenixKuGou",
        "PaxsenixYouTube",
    )

    // ── App-layer names (used by priority drag screen) ────────────────────────
    val providerNames = appProviderMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = appProviderMap[name]

    fun getProviderName(provider: LyricsProvider): String? =
        appProviderMap.entries.find { it.value == provider }?.key

    fun deserializeProviderOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return getDefaultProviderOrder()
        return orderString.split(",").map { it.trim() }.filter { it in providerNames }
    }

    fun serializeProviderOrder(providers: List<String>): String =
        providers.filter { it in providerNames }.joinToString(",")

    fun getDefaultProviderOrder(): List<String> = listOf(
        "BetterLyrics",
        "LrcLib",
        "Kugou",
        "SimpMusic",
    )

    fun getOrderedProviders(orderString: String): List<LyricsProvider> =
        deserializeProviderOrder(orderString).mapNotNull { getProviderByName(it) }

    // ── Paxsenix order helpers ────────────────────────────────────────────────

    fun deserializePaxsenixOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return paxsenixProviderNames
        return orderString.split(",").map { it.trim() }.filter { it in paxsenixProviderNames }
    }

    fun serializePaxsenixOrder(providers: List<String>): String =
        providers.filter { it in paxsenixProviderNames }.joinToString(",")
}
