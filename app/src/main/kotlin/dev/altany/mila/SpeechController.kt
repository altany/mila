package dev.altany.mila

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Wraps Android's SpeechRecognizer for hands-free use: listening starts
 * programmatically (no mic tap) and ends on our own schedule.
 *
 * Google's recognizer decides for itself when speech has ended and largely
 * treats the EXTRA_SPEECH_INPUT_* timeouts as hints. In a car the cabin is
 * never truly silent, so left alone it can hold the mic open until its own
 * 60-second server cap — the driver says an address and nothing happens for a
 * minute. So we do the endpointing here: once words start arriving, a pause
 * with no new words means the sentence is over, and a hard ceiling backstops
 * the whole thing.
 *
 * In the car the audio comes from the car's own microphone when it can
 * (see CarMicFeed), and from the phone's otherwise.
 */
class SpeechController(private val context: Context) {

    interface Listener {
        fun onListening()
        fun onPartial(text: String)

        /**
         * All hypotheses the recognizer offered, best-first. Greek homophones
         * are easily confused ("κάλεσε" comes back as "θάλασσα"), so the
         * caller gets every reading rather than just the top one.
         */
        fun onResult(alternatives: List<String>)
        fun onError(message: String, lastPartial: String?)
    }

    var listener: Listener? = null

    private var recognizer: SpeechRecognizer? = null
    private var lastPartial: String? = null
    private var carMic: CarMicFeed? = null

    /** Set once the car mic fails, so later turns don't keep trying it. */
    private var carMicFailed = false
    private val handler = Handler(Looper.getMainLooper())

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening() {
        lastPartial = null
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(recognitionListener)
            recognizer = it
        }
        handler.removeCallbacksAndMessages(null)
        r.cancel()
        closeCarMic()
        val feed = if (carMicFailed) null else CarMicFeed.openOrNull(context, ::onCarMicEnded)
        carMic = feed
        r.startListening(buildIntent().also { feed?.addTo(it) })
        handler.postDelayed(::finishListening, MAX_LISTEN_MS)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        closeCarMic()
    }

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        closeCarMic()
    }

    /**
     * Ask the recognizer to wrap up and return what it has heard. Unlike
     * cancel(), this still delivers a result.
     */
    private fun finishListening() {
        handler.removeCallbacksAndMessages(null)
        carMic?.stop()
        recognizer?.stopListening()
        handler.postDelayed(::stopWaitingForResult, RESULT_GRACE_MS)
    }

    /**
     * The recognizer was asked for its result and never answered. Seen with
     * piped audio; don't leave the driver looking at "listening" forever.
     */
    private fun stopWaitingForResult() {
        recognizer?.cancel()
        closeCarMic()
        val partial = lastPartial
        if (partial != null) {
            listener?.onResult(listOf(partial))
        } else {
            listener?.onError(context.getString(R.string.speech_error_no_match), null)
        }
    }

    /** The car mic stopped on its own: dismissed on the car screen, or broke. */
    private fun onCarMicEnded(feed: CarMicFeed, failed: Boolean) {
        if (failed) carMicFailed = true
        if (feed === carMic) finishListening()
    }

    private fun closeCarMic() {
        carMic?.close()
        carMic = null
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, RECOGNITION_LOCALE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, RECOGNITION_LOCALE)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        // Hints only — the timeouts above are what actually bound the wait.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
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
            // Words are arriving: restart the "they've stopped talking" clock.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed(::finishListening, SILENCE_AFTER_SPEECH_MS)
        }

        override fun onResults(results: Bundle) {
            handler.removeCallbacksAndMessages(null)
            closeCarMic()
            val alternatives = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                // With piped car-mic audio the final bundle often comes back
                // empty even though the partials were right.
                ?: listOfNotNull(lastPartial)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "heard: $alternatives")
            }

            if (alternatives.isNotEmpty()) {
                listener?.onResult(alternatives)
            } else {
                listener?.onError(context.getString(R.string.speech_error_no_match), null)
            }
        }

        override fun onError(error: Int) {
            // Cancel/restart races surface as these and resolve themselves —
            // and may arrive after the next turn has opened its car mic.
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                error == SpeechRecognizer.ERROR_CLIENT
            ) return
            handler.removeCallbacksAndMessages(null)
            closeCarMic()
            // If we already heard something usable, use it rather than dead-ending.
            val partial = lastPartial
            if (partial != null &&
                (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
                listener?.onResult(listOf(partial))
                return
            }
            val message = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    context.getString(R.string.speech_error_no_match)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    context.getString(R.string.speech_error_network)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    context.getString(R.string.speech_error_permissions)
                else -> context.getString(R.string.speech_error_generic, error)
            }
            listener?.onError(message, partial)
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "Pesto"
        const val RECOGNITION_LOCALE = "el-GR"

        /** Silence after words have started that means the sentence is over. */
        private const val SILENCE_AFTER_SPEECH_MS = 1800L

        /** Absolute ceiling on one listening turn. */
        private const val MAX_LISTEN_MS = 12000L

        /** How long to wait for the result after asking for it. */
        private const val RESULT_GRACE_MS = 3000L
    }
}
