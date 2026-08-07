// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
package fr.bonobo.phonezen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import fr.bonobo.phonezen.utils.VoicemailNotificationHelper

class VoicemailSmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VoicemailSmsReceiver"
        const val ACTION_VOICEMAIL_RECEIVED = "fr.bonobo.phonezen.ACTION_VOICEMAIL_RECEIVED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        messages.forEach { message ->
            val body = message.displayMessageBody ?: return@forEach
            Log.d(TAG, "SMS reçu: $body")

            val keywords = listOf(
                "répondeur", "message vocal", "vocale", "messagerie",
                "voicemail", "laissé un message", "appel manqué", "888"
            )

            if (keywords.any { body.contains(it, ignoreCase = true) }) {
                Log.d(TAG, "Détection répondeur — envoi du signal au ViewModel")

                // 1. Notification système
                VoicemailNotificationHelper.showVoicemailNotification(context)

                // 2. Signal interne à l'app
                val updateIntent = Intent(ACTION_VOICEMAIL_RECEIVED).apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }
}