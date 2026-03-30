package fr.bonobo.phonezen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.withTimeoutOrNull

class PhoneZenCallScreeningService : CallScreeningService() {

    private val TAG          = "PhoneZenService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: SpamDetector

    companion object {
        const val CHANNEL_ID        = "phonezen_blocked"
        const val CHANNEL_NAME      = "Appels bloqués"
        const val NOTIF_ID_BASE     = 1000
        const val ACTION_CALLBACK   = "fr.bonobo.phonezen.ACTION_CALLBACK"
        const val EXTRA_NUMBER      = "extra_number"
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

        // Tout le traitement dans une coroutine IO pour éviter le blocage du thread principal
        serviceScope.launch {

            // 2. Contact connu → jamais bloquer
            // Timeout de sécurité : si le lookup prend trop longtemps, on laisse passer
            val contactName = withTimeoutOrNull(1500L) {
                PhoneUtils.lookupContactName(applicationContext, number)
            }

            if (contactName != null) {
                Log.d(TAG, "Appel autorisé : $number est un contact ($contactName)")
                respondToCall(callDetails, CallResponse.Builder().build())
                return@launch
            }

            Log.d(TAG, "Lookup contact pour $number : non trouvé ou timeout, poursuite de l'analyse")

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
                return@launch
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
                    notify      = true   // notification avec bouton Rappeler pour tous les blocages
                )
            } else {
                Log.d(TAG, "Appel autorisé : $number")
                respondToCall(callDetails, CallResponse.Builder().build())
            }
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

        if (notify) {
            sendBlockNotification(number, reason)
        }
    }

    // ─────────────────────────────────────────────
    // NOTIFICATION avec action "Rappeler"
    // ─────────────────────────────────────────────
    private fun sendBlockNotification(number: String, reason: String) {
        // Intent principal → ouvre MainActivity
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            applicationContext, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action "Rappeler" → lance un appel téléphonique direct
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPending = PendingIntent.getActivity(
            applicationContext,
            number.hashCode(),
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action "Whitelist" → BroadcastReceiver pour ajouter à la liste blanche
        val whitelistIntent = Intent(ACTION_CALLBACK).apply {
            setPackage(applicationContext.packageName)
            putExtra(EXTRA_NUMBER, number)
            putExtra("action", "whitelist")
        }
        val whitelistPending = PendingIntent.getBroadcast(
            applicationContext,
            number.hashCode() + 1,
            whitelistIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("🚫 Appel bloqué")
            .setContentText("$number · $reason")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Le numéro $number a été automatiquement bloqué.\n$reason")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .setAutoCancel(true)
            // Action 1 : Rappeler
            .addAction(
                android.R.drawable.ic_menu_call,
                "📞 Rappeler",
                callPending
            )
            // Action 2 : Ajouter à la liste blanche
            .addAction(
                android.R.drawable.ic_menu_add,
                "🛡 Ne plus bloquer",
                whitelistPending
            )
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
                description = "Notifications pour les appels bloqués par PhoneZen"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}

// ─────────────────────────────────────────────
// BroadcastReceiver — action "Ne plus bloquer" depuis la notification
// À déclarer dans AndroidManifest.xml :
// <receiver android:name=".service.BlockedCallActionReceiver"
//           android:exported="false">
//     <intent-filter>
//         <action android:name="fr.bonobo.phonezen.ACTION_CALLBACK"/>
//     </intent-filter>
// </receiver>
// ─────────────────────────────────────────────
class BlockedCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(PhoneZenCallScreeningService.EXTRA_NUMBER) ?: return
        val action = intent.getStringExtra("action") ?: return
        if (action == "whitelist") {
            val detector = SpamDetector(context)
            detector.addToWhitelist(PhoneUtils.normalizeNumber(number))
        }
    }
}