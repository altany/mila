package dev.altany.mila

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dev.altany.mila.match.Contact
import dev.altany.mila.match.ContactMatcher

/**
 * The single car screen: starts listening as soon as it appears, shows the
 * listening/error state, and offers the two modes as large buttons. Speaking
 * an address (Navigate) or a contact name (Call) is the whole interaction.
 */
class MainScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private enum class Mode { NAVIGATE, CALL }

    private sealed interface UiState {
        data object NeedsSetup : UiState
        data object NoRecognizer : UiState
        data object Listening : UiState
        data class Error(val message: String, val partial: String?) : UiState
    }

    private var mode = Mode.NAVIGATE
    private var state: UiState = UiState.Listening
    private var cachedContacts: List<Contact>? = null
    private val speech = SpeechController(carContext)
    private val handler = Handler(Looper.getMainLooper())
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 80)

    init {
        lifecycle.addObserver(this)
        speech.listener = object : SpeechController.Listener {
            override fun onListening() {}

            // Partials are kept by the controller for error recovery; the
            // template is not refreshed per-partial because every content
            // change costs a step of the host's task quota.
            override fun onPartial(text: String) {}

            override fun onResult(text: String) {
                handleResult(text)
            }

            override fun onError(message: String, lastPartial: String?) {
                setState(UiState.Error(message, lastPartial))
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        checkAndStart()
    }

    private fun checkAndStart() {
        when {
            !SetupActivity.allPermissionsGranted(carContext) -> setState(UiState.NeedsSetup)
            !speech.isAvailable -> setState(UiState.NoRecognizer)
            else -> {
                preloadContacts()
                startListening()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        handler.removeCallbacksAndMessages(null)
        speech.stop()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        speech.destroy()
        toneGenerator.release()
    }

    private fun startListening() {
        setState(UiState.Listening)
        // Short prompt tone so the driver knows the mic is open, then start —
        // starting immediately can race the host's audio focus handover.
        toneGenerator.startTone(ToneGenerator.TONE_PROP_PROMPT, 150)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ speech.startListening() }, 300)
    }

    private fun handleResult(text: String) {
        when (mode) {
            Mode.NAVIGATE -> {
                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.navigating_to, text),
                    CarToast.LENGTH_LONG
                ).show()
                NavigationLauncher.navigateTo(carContext, text)
            }
            Mode.CALL -> handleCallRequest(text)
        }
    }

    /**
     * Contacts are read once in the background when the screen opens, so the
     * provider query never runs on the main thread while the driver waits.
     */
    private fun preloadContacts() {
        if (cachedContacts != null) return
        Thread {
            val loaded = runCatching { ContactsRepository.loadAll(carContext) }.getOrNull()
            handler.post { cachedContacts = loaded }
        }.start()
    }

    private fun handleCallRequest(spoken: String) {
        val contacts = cachedContacts
        if (contacts == null) {
            // Still loading (or the read failed): do it off the main thread and
            // come back with the answer.
            Thread {
                val loaded = runCatching { ContactsRepository.loadAll(carContext) }.getOrNull()
                handler.post {
                    if (loaded == null) {
                        setState(UiState.NeedsSetup)
                    } else {
                        cachedContacts = loaded
                        matchAndAct(spoken, loaded)
                    }
                }
            }.start()
            return
        }
        matchAndAct(spoken, contacts)
    }

    private fun matchAndAct(spoken: String, contacts: List<Contact>) {
        when (val result = ContactMatcher.match(spoken, contacts)) {
            is ContactMatcher.Result.Single -> CallLauncher.call(carContext, result.contact)
            is ContactMatcher.Result.Choice ->
                screenManager.push(ContactPickerScreen(carContext, result.candidates, spoken))
            ContactMatcher.Result.None -> setState(
                UiState.Error(carContext.getString(R.string.no_contact_found, spoken), null)
            )
        }
    }

    private fun setState(newState: UiState) {
        state = newState
        invalidate()
    }

    private fun switchMode(newMode: Mode) {
        mode = newMode
        startListening()
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(R.string.app_name))
            .build()

        val message: String
        val actions: List<Action>
        val icon: Int

        when (val s = state) {
            is UiState.NeedsSetup -> {
                message = carContext.getString(R.string.car_needs_setup)
                actions = listOf(action(R.string.recheck, primary = true) {
                    checkAndStart()
                })
                icon = R.drawable.ic_mic
            }
            is UiState.NoRecognizer -> {
                message = carContext.getString(R.string.no_recognizer)
                actions = emptyList()
                icon = R.drawable.ic_mic
            }
            is UiState.Listening -> {
                message = carContext.getString(
                    if (mode == Mode.NAVIGATE) R.string.listening_navigate
                    else R.string.listening_call
                )
                actions = listOf(
                    action(R.string.mode_navigate, primary = mode == Mode.NAVIGATE) {
                        switchMode(Mode.NAVIGATE)
                    },
                    action(R.string.mode_call, primary = mode == Mode.CALL) {
                        switchMode(Mode.CALL)
                    },
                )
                icon = R.drawable.ic_mic
            }
            is UiState.Error -> {
                message = if (s.partial != null) {
                    s.message + "\n" + carContext.getString(R.string.heard, s.partial)
                } else {
                    s.message
                }
                actions = buildList {
                    add(action(R.string.error_retry, primary = true) { startListening() })
                    if (s.partial != null) {
                        add(action(R.string.error_use_partial, primary = false) {
                            handleResult(s.partial)
                        })
                    } else {
                        add(
                            if (mode == Mode.NAVIGATE) {
                                action(R.string.mode_call, primary = false) {
                                    switchMode(Mode.CALL)
                                }
                            } else {
                                action(R.string.mode_navigate, primary = false) {
                                    switchMode(Mode.NAVIGATE)
                                }
                            }
                        )
                    }
                }
                icon = R.drawable.ic_mic
            }
        }

        val builder = MessageTemplate.Builder(message)
            .setHeader(header)
            .setIcon(
                CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build()
            )
        actions.forEach { builder.addAction(it) }
        return builder.build()
    }

    private fun action(titleRes: Int, primary: Boolean, onClick: () -> Unit): Action {
        val builder = Action.Builder()
            .setTitle(carContext.getString(titleRes))
            .setOnClickListener(onClick)
        if (primary) builder.setFlags(Action.FLAG_PRIMARY)
        return builder.build()
    }
}
