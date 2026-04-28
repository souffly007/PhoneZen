package fr.bonobo.phonezen.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bonobo.phonezen.service.CallManager

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        Log.d("CallActionReceiver", "Action reçue : $action")

        if (action == "ACTION_HANGUP") {
            try {
                CallManager.hangUp()
                Log.d("CallActionReceiver", "Appel raccroché via notification ✅")
            } catch (e: Exception) {
                Log.e("CallActionReceiver", "Erreur lors du raccrochage : ${e.message}")
            }
        }
    }
}