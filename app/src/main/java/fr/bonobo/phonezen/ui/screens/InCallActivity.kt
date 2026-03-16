package fr.bonobo.phonezen.ui.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.viewmodel.InCallViewModel

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Permet d'afficher l'activité par-dessus l'écran de verrouillage
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        setContent {
            PhoneZenTheme {
                val vm: InCallViewModel = viewModel()

                // RECTIFICATION : On observe 'state' au lieu de 'callState'
                val uiState by vm.state.collectAsState()

                // On passe le ViewModel à l'écran Compose
                InCallScreen(
                    vm = vm,
                    onFinish = { finish() }
                )
            }
        }
    }
}