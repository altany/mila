package dev.altany.mila

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import dev.altany.mila.match.ScoredContact

/**
 * Shown when several contacts score close together: better to ask than to
 * dial the wrong person. Tapping a row places the call.
 */
class ContactPickerScreen(
    carContext: CarContext,
    private val candidates: List<ScoredContact>,
    private val spoken: String,
    private val onSayAgain: () -> Unit,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        candidates.forEach { scored ->
            list.addItem(
                Row.Builder()
                    .setTitle(scored.contact.name)
                    .addText(scored.contact.number)
                    .setOnClickListener {
                        CallLauncher.call(carContext, scored.contact)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        // Changing your mind here shouldn't mean going back and hunting for a
        // button; one tap puts the mic straight back on.
        list.addItem(
            Row.Builder()
                .setTitle(carContext.getString(R.string.error_retry))
                .setOnClickListener { onSayAgain() }
                .build()
        )

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.BACK)
                    .setTitle(carContext.getString(R.string.heard, spoken))
                    .build()
            )
            .setSingleList(list.build())
            .build()
    }
}
