package com.tobeksoft.tunerfish.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bass and treble, like the two knobs on an amplifier: a low shelf and a high shelf, each set in
 * whole dB from -[MAX_DB] to +[MAX_DB], 0 = flat. Done in the app's own player, not Android's
 * equaliser, so it sounds the same on every phone.
 */
object Tone {
    const val MAX_DB = 12
    /** Where each shelf is half-way to its full boost or cut. */
    const val BASS_HZ = 100.0
    const val TREBLE_HZ = 10_000.0

    fun clamp(db: Int) = db.coerceIn(-MAX_DB, MAX_DB)

    fun label(db: Int) = when {
        db > 0 -> "+$db dB"
        db < 0 -> "−${-db} dB"
        else -> "0 dB"
    }
}

/** One shelving filter (RBJ Audio EQ Cookbook, shelf slope 1): its coefficients, normalised by a0. */
class Shelf private constructor(
    internal val b0: Double, internal val b1: Double, internal val b2: Double,
    internal val a1: Double, internal val a2: Double,
) {
    /** The filter's gain at [hz], in dB. */
    fun gainDbAt(hz: Double, sampleRate: Int): Double {
        val w = 2 * PI * hz / sampleRate
        // H(e^jw) = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
        val num = hypot(b0 + b1 * cos(w) + b2 * cos(2 * w), -(b1 * sin(w) + b2 * sin(2 * w)))
        val den = hypot(1 + a1 * cos(w) + a2 * cos(2 * w), -(a1 * sin(w) + a2 * sin(2 * w)))
        return 20 * log10(num / den)
    }

    companion object {
        fun low(sampleRate: Int, hz: Double, db: Int) = make(sampleRate, hz, db, high = false)
        fun high(sampleRate: Int, hz: Double, db: Int) = make(sampleRate, hz, db, high = true)

        private fun make(sampleRate: Int, hz: Double, db: Int, high: Boolean): Shelf {
            val a = 10.0.pow(db / 40.0)
            val w0 = 2 * PI * hz.coerceAtMost(sampleRate * 0.45) / sampleRate // stay below Nyquist
            val c = cos(w0)
            val alpha2 = 2 * sqrt(a) * sin(w0) / 2 * sqrt(2.0) // 2·√A·α, with slope 1
            val s = if (high) -1 else 1 // the high shelf is the low one with the cosine terms' signs flipped
            val a0 = (a + 1) + s * (a - 1) * c + alpha2
            return Shelf(
                b0 = a * ((a + 1) - s * (a - 1) * c + alpha2) / a0,
                b1 = s * 2 * a * ((a - 1) - s * (a + 1) * c) / a0,
                b2 = a * ((a + 1) - s * (a - 1) * c - alpha2) / a0,
                a1 = -s * 2 * ((a - 1) + s * (a + 1) * c) / a0,
                a2 = ((a + 1) + s * (a - 1) * c - alpha2) / a0,
            )
        }
    }
}

/**
 * Bass and treble applied to interleaved float samples (-1..1), one filter state per channel. The
 * levels can change mid-stream with [set]: the state is kept, so there's no click.
 */
class ToneFilter(private val sampleRate: Int, private val channels: Int) {
    private var bass = Shelf.low(sampleRate, Tone.BASS_HZ, 0)
    private var treble = Shelf.high(sampleRate, Tone.TREBLE_HZ, 0)
    // Transposed direct form II: two state values per filter per channel.
    private val bassState = DoubleArray(channels * 2)
    private val trebleState = DoubleArray(channels * 2)

    var isFlat = true; private set

    fun set(bassDb: Int, trebleDb: Int) {
        bass = Shelf.low(sampleRate, Tone.BASS_HZ, Tone.clamp(bassDb))
        treble = Shelf.high(sampleRate, Tone.TREBLE_HZ, Tone.clamp(trebleDb))
        isFlat = bassDb == 0 && trebleDb == 0
    }

    /** Filters the first [count] samples in place. Boosts can push past full scale, so the result is clipped to -1..1. */
    fun process(samples: FloatArray, count: Int) {
        if (isFlat) return
        for (i in 0 until count) {
            val ch = i % channels
            val y = run(treble, trebleState, ch, run(bass, bassState, ch, samples[i].toDouble()))
            samples[i] = y.coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private fun run(f: Shelf, state: DoubleArray, ch: Int, x: Double): Double {
        val z = ch * 2
        val y = f.b0 * x + state[z]
        state[z] = f.b1 * x - f.a1 * y + state[z + 1]
        state[z + 1] = f.b2 * x - f.a2 * y
        return y
    }
}
