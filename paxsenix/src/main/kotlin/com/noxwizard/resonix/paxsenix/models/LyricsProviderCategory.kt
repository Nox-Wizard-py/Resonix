package com.noxwizard.resonix.paxsenix.models

/**
 * Defines the strict categorization and reliability weighting of lyrics providers.
 * Used by the Multi-Candidate Resolver to rank results.
 */
enum class LyricsProviderCategory(
    val reliabilityWeight: Float
) {
    /** 
     * Premium providers with verified, high-quality word-level sync.
     * Examples: Apple Music, Spotify, Musixmatch (Premium).
     */
    PREMIUM_WORD_SYNC(1.0f),
    
    /** 
     * Premium providers with verified line-level sync.
     * Examples: NetEase, QQ Music.
     */
    PREMIUM_LINE_SYNC(0.85f),
    
    /** 
     * Standard community-sourced providers. Sync quality varies.
     * Examples: LRCLib, KuGou.
     */
    STANDARD_SYNC(0.6f),
    
    /** 
     * Fallback providers offering only unsynced lyrics or very low reliability sync.
     * Examples: YouTube Subtitles.
     */
    FALLBACK(0.3f),
    
    /** 
     * Experimental or unstable providers.
     */
    EXPERIMENTAL(0.1f)
}
