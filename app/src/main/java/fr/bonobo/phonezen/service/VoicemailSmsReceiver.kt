package fr.bonobo.phonezen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import fr.bonobo.phonezen.utils.VoicemailNotificationHelper

class VoicemailSmsReceiver : BroadcastReceiver() {

    private val TAG = "VoicemailSmsReceiver"

    // Action personnalisée pour communiquer avec le ViewModel
    companion object {
        const val ACTION_VOICEMAIL_RECEIVED = "fr.bonobo.phonezen.ACTION_VOICEMAIL_RECEIVED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages?.forEach { message ->
                val body = message.displayMessageBody ?: return@forEach
                Log.d(TAG, "📱 SMS reçu: $body")

                val keywords = listOf(
                    "répondeur", "message vocal", "vocale", "messagerie",
                    "voicemail", "laissé un message", "appel manqué", "888"
                )

                if (keywords.any { body.contains(it, ignoreCase = true) }) {
                    Log.d(TAG, "✅ Détection répondeur ! Envoi du signal au ViewModel")

                    // 1. Afficher la notification système
                    VoicemailNotificationHelper.showVoicemailNotification(context)

                    // 2. Envoyer le signal interne à l'application
                    val updateIntent = Intent(ACTION_VOICEMAIL_RECEIVED).apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(updateIntent)
                }
            }
        }
    }
}