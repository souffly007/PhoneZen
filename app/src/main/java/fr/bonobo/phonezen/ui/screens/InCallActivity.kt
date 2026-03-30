package fr.bonobo.phonezen.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.viewmodel.InCallViewModel

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Afficher par-dessus l'écran de verrouillage et garder l'écran allumé
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContent {
            PhoneZenTheme {
                val vm: InCallViewModel = viewModel()
                InCallScreen(
                    vm       = vm,
                    onFinish = { finish() }
                )
            }
        }
    }

    /**
     * Quand l'utilisateur appuie sur Home pendant un appel,
     * on force le retour au premier plan à la fin de l'appel.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        bringToFront()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        bringToFront()
    }

    private fun bringToFront() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.appTasks.firstOrNull()?.moveToFront()
    }
}