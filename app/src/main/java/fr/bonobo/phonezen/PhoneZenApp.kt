// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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

        // ── Crash reporter — doit être installé en premier ────────────────
        CrashHandler.install(this)

        createNotificationChannels()

        // Planifie la sync Supabase toutes les 24h (idempotent, safe à appeler à chaque démarrage)
        HealthcareWhitelistSyncWorker.schedule(this)
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