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
import fr.bonobo.phonezen.utils.CountryRulesManager
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.LinkedHashMap

class PhoneZenCallScreeningService : CallScreeningService() {

    private val TAG          = "PhoneZenService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var detector: SpamDetector
    private lateinit var hospitalWhitelistManager: HospitalWhitelistManager
    
    // Cache pour les résultats récents (5 dernières minutes) - max 100 entrées
    private val analysisCache = object : LinkedHashMap<String, Pair<fr.bonobo.phonezen.data.model.SpamResult, Long>>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<fr.bonobo.phonezen.data.model.SpamResult, Long>>): Boolean {
            return size > 100 || (System.currentTimeMillis() - eldest.value.second > CACHE_EXPIRY_MS)
        }
    }
    private val cacheLock = Any()

    companion object {
        const val CHANNEL_ID      = "phonezen_blocked"
        const val CHANNEL_NAME    = "Appels bloqués"
        const val NOTIF_ID_BASE   = 1000
        const val ACTION_CALLBACK = "fr.bonobo.phonezen.ACTION_CALLBACK"
        const val EXTRA_NUMBER    = "extra_number"
        private const val PREF_FILE               = "phonezen_prefs"
        private const val PREF_HOSPITAL_WHITELIST = "hospital_whitelist_enabled"
        private const val ANALYSIS_TIMEOUT_MS      = 5000L  // Augmenté de 3500 à 5000
        private const val CACHE_EXPIRY_MS         = 5 * 60 * 1000L  // 5 minutes
    }

    override fun onCreate() {
        super.onCreate()
        detector = SpamDetector(applicationContext)
        val db   = AppDatabase.getDatabase(applicationContext)
        hospitalWhitelistManager = (application as fr.bonobo.phonezen.PhoneZenApp).hospitalWhitelistManager
        createNotificationChannel()
    }

    private fun isHospitalWhitelistEnabled(): Boolean =
        applicationContext
            .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(PREF_HOSPITAL_WHITELIST, true)
    
    /**
     * Récupère un résultat depuis le cache si valide
     */
    private fun getCachedResult(key: String): fr.bonobo.phonezen.data.model.SpamResult? {
        synchronized(cacheLock) {
            val cached = analysisCache[key] ?: return null
            if (System.currentTimeMillis() - cached.second < CACHE_EXPIRY_MS) {
                return cached.first
            }
            analysisCache.remove(key)
            return null
        }
    }
    
    /**
     * Met en cache un résultat
     */
    private fun cacheResult(key: String, result: fr.bonobo.phonezen.data.model.SpamResult) {
        synchronized(cacheLock) {
            analysisCache[key] = Pair(result, System.currentTimeMillis())
        }
    }

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

        if (Build.VERSION.SDK_INT >= 29 && callDetails.callDirection == Call.Details.DIRECTION_OUTGOING) return
        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        val region = fr.bonobo.phonezen.utils.CountrySettings(applicationContext).forAccount(callDetails.accountHandle)
        val normalized = PhoneUtils.normalizeNumber(rawNumber, region)
        val cacheKey = "$region:$normalized"
        
        // Vérifier le cache d'abord
        val cachedResult = getCachedResult(cacheKey)
        if (cachedResult != null) {
            Log.d(TAG, "Résultat depuis cache pour $cacheKey")
            if (cachedResult.isSpam) {
                blockCall(callDetails, rawNumber, cachedResult.reason, cachedResult.riskLevel.name, false)
            } else {
                respondToCall(callDetails, CallResponse.Builder().build())
            }
            return
        }
        
        val responded = java.util.concurrent.atomic.AtomicBoolean(false)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val deadline = Runnable {
            if (responded.compareAndSet(false, true)) {
                Log.w(TAG, "Timeout atteint pour $rawNumber, autorisation par défaut")
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
        handler.postDelayed(deadline, ANALYSIS_TIMEOUT_MS)
        
        serviceScope.launch {
            val result = try {
                withTimeoutOrNull(ANALYSIS_TIMEOUT_MS) {
                    val countryCode = region?.take(2)?.uppercase() ?: "FR"
                    val countryRules = CountryRulesManager.loadRules(countryCode, applicationContext)
                    
                    // Vérifier les urgences par pays EN PREMIER (priorité absolue)
                    if (countryRules.isEmergency(rawNumber)) {
                        return@withTimeoutOrNull fr.bonobo.phonezen.data.model.SpamResult(
                            false, 
                            reason = "Numéro d'urgence ($countryCode)"
                        )
                    }
                    
                    // Vérifier les urgences legacy (compatibilité)
                    if (fr.bonobo.phonezen.utils.InternationalNumbers.isEmergency(rawNumber, region)) {
                        return@withTimeoutOrNull fr.bonobo.phonezen.data.model.SpamResult(false, reason = "Numéro d'urgence")
                    }
                    
                    // Vérifier la liste blanche
                    if (detector.isWhitelisted(rawNumber, region)) {
                        return@withTimeoutOrNull fr.bonobo.phonezen.data.model.SpamResult(false, reason = "Liste blanche")
                    }
                    
                    val frenchLocal = fr.bonobo.phonezen.utils.InternationalNumbers.frenchLocal(rawNumber, region)
                    
                    // Vérifier les hôpitaux (uniquement pour la France)
                    if (countryCode == "FR" && frenchLocal != null && isHospitalWhitelistEnabled() && hospitalWhitelistManager.isHospitalNumber(frenchLocal)) {
                        return@withTimeoutOrNull fr.bonobo.phonezen.data.model.SpamResult(false, reason = "Établissement de santé")
                    }
                    
                    // Vérifier le blocage manuel
                    val db = AppDatabase.getDatabase(applicationContext)
                    val blocked = db.blockedNumberDao().getAllOnce().any {
                        fr.bonobo.phonezen.utils.InternationalNumbers.matchesStored(normalized, it.number, region)
                    }
                    if (blocked) {
                        return@withTimeoutOrNull fr.bonobo.phonezen.data.model.SpamResult(true, reason = "Bloqué manuellement", riskLevel = fr.bonobo.phonezen.data.model.RiskLevel.HIGH)
                    }
                    
                    // Analyse complète (inclut maintenant les règles par pays)
                    val contact = PhoneUtils.resolveContactInfo(applicationContext, rawNumber, region)
                    detector.analyze(rawNumber, contact.first, contact.second, region)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Filtrage indisponible pour $rawNumber, appel autorisé", e)
                null
            }
            
            if (responded.compareAndSet(false, true)) {
                handler.removeCallbacks(deadline)
                
                val finalResult = result ?: fr.bonobo.phonezen.data.model.SpamResult(
                    isSpam = false,
                    reason = "Erreur d'analyse, autorisé par défaut"
                )
                
                // Mettre en cache le résultat
                cacheResult(cacheKey, finalResult)
                
                if (finalResult.isSpam) {
                    blockCall(callDetails, rawNumber, finalResult.reason, finalResult.riskLevel.name, true)
                } else {
                    respondToCall(callDetails, CallResponse.Builder().build())
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    private fun blockCall(
        callDetails: Call.Details,
        number     : String,
        reason     : String,
        riskLevel  : String,
        notify     : Boolean
    ) {
        val storedNumber = PhoneUtils.normalizeNumber(number,
            fr.bonobo.phonezen.utils.CountrySettings(applicationContext).forAccount(callDetails.accountHandle))
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .apply { if (Build.VERSION.SDK_INT >= 29) setSilenceCall(true) }
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        )

        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.blockedCallDao().insert(
                    BlockedCall(number = storedNumber, reason = reason, riskLevel = riskLevel)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Erreur enregistrement historique : ${e.message}")
            }
        }

        if (notify) sendBlockNotification(storedNumber, reason)
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
