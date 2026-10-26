package com.tobeksoft.tunerfish.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.tobeksoft.tunerfish.core.ToneFilter
import java.nio.ByteBuffer

/** The bass and treble the player uses now. Settings writes them; the audio thread reads them. */
object ToneLevels {
    @Volatile var bass = 0
    @Volatile var treble = 0
}

/**
 * Applies [ToneLevels] to the decoded audio, in ExoPlayer's audio sink (see PlaybackService). Works
 * on 16-bit and float PCM, what the decoders give; anything else passes by untouched.
 */
@OptIn(UnstableApi::class)
class ToneProcessor : BaseAudioProcessor() {
    private var filter: ToneFilter? = null
    private var bass = 0
    private var treble = 0
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        filter = null // a seek or a new track: start from silence, not the old track's tail
    }

    override fun onReset() {
        filter = null
        scratch = FloatArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val format = inputAudioFormat
        val f = filter ?: ToneFilter(format.sampleRate, format.channelCount).also {
            filter = it
            it.set(bass, treble)
        }
        val b = ToneLevels.bass
        val t = ToneLevels.treble
        if (b != bass || t != treble) {
            bass = b
            treble = t
            f.set(b, t)
        }

        val out = replaceOutputBuffer(bytes)
        if (f.isFlat) {
            out.put(inputBuffer)
        } else if (format.encoding == C.ENCODING_PCM_FLOAT) {
            val n = bytes / 4
            val samples = scratchOf(n)
            for (i in 0 until n) samples[i] = inputBuffer.getFloat()
            f.process(samples, n)
            for (i in 0 until n) out.putFloat(samples[i])
        } else {
            val n = bytes / 2
            val samples = scratchOf(n)
            for (i in 0 until n) samples[i] = inputBuffer.getShort() / 32768f
            f.process(samples, n)
            for (i in 0 until n) out.putShort((samples[i] * 32767f).toInt().toShort())
        }
        out.flip()
    }

    private fun scratchOf(n: Int): FloatArray {
        if (scratch.size < n) scratch = FloatArray(n)
        return scratch
    }
}
