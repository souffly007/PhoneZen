// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import fr.bonobo.phonezen.data.model.CallPopupMode
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.service.CallManager
import fr.bonobo.phonezen.ui.screens.CompactCallOverlay
import fr.bonobo.phonezen.ui.screens.MiniCallOverlay
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.utils.ContactResolver

/**
 * CallOverlayService
 * ------------------
 * Affiche un overlay flottant (COMPACT ou MINI) par-dessus toutes les apps
 * lors d'un appel entrant ou actif, via WindowManager + TYPE_APPLICATION_OVERLAY.
 *
 * Démarré par InCallActivity si le mode n'est pas FULLSCREEN.
 * S'arrête automatiquement quand l'appel se termine.
 */
class CallOverlayService : Service(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    companion object {
        private const val TAG = "CallOverlayService"

        const val EXTRA_MODE   = "popup_mode"
        const val EXTRA_NUMBER = "call_number"

        fun start(context: Context, mode: CallPopupMode, number: String) {
            val intent = Intent(context, CallOverlayService::class.java).apply {
                putExtra(EXTRA_MODE,   mode.name)
                putExtra(EXTRA_NUMBER, number)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallOverlayService::class.java))
        }
    }

    // ── Lifecycle boilerplate (requis pour ComposeView hors Activity) ─────────
    private val lifecycleRegistry  = LifecycleRegistry(this)
    private val vmStore            = ViewModelStore()
    private val savedStateCtrl     = SavedStateRegistryController.create(this)

    override val lifecycle        : Lifecycle            get() = lifecycleRegistry
    override val viewModelStore   : ViewModelStore       get() = vmStore
    override val savedStateRegistry: SavedStateRegistry  get() = savedStateCtrl.savedStateRegistry

    // ── State ─────────────────────────────────────────────────────────────────
    private var overlayView   : ComposeView?     = null
    private var windowManager : WindowManager?   = null

    var callerName   by mutableStateOf("")
    var callerNumber by mutableStateOf("")
    var callStatus   by mutableStateOf(CallStatus.RINGING)

    private val callListener: (android.telecom.Call?, CallStatus) -> Unit = { call, status ->
        callStatus   = status
        callerNumber = call?.details?.handle?.schemeSpecificPart ?: callerNumber
        if (status == CallStatus.DISCONNECTED || status == CallStatus.IDLE) {
            Log.d(TAG, "Appel terminé → arrêt du service overlay")
            stopSelf()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        savedStateCtrl.performAttach()
        savedStateCtrl.performRestore(null)   // ← ligne manquante : restaure AVANT de bouger le lifecycle
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modeName = intent?.getStringExtra(EXTRA_MODE)   ?: CallPopupMode.COMPACT.name
        val number   = intent?.getStringExtra(EXTRA_NUMBER) ?: ""
        val mode     = runCatching { CallPopupMode.valueOf(modeName) }.getOrDefault(CallPopupMode.COMPACT)

        callerNumber = number
        callerName   = ContactResolver.resolveName(this, number) ?: number
        callStatus   = CallStatus.RINGING

        CallManager.addListener(callListener)
        showOverlay(mode)

        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        CallManager.removeListener(callListener)
        removeOverlay()
        vmStore.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun showOverlay(mode: CallPopupMode) {
        removeOverlay()

        val (gravity, height) = when (mode) {
            CallPopupMode.MINI    -> Gravity.TOP or Gravity.START to WindowManager.LayoutParams.WRAP_CONTENT
            CallPopupMode.COMPACT -> Gravity.BOTTOM or Gravity.START to WindowManager.LayoutParams.WRAP_CONTENT
            else                  -> Gravity.BOTTOM or Gravity.START to WindowManager.LayoutParams.WRAP_CONTENT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CallOverlayService)
            setViewTreeViewModelStoreOwner(this@CallOverlayService)
            setViewTreeSavedStateRegistryOwner(this@CallOverlayService)
            setContent {
                PhoneZenTheme {
                    when (mode) {
                        CallPopupMode.MINI    -> MiniCallOverlay(
                            service  = this@CallOverlayService,
                            onExpand = { expandToFullScreen() }
                        )
                        else                  -> CompactCallOverlay(
                            service  = this@CallOverlayService,
                            onExpand = { expandToFullScreen() }
                        )
                    }
                }
            }
        }

        overlayView = view
        try {
            windowManager?.addView(view, params)
            Log.d(TAG, "Overlay $mode affiché")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur addView: ${e.message}")
        }
    }

    private fun removeOverlay() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { /* déjà retiré */ }
            overlayView = null
        }
    }

    /** Bascule vers InCallActivity plein écran (ex: tap sur l'overlay) */
    fun expandToFullScreen() {
        val intent = fr.bonobo.phonezen.ui.screens.InCallActivity
            .getLaunchIntent(this)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivity(intent)
    }

    /** Répondre à l'appel depuis l'overlay */
    fun answer() {
        CallManager.answer()
        expandToFullScreen()
    }

    /** Refuser/Raccrocher depuis l'overlay */
    fun reject() = CallManager.hangUp()
}