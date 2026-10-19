package dev.altany.mila

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.RequiresApi
import dev.altany.mila.match.GreekText
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.concurrent.thread

/**
 * EXPERIMENT — delete this file (and its button in SetupActivity) if the
 * answer is no.
 *
 * The question: does Google's online recognizer transcribe audio handed to it
 * through EXTRA_AUDIO_SOURCE, or does it ignore that and open the phone mic?
 * If it accepts it, the car's microphone (CarAudioRecord) can feed it. Greek
 * has no on-device model, so the online recognizer is the only option.
 *
 * The probe has the phone's TTS speak a Greek phrase into a file (nothing
 * plays aloud), pipes that audio into the recognizer and checks whether the
 * phrase comes back. The phone mic only hears a quiet room, so getting the
 * phrase back means the piped audio was used.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AudioInjectionProbe(
    private val context: Context,
    private val report: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var readEnd: ParcelFileDescriptor? = null
    private var writeEnd: ParcelFileDescriptor? = null
    private var writer: Thread? = null
    private var finished = true

    fun run() {
        if (!finished) return
        finished = false
        report(context.getString(R.string.probe_preparing))
        tts = TextToSpeech(context) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) synthesize()
                else finish(context.getString(R.string.probe_no_tts))
            }
        }
    }

    fun cancel() {
        finished = true
        cleanUp()
    }

    private fun synthesize() {
        val engine = tts ?: return
        val lang = engine.setLanguage(Locale.forLanguageTag(SpeechController.RECOGNITION_LOCALE))
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            finish(context.getString(R.string.probe_no_greek_voice))
            return
        }
        val file = File(context.cacheDir, "probe.wav")
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                main.post { recognize(file) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                main.post { finish(context.getString(R.string.probe_no_tts)) }
            }
        })
        engine.synthesizeToFile(PHRASE, Bundle(), file, "probe")
    }

    private fun recognize(wav: File) {
        if (finished) return
        val pcm = try {
            Wav.toMono16k(wav.readBytes())
        } catch (e: IllegalArgumentException) {
            finish(context.getString(R.string.probe_bad_audio, e.message))
            return
        }
        report(context.getString(R.string.probe_listening))

        val (read, write) = ParcelFileDescriptor.createPipe()
        readEnd = read
        writeEnd = write

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = startWriting(pcm, write)
            override fun onPartialResults(partialResults: Bundle?) {
                texts(partialResults).firstOrNull()?.let {
                    report(context.getString(R.string.probe_hearing, it))
                }
            }
            override fun onResults(results: Bundle?) = verdict(texts(results), null)
            override fun onError(error: Int) = verdict(emptyList(), error)
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Same settings as the real recognizer, plus the piped audio. The
        // format extras match what CarAudioRecord produces: 16 kHz mono PCM16.
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, SpeechController.RECOGNITION_LOCALE)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SpeechController.RECOGNITION_LOCALE)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, Wav.TARGET_RATE)
        }
        r.startListening(intent)

        // If the recognizer neither answers nor errors, wrap it up ourselves.
        main.postDelayed({ recognizer?.stopListening() }, LISTEN_MS)
        main.postDelayed({ verdict(emptyList(), null) }, LISTEN_MS + GRACE_MS)
    }

    /** Feeds the audio at real-time pace, framed by silence, then closes the pipe. */
    private fun startWriting(pcm: ByteArray, write: ParcelFileDescriptor) {
        if (writer != null) return
        writer = thread(name = "probe-writer") {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                    val silence = ByteArray(CHUNK_BYTES)
                    repeat(LEAD_SILENCE_CHUNKS) { out.write(silence); Thread.sleep(CHUNK_MS) }
                    var i = 0
                    while (i < pcm.size) {
                        val n = minOf(CHUNK_BYTES, pcm.size - i)
                        out.write(pcm, i, n)
                        i += n
                        Thread.sleep(CHUNK_MS)
                    }
                    repeat(TAIL_SILENCE_CHUNKS) { out.write(silence); Thread.sleep(CHUNK_MS) }
                }
            } catch (e: IOException) {
                // Pipe closed under us: the probe finished or the recognizer stopped reading.
            } catch (e: InterruptedException) {
                // Cancelled.
            }
        }
    }

    private fun verdict(heard: List<String>, error: Int?) {
        if (finished) return
        val match = heard.firstOrNull(::soundsLikePhrase)
        finish(
            when {
                match != null -> context.getString(R.string.probe_works, match)
                heard.isNotEmpty() -> context.getString(R.string.probe_other_words, heard.first())
                error != null -> context.getString(R.string.probe_nothing_error, error)
                else -> context.getString(R.string.probe_nothing)
            }
        )
    }

    private fun soundsLikePhrase(text: String): Boolean {
        val heard = GreekText.normalize(text).split(Regex("\\s+")).toSet()
        val expected = GreekText.normalize(PHRASE).split(Regex("\\s+"))
        return expected.count { it in heard } * 2 >= expected.size
    }

    private fun texts(bundle: Bundle?): List<String> =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            .filter { it.isNotBlank() }

    private fun finish(message: String) {
        finished = true
        cleanUp()
        report(message)
    }

    private fun cleanUp() {
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        writer?.interrupt()
        writer = null
        // Kept open until here: closing the read end early hands the
        // recognizer a dead descriptor.
        runCatching { writeEnd?.close() }
        runCatching { readEnd?.close() }
        writeEnd = null
        readEnd = null
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val PHRASE = "Πλοήγηση στην οδό Πανεπιστημίου στην Αθήνα"
        private const val CHUNK_MS = 100L
        private const val CHUNK_BYTES = Wav.TARGET_RATE / 10 * 2 // 100 ms of 16-bit mono
        private const val LEAD_SILENCE_CHUNKS = 3
        private const val TAIL_SILENCE_CHUNKS = 20
        private const val LISTEN_MS = 12000L
        private const val GRACE_MS = 4000L
    }
}
