// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.OutcomeReceiver
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.telecom.Call
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.R
import fr.bonobo.phonezen.data.model.AudioRoute
import fr.bonobo.phonezen.ui.screens.InCallActivity
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import fr.bonobo.phonezen.utils.VoicemailNotificationHelper
import fr.bonobo.phonezen.viewmodel.MainViewModel
import java.util.Locale

class PhoneZenInCallService : InCallService() {

    companion object {
        private const val CHANNEL_ID_BLOCKED   = "blocked_calls"
        private const val CHANNEL_ID_ACTIVE    = "phonezen_calls_v2"
        private const val CHANNEL_ID_VOICEMAIL = "voicemail"
        private const val NOTIF_ID_FOREGROUND  = 1001
        private const val ACTION_HANGUP        = "fr.bonobo.phonezen.ACTION_HANGUP"
        private const val TAG                  = "PhoneZenInCall"

        private val VIBRATION_PATTERN = longArrayOf(0, 800, 400, 800, 400)
    }

    private var proximityLock: android.os.PowerManager.WakeLock? = null
    private fun updateProximity() {
        val use = CallManager.getCalls().any { it.state == Call.STATE_ACTIVE } &&
            callAudioState?.route == android.telecom.CallAudioState.ROUTE_EARPIECE
        try {
            if (use) {
                if (proximityLock == null) {
                    val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
                    if (pm.isWakeLockLevelSupported(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        proximityLock = pm.newWakeLock(android.os.PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                            "PhoneZen:callProximity").apply { setReferenceCounted(false) }
                    }
                }
                proximityLock?.let { if (!it.isHeld) it.acquire() }
            } else proximityLock?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) { Log.w(TAG, "Capteur de proximité indisponible", e) }
    }

    private var vibrator: Vibrator? = null
    private var voicemailListener: VoicemailListener? = null
    private var tts: TextToSpeech? = null
    private var availableAudioEndpoints: List<CallEndpoint> = emptyList()

    private val drivingModeManager by lazy { DrivingModeManager(applicationContext) }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.onStateChanged(call, state)
            updateProximity()
            if (CallManager.getCallCount() > 0) updateForegroundNotificationWithActions()
            when (state) {
                Call.STATE_ACTIVE,
                Call.STATE_DISCONNECTED,
                Call.STATE_DISCONNECTING -> stopRingerVibration()
            }
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                if (CallManager.getCallCount() == 0) stopForeground(true)
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        try {
            VoicemailNotificationHelper.createChannel(this)
            voicemailListener = VoicemailListener(this)
            voicemailListener?.register()
            Log.d(TAG, "VoicemailListener initialisé")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur VoicemailListener: ${e.message}")
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onDestroy() {
        try {
            voicemailListener?.unregister()
            Log.d(TAG, "VoicemailListener désinscrit")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur désinscription VoicemailListener: ${e.message}")
        }
        proximityLock?.let { if (it.isHeld) it.release() }
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANGUP) {
            Log.d(TAG, "Raccrochage depuis notification")
            try { CallManager.rejectRingingOrHangUp() } catch (e: Exception) {
                Log.e(TAG, "Erreur raccrochage: ${e.message}")
            }
            stopRingerVibration()
            stopForeground(true)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // ─── Appels ───────────────────────────────────────────────────────────────

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded : état=${call.state}")

        call.registerCallback(callCallback)
        CallManager.setInCallService(this)
        CallManager.setCall(call)

        startForegroundServiceSafe()

        val number = call.details.handle?.schemeSpecificPart ?: ""

        if ((if (Build.VERSION.SDK_INT >= 29) call.details.callDirection == Call.Details.DIRECTION_INCOMING else call.state == Call.STATE_RINGING)) {
            // ── Mode conduite ─────────────────────────────────────────────
            if (drivingModeManager.isDriving.value) {
                announceCallerName(call, number)
                sendAutoSms(number)
            }
        }

        // ── Lancement de l'InCall UI PhoneZen ────────────────────────────
        launchInCallActivity()

        if ((if (Build.VERSION.SDK_INT >= 29) call.details.callDirection == Call.Details.DIRECTION_INCOMING else call.state == Call.STATE_RINGING)) {
            startRingerVibration()
        }

        updateForegroundNotificationWithActions(number)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")
        call.unregisterCallback(callCallback)
        stopRingerVibration()
        tts?.stop()

        CallManager.onStateChanged(call, Call.STATE_DISCONNECTED)
        updateProximity()

        if (CallManager.getCallCount() == 0) {
            CallManager.clear()
            CallManager.setInCallService(null)
            stopForeground(true)
        }
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
        updateProximity()
        Log.d(TAG, "Audio state changed: $audioState")
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        availableAudioEndpoints = availableEndpoints
    }

    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        CallManager.onEndpointChanged(callEndpoint)
    }

    fun requestAudioRoute(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val endpointType = when (route) {
                AudioRoute.EARPIECE -> CallEndpoint.TYPE_EARPIECE
                AudioRoute.SPEAKER -> CallEndpoint.TYPE_SPEAKER
                AudioRoute.BLUETOOTH -> CallEndpoint.TYPE_BLUETOOTH
            }
            val endpoint = availableAudioEndpoints.firstOrNull { it.endpointType == endpointType }
            if (endpoint != null) {
                requestCallEndpointChange(endpoint, mainExecutor,
                    object : OutcomeReceiver<Void?, CallEndpointException> {
                        override fun onResult(result: Void?) = Unit
                        override fun onError(error: CallEndpointException) {
                            Log.w(TAG, "Impossible de sélectionner la route $route", error)
                            requestLegacyAudioRoute(route)
                        }
                    })
                return
            }
        }
        requestLegacyAudioRoute(route)
    }

    @Suppress("DEPRECATION")
    private fun requestLegacyAudioRoute(route: AudioRoute) {
        setAudioRoute(when (route) {
            AudioRoute.EARPIECE -> android.telecom.CallAudioState.ROUTE_EARPIECE
            AudioRoute.SPEAKER -> android.telecom.CallAudioState.ROUTE_SPEAKER
            AudioRoute.BLUETOOTH -> android.telecom.CallAudioState.ROUTE_BLUETOOTH
        })
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d(TAG, "onRebind — réhydratation CallManager")
        reconnectActiveCalls()
    }

    private fun reconnectActiveCalls() {
        try {
            CallManager.setInCallService(this)
            val activeCalls = calls ?: emptyList()
            Log.d(TAG, "reconnectActiveCalls — ${activeCalls.size} appel(s) actif(s)")

            activeCalls.forEach { call ->
                try {
                    call.registerCallback(callCallback)
                    CallManager.setCall(call)
                    CallManager.onStateChanged(call, call.state)
                    val number = call.details?.handle?.schemeSpecificPart ?: ""
                    Log.d(TAG, "Appel reconnecté: state=${call.state}, number=$number")
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur reconnexion appel: ${e.message}", e)
                }
            }

            if (activeCalls.isNotEmpty()) {
                val number = activeCalls.firstOrNull()
                    ?.details?.handle?.schemeSpecificPart ?: ""
                startForegroundServiceSafe()
                updateForegroundNotificationWithActions(number)
                // launchInCallActivity() supprimé — causait le double écran
            }
        } catch (e: Exception) {
            Log.e(TAG, "reconnectActiveCalls error: ${e.message}", e)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return true
    }

    // ─── Mode conduite ────────────────────────────────────────────────────────

    private fun announceCallerName(call: Call, number: String) {
        val callerName = call.details?.callerDisplayName
            ?.takeIf { it.isNotBlank() }
            ?: run {
                MainViewModel.instance?.buildNumberToNameMap()?.get(
                    PhoneUtils.normalizeNumber(number)
                ) ?: number.ifBlank { "Numéro inconnu" }
            }

        tts?.shutdown()
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.FRENCH
                tts?.speak(
                    "Appel entrant de $callerName",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "driving_caller_announcement"
                )
                Log.i(TAG, "TTS : annonce appelant '$callerName'")
            } else {
                Log.w(TAG, "TTS init échouée — status=$status")
            }
        }
    }

    private fun sendAutoSms(number: String) {
        if (number.isBlank()) return
        val whitelist = MainViewModel.instance?.whitelist?.value  ?: emptySet()
        val favorites = MainViewModel.instance?.favorites?.value  ?: emptyList()

        if (drivingModeManager.isExemptFromAutoSms(number, whitelist, favorites)) {
            Log.i(TAG, "SMS auto non envoyé — $number exempté (favori ou whitelist)")
            return
        }

        try {
            android.telephony.SmsManager.getDefault().sendTextMessage(
                number, null, DrivingModeManager.SMS_AUTO_REPLY, null, null
            )
            Log.i(TAG, "SMS auto envoyé à $number")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur envoi SMS auto", e)
        }
    }

    // ─── Vibration ────────────────────────────────────────────────────────────

    private fun startRingerVibration() {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(VIBRATION_PATTERN, 0)
            }
            Log.d(TAG, "Vibration démarrée")
        } catch (e: Exception) {
            Log.e(TAG, "startRingerVibration error: ${e.message}")
        }
    }

    private fun stopRingerVibration() {
        try {
            vibrator?.cancel()
            Log.d(TAG, "Vibration arrêtée")
        } catch (e: Exception) {
            Log.e(TAG, "stopRingerVibration error: ${e.message}")
        }
    }

    // ─── Utilitaires ──────────────────────────────────────────────────────────

    private fun launchInCallActivity() {
        try {
            startActivity(InCallActivity.getLaunchIntent(this))
            Log.d(TAG, "launchInCallActivity : InCallActivity lancée ✅")
        } catch (e: Exception) {
            Log.e(TAG, "launchInCallActivity error: ${e.message}", e)
        }
    }

    private fun startForegroundServiceSafe() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("PhoneZen")
            .setContentText(getString(R.string.notif_call_ongoing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID_FOREGROUND, notification)
    }

    private fun updateForegroundNotificationWithActions(phoneNumber: String = "", allowFullScreen: Boolean = true) {
        val ringing = CallManager.getCalls().any { it.state == Call.STATE_RINGING }
        val openIntent = InCallActivity.getLaunchIntent(this)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangupPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PhoneZenInCallService::class.java).apply { action = ACTION_HANGUP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(getString(if (ringing) R.string.extra_incoming else R.string.notif_call_ongoing))
            .setContentText(if (phoneNumber.isNotEmpty()) phoneNumber else getString(R.string.notif_phone_call))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(if (ringing && allowFullScreen) openPendingIntent else null, ringing && allowFullScreen)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.extra_hang_up), hangupPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, getString(R.string.notif_open), openPendingIntent)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_FOREGROUND, notification)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        createNotificationChannels()
        val call = CallManager.getCalls().firstOrNull {
            it.state != Call.STATE_DISCONNECTED
        } ?: return
        // Refresh the text without reopening the call screen after a language change.
        updateForegroundNotificationWithActions(
            call.details?.handle?.schemeSpecificPart.orEmpty(),
            allowFullScreen = false
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        fun updateChannel(id: String, nameRes: Int, descriptionRes: Int, silent: Boolean = false) {
            // Reuse existing settings; only refresh translated names and descriptions.
            val channel = nm.getNotificationChannel(id) ?: NotificationChannel(
                id, getString(nameRes), NotificationManager.IMPORTANCE_HIGH
            ).apply { if (silent) setSound(null, null) }
            channel.name = getString(nameRes)
            channel.description = getString(descriptionRes)
            nm.createNotificationChannel(channel)
        }
        updateChannel(CHANNEL_ID_ACTIVE, R.string.notif_active_channel, R.string.notif_active_desc, silent = true)
        updateChannel(CHANNEL_ID_BLOCKED, R.string.notif_blocked_channel, R.string.notif_blocked_desc)
        updateChannel(CHANNEL_ID_VOICEMAIL, R.string.notif_voicemail_channel, R.string.notif_voicemail_desc)
    }

    private fun showBlockedNotification(number: String?, reason: String) {
        val displayNumber = if (number.isNullOrBlank()) "Numéro masqué" else number
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_BLOCKED)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🚫 Appel indésirable bloqué")
            .setContentText(displayNumber)
            .setSubText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(System.currentTimeMillis().toInt(), notification)
    }
}
