// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class BlockedCallActionReceiver {
// Fichier : src/main/java/fr/bonobo/phonezen/service/BlockedCallActionReceiver.kt
package fr.bonobo.phonezen.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlockedCallActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_WHITELIST_UPDATED = "fr.bonobo.phonezen.WHITELIST_UPDATED"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(PhoneZenCallScreeningService.EXTRA_NUMBER) ?: return
        val action = intent.getStringExtra("action") ?: return

        if (action == "whitelist") {
            val normalized = PhoneUtils.normalizeNumber(number)
            val detector = SpamDetector(context)

            detector.addToWhitelist(normalized)
            detector.removeCommunityBlocked(normalized)

            val alt = when {
                normalized.startsWith("+33") -> "0" + normalized.substring(3)
                normalized.startsWith("0") -> "+33" + normalized.substring(1)
                else -> null
            }
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.blockedNumberDao().deleteByNumber(normalized)
                    alt?.let { db.blockedNumberDao().deleteByNumber(it) }
                } catch (e: Exception) {
                    Log.e("BlockedCallActionReceiver", "Erreur suppression : ${e.message}")
                }
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(PhoneZenCallScreeningService.NOTIF_ID_BASE + number.hashCode())

            val updateIntent = Intent(ACTION_WHITELIST_UPDATED).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(updateIntent)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "✅ $normalized ajouté à la liste blanche", Toast.LENGTH_SHORT).show()
            }
        }
    }
}