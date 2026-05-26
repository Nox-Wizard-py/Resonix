package com.noxwizard.resonix.paxsenix.utils

private val FEAT_REGEX = Regex("""\s*[\(\[]?(?:feat|ft|featuring)\.?\s+[^\)\]]+[\)\]]?""", RegexOption.IGNORE_CASE)
private val PARENS_REGEX = Regex("""\s*\([^)]*(?:official|audio|video|lyric|music|remaster|remix|live|version|edit|mv|hd|4k)[^)]*\)""", RegexOption.IGNORE_CASE)
private val BRACKET_REGEX = Regex("""\s*\[[^\]]*(?:official|audio|video|lyric|music|remaster|remix|live|version|edit|mv|hd|4k)[^\]]*\]""", RegexOption.IGNORE_CASE)
private val MULTI_SPACE = Regex("""\s{2,}""")

object TrackNormalizer {

    fun normalizeTitle(title: String): String =
        title
            .replace(FEAT_REGEX, "")
            .replace(PARENS_REGEX, "")
            .replace(BRACKET_REGEX, "")
            .replace(MULTI_SPACE, " ")
            .trim()
            .lowercase()

    fun normalizeArtist(artist: String): String =
        artist
            .replace(FEAT_REGEX, "")
            .replace(Regex("""\s*[,&]\s*.+"""), "")  // keep only primary artist
            .trim()
            .lowercase()

    fun normalizeAlbum(album: String?): String? =
        album?.trim()?.lowercase()

    // Full normalization used as search key
    fun normalize(text: String): String =
        text
            .replace(MULTI_SPACE, " ")
            .trim()
            .lowercase()
}
