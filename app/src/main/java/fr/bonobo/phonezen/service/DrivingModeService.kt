// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class DrivingModeService {
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
package fr.bonobo.phonezen.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import fr.bonobo.phonezen.MainActivity
import fr.bonobo.phonezen.R
import androidx.core.app.NotificationManagerCompat

/**
 * Service foreground qui maintient DrivingModeManager actif en arrière-plan.
 * Nécessaire pour que la détection GPS/accéléromètre continue quand l'app est fermée.
 */
class DrivingModeService : Service() {

    companion object {
        private const val TAG             = "DrivingModeService"
        private const val CHANNEL_ID      = "driving_mode_channel"
        private const val NOTIFICATION_ID = 42
    }

    private lateinit var drivingModeManager: DrivingModeManager

    override fun onCreate() {
        super.onCreate()
        drivingModeManager = DrivingModeManager(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        drivingModeManager.startAutoDetection()
        Log.i(TAG, "DrivingModeService démarré")
    }

    override fun onDestroy() {
        drivingModeManager.stopAutoDetection()
        Log.i(TAG, "DrivingModeService arrêté")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mode conduite",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Détection automatique du mode conduite" }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi     = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mode conduite")
            .setContentText("Détection en cours…")
            .setSmallIcon(R.drawable.ic_menu_call)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}