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

    private var displayMode by mutableStateOf(CallPopupMode.FULLSCREEN)
    private var callerNumber by mutableStateOf("")
    private var callerName by mutableStateOf("")
    private var displayStatus by mutableStateOf(CallStatus.IDLE)
    private val overlayControls = object : CallOverlayControls {
        override val callerName: String get() = this@InCallActivity.callerName
        override val callerNumber: String get() = this@InCallActivity.callerNumber
        override val callStatus: CallStatus get() = displayStatus
        override fun answer() = CallManager.answer()
        override fun reject() = CallManager.rejectRingingOrHangUp()
    }

    private fun refreshCallUi() {
        val call = CallManager.getCalls().firstOrNull { it.state == Call.STATE_RINGING } ?: CallManager.getCall()
        if (call == null) { returnToMainActivity(); return }
        callerNumber = call.details.handle?.schemeSpecificPart.orEmpty()
        callerName = ContactResolver.displayName(this, callerNumber)
        displayStatus = CallManager.fromState(call.state)
        if (displayStatus == CallStatus.RINGING) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        refreshSecondCallUi()
    }

    private fun applyDisplayMode() {
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (displayMode == CallPopupMode.FULLSCREEN) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        else window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
            if (displayMode == CallPopupMode.FULLSCREEN) WindowManager.LayoutParams.MATCH_PARENT
            else WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(if (displayMode == CallPopupMode.MINI) android.view.Gravity.TOP else android.view.Gravity.BOTTOM)
        // Keep popup content outside Android's status/navigation bars.  The
        // values vary by device (gesture navigation, 3-button navigation,
        // cut-outs), so read the current insets instead of hard-coding pixels.
        window.decorView.post {
            val insets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                window.decorView.rootWindowInsets?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        it.getInsets(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    } else {
                        @Suppress("DEPRECATION")
                        android.graphics.Insets.of(it.systemWindowInsetLeft, it.systemWindowInsetTop, it.systemWindowInsetRight, it.systemWindowInsetBottom)
                    }
                }
            } else null
            val safeInset = if (displayMode == CallPopupMode.MINI) insets?.top ?: 0 else insets?.bottom ?: 0
            val attrs = window.attributes
            attrs.y = safeInset + (8 * resources.displayMetrics.density).toInt()
            window.attributes = attrs
        }
    }

    private var secondCallName   by mutableStateOf<String?>(null)
    private var secondCallNumber by mutableStateOf<String?>(null)
    private var hasSecondCall    by mutableStateOf(false)
    private var waitingCall      : Call? = null

    private val callStatusListener: (Call?, CallStatus) -> Unit = { _, _ ->
        refreshCallUi()
    }

    private var hasReturnedToMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashHandler.lastAction = "InCallActivity.onCreate"
        setupLockScreenFlags()
        CallOverlayService.stop(this)

        displayMode = if (intent.getBooleanExtra("force_fullscreen", false)) CallPopupMode.FULLSCREEN else getPopupMode()
        applyDisplayMode()
        refreshCallUi()
        if (isFinishing) return

        setContent {
            PhoneZenTheme {
                if (displayMode != CallPopupMode.FULLSCREEN) {
                    val expand = { displayMode = CallPopupMode.FULLSCREEN; applyDisplayMode() }
                    if (displayMode == CallPopupMode.MINI) MiniCallOverlay(overlayControls, expand)
                    else CompactCallOverlay(overlayControls, expand)
                } else {
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
    }

    override fun onResume() {
        super.onResume()
        CrashHandler.lastAction = "InCallActivity.onResume"
        CallManager.reconnectIfNeeded()
        CallManager.addListener(callStatusListener)
        refreshCallUi()
        applyDisplayMode()
    }

    override fun onPause() {
        super.onPause()
        CrashHandler.lastAction = "InCallActivity.onPause"
    }

    override fun onDestroy() {
        CrashHandler.lastAction = "InCallActivity.onDestroy"
        CallManager.removeListener(callStatusListener)
        super.onDestroy()
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        CrashHandler.lastAction = "InCallActivity.onNewIntent"
        setIntent(intent)
        if (intent.getBooleanExtra("force_fullscreen", false)) displayMode = CallPopupMode.FULLSCREEN
        applyDisplayMode()
        refreshCallUi()
    }

    // ── Mode popup ────────────────────────────────────────────────────────────

    private fun getPopupMode(): CallPopupMode {
        val prefs    = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val modeName = prefs.getString(PREF_POPUP_MODE, CallPopupMode.FULLSCREEN.name)
            ?: CallPopupMode.FULLSCREEN.name
        return runCatching { CallPopupMode.valueOf(modeName) }
            .getOrDefault(CallPopupMode.FULLSCREEN)
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

    }

    // ── Fin d'appel / retour à MainActivity ──────────────────────────────────

    private fun returnToMainActivity() {
        if (hasReturnedToMain) return
        hasReturnedToMain = true
        CrashHandler.lastAction = "InCallActivity.returnToMainActivity"
        if (CallManager.getCallCount() > 0) { hasReturnedToMain = false; return }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        try {
            startActivity(Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.e(TAG, "Accueil Android indisponible", e)
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
