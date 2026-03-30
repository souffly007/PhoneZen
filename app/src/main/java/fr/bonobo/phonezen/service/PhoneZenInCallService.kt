// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.ui.screens.InCallActivity
import fr.bonobo.phonezen.utils.SpamDetector

class PhoneZenInCallService : InCallService() {

    companion object {
        private const val CHANNEL_ID_BLOCKED = "blocked_calls"
        private const val CHANNEL_ID_ACTIVE  = "phone_zen_calls"
        private const val NOTIF_ID_FOREGROUND = 1001
        private const val TAG = "PhoneZenInCall"
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.onStateChanged(call, state)
            // Si l'appel est terminé, on arrête le service de premier plan
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                stopForeground(true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)

        // ── ÉTAPE 1 : ENREGISTREMENT PRIORITAIRE ──
        // On enregistre le call IMMEDIATEMENT pour que l'Activity le trouve dès son lancement
        call.registerCallback(callCallback)
        CallManager.setCall(call)
        CallManager.setInCallService(this)

        // ── ÉTAPE 2 : SÉCURITÉ ANDROID ──
        startForegroundServiceSafe()

        // ── ÉTAPE 3 : ANALYSE DU NUMÉRO ──
        val number = call.details.handle?.schemeSpecificPart ?: ""

        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            val detector = SpamDetector(applicationContext)
            val result = detector.analyze(number)

            if (result.isSpam) {
                try {
                    call.disconnect()
                } catch (e: Exception) {
                    Log.e(TAG, "Erreur lors de la déconnexion spam : ${e.message}")
                }

                // On nettoie le Manager car l'appel est rejeté
                showBlockedNotification(number, result.reason)
                CallManager.clear()
                stopForeground(true)
                return
            }
            // NOTE : Pas de sonnerie manuelle ici.
            // En laissant faire le système, Android gère la sonnerie spécifique du contact.
        }

        // ── ÉTAPE 4 : LANCEMENT DE L'INTERFACE ──
        // L'Activity va maintenant trouver un CallManager déjà rempli (plus de rectangle vide)
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallManager.clear()
        CallManager.setInCallService(null)
        stopForeground(true)
    }

    // ─────────────────────────────────────────────
    // GESTION NOTIFICATIONS & CANAUX
    // ─────────────────────────────────────────────

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

    private fun createNotificationChannels() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID_ACTIVE) == null) {
                val activeChannel = NotificationChannel(
                    CHANNEL_ID_ACTIVE,
                    "Appels Actifs",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Affiche que PhoneZen gère l'appel actuel"
                }
                nm.createNotificationChannel(activeChannel)
            }
            if (nm.getNotificationChannel(CHANNEL_ID_BLOCKED) == null) {
                val blockedChannel = NotificationChannel(
                    CHANNEL_ID_BLOCKED,
                    "Sécurité (Spam)",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications des numéros bloqués par PhoneZen"
                }
                nm.createNotificationChannel(blockedChannel)
            }
        }
    }

    private fun showBlockedNotification(number: String?, reason: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val displayNumber = if (number.isNullOrBlank()) "Numéro masqué" else number
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_BLOCKED)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🚫 Appel indésirable bloqué")
            .setContentText(displayNumber)
            .setSubText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}