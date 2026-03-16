// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.
package fr.bonobo.phonezen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.ui.screens.InCallActivity
import fr.bonobo.phonezen.utils.SpamDetector

class PhoneZenInCallService : InCallService() {

    companion object {
        private const val CHANNEL_ID   = "blocked_calls"
        private const val CHANNEL_NAME = "Appels bloqués"
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.onStateChanged(call, state)
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallManager.onStateChanged(call, call.state)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.setInCallService(this)

        if (call.details.callDirection == Call.Details.DIRECTION_INCOMING) {
            val number   = call.details.handle?.schemeSpecificPart
            val detector = SpamDetector(applicationContext)
            val result   = detector.analyze(number)

            if (result.isSpam) {
                call.reject(false, null)
                showBlockedNotification(number, result.reason)
                return
            }
        }

        call.registerCallback(callCallback)
        CallManager.setCall(call)
        CallManager.onAudioStateChanged(callAudioState)

        startActivity(
            Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallManager.clear()
        CallManager.setInCallService(null)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    private fun showBlockedNotification(number: String?, reason: String) {
        createChannel()
        val displayNumber = if (number.isNullOrBlank()) "Numéro privé/masqué" else number
        val notifId       = System.currentTimeMillis().toInt()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("🚫 Appel bloqué")
            .setContentText(displayNumber)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("📵 $displayNumber\n⚠️ $reason")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Force recréation du canal pour effacer tout ancien canal corrompu
        nm.deleteNotificationChannel(CHANNEL_ID)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description    = "Notifications des appels bloqués par PhoneZen"
                enableVibration(true)
            }
        )
    }
}