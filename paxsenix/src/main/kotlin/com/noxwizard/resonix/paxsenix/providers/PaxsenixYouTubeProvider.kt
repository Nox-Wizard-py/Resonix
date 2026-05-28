package com.noxwizard.resonix.paxsenix.providers

import com.noxwizard.resonix.paxsenix.api.PaxsenixLyrics
import com.noxwizard.resonix.paxsenix.models.LyricsDocument
import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.models.LyricsProviderCategory
import com.noxwizard.resonix.paxsenix.models.LyricsTrack
import com.noxwizard.resonix.paxsenix.models.SyncType
import com.noxwizard.resonix.paxsenix.utils.LyricsPayloadSanitizer
import com.noxwizard.resonix.paxsenix.utils.MatchScorer
import com.noxwizard.resonix.paxsenix.utils.TrackNormalizer
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

class PaxsenixYouTubeProvider(
    private val confidenceThreshold: Float = 0.5f,
) : LyricsProvider {

    override val name: String = "YouTube (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.FALLBACK

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        val cleanTitle = TrackNormalizer.cleanTitle(track.title)
        val cleanArtist = TrackNormalizer.cleanArtist(track.artist)

        val raw = PaxsenixLyrics.getYouTubeLyrics(
            cleanTitle, cleanArtist, track.durationSec
        ).getOrNull() ?: return@runCatching null

        if (raw.isBlank()) return@runCatching null
        val sanitized = LyricsPayloadSanitizer.sanitize(raw)

        val lines = mutableListOf<LyricsLine>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(sanitized)))
            val textNodes = doc.getElementsByTagName("text")

            for (i in 0 until textNodes.length) {
                val node = textNodes.item(i)
                val startSec = node.attributes.getNamedItem("start")?.nodeValue?.toFloatOrNull() ?: 0f
                val text = node.textContent ?: ""

                if (text.isNotBlank()) {
                    lines.add(
                        LyricsLine(
                            text = text.replace("\n", " ").trim(),
                            startMs = (startSec * 1000).toLong(),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            return@runCatching null
        }

        if (lines.isEmpty()) return@runCatching null

        val confidence = MatchScorer.score(track, track.title, track.artist, candidateDurationSec = track.durationSec)

        if (confidence < confidenceThreshold) return@runCatching null

        LyricsDocument(
            rawText = sanitized,
            lines = lines,
            providerName = name,
            providerCategory = category,
            syncType = SyncType.LINE_SYNCED,
        )
    }.getOrNull()
}
