package dev.altany.mila

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.core.content.ContextCompat
import dev.altany.mila.match.Contact

object CallLauncher {

    fun call(carContext: CarContext, contact: Contact) {
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_needs_setup),
                CarToast.LENGTH_LONG,
            ).show()
            return
        }

        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(contact.number)}"))
        try {
            carContext.startCarApp(intent)
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.calling, contact.name),
                CarToast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.call_failed),
                CarToast.LENGTH_LONG,
            ).show()
        }
    }
}
