package dev.altany.mila

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class MilaSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return HelloScreen(carContext)
    }
}
