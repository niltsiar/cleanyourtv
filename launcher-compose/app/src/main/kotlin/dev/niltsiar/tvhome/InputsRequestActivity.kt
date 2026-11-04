package dev.niltsiar.tvhome

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import dev.niltsiar.tvhome.core.model.HomeActions

/**
 * Answers com.android.tv.action.VIEW_INPUTS, the system input switcher that the remote's Input button turns into
 * (through the TV's Live TV app or the input key service), by bringing Home forward with its Inputs list open. The
 * manifest keeps it disabled and MainActivity switches it on only while handle_view_inputs is set, so a TV with its
 * own input picker is never offered a chooser. It has no UI of its own.
 */
class InputsRequestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(HomeActions.VIEW_INPUTS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }
}
