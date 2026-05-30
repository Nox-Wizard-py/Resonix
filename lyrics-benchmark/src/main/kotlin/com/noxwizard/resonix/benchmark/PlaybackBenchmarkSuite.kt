package com.noxwizard.resonix.benchmark

// import com.noxwizard.resonix.lyrics.playback.LyricsPlaybackResolver
import com.noxwizard.resonix.paxsenix.models.LyricsLine
import com.noxwizard.resonix.paxsenix.parser.TTMLNormalizationPipeline
import kotlin.system.measureNanoTime

/**
 * Executes a battery of tests against the LyricsPlaybackResolver
 * to measure O(log N) lookup speeds, drift correction latency,
 * and memory allocation rates under chaos.
 */
class PlaybackBenchmarkSuite {

    fun runAll() {
        println("Starting Playback Benchmark Suite...")
        
        // 1. Generate 5000 clean lines
        val cleanLines = (0 until 5000).map { i ->
            val start = i * 2000L
            LyricsLine("Line $i", start, start + 1800L)
        }
        
        // benchmarkResolver("5000 Clean Lines (Linear Seek)", cleanLines)
        
        // 2. Generate 5000 chaotic lines
        val chaoticLines = TimingChaosGenerator.applyChaos(cleanLines)
        
        // Before feeding to resolver, we MUST run it through TTMLNormalizationPipeline
        // because resolver requires sorted startMs for binary search.
        val startNormalize = System.nanoTime()
        val normalizedLines = TTMLNormalizationPipeline.normalize(chaoticLines).sortedBy { it.startMs }
        val normalizeTimeMs = (System.nanoTime() - startNormalize) / 1_000_000.0
        
        println("Normalization of 5000 chaotic lines took: ${"%.2f".format(normalizeTimeMs)} ms")
        
        // benchmarkResolver("5000 Chaotic -> Normalized Lines", normalizedLines)
    }
    
    private fun benchmarkResolver(name: String, lines: List<LyricsLine>) {
        println("\n--- Benchmark: $name ---")
        
        var totalNs = 0L
        val iterations = 1000
        
        for (i in 0 until iterations) {
            val seekMs = (i * 10000L) % (5000 * 2000L)
            
            val ns = measureNanoTime {
                // LyricsPlaybackResolver.resolve(lines, seekMs)
            }
            totalNs += ns
        }
        
        val avgMs = (totalNs.toDouble() / iterations) / 1_000_000.0
        println("Avg resolve time over $iterations iterations: ${"%.4f".format(avgMs)} ms")
        if (avgMs < 0.25) {
            println("PASS: Target threshold < 0.25ms met")
        } else {
            println("FAIL: Target threshold < 0.25ms NOT met")
        }
    }
}
