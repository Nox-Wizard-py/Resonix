package com.noxwizard.resonix.paxsenix.utils

private val titleCleanupPatterns = listOf(
    Regex("""\\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
    Regex("""\\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
    Regex("""\\s*【.*?】"""),
    Regex("""\\s*\|.*$"""),
    Regex("""\\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
    Regex("""\\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
    Regex("""\\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
    Regex("""\\s*feat\..*$""", RegexOption.IGNORE_CASE),
    Regex("""\\s*ft\..*$""", RegexOption.IGNORE_CASE),
    Regex("""\\s*\([^)]*\d{4}[^)]*\)""", RegexOption.IGNORE_CASE),
)

private val artistSeparators = listOf(
    " & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with "
)

private val MULTI_SPACE = Regex("""\s{2,}""")

object TrackNormalizer {

    fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.replace(MULTI_SPACE, " ").trim()
    }

    fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    /** Lowercase normalized title for scoring/matching. */
    fun normalizeTitle(title: String): String =
        cleanTitle(title).lowercase()

    /** Lowercase normalized primary artist for scoring/matching. */
    fun normalizeArtist(artist: String): String =
        cleanArtist(artist).lowercase()

    fun normalizeAlbum(album: String?): String? =
        album?.trim()?.lowercase()

    /** Generic collapse — used for cache keys etc. */
    fun normalize(text: String): String =
        text.replace(MULTI_SPACE, " ").trim().lowercase()
}
