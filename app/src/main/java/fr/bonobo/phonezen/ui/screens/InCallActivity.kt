// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.VideoProfile
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.bonobo.phonezen.MainActivity
import fr.bonobo.phonezen.data.model.CallPopupMode
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.service.CallManager
import fr.bonobo.phonezen.service.CallOverlayService
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.utils.ContactResolver
import fr.bonobo.phonezen.utils.CrashHandler
import fr.bonobo.phonezen.viewmodel.InCallViewModel

class InCallActivity : ComponentActivity() {

    companion object {
        private const val TAG             = "InCallActivity"
        private const val PREF_NAME       = "phonezen_prefs"
        private const val PREF_POPUP_MODE = "call_popup_mode"

        fun getLaunchIntent(context: Context): Intent =
            Intent(context, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }

    private var proximityWakeLock: PowerManager.WakeLock? = null

    private var secondCallName   by mutableStateOf<String?>(null)
    private var secondCallNumber by mutableStateOf<String?>(null)
    private var hasSecondCall    by mutableStateOf(false)
    private var waitingCall      : Call? = null

    private val callStatusListener: (Call?, CallStatus) -> Unit = { _, _ ->
        refreshSecondCallUi()
    }

    private var hasReturnedToMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.lastAction = "InCallActivity.onCreate"
        setupLockScreenFlags()
        initProximityWakeLock()

        val popupMode = getPopupMode()
        val number    = CallManager.getCall()?.details?.handle?.schemeSpecificPart ?: ""

        if (number.isNotBlank()) {
            CrashHandler.lastAnalyzedNumber = number
        }

        if (popupMode != CallPopupMode.FULLSCREEN && number.isNotBlank()) {
            CrashHandler.lastAction = "InCallActivity.onCreate: délégation overlay $popupMode"
            Log.d(TAG, "Mode $popupMode → délégation au CallOverlayService")
            CallOverlayService.start(this, popupMode, number)
            finish()
            return
        }

        setContent {
            PhoneZenTheme {
                val vm: InCallViewModel = viewModel()
                InCallScreen(
                    vm                 = vm,
                    onFinish           = { returnToMainActivity() },
                    secondCallName     = secondCallName,
                    secondCallNumber   = secondCallNumber,
                    hasSecondCall      = hasSecondCall,
                    onAnswerSecondCall = { answerWaitingCall() },
                    onRejectSecondCall = { rejectWaitingCall() },
                    onStayOnFirstCall  = { stayOnFirstCall() },
                    onSwapCalls        = { swapCalls() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CrashHandler.lastAction = "InCallActivity.onResume"
        CallManager.reconnectIfNeeded()
        CallManager.addListener(callStatusListener)
        refreshSecondCallUi()
        acquireProximityWakeLock()
    }

    override fun onPause() {
        super.onPause()
        CrashHandler.lastAction = "InCallActivity.onPause"
        CallManager.removeListener(callStatusListener)
        releaseProximityWakeLock()
    }

    override fun onDestroy() {
        CrashHandler.lastAction = "InCallActivity.onDestroy"
        CallManager.removeListener(callStatusListener)
        releaseProximityWakeLock()
        super.onDestroy()
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CrashHandler.lastAction = "InCallActivity.onNewIntent"
        setIntent(intent)
        refreshSecondCallUi()
    }

    // ── Mode popup ────────────────────────────────────────────────────────────

    private fun getPopupMode(): CallPopupMode {
        val prefs    = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(PREF_POPUP_MODE, CallPopupMode.FULLSCREEN.name)
            ?: CallPopupMode.FULLSCREEN.name
        return runCatching { CallPopupMode.valueOf(modeName) }
            .getOrDefault(CallPopupMode.FULLSCREEN)
    }

    // ── Capteur de proximité ──────────────────────────────────────────────────

    private fun initProximityWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                proximityWakeLock = pm.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "PhoneZen:proximity"
                ).apply { setReferenceCounted(false) }
                Log.d(TAG, "ProximityWakeLock initialisé")
            }
        } catch (e: Exception) {
            Log.e(TAG, "initProximityWakeLock error: ${e.message}")
        }
    }

    private fun acquireProximityWakeLock() {
        try {
            proximityWakeLock?.takeIf { !it.isHeld }?.acquire()
        } catch (e: Exception) {
            Log.e(TAG, "acquireProximityWakeLock error: ${e.message}")
        }
    }

    private fun releaseProximityWakeLock() {
        try {
            proximityWakeLock?.takeIf { it.isHeld }
                ?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        } catch (e: Exception) {
            Log.e(TAG, "releaseProximityWakeLock error: ${e.message}")
        }
    }

    // ── Lock screen ───────────────────────────────────────────────────────────

    private fun setupLockScreenFlags() {
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
    }

    // ── Fin d'appel / retour à MainActivity ──────────────────────────────────

    private fun returnToMainActivity() {
        if (hasReturnedToMain) return
        hasReturnedToMain = true
        CrashHandler.lastAction = "InCallActivity.returnToMainActivity"
        Log.d(TAG, "Plus aucun appel → retour vers MainActivity")
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "Erreur returnToMainActivity: ${e.message}")
        }
        finish()
    }

    // ── Gestion du double appel ───────────────────────────────────────────────

    private fun refreshSecondCallUi() {
        try {
            val calls = CallManager.getCalls()
            if (calls.size < 2) {
                hasSecondCall = false; secondCallNumber = null
                secondCallName = null; waitingCall = null
                return
            }
            val primaryNumber = calls
                .firstOrNull { it.state == Call.STATE_ACTIVE }
                ?.details?.handle?.schemeSpecificPart
                ?: calls.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
                    ?.details?.handle?.schemeSpecificPart

            val found = calls.firstOrNull { call ->
                val num       = call.details?.handle?.schemeSpecificPart
                val different = !num.isNullOrBlank() && num != primaryNumber
                val waiting   = call.state == Call.STATE_RINGING || call.state == Call.STATE_HOLDING
                different && waiting
            }
            waitingCall      = found
            val number       = found?.details?.handle?.schemeSpecificPart
            hasSecondCall    = found != null
            secondCallNumber = number
            secondCallName   = ContactResolver.displayName(this, number)
        } catch (e: Exception) {
            Log.e(TAG, "refreshSecondCallUi error: ${e.message}", e)
            CrashHandler.logError(this, "refreshSecondCallUi", e)
            hasSecondCall = false; secondCallNumber = null
            secondCallName = null; waitingCall = null
        }
    }

    private fun answerWaitingCall() {
        CrashHandler.lastAction = "InCallActivity.answerWaitingCall"
        waitingCall?.let {
            try {
                it.answer(VideoProfile.STATE_AUDIO_ONLY)
                waitingCall = null; hasSecondCall = false
                secondCallNumber = null; secondCallName = null
                refreshSecondCallUi()
            } catch (e: Exception) {
                Log.e(TAG, "answerWaitingCall: ${e.message}")
                CrashHandler.logError(this, "answerWaitingCall", e)
            }
        }
    }

    private fun rejectWaitingCall() {
        CrashHandler.lastAction = "InCallActivity.rejectWaitingCall"
        waitingCall?.let {
            try {
                it.reject(false, null)
                waitingCall = null; hasSecondCall = false
                secondCallNumber = null; secondCallName = null
                refreshSecondCallUi()
            } catch (e: Exception) {
                Log.e(TAG, "rejectWaitingCall: ${e.message}")
                CrashHandler.logError(this, "rejectWaitingCall", e)
            }
        }
    }

    private fun stayOnFirstCall() {
        CrashHandler.lastAction = "InCallActivity.stayOnFirstCall"
        waitingCall?.let {
            try {
                it.reject(false, null)
                waitingCall = null; hasSecondCall = false
                secondCallNumber = null; secondCallName = null
            } catch (e: Exception) {
                Log.e(TAG, "stayOnFirstCall: ${e.message}")
                CrashHandler.logError(this, "stayOnFirstCall", e)
            }
        }
    }

    private fun swapCalls() {
        CrashHandler.lastAction = "InCallActivity.swapCalls"
        try {
            val calls      = CallManager.getCalls()
            val activeCall = calls.firstOrNull { it.state == Call.STATE_ACTIVE }
            val heldCall   = calls.firstOrNull { it.state == Call.STATE_HOLDING }
            if (activeCall != null && heldCall != null) {
                activeCall.hold(); heldCall.unhold()
            }
            refreshSecondCallUi()
        } catch (e: Exception) {
            Log.e(TAG, "swapCalls: ${e.message}")
            CrashHandler.logError(this, "swapCalls", e)
        }
    }
}
