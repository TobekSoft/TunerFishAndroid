package com.tobeksoft.tunerfish.core

/**
 * When a play made offline counts, since Jellyfin can't judge it: the track is longer than half a
 * minute and was heard to half-way, or for four minutes (the long-standing scrobbling rule). Online,
 * the server applies its own rule to the stop report.
 */
object PlayRule {
    const val SHORTEST_MS = 30_000L
    const val ENOUGH_MS = 240_000L

    fun counts(positionMs: Long, durationMs: Long?): Boolean {
        if (durationMs == null || durationMs <= SHORTEST_MS) return false
        return positionMs >= durationMs / 2 || positionMs >= ENOUGH_MS
    }
}
