package dev.altany.mila

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps Android's SpeechRecognizer for hands-free use: listening starts
 * programmatically (no mic tap) and the silence timeouts are stretched so the
 * recognizer doesn't give up while the driver is still forming a sentence.
 */
class SpeechController(private val context: Context) {

    interface Listener {
        fun onListening()
        fun onPartial(text: String)
        fun onResult(text: String)
        fun onError(message: String, lastPartial: String?)
    }

    var listener: Listener? = null

    private var recognizer: SpeechRecognizer? = null
    private var lastPartial: String? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        lastPartial = null
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(recognitionListener)
            recognizer = it
        }
        r.cancel()
        r.startListening(buildIntent())
    }

    fun stop() {
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, RECOGNITION_LOCALE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, RECOGNITION_LOCALE)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Keep the mic open long enough for real driving speech: don't finish
        // on a short pause, and never bail out in under a second.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listener?.onListening()
        }

        override fun onPartialResults(partialResults: Bundle) {
            val text = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() } ?: return
            lastPartial = text
            listener?.onPartial(text)
        }

        override fun onResults(results: Bundle) {
            val text = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: lastPartial
            if (text != null) {
                listener?.onResult(text)
            } else {
                listener?.onError(context.getString(R.string.speech_error_no_match), null)
            }
        }

        override fun onError(error: Int) {
            // The recognizer sometimes quits early; if it already heard
            // something usable, surface that instead of a dead end.
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    context.getString(R.string.speech_error_no_match)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    context.getString(R.string.speech_error_network)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    context.getString(R.string.speech_error_permissions)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT ->
                    // Cancel/restart races surface as these; not worth showing.
                    return
                else -> context.getString(R.string.speech_error_generic, error)
            }
            listener?.onError(message, lastPartial)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        const val RECOGNITION_LOCALE = "el-GR"
    }
}
