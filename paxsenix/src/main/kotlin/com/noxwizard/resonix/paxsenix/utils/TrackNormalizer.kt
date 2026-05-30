package com.noxwizard.resonix.paxsenix.utils

private val titleCleanupPatterns = listOf(
    // Brackets with noise keywords (official, video, lyrics, remaster, remix, etc.)
    Regex("""[\(\[【]\s*(?:official|video|audio|lyrics?|lyric|visualizer|hd|hq|4k|remaster(?:ed)?|remix|live|acoustic|version|edit|extended|radio|clean|explicit|mv|music\s*video)[^\)\]】]*[\)\]】]""", RegexOption.IGNORE_CASE),
    // OST / soundtrack labels in brackets
    Regex("""[\(\[【]\s*(?:ost|original\s+(?:motion\s+picture\s+)?soundtrack|theme\s+song)[^\)\]】]*[\)\]】]""", RegexOption.IGNORE_CASE),
    // "from <Movie>" in brackets
    Regex("""[\(\[【]\s*from\s+[^\)\]】]{1,60}[\)\]】]""", RegexOption.IGNORE_CASE),
    // "from <Movie>" at end of title (outside brackets)
    Regex("""\s+from\s+.{1,60}$""", RegexOption.IGNORE_CASE),
    // CJK full-width brackets
    Regex("""\s*【[^】]*】"""),
    // Pipe suffix
    Regex("""\s*\|.*$"""),
    // Trailing " - official/video/audio/lyrics/visualizer"
    Regex("""\s*-\s*(?:official|video|audio|lyrics?|lyric|visualizer|remaster(?:ed)?|remix|live|acoustic|version|edit)\s*$""", RegexOption.IGNORE_CASE),
    // feat. / ft. / featuring (parenthesized)
    Regex("""\s*[\(\[]?(?:feat\.?|ft\.?|featuring)\s+[^\)\]]*[\)\]]?""", RegexOption.IGNORE_CASE),
    // feat. / ft. standalone at end of title
    Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    // Year in brackets e.g. (2013) or (Remaster 2013)
    Regex("""\s*[\(\[]\s*(?:remaster(?:ed)?\s*)?\d{4}\s*[\)\]]""", RegexOption.IGNORE_CASE),
    // Slowed / reverb / sped-up suffixes (outside brackets)
    Regex("""\s*-\s*(?:slowed|reverb|sped[\s\-]up|nightcore|bass\s*boost(?:ed)?)\s*$""", RegexOption.IGNORE_CASE),
)

private val artistSeparators = listOf(
    " & ", " and ", ", ", " x ", " X ", " × ",
    " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ",
    " vs. ", " vs ",
)

private val MULTI_SPACE = Regex("""\s{2,}""")
private val EMOJI_PATTERN = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2B50\u2B55\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE\u2702\u2705\u2708-\u270D\u270F\u2712\u2714\u2716\u271D\u2721\u2728\u2733\u2734\u2744\u2747\u274C\u274E\u2753-\u2755\u2757\u2763\u2764\u2795-\u2797\u27A1\u27B0\u27BF\u2934\u2935\u2B05-\u2B07\u2B1B\u2B1C\u3030\u303D\u3297\u3299]+""")

private val SMART_PUNCTUATION = mapOf(
    '\u2018' to '\'', '\u2019' to '\'',
    '\u201C' to '"', '\u201D' to '"',
    '\u2014' to '-', '\u2013' to '-',
    '\u00B7' to '-', '\u2022' to '-',
)

private fun normalizeUnicode(text: String): String {
    val sb = StringBuilder(text.length)
    for (ch in text) sb.append(SMART_PUNCTUATION[ch] ?: ch)
    return EMOJI_PATTERN.replace(sb.toString(), "")
}

object TrackNormalizer {

    fun cleanTitle(title: String): String {
        var cleaned = normalizeUnicode(title.trim())
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.replace(MULTI_SPACE, " ").trim()
    }

    fun cleanArtist(artist: String): String {
        var cleaned = normalizeUnicode(artist.trim())
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
        normalizeUnicode(text).replace(MULTI_SPACE, " ").trim().lowercase()
}

