package dev.altany.mila

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognizerIntent
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import androidx.car.app.versioning.CarAppApiLevels
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Streams the car's microphone into the speech recognizer for one listening
 * turn.
 *
 * The phone may be in a bag or a door pocket; the car's microphone is aimed at
 * the driver. CarAudioRecord gives raw 16 kHz mono PCM16 and does no
 * recognition itself, but since Android 13 the recognizer will read audio
 * from a pipe (EXTRA_AUDIO_SOURCE) instead of opening the phone mic. Google's
 * online recognizer accepts this for Greek — checked with a bench probe that
 * piped synthesized speech in while the room was silent.
 *
 * While it records, Android Auto shows its own microphone overlay on the car
 * screen. Dismissing that ends the turn.
 */
internal class CarMicFeed private constructor(
    private val carContext: CarContext,
    private val record: CarAudioRecord,
    private val onEnded: (feed: CarMicFeed, failed: Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val readEnd: ParcelFileDescriptor
    private val writeEnd: ParcelFileDescriptor
    private val stopped = AtomicBoolean(false)
    private var focusRequest: AudioFocusRequest? = null

    init {
        val pipe = ParcelFileDescriptor.createPipe()
        readEnd = pipe[0]
        writeEnd = pipe[1]
    }

    /** Points the recognizer at this feed instead of the phone mic. */
    @SuppressLint("InlinedApi") // Only created on Android 13+, see openOrNull.
    fun addTo(intent: Intent) {
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readEnd)
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
        intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        intent.putExtra(
            RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,
            CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE,
        )
    }

    /** Stop feeding audio. The recognizer sees end-of-stream and wraps up. */
    fun stop() {
        stopped.set(true)
    }

    /** Release everything. Only once the recognizer is done with the pipe. */
    fun close() {
        stop()
        focusRequest?.let { carContext.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        focusRequest = null
        runCatching { readEnd.close() }
    }

    private fun start(): Boolean {
        // Take audio focus so music pauses and isn't recorded along with the driver.
        val audioManager = carContext.getSystemService(AudioManager::class.java) ?: return false
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS) end(failed = false)
            }
            .build()
        if (audioManager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            runCatching { writeEnd.close() }
            return false
        }
        focusRequest = request

        // Start/stop are calls to the car host, so they stay off the main thread too.
        thread(name = "car-mic") {
            var failed = false
            try {
                record.startRecording()
                ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { out ->
                    val buffer = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                    while (!stopped.get()) {
                        val n = record.read(buffer, 0, buffer.size)
                        if (n < 0) break // Driver dismissed the mic on the car screen.
                        if (n > 0) out.write(buffer, 0, n)
                    }
                }
            } catch (e: IOException) {
                // The recognizer closed its end; it has what it needs.
            } catch (e: RuntimeException) {
                Log.w(TAG, "car microphone failed", e)
                failed = true
            } finally {
                runCatching { writeEnd.close() }
                runCatching { record.stopRecording() }
                if (!stopped.get()) end(failed)
            }
        }
        return true
    }

    private fun end(failed: Boolean) {
        if (stopped.getAndSet(true)) return
        main.post { onEnded(this, failed) }
    }

    companion object {
        private const val TAG = "Pesto"

        /**
         * A feed for this turn, or null when the car mic can't be used and the
         * recognizer should open the phone mic as before: Android below 13,
         * an Android Auto too old for car-mic access (API level 5), or the
         * host refusing.
         */
        @SuppressLint("MissingPermission") // MainScreen checks RECORD_AUDIO before listening.
        fun openOrNull(
            context: Context,
            onEnded: (feed: CarMicFeed, failed: Boolean) -> Unit,
        ): CarMicFeed? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
            val car = context as? CarContext ?: return null
            val record = try {
                if (car.carAppApiLevel < CarAppApiLevels.LEVEL_5) return null
                CarAudioRecord.create(car)
            } catch (e: RuntimeException) {
                Log.w(TAG, "car microphone unavailable", e)
                return null
            }
            val feed = CarMicFeed(car, record, onEnded)
            if (!feed.start()) {
                feed.close()
                return null
            }
            return feed
        }
    }
}
