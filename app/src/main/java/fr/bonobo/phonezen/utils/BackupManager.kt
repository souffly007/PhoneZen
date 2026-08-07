// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.local.BlockedCall
import fr.bonobo.phonezen.data.local.CallNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_VERSION = 1

    // ─────────────────────────────────────────────
    // SAUVEGARDE
    // ─────────────────────────────────────────────

    suspend fun createBackup(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
            val db    = AppDatabase.getDatabase(context)

            val root = JSONObject()

            // Métadonnées
            root.put("version", BACKUP_VERSION)
            root.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date()))
            root.put("app", "PhoneZen")

            // ── SharedPreferences ──
            val settings = JSONObject().apply {
                put("block_private_numbers",   prefs.getBoolean("block_private_numbers", false))
                put("hide_blocked",            prefs.getBoolean("hide_blocked", true))
                put("community_block_enabled", prefs.getBoolean("community_block_enabled", true))
                put("do_not_disturb",          prefs.getBoolean("do_not_disturb", false))
                put("schedule_enabled",        prefs.getBoolean("schedule_enabled", false))
                put("schedule_start_hour",     prefs.getInt("schedule_start_hour", 22))
                put("schedule_start_minute",   prefs.getInt("schedule_start_minute", 0))
                put("schedule_end_hour",       prefs.getInt("schedule_end_hour", 8))
                put("schedule_end_minute",     prefs.getInt("schedule_end_minute", 0))
            }
            root.put("settings", settings)

            // ── Favoris ──
            val favArray = JSONArray()
            (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).forEach { favArray.put(it) }
            root.put("favorites", favArray)

            // ── Liste blanche ──
            val whitelistArray = JSONArray()
            (prefs.getStringSet("whitelist", emptySet()) ?: emptySet()).forEach { whitelistArray.put(it) }
            root.put("whitelist", whitelistArray)

            // ── Numéros bloqués (Room) ──
            val blockedArray = JSONArray()
            db.blockedCallDao().getAllBlockedCallsOnce().forEach { call ->
                blockedArray.put(JSONObject().apply {
                    put("number",    call.number)
                    put("reason",    call.reason)
                    put("riskLevel", call.riskLevel)
                    put("timestamp", call.timestamp)
                })
            }
            root.put("blocked_calls", blockedArray)

            // ── Notes (Room) ──
            val notesArray = JSONArray()
            db.callNoteDao().getAllNotesOnce().forEach { note ->
                notesArray.put(JSONObject().apply {
                    put("number",    note.number)
                    put("note",      note.note)
                    put("timestamp", note.timestamp)
                })
            }
            root.put("call_notes", notesArray)

            // ── Écriture du fichier ──
            val fileName = "phonezen_backup_${
                SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(Date())
            }.json"

            val backupDir  = File(context.cacheDir, "backups").also { it.mkdirs() }
            val backupFile = File(backupDir, fileName)
            backupFile.writeText(root.toString(2))

            Log.d(TAG, "Sauvegarde créée : ${backupFile.absolutePath}")

            FileProvider.getUriForFile(
                context,
                "fr.bonobo.phonezen.fileprovider",
                backupFile)

        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde : ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────
    // RESTAURATION
    // ─────────────────────────────────────────────

    suspend fun restoreBackup(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
                ?: return@withContext RestoreResult.Error("Impossible de lire le fichier")

            val root = JSONObject(json)

            if (!root.has("app") || root.optString("app") != "PhoneZen") {
                return@withContext RestoreResult.Error("Fichier de sauvegarde invalide")
            }

            val prefs  = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
            val db     = AppDatabase.getDatabase(context)
            val editor = prefs.edit()

            // ── Paramètres ──
            root.optJSONObject("settings")?.let { s ->
                editor.putBoolean("block_private_numbers",   s.optBoolean("block_private_numbers", false))
                editor.putBoolean("hide_blocked",            s.optBoolean("hide_blocked", true))
                editor.putBoolean("community_block_enabled", s.optBoolean("community_block_enabled", true))
                editor.putBoolean("do_not_disturb",          s.optBoolean("do_not_disturb", false))
                editor.putBoolean("schedule_enabled",        s.optBoolean("schedule_enabled", false))
                editor.putInt("schedule_start_hour",         s.optInt("schedule_start_hour", 22))
                editor.putInt("schedule_start_minute",       s.optInt("schedule_start_minute", 0))
                editor.putInt("schedule_end_hour",           s.optInt("schedule_end_hour", 8))
                editor.putInt("schedule_end_minute",         s.optInt("schedule_end_minute", 0))
            }

            // ── Favoris ──
            root.optJSONArray("favorites")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                editor.putStringSet("favorites", set)
            }

            // ── Liste blanche ──
            root.optJSONArray("whitelist")?.let { arr ->
                val set = mutableSetOf<String>()
                for (i in 0 until arr.length()) set.add(arr.getString(i))
                editor.putStringSet("whitelist", set)
            }

            editor.commit()

            // ── Numéros bloqués (Room) ──
            root.optJSONArray("blocked_calls")?.let { arr ->
                db.blockedCallDao().deleteAll()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    db.blockedCallDao().insert(
                        BlockedCall(
                            id        = 0,
                            number    = obj.optString("number"),
                            reason    = obj.optString("reason"),
                            riskLevel = obj.optString("riskLevel", "MANUAL"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            // ── Notes (Room) ──
            root.optJSONArray("call_notes")?.let { arr ->
                db.callNoteDao().deleteAll()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    db.callNoteDao().upsert(
                        CallNote(
                            number    = obj.optString("number"),
                            note      = obj.optString("note"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            Log.d(TAG, "Restauration réussie depuis $uri")
            RestoreResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Erreur restauration : ${e.message}")
            RestoreResult.Error("Erreur : ${e.message}")
        }
    }

    // ─────────────────────────────────────────────
    // RÉSULTAT
    // ─────────────────────────────────────────────
    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Error(val message: String) : RestoreResult()
    }
}