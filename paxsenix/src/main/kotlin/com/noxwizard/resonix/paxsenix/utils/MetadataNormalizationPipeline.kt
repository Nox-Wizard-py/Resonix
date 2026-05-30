package com.noxwizard.resonix.paxsenix.utils

/**
 * Result of normalizing a track's metadata.
 *
 * Stores three levels of normalization for progressive query fallback:
 * - [originalTitle] / [originalArtist] — raw, unchanged
 * - [normalizedTitle] / [normalizedArtist] — feat/ft/remix/OST removed, unicode cleaned
 * - [aggressiveTitle] / [primaryArtist] — maximum stripping, primary artist only
 */
data class NormalizedTrack(
    val originalTitle: String,
    val normalizedTitle: String,
    val aggressiveTitle: String,
    val originalArtist: String,
    val normalizedArtist: String,
    val primaryArtist: String,
)

/**
 * Mandatory pre-resolution metadata normalization pipeline.
 *
 * Handles:
 * - feat. / ft. / featuring / with
 * - remix / remaster / live / acoustic / slowed / reverb
 * - OST labels / "from movie" / "from series"
 * - Bracketed suffixes: (Official Video), [Lyrics], 【MV】
 * - Unicode punctuation → ASCII equivalents
 * - Emoji stripping
 * - Smart quotes → plain quotes
 * - Duplicate spaces
 */
object MetadataNormalizationPipeline {

    // ── Title decorators ───────────────────────────────────────────────────────

    // Parenthesized / bracketed suffixes with known noise keywords
    private val BRACKET_NOISE_PATTERN = Regex(
        """[\(\[【]\s*(?:official|video|audio|lyrics?|lyric|visualizer|hd|hq|4k|mv|music\s*video|"""
                + """clip|teaser|trailer|cover|tribute|edit|extended|radio|clean|explicit|"""
                + """remaster(?:ed)?|remix|live|acoustic|version|slowed|reverb|"""
                + """ost|original\s+(?:motion\s+picture\s+)?soundtrack|"""
                + """from\s+.+?|feat\.?\s+.+?|ft\.?\s+.+?|featuring\s+.+?)[^\)\]】]*[\)\]】]""",
        RegexOption.IGNORE_CASE
    )

    // "from <Movie/Series>" at end of title (not in brackets)
    private val FROM_MOVIE_PATTERN = Regex(
        """\s+from\s+.{1,60}$""",
        RegexOption.IGNORE_CASE
    )

    // feat / ft / featuring in title (outside brackets)
    private val FEAT_PATTERN = Regex(
        """\s*[\(\[]?(?:feat\.?|ft\.?|featuring|with)\s+[^\)\]]*[\)\]]?""",
        RegexOption.IGNORE_CASE
    )

    // Version / remaster / remix / live / acoustic / slowed / reverb (outside brackets)
    private val VERSION_DECORATORS = Regex(
        """\s*-\s*(?:official|audio|video|lyrics?|remaster(?:ed)?|remix|live|acoustic|slowed|reverb|version|edit)\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Year in parentheses e.g. (2013) or (Remaster 2015)
    private val YEAR_BRACKET_PATTERN = Regex(
        """\s*[\(\[]\s*(?:remaster(?:ed)?\s*)?\d{4}\s*[\)\]]""",
        RegexOption.IGNORE_CASE
    )

    // Pipe separator and everything after: "Song Title | Artist Name"
    private val PIPE_SUFFIX_PATTERN = Regex("""\s*\|.*$""")

    // Emoji: Unicode ranges for emoji (broad coverage)
    private val EMOJI_PATTERN = Regex(
        """[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\u2B50\u2B55\u231A\u231B\u23E9-\u23F3\u23F8-\u23FA\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE\u2614\u2615\u2648-\u2653\u267F\u2693\u26A1\u26AA\u26AB\u26BD\u26BE\u26C4\u26C5\u26CE\u26D4\u26EA\u26F2\u26F3\u26F5\u26FA\u26FD\u2702\u2705\u2708-\u270D\u270F\u2712\u2714\u2716\u271D\u2721\u2728\u2733\u2734\u2744\u2747\u274C\u274E\u2753-\u2755\u2757\u2763\u2764\u2795-\u2797\u27A1\u27B0\u27BF\u2934\u2935\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55\u3030\u303D\u3297\u3299]+"""
    )

    // Smart quotes → plain
    private val SMART_QUOTES = mapOf(
        '\u2018' to '\'', '\u2019' to '\'',
        '\u201C' to '"', '\u201D' to '"',
        '\u2014' to '-', '\u2013' to '-',
        '\u00B7' to '-',
    )

    // Multi-space
    private val MULTI_SPACE = Regex("""\s{2,}""")

    // ── Artist separators ──────────────────────────────────────────────────────

    private val ARTIST_SEPARATORS = listOf(
        " & ", " and ", ", ", " x ", " X ", " × ",
        " feat. ", " feat ", " ft. ", " ft ",
        " featuring ", " with ", " vs. ", " vs ",
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Normalizes [title] and [artist] into a [NormalizedTrack] with three levels of cleaning.
     */
    fun normalize(title: String, artist: String): NormalizedTrack {
        val normalizedTitle = normalizeTitle(title)
        val aggressiveTitle = aggressiveNormalizeTitle(normalizedTitle)
        val normalizedArtist = normalizeArtist(artist)
        val primaryArtist = extractPrimaryArtist(artist)

        return NormalizedTrack(
            originalTitle = title.trim(),
            normalizedTitle = normalizedTitle,
            aggressiveTitle = aggressiveTitle,
            originalArtist = artist.trim(),
            normalizedArtist = normalizedArtist,
            primaryArtist = primaryArtist,
        )
    }

    /**
     * Standard normalization: removes brackets with noise keywords, feat, OST labels,
     * unicode punctuation, emoji. Keeps the core title.
     */
    fun normalizeTitle(title: String): String {
        var t = replaceSmartPunctuation(title)
        t = EMOJI_PATTERN.replace(t, "")
        t = BRACKET_NOISE_PATTERN.replace(t, "")
        t = YEAR_BRACKET_PATTERN.replace(t, "")
        t = FEAT_PATTERN.replace(t, "")
        t = FROM_MOVIE_PATTERN.replace(t, "")
        t = VERSION_DECORATORS.replace(t, "")
        t = PIPE_SUFFIX_PATTERN.replace(t, "")
        return t.replace(MULTI_SPACE, " ").trim()
    }

    /**
     * Aggressive normalization: applies standard normalization, then strips
     * any remaining parenthesized / bracketed content entirely.
     */
    fun aggressiveNormalizeTitle(title: String): String {
        var t = normalizeTitle(title)
        // Strip any remaining () [] 【】 content entirely
        t = Regex("""[\(\[【][^\)\]】]*[\)\]】]""").replace(t, "")
        // Strip trailing dash sections
        t = Regex("""\s*-\s*\S.*$""").replace(t, "")
        return t.replace(MULTI_SPACE, " ").trim()
    }

    /**
     * Normalizes artist: replaces smart punctuation, strips emoji.
     */
    fun normalizeArtist(artist: String): String {
        var a = replaceSmartPunctuation(artist)
        a = EMOJI_PATTERN.replace(a, "")
        return a.replace(MULTI_SPACE, " ").trim()
    }

    /**
     * Extracts the primary (first listed) artist from a compound artist string.
     */
    fun extractPrimaryArtist(artist: String): String {
        var a = normalizeArtist(artist)
        for (sep in ARTIST_SEPARATORS) {
            val idx = a.indexOf(sep, ignoreCase = true)
            if (idx > 0) {
                a = a.substring(0, idx)
                break
            }
        }
        return a.trim()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun replaceSmartPunctuation(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(SMART_QUOTES[ch] ?: ch)
        }
        return sb.toString()
    }
}
