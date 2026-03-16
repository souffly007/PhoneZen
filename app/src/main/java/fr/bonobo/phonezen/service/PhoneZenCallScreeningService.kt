package fr.bonobo.phonezen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.MainActivity
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.local.BlockedCall
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneZenCallScreeningService : CallScreeningService() {

    private val TAG          = "PhoneZenService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: SpamDetector

    companion object {
        const val CHANNEL_ID      = "phonezen_blocked"
        const val CHANNEL_NAME    = "Appels bloqués"
        const val NOTIF_ID_BASE   = 1000
    }

    override fun onCreate() {
        super.onCreate()
        detector = SpamDetector(applicationContext)
        createNotificationChannel()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. Autoriser immédiatement les appels sortants
        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = callDetails.handle?.schemeSpecificPart ?: ""

        // 2. Contact connu → jamais bloquer
        val contactName = PhoneUtils.lookupContactName(applicationContext, number)
        if (contactName != null) {
            Log.d(TAG, "Appel autorisé : $number est un contact ($contactName)")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 3. Vérification communautaire (cache local SharedPreferences)
        if (detector.isCommunityBlocked(number)) {
            Log.w(TAG, "BLOCAGE COMMUNAUTAIRE : $number")
            blockCall(
                callDetails = callDetails,
                number      = number,
                reason      = "Signalé par la communauté (≥ ${SpamDetector.COMMUNITY_BLOCK_THRESHOLD} signalements)",
                riskLevel   = "COMMUNITY",
                notify      = true
            )
            return
        }

        // 4. Analyse ARCEP / SpamDetector classique
        val result = detector.analyze(number)

        if (result.isSpam) {
            Log.w(TAG, "BLOCAGE ARCEP : $number (${result.reason})")
            blockCall(
                callDetails = callDetails,
                number      = number,
                reason      = result.reason ?: "Démarchage détecté",
                riskLevel   = result.riskLevel.name,
                notify      = false   // pas de notif pour les blocages ARCEP classiques
            )
        } else {
            Log.d(TAG, "Appel autorisé : $number")
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }

    // ─────────────────────────────────────────────
    // BLOCAGE + ENREGISTREMENT + NOTIFICATION
    // ─────────────────────────────────────────────
    private fun blockCall(
        callDetails: Call.Details,
        number     : String,
        reason     : String,
        riskLevel  : String,
        notify     : Boolean
    ) {
        // Réponse de blocage
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        )

        // Enregistrement asynchrone dans Room
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.blockedCallDao().insert(
                    BlockedCall(
                        number    = number,
                        reason    = reason,
                        riskLevel = riskLevel
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur enregistrement BDD : ${e.message}")
            }
        }

        // Notification uniquement pour les blocages communautaires
        if (notify) {
            sendBlockNotification(number, reason)
        }
    }

    // ─────────────────────────────────────────────
    // NOTIFICATION
    // ─────────────────────────────────────────────
    private fun sendBlockNotification(number: String, reason: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("🚫 Appel bloqué — Communauté")
            .setContentText("$number · $reason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Le numéro $number a été automatiquement bloqué.\n$reason")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_BASE + number.hashCode(), notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour les appels bloqués par la communauté PhoneZen"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}