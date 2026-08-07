// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.bonobo.phonezen.blocking.HospitalWhitelistManager
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.repository.HealthcareRepository
import java.util.concurrent.TimeUnit

/**
 * Worker WorkManager pour la synchronisation périodique de la whitelist santé.
 *
 * Planifié toutes les 24h, uniquement sur réseau disponible.
 * En cas d'échec (réseau absent, Supabase KO), WorkManager retente
 * automatiquement avec backoff exponentiel — le cache Room est conservé.
 *
 * Enregistrement : appeler [schedule] depuis [PhoneZenApp.onCreate].
 */
class HealthcareWhitelistSyncWorker(
    appContext: Context,
    params    : WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG         = "HealthcareSync"
        private const val WORK_NAME   = "healthcare_whitelist_sync"
        private const val INTERVAL_H  = 24L

        /**
         * Planifie le worker périodique.
         * [ExistingPeriodicWorkPolicy.KEEP] : si déjà planifié, on ne repart pas de zéro.
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<HealthcareWhitelistSyncWorker>(
                INTERVAL_H, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.i(TAG, "Worker planifié (toutes les ${INTERVAL_H}h)")
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Démarrage sync whitelist santé")

        val db         = AppDatabase.getDatabase(applicationContext)
        val repository = HealthcareRepository(applicationContext, db.healthcareWhitelistDao())

        // Skip si le cache est encore frais
        if (!repository.needsSync()) {
            Log.i(TAG, "Cache encore valide — sync ignorée")
            return Result.success()
        }

        val synced = repository.syncFromSupabase()

        return if (synced >= 0) {
            // Recharge le cache mémoire du manager si l'app est en vie
            // (PhoneZenApp expose le manager comme singleton)
            try {
                val manager = HospitalWhitelistManager(repository)
                manager.reload()
            } catch (e: Exception) {
                Log.w(TAG, "Impossible de recharger le manager en mémoire", e)
            }

            Log.i(TAG, "Sync terminée : $synced entrées")
            Result.success()
        } else {
            Log.w(TAG, "Sync échouée — retry WorkManager")
            Result.retry()
        }
    }
}