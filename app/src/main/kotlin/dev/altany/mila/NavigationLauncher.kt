package dev.altany.mila

import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast

/**
 * Hands the spoken destination to the navigation app on the car screen.
 * Tries the richer google.navigation: URI first; the car host only guarantees
 * geo: for ACTION_NAVIGATE, so that stays as the fallback.
 */
object NavigationLauncher {

    fun navigateTo(carContext: CarContext, query: String) {
        val encoded = Uri.encode(query)
        try {
            carContext.startCarApp(
                Intent(CarContext.ACTION_NAVIGATE, Uri.parse("google.navigation:q=$encoded"))
            )
        } catch (first: Exception) {
            try {
                carContext.startCarApp(
                    Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=$encoded"))
                )
            } catch (second: Exception) {
                CarToast.makeText(
                    carContext,
                    carContext.getString(R.string.navigation_failed),
                    CarToast.LENGTH_LONG
                ).show()
            }
        }
    }
}
