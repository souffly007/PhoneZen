// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import fr.bonobo.phonezen.blocking.HospitalWhitelistManager
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.repository.HealthcareRepository
import fr.bonobo.phonezen.service.HealthcareWhitelistSyncWorker
import fr.bonobo.phonezen.utils.CrashHandler

class PhoneZenApp : Application() {

    // ── Singletons accessibles depuis toute l'app ─────────────────────────
    val db by lazy { AppDatabase.getDatabase(this) }

    val healthcareRepository by lazy {
        HealthcareRepository(this, db.healthcareWhitelistDao())
    }

    val hospitalWhitelistManager by lazy {
        HospitalWhitelistManager(healthcareRepository)
    }

    // ─────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        fr.bonobo.phonezen.utils.PhoneUtils.regionProvider = {
            fr.bonobo.phonezen.utils.CountrySettings(this).defaultRegion()
        }

        // ── Crash reporter — doit être installé en premier ────────────────
        CrashHandler.install(this)
        
        // Enregistrer l'instance pour les ViewModels qui en ont besoin
        registerInstance()

        // Pré-charger les règles pour les pays courants (améliore les performances)
        preloadCommonCountryRules()

        hospitalWhitelistManager
        createNotificationChannels()

        // Planifie la sync Supabase toutes les 24h (idempotent, safe à appeler à chaque démarrage)
        HealthcareWhitelistSyncWorker.schedule(this)
    }
    
    /**
     * Pré-charge les règles pour les pays les plus courants.
     * Cela évite un chargement à la demande lors du premier appel.
     */
    private fun preloadCommonCountryRules() {
        Thread {
            try {
                val commonCountries = listOf("FR", "DE", "BE", "ES", "IT", "CH", "GB", "NL", "PT", "US", "CA")
                commonCountries.forEach { country ->
                    fr.bonobo.phonezen.utils.CountryRulesManager.loadRules(country, this)
                }
                Log.d("PhoneZenApp", "Règles pré-chargées pour ${commonCountries.size} pays")
            } catch (e: Exception) {
                Log.e("PhoneZenApp", "Erreur pré-chargement règles par pays: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Enregistre cette instance pour un accès global (utilisé par certains ViewModels).
     * 
     * ATTENTION : À utiliser avec prudence. Préférer l'injection de dépendances.
     */
    private fun registerInstance() {
        instance = this
    }
    
    companion object {
        @Volatile
        private var instance: PhoneZenApp? = null
        
        /**
         * Obtient l'instance singleton de l'application.
         * 
         * @return L'instance de PhoneZenApp
         * @throws IllegalStateException si l'application n'a pas encore été initialisée
         */
        fun getInstance(): PhoneZenApp {
            return instance ?: error("PhoneZenApp not yet initialized")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val callChannel = NotificationChannel(
                "phone_zen_calls",
                "Appels actifs",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Affiché pendant que vous êtes au téléphone avec PhoneZen"
                enableVibration(false)
            }

            val protectionChannel = NotificationChannel(
                "phone_zen_protection",
                "Protection Anti-Spam",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Notifications de filtrage et de blocage"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(callChannel)
            manager.createNotificationChannel(protectionChannel)
        }
    }
}
