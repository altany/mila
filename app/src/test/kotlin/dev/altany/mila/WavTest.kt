package dev.altany.mila

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavTest {

    private fun wav(channels: Int, rate: Int, samples: ShortArray, dataSize: Int = samples.size * 2): ByteArray {
        val b = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16)
            .putShort(1).putShort(channels.toShort()).putInt(rate)
            .putInt(rate * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        b.put("data".toByteArray()).putInt(dataSize)
        samples.forEach { b.putShort(it) }
        return b.array()
    }

    private fun shorts(bytes: ByteArray): ShortArray {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(bytes.size / 2) { b.getShort() }
    }

    @Test
    fun `16 kHz mono passes through unchanged`() {
        val samples = shortArrayOf(0, 100, -100, 32767, -32768)
        assertArrayEquals(samples, shorts(Wav.toMono16k(wav(1, 16000, samples))))
    }

    @Test
    fun `stereo is averaged and 32 kHz halved`() {
        val stereo = ShortArray(128) { if (it % 2 == 0) 1000 else 3000 } // 64 frames
        val out = shorts(Wav.toMono16k(wav(2, 32000, stereo)))
        assertEquals(32, out.size)
        out.forEach { assertEquals(2000.toShort(), it) }
    }

    @Test
    fun `24 kHz TTS output becomes two thirds as many samples`() {
        val out = Wav.toMono16k(wav(1, 24000, ShortArray(2400)))
        assertEquals(1600 * 2, out.size)
    }

    @Test
    fun `unset data size reads to end of file`() {
        val samples = shortArrayOf(5, 6, 7)
        assertArrayEquals(samples, shorts(Wav.toMono16k(wav(1, 16000, samples, dataSize = -1))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-WAV input`() {
        Wav.toMono16k(ByteArray(64))
    }
}
