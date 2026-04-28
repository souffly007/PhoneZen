package fr.bonobo.phonezen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import android.widget.Toast
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
        const val CHANNEL_ID      = "phonezen_blocked"
        const val CHANNEL_NAME    = "Appels bloqués"
        const val NOTIF_ID_BASE   = 1000
        const val ACTION_CALLBACK = "fr.bonobo.phonezen.ACTION_CALLBACK"
        const val EXTRA_NUMBER    = "extra_number"
    }

    override fun onCreate() {
        super.onCreate()
        detector = SpamDetector(applicationContext)
        createNotificationChannel()
    }

    // ─────────────────────────────────────────────
    // POINT D'ENTRÉE PRINCIPAL
    // Ordre de priorité :
    //   1. Appel sortant        → autoriser immédiatement
    //   2. Contact connu        → autoriser
    //   3. Liste noire manuelle → bloquer (Room blocked_numbers)
    //   4. Communautaire        → bloquer (SharedPrefs)
    //   5. ARCEP / SpamDetector → bloquer ou autoriser
    // ─────────────────────────────────────────────
    override fun onScreenCall(callDetails: Call.Details) {

        // 1. Appel sortant → toujours autoriser
        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""

        serviceScope.launch {

            // 2. Contact connu → autoriser (lookup avec timeout)
            val contactName = withTimeoutOrNull(1500L) {
                PhoneUtils.lookupContactName(applicationContext, rawNumber)
            }
            if (contactName != null) {
                Log.d(TAG, "Autorisé (contact) : $rawNumber → $contactName")
                respondToCall(callDetails, CallResponse.Builder().build())
                return@launch
            }

            // 3. ✅ Liste noire manuelle (Room blocked_numbers)
            //    On teste les deux formats pour éviter les faux négatifs +33 / 0X
            val normalized = PhoneUtils.normalizeNumber(rawNumber)
            val alt = when {
                normalized.startsWith("+33") -> "0" + normalized.substring(3)
                normalized.startsWith("0")   -> "+33" + normalized.substring(1)
                else                         -> null
            }
            val db = AppDatabase.getDatabase(applicationContext)
            val isManuallyBlocked =
                db.blockedNumberDao().isBlocked(normalized) > 0 ||
                        (alt != null && db.blockedNumberDao().isBlocked(alt) > 0)

            if (isManuallyBlocked) {
                Log.w(TAG, "BLOCAGE LISTE NOIRE MANUELLE : $rawNumber")
                blockCall(
                    callDetails = callDetails,
                    number      = rawNumber,
                    reason      = "Bloqué manuellement",
                    riskLevel   = "MANUAL",
                    notify      = true
                )
                return@launch
            }

            // 4. Liste communautaire (cache SharedPrefs)
            if (detector.isCommunityBlocked(rawNumber)) {
                Log.w(TAG, "BLOCAGE COMMUNAUTAIRE : $rawNumber")
                blockCall(
                    callDetails = callDetails,
                    number      = rawNumber,
                    reason      = "Signalé par la communauté (≥ ${SpamDetector.COMMUNITY_BLOCK_THRESHOLD} signalements)",
                    riskLevel   = "COMMUNITY",
                    notify      = true
                )
                return@launch
            }

            // 5. Analyse ARCEP + profils + DND + horaires
            val result = detector.analyze(
                rawNumber  = rawNumber,
                isContact  = false,
                isFavorite = false
            )

            if (result.isSpam) {
                Log.w(TAG, "BLOCAGE ARCEP : $rawNumber (${result.reason})")
                blockCall(
                    callDetails = callDetails,
                    number      = rawNumber,
                    reason      = result.reason.ifBlank { "Démarchage détecté" },
                    riskLevel   = result.riskLevel.name,
                    notify      = true
                )
            } else {
                Log.d(TAG, "Autorisé : $rawNumber")
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }

    // ─────────────────────────────────────────────
    // BLOCAGE + ENREGISTREMENT HISTORIQUE + NOTIFICATION
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

        // Enregistrement dans l'historique (blocked_calls)
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
                Log.e(TAG, "Erreur enregistrement historique : ${e.message}")
            }
        }

        if (notify) sendBlockNotification(number, reason)
    }

    // ─────────────────────────────────────────────
    // NOTIFICATION avec actions "Rappeler" et "Ne plus bloquer"
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

        // Action "Rappeler"
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPending = PendingIntent.getActivity(
            applicationContext,
            "call_$number".hashCode(),
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action "Ne plus bloquer" → BroadcastReceiver
        val whitelistIntent = Intent(ACTION_CALLBACK).apply {
            setPackage(applicationContext.packageName)
            putExtra(EXTRA_NUMBER, number)
            putExtra("action", "whitelist")
        }
        val whitelistPending = PendingIntent.getBroadcast(
            applicationContext,
            "whitelist_$number".hashCode(),
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
            .addAction(android.R.drawable.ic_menu_call, "📞 Rappeler",        callPending)
            .addAction(android.R.drawable.ic_menu_add,  "🛡 Ne plus bloquer", whitelistPending)
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
//
// À DÉCLARER dans AndroidManifest.xml :
//
// <receiver
//     android:name=".service.BlockedCallActionReceiver"
//     android:exported="false">
//     <intent-filter>
//         <action android:name="fr.bonobo.phonezen.ACTION_CALLBACK"/>
//     </intent-filter>
// </receiver>
// ─────────────────────────────────────────────
class BlockedCallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_WHITELIST_UPDATED = "fr.bonobo.phonezen.WHITELIST_UPDATED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(PhoneZenCallScreeningService.EXTRA_NUMBER) ?: return
        val action = intent.getStringExtra("action") ?: return

        if (action == "whitelist") {
            val normalized = PhoneUtils.normalizeNumber(number)
            val detector   = SpamDetector(context)

            // Ajouter à la whitelist
            detector.addToWhitelist(normalized)

            // Retirer du cache communautaire pour éviter un re-blocage
            detector.removeCommunityBlocked(normalized)

            // ✅ Retirer aussi de la liste noire Room (les deux formats)
            val alt = when {
                normalized.startsWith("+33") -> "0" + normalized.substring(3)
                normalized.startsWith("0")   -> "+33" + normalized.substring(1)
                else                         -> null
            }
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.blockedNumberDao().deleteByNumber(normalized)
                    alt?.let { db.blockedNumberDao().deleteByNumber(it) }
                } catch (e: Exception) {
                    Log.e("BlockedCallActionReceiver", "Erreur suppression liste noire : ${e.message}")
                }
            }

            // Annuler la notification correspondante
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(PhoneZenCallScreeningService.NOTIF_ID_BASE + number.hashCode())

            // Notifier le ViewModel pour recharger la whitelist
            val updateIntent = Intent(ACTION_WHITELIST_UPDATED).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(updateIntent)

            // Toast de confirmation
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "✅ $normalized ajouté à la liste blanche",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}