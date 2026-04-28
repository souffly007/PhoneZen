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
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.ui.screens.InCallActivity
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import fr.bonobo.phonezen.utils.VoicemailNotificationHelper

class PhoneZenInCallService : InCallService() {

    companion object {
        private const val CHANNEL_ID_BLOCKED  = "blocked_calls"
        private const val CHANNEL_ID_ACTIVE   = "phone_zen_calls"
        private const val CHANNEL_ID_VOICEMAIL = "voicemail"
        private const val NOTIF_ID_FOREGROUND = 1001
        private const val ACTION_HANGUP       = "fr.bonobo.phonezen.ACTION_HANGUP"
        private const val TAG                 = "PhoneZenInCall"

        private val VIBRATION_PATTERN = longArrayOf(0, 800, 400, 800, 400)
    }

    private var vibrator: Vibrator? = null
    private var voicemailListener: VoicemailListener? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.onStateChanged(call, state)
            when (state) {
                Call.STATE_ACTIVE,
                Call.STATE_DISCONNECTED,
                Call.STATE_DISCONNECTING -> stopRingerVibration()
            }
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                // Ne stopper le foreground que s'il n'y a plus aucun appel
                if (CallManager.getCallCount() == 0) {
                    stopForeground(true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        // Initialisation du VoicemailListener
        try {
            VoicemailNotificationHelper.createChannel(this)
            voicemailListener = VoicemailListener(this)
            voicemailListener?.register()
            Log.d(TAG, "VoicemailListener initialisé avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation du VoicemailListener: ${e.message}")
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
            Log.e(TAG, "Erreur lors de la désinscription du VoicemailListener: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANGUP) {
            Log.d(TAG, "Raccrochage depuis la notification")
            try { CallManager.hangUp() } catch (e: Exception) {
                Log.e(TAG, "Erreur lors du raccrochage: ${e.message}")
            }
            stopRingerVibration()
            stopForeground(true)
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded : état=${call.state}")

        // ── ÉTAPE 1 : ENREGISTREMENT PRIORITAIRE ──
        call.registerCallback(callCallback)
        CallManager.setInCallService(this)
        CallManager.setCall(call)

        // ── ÉTAPE 2 : SÉCURITÉ ANDROID ──
        startForegroundServiceSafe()

        // ── ÉTAPE 3 : ANALYSE SPAM (appels entrants uniquement) ──
        val number = call.details.handle?.schemeSpecificPart ?: ""

        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            val detector = SpamDetector(applicationContext)
            val (isContact, isFavorite) = PhoneUtils.resolveContactInfo(applicationContext, number)
            Log.d(TAG, "resolveContactInfo: number=$number isContact=$isContact isFavorite=$isFavorite")

            val result = detector.analyze(
                rawNumber  = number,
                isContact  = isContact,
                isFavorite = isFavorite
            )

            if (result.isSpam) {
                Log.w(TAG, "Appel spam bloqué : $number — ${result.reason}")
                try { call.disconnect() } catch (e: Exception) {
                    Log.e(TAG, "Erreur disconnect spam : ${e.message}")
                }
                showBlockedNotification(number, result.reason ?: "Spam détecté")
                // Retire cet appel spam de la map proprement
                CallManager.onStateChanged(call, Call.STATE_DISCONNECTED)
                if (CallManager.getCallCount() == 0) {
                    CallManager.clear()
                    stopForeground(true)
                }
                return
            }
        }

        // ── ÉTAPE 4 : LANCEMENT DE L'INTERFACE ──
        launchInCallActivity()

        // ── ÉTAPE 5 : VIBRATION SONNERIE (entrant uniquement) ──
        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            startRingerVibration()
        }

        // ── ÉTAPE 6 : NOTIFICATION AVEC BOUTONS ──
        updateForegroundNotificationWithActions(number)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")
        call.unregisterCallback(callCallback)
        stopRingerVibration()

        // Signale la déconnexion à CallManager (retire l'appel de la map)
        CallManager.onStateChanged(call, Call.STATE_DISCONNECTED)

        // clear() + nettoyage seulement si plus aucun appel
        if (CallManager.getCallCount() == 0) {
            CallManager.clear()
            CallManager.setInCallService(null)
            stopForeground(true)
        }
        // Si des appels restent (call-waiting), on laisse tout en place :
        // inCallService reste actif, la map pointe sur l'appel restant.
    }

    override fun onCallAudioStateChanged(audioState: android.telecom.CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
        Log.d(TAG, "Audio state changed to: $audioState")
    }

    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        Log.d(TAG, "onRebind : reconnexion après kill — réhydratation CallManager")
        reconnectActiveCalls()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return true
    }

    private fun reconnectActiveCalls() {
        CallManager.setInCallService(this)

        val activeCalls = calls.filter { call ->
            call.state != Call.STATE_DISCONNECTED &&
                    call.state != Call.STATE_DISCONNECTING
        }

        if (activeCalls.isEmpty()) {
            Log.d(TAG, "reconnectActiveCalls : aucun appel actif")
            return
        }

        activeCalls.forEach { call ->
            Log.d(TAG, "reconnectActiveCalls : appel récupéré, état=${call.state}")
            call.registerCallback(callCallback)
            CallManager.setCall(call)
        }

        launchInCallActivity()
    }

    // -----------------------------------------------------------------------
    // Vibration
    // -----------------------------------------------------------------------

    private fun startRingerVibration() {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(VIBRATION_PATTERN, 0)
            }
            Log.d(TAG, "startRingerVibration: vibration démarrée")
        } catch (e: Exception) {
            Log.e(TAG, "startRingerVibration error: ${e.message}")
        }
    }

    private fun stopRingerVibration() {
        try {
            vibrator?.cancel()
            Log.d(TAG, "stopRingerVibration: vibration arrêtée")
        } catch (e: Exception) {
            Log.e(TAG, "stopRingerVibration error: ${e.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Utilitaires
    // -----------------------------------------------------------------------

    private fun launchInCallActivity() {
        startActivity(Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        })
    }

    private fun startForegroundServiceSafe() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("PhoneZen")
            .setContentText("Appel en cours...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID_FOREGROUND, notification)
    }

    private fun updateForegroundNotificationWithActions(phoneNumber: String = "") {
        val openAppIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hangupPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, PhoneZenInCallService::class.java).apply { action = ACTION_HANGUP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ACTIVE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("📞 Appel en cours")
            .setContentText(if (phoneNumber.isNotEmpty()) phoneNumber else "Appel téléphonique")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Raccrocher", hangupPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Ouvrir", openAppPendingIntent)
            .build()

        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID_FOREGROUND, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Canal pour les appels actifs
            if (nm.getNotificationChannel(CHANNEL_ID_ACTIVE) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID_ACTIVE, "Appels Actifs",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Affiche que PhoneZen gère l'appel actuel" })
            }

            // Canal pour les appels bloqués (spam)
            if (nm.getNotificationChannel(CHANNEL_ID_BLOCKED) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID_BLOCKED, "Sécurité (Spam)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notifications des numéros bloqués par PhoneZen" })
            }

            // Canal pour les notifications de messagerie vocale
            if (nm.getNotificationChannel(CHANNEL_ID_VOICEMAIL) == null) {
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_ID_VOICEMAIL, "Messagerie vocale",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Notifications pour la messagerie vocale" })
            }
        }
    }

    private fun showBlockedNotification(number: String?, reason: String) {
        val displayNumber = if (number.isNullOrBlank()) "Numéro masqué" else number
        val notification  = NotificationCompat.Builder(this, CHANNEL_ID_BLOCKED)
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