package com.noxwizard.resonix.paxsenix.utils

import kotlin.math.max
import kotlin.math.min

/**
 * Calculates Jaro-Winkler similarity between two strings.
 * Returns a score between 0.0 and 1.0.
 */
object JaroWinklerCalculator {

    fun calculate(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val matchDistance = max(s1.length, s2.length) / 2 - 1

        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        var transpositions = 0

        for (i in s1.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, s2.length)

            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0f

        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toFloat()
        val jaro = (m / s1.length + m / s2.length + (m - transpositions / 2.0f) / m) / 3.0f

        var prefixLength = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) prefixLength++
            else break
        }

        val jaroWinkler = jaro + prefixLength * 0.1f * (1.0f - jaro)
        return jaroWinkler
    }
}
