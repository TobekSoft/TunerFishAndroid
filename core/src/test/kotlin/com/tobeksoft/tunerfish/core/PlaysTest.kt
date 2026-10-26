package com.tobeksoft.tunerfish.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaysTest {
    private val minute = 60_000L

    @Test
    fun anOfflinePlay_countsAtHalfWay() {
        assertFalse(PlayRule.counts(positionMs = 89_000, durationMs = 3 * minute))
        assertTrue(PlayRule.counts(positionMs = 90_000, durationMs = 3 * minute))
    }

    @Test
    fun aLongTrack_countsAfterFourMinutes() {
        assertTrue(PlayRule.counts(positionMs = 4 * minute, durationMs = 20 * minute))
        assertFalse(PlayRule.counts(positionMs = 4 * minute - 1, durationMs = 20 * minute))
    }

    @Test
    fun veryShortTracks_andUnknownLengths_neverCount() {
        assertFalse(PlayRule.counts(positionMs = 30_000, durationMs = 30_000))
        assertFalse(PlayRule.counts(positionMs = 10 * minute, durationMs = null))
    }
}
