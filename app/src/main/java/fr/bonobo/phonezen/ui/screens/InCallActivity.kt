// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.bonobo.phonezen.service.CallManager
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.viewmodel.InCallViewModel

class InCallActivity : ComponentActivity() {

    companion object {
        private const val TAG = "InCallActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Afficher par-dessus l'écran de verrouillage et garder l'écran allumé
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

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

    override fun onResume() {
        super.onResume()

        // FIX reconnexion après kill :
        // Si l'app a été killée pendant un appel et que l'utilisateur la relance
        // manuellement (pas via InCallActivity), CallManager.currentCall est null.
        // On tente de récupérer l'appel depuis le service.
        val reconnected = CallManager.reconnectIfNeeded()
        if (reconnected) {
            Log.d(TAG, "onResume : appel récupéré après kill ✅")
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bringToFront()
    }

    /**
     * Ramène l'activité au premier plan de manière sécurisée
     * avec gestion des exceptions pour éviter les crashes
     * sur Android 10+ (API 29+)
     */
    private fun bringToFront() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            // On vérifie que la liste n'est pas vide ET on entoure d'un try-catch
            am.appTasks.firstOrNull()?.let { task ->
                task.moveToFront()
                Log.d(TAG, "bringToFront: activité ramenée au premier plan ✅")
            } ?: run {
                Log.w(TAG, "bringToFront: aucune tâche trouvée")
            }
        } catch (e: Exception) {
            // On log l'erreur sans faire crasher l'application
            Log.e(TAG, "Impossible de ramener l'appel au premier plan : ${e.message}", e)
        }
    }
}