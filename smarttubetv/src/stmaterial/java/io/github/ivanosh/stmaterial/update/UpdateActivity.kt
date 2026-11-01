package io.github.ivanosh.stmaterial.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import io.github.ivanosh.stmaterial.dialog.DialogScreen
import io.github.ivanosh.stmaterial.dialog.DialogState
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme

/**
 * The prompt over the browse screen when a check found an update. Translucent like the other dialogs. It is only
 * ever started by [UpdateScheduler] from the browse screen while that is in front, so it never covers the player.
 */
class UpdateActivity : ComponentActivity() {
    private val state = DialogState()
    private lateinit var dialog: UpdateDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val offer = UpdateScheduler.takeOfferForDialog()
        if (offer == null) {
            finish()
            return
        }
        dialog = UpdateDialog(this, state, ::finish)
        state.push(dialog.offerPage(offer))
        setContentView(ComposeView(this).apply {
            setContent { MaterialTvTheme { DialogScreen(state, dialog::activate) } }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!dialog.onBack()) finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::dialog.isInitialized) dialog.release()
    }
}
