package com.tobeksoft.tunerfish.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneTest {
    private val rate = 44_100

    @Test
    fun levels_areWholeDbWithinTwelve_andLabelledWithASign() {
        assertEquals(12, Tone.clamp(20))
        assertEquals(-12, Tone.clamp(-13))
        assertEquals("+3 dB", Tone.label(3))
        assertEquals("−4 dB", Tone.label(-4))
        assertEquals("0 dB", Tone.label(0))
    }

    @Test
    fun bassShelf_liftsTheLowsOnly_halfWayAtItsFrequency() {
        val f = Shelf.low(rate, Tone.BASS_HZ, 6)
        assertEquals(6.0, f.gainDbAt(20.0, rate), 0.5)
        assertEquals(3.0, f.gainDbAt(Tone.BASS_HZ, rate), 0.3)
        assertEquals(0.0, f.gainDbAt(5_000.0, rate), 0.2)
    }

    @Test
    fun trebleShelf_cutsTheHighsOnly() {
        val f = Shelf.high(rate, Tone.TREBLE_HZ, -6)
        assertEquals(-6.0, f.gainDbAt(20_000.0, rate), 0.7)
        assertEquals(-3.0, f.gainDbAt(Tone.TREBLE_HZ, rate), 0.3)
        assertEquals(0.0, f.gainDbAt(200.0, rate), 0.2)
    }

    @Test
    fun zeroDb_isFlat_andLeavesSamplesAlone() {
        assertEquals(0.0, Shelf.low(rate, Tone.BASS_HZ, 0).gainDbAt(50.0, rate), 1e-9)
        val filter = ToneFilter(rate, 2)
        val samples = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f)
        filter.process(samples, samples.size)
        assertEquals(listOf(0.1f, -0.2f, 0.3f, -0.4f), samples.toList())
    }

    @Test
    fun aLowTone_comesOutLouder_withTheBassUp() {
        val filter = ToneFilter(rate, 1).apply { set(bassDb = 6, trebleDb = 0) }
        val out = sine(40.0, 0.25f, rate) // a second's worth, so the filter settles
        filter.process(out, out.size)
        val gainDb = 20 * log10(peak(out, from = out.size / 2) / 0.25)
        assertEquals(6.0, gainDb, 0.6)
    }

    @Test
    fun channels_areFilteredSeparately() {
        // Left: a low tone. Right: silence. Boosting the bass mustn't leak the left into the right.
        val left = sine(40.0, 0.25f, rate)
        val stereo = FloatArray(left.size * 2) { if (it % 2 == 0) left[it / 2] else 0f }
        ToneFilter(rate, 2).apply { set(6, 6) }.process(stereo, stereo.size)
        assertTrue((1 until stereo.size step 2).all { stereo[it] == 0f })
    }

    @Test
    fun boostsNeverPastFullScale() {
        val loud = sine(40.0, 0.95f, rate)
        ToneFilter(rate, 1).apply { set(12, 12) }.process(loud, loud.size)
        assertTrue(loud.all { abs(it) <= 1f })
    }

    private fun sine(hz: Double, amplitude: Float, rate: Int) =
        FloatArray(rate) { (amplitude * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun peak(samples: FloatArray, from: Int) = (from until samples.size).maxOf { abs(samples[it].toDouble()) }
}
