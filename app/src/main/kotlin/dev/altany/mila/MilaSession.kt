package dev.altany.mila

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.Session

class MilaSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        return MainScreen(carContext)
    }

    /**
     * Reopening the app — from the launcher or by asking the assistant — should
     * always land on a listening screen, not on whatever was left on top.
     */
    override fun onNewIntent(intent: Intent) {
        carContext.getCarService(ScreenManager::class.java).popToRoot()
    }
}
