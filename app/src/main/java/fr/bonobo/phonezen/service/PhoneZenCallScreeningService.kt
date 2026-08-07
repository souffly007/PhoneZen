// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.MainActivity
import fr.bonobo.phonezen.blocking.HospitalWhitelistManager
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.local.BlockedCall
import fr.bonobo.phonezen.data.repository.HealthcareRepository
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
    private lateinit var hospitalWhitelistManager: HospitalWhitelistManager

    companion object {
        const val CHANNEL_ID      = "phonezen_blocked"
        const val CHANNEL_NAME    = "Appels bloqués"
        const val NOTIF_ID_BASE   = 1000
        const val ACTION_CALLBACK = "fr.bonobo.phonezen.ACTION_CALLBACK"
        const val EXTRA_NUMBER    = "extra_number"
        private const val PREF_FILE               = "phonezen_prefs"
        private const val PREF_HOSPITAL_WHITELIST = "hospital_whitelist_enabled"
    }

    override fun onCreate() {
        super.onCreate()
        detector = SpamDetector(applicationContext)
        val db   = AppDatabase.getDatabase(applicationContext)
        hospitalWhitelistManager = HospitalWhitelistManager(
            HealthcareRepository(applicationContext, db.healthcareWhitelistDao())
        )
        createNotificationChannel()
    }

    private fun isHospitalWhitelistEnabled(): Boolean =
        applicationContext
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_HOSPITAL_WHITELIST, true)

    private fun handleDrivingModeReply(number: String) {
        val manager = DrivingModeManager(applicationContext)
        if (!manager.isDriving.value) return
        try {
            val smsManager = android.telephony.SmsManager.getDefault()
            smsManager.sendTextMessage(
                number, null,
                DrivingModeManager.SMS_AUTO_REPLY,
                null, null
            )
            Log.i("DrivingMode", "SMS auto envoyé à $number")
        } catch (e: Exception) {
            Log.e("DrivingMode", "Erreur envoi SMS auto", e)
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {

        val earlyNumber = callDetails.handle?.schemeSpecificPart ?: ""
        if (earlyNumber.isNotBlank()
            && isHospitalWhitelistEnabled()
            && hospitalWhitelistManager.isHospitalNumber(earlyNumber)
        ) {
            val name = hospitalWhitelistManager.getHospitalName(earlyNumber)
                ?: "Établissement de santé"
            Log.i(TAG, "🏥 Appel hôpital autorisé : $earlyNumber → $name")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        if (callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = earlyNumber

        serviceScope.launch {

            val contactName = withTimeoutOrNull(1500L) {
                PhoneUtils.lookupContactName(applicationContext, rawNumber)
            }
            if (contactName != null) {
                Log.d(TAG, "Autorisé (contact) : $rawNumber → $contactName")
                respondToCall(callDetails, CallResponse.Builder().build())
                return@launch
            }

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
                blockCall(callDetails, rawNumber, "Bloqué manuellement", "MANUAL", true)
                return@launch
            }

            if (detector.isCommunityBlocked(rawNumber)) {
                Log.w(TAG, "BLOCAGE COMMUNAUTAIRE : $rawNumber")
                blockCall(
                    callDetails, rawNumber,
                    "Signalé par la communauté (≥ ${SpamDetector.COMMUNITY_BLOCK_THRESHOLD} signalements)",
                    "COMMUNITY", true
                )
                return@launch
            }

            val result = detector.analyze(
                rawNumber  = rawNumber,
                isContact  = false,
                isFavorite = false
            )

            if (result.isSpam) {
                Log.w(TAG, "BLOCAGE ARCEP : $rawNumber (${result.reason})")
                blockCall(
                    callDetails, rawNumber,
                    result.reason.ifBlank { "Démarchage détecté" },
                    result.riskLevel.name, true
                )
            } else {
                Log.d(TAG, "Autorisé : $rawNumber")
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }

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

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.blockedCallDao().insert(
                    BlockedCall(number = number, reason = reason, riskLevel = riskLevel)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur enregistrement historique : ${e.message}")
            }
        }

        if (notify) sendBlockNotification(number, reason)
    }

    private fun sendBlockNotification(number: String, reason: String) {
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            applicationContext, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val callPending = PendingIntent.getActivity(
            applicationContext,
            "call_$number".hashCode(),
            callIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
                CHANNEL_ID, CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour les appels bloqués par PhoneZen"
            }
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}