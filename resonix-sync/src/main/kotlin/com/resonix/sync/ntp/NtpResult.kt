package com.resonix.sync.ntp

/**
 * Result of a completed NTP probe cycle.
 *
 * @property offsetMs Clock offset in milliseconds. Add this to local time to get estimated server time.
 *                    Positive = local clock is behind server. Negative = local clock is ahead.
 * @property rttMs    Minimum round-trip time observed across all pure probe pairs in milliseconds.
 *                    Lower values indicate less queuing jitter and more reliable offset estimates.
 * @property confidence Quality score in [0.0, 1.0]. Computed as pure_probe_count / total_probe_count.
 *                       Values above 0.5 are generally reliable; below 0.2 suggest heavy network jitter.
 */
data class NtpResult(
    val offsetMs: Long,
    val rttMs: Long,
    val confidence: Float,
)
