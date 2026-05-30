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
    private val confidenceThreshold: Float = 0.55f,
) : LyricsProvider {

    override val name: String = "YouTube (Paxsenix)"
    override val category: LyricsProviderCategory = LyricsProviderCategory.FALLBACK

    override suspend fun search(track: LyricsTrack): LyricsDocument? = runCatching {
        var diagnosticsRejectedReason = "Unknown"
        var searchCount = 1
        var candidateTitle = track.title
        var candidateArtist = track.artist
        var durationDelta = -1L
        var fetchSuccess = false
        var parseSuccess = false
        var lineCount = 0
        var finalConfidence = 0f

        try {
            val cleanTitle = TrackNormalizer.cleanTitle(track.title)
            val cleanArtist = TrackNormalizer.cleanArtist(track.artist)

            val raw = PaxsenixLyrics.getYouTubeLyrics(
                cleanTitle, cleanArtist, track.durationSec
            ).getOrNull()

            if (raw.isNullOrBlank()) {
                diagnosticsRejectedReason = "Fetch Failed or Empty Raw"
                return@runCatching null
            }
            fetchSuccess = true

            val sanitized = LyricsPayloadSanitizer.sanitize(raw)
            val lines = mutableListOf<LyricsLine>()
            
            try {
                val factory = DocumentBuilderFactory.newInstance()
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
                factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
                factory.setExpandEntityReferences(false)
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
                diagnosticsRejectedReason = "Parse Failed: ${e.message}"
                return@runCatching null
            }

            if (lines.isEmpty() || !lines.any { it.text.isNotBlank() }) {
                diagnosticsRejectedReason = "Renderability Gate: Empty/Blank Parsed Lines"
                return@runCatching null
            }
            parseSuccess = true
            lineCount = lines.size

            finalConfidence = MatchScorer.score(track, track.title, track.artist, candidateDurationSec = track.durationSec)

            if (finalConfidence < confidenceThreshold) {
                diagnosticsRejectedReason = "Confidence Too Low (<$confidenceThreshold)"
                return@runCatching null
            }

            diagnosticsRejectedReason = "None"
            LyricsDocument(
                rawText = sanitized,
                lines = lines,
                providerName = name,
                providerCategory = category,
                syncType = SyncType.LINE_SYNCED,
            )
        } finally {
            System.err.println("PaxsenixDiagnostics:\n" + """
                |Provider: $name
                |Search Results: $searchCount
                |Selected Candidate: $candidateTitle - $candidateArtist
                |Duration Delta: ${if (durationDelta == -1L) "N/A" else "${durationDelta}ms"}
                |Lyrics Fetch Success: $fetchSuccess
                |Parse Success: $parseSuccess
                |Line Count: $lineCount
                |Final Confidence: $finalConfidence
                |Rejected Reason: $diagnosticsRejectedReason
            """.trimMargin())
        }
    }.getOrNull()
}
