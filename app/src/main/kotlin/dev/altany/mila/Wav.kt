package dev.altany.mila

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * EXPERIMENT — part of AudioInjectionProbe; delete with it.
 *
 * Turns a 16-bit PCM WAV file (as written by TextToSpeech.synthesizeToFile)
 * into raw 16 kHz mono 16-bit PCM, the format CarAudioRecord produces.
 */
internal object Wav {

    const val TARGET_RATE = 16000

    fun toMono16k(wav: ByteArray): ByteArray {
        require(wav.size >= 12 && ascii(wav, 0) == "RIFF" && ascii(wav, 8) == "WAVE") { "not a WAV file" }
        val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        var channels = 0
        var rate = 0
        var bits = 0
        var dataStart = -1
        var dataSize = 0
        var pos = 12
        while (pos + 8 <= wav.size) {
            val id = ascii(wav, pos)
            var size = buf.getInt(pos + 4)
            val body = pos + 8
            // Streamed WAVs can leave the size unset; take what's there.
            if (size < 0 || body + size > wav.size) size = wav.size - body
            when (id) {
                "fmt " -> {
                    channels = buf.getShort(body + 2).toInt()
                    rate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    dataStart = body
                    dataSize = size
                }
            }
            pos = body + size + (size and 1)
        }
        require(dataStart >= 0) { "no audio data" }
        require(bits == 16 && channels in 1..2 && rate > 0) { "unsupported format: $bits-bit, $channels ch, $rate Hz" }

        val frames = dataSize / (2 * channels)
        val mono = ShortArray(frames) { f ->
            var sum = 0
            for (c in 0 until channels) sum += buf.getShort(dataStart + (f * channels + c) * 2)
            (sum / channels).toShort()
        }

        // Linear interpolation is plenty for speech recognition.
        val outFrames = (frames.toLong() * TARGET_RATE / rate).toInt()
        val out = ByteBuffer.allocate(outFrames * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (j in 0 until outFrames) {
            val src = j.toDouble() * rate / TARGET_RATE
            val i0 = src.toInt().coerceAtMost(frames - 1)
            val i1 = (i0 + 1).coerceAtMost(frames - 1)
            val frac = src - i0
            out.putShort((mono[i0] * (1 - frac) + mono[i1] * frac).toInt().toShort())
        }
        return out.array()
    }

    private fun ascii(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}
