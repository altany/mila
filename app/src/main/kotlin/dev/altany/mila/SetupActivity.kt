package dev.altany.mila

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/**
 * Runtime permission dialogs cannot be shown on the car screen, so this
 * phone-side screen is the one-time setup: grant mic, contacts and calls,
 * then everything else happens in the car.
 */
class SetupActivity : ComponentActivity() {

    private lateinit var statusText: TextView
    private lateinit var checklist: TextView
    private lateinit var grantButton: Button

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.setup_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            gravity = Gravity.CENTER
        })

        statusText = TextView(this).apply {
            text = getString(R.string.setup_explainer)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        root.addView(statusText)

        // Showing each permission's state makes the change visible: without it
        // the screen looks identical before and after granting.
        checklist = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad)
        }
        root.addView(checklist)

        grantButton = Button(this).apply {
            text = getString(R.string.setup_grant)
            setOnClickListener { requestPermissions.launch(REQUIRED_PERMISSIONS) }
        }
        root.addView(grantButton)

        setContentView(root)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val labels = listOf(
            Manifest.permission.RECORD_AUDIO to R.string.perm_mic,
            Manifest.permission.READ_CONTACTS to R.string.perm_contacts,
            Manifest.permission.CALL_PHONE to R.string.perm_calls,
        )
        checklist.text = labels.joinToString("\n") { (permission, label) ->
            val granted = ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
            "${if (granted) "✓" else "○"}  ${getString(label)}"
        }

        val ready = allPermissionsGranted(this)
        statusText.text = getString(
            if (ready) R.string.setup_done else R.string.setup_explainer
        )
        grantButton.visibility = if (ready) View.GONE else View.VISIBLE
    }

    companion object {
        val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
        )

        fun allPermissionsGranted(context: android.content.Context): Boolean =
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }
}
