package fr.bonobo.phonezen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class PhoneZenApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // On prépare le terrain pour Android 8+ dès le lancement
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        // Les "Channels" sont obligatoires à partir d'Android 8 (Oreo - API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            // 1. Canal pour les appels actifs (Priorité haute pour l'InCallService)
            val callChannel = NotificationChannel(
                "phone_zen_calls",
                "Appels actifs",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Affiché pendant que vous êtes au téléphone avec PhoneZen"
                // On évite de faire vibrer le téléphone pour la notification de service
                enableVibration(false)
            }

            // 2. Canal pour les blocages/protections (Optionnel, si tu veux notifier un blocage)
            val protectionChannel = NotificationChannel(
                "phone_zen_protection",
                "Protection Anti-Spam",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Notifications de filtrage et de blocage"
            }

            // On enregistre les canaux auprès du système
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(callChannel)
            manager.createNotificationChannel(protectionChannel)
        }
    }
}
