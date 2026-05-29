package com.noxwizard.resonix.lyrics

/**
 * Registry of all active lyrics providers in priority order.
 *
 * Priority (highest first):
 *   1. BetterLyrics : Musixmatch  — word-synced, premium metadata
 *   2. Paxsenix : Musixmatch      — word-synced, broad coverage
 *   3. BetterLyrics : Spotify     — word-synced karaoke
 *   4. Paxsenix : Spotify         — word-synced fallback
 *   5. Paxsenix : Apple Music     — TTML word-sync
 *   6. BetterLyrics TTML          — generic TTML word-sync
 *   7. LRCLib                     — line-synced community
 *   8. KuGou                      — line-synced community
 *   9. SimpMusic                  — line-synced fallback
 *
 * Legacy providers (LyricsPlus, YouTubeSubtitle, YouTube Music) are removed.
 */
object LyricsProviderRegistry {
    private val providerMap = mapOf(
        "BetterLyrics" to BetterLyricsProvider,
        "LrcLib" to LrcLibLyricsProvider,
        "Kugou" to KuGouLyricsProvider,
        "SimpMusic" to SimpMusicLyricsProvider,
    )

    val providerNames = providerMap.keys.toList()

    fun getProviderByName(name: String): LyricsProvider? = providerMap[name]

    fun getProviderName(provider: LyricsProvider): String? =
        providerMap.entries.find { it.value == provider }?.key

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
}
