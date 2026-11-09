// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import fr.bonobo.phonezen.R

import androidx.room.withTransaction
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
    private const val BACKUP_VERSION = 2

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

            val allPrefs = JSONObject()
            prefs.all.forEach { (key, value) ->
                if (value is Set<*>) allPrefs.put(key, JSONArray(value.toList()))
                else allPrefs.put(key, value)
            }
            root.put("all_settings", allPrefs)
            val manual = JSONArray()
            db.blockedNumberDao().getAllOnce().forEach { entry ->
                manual.put(JSONObject().put("number", entry.number).put("label", entry.label).put("timestamp", entry.timestamp))
            }
            root.put("blocked_numbers", manual)

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
                "${context.packageName}.fileprovider",
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
                ?: return@withContext RestoreResult.Error(context.getString(R.string.backup_unreadable))

            val root = JSONObject(json)

            if (!root.has("app") || root.optString("app") != "PhoneZen") {
                return@withContext RestoreResult.Error(context.getString(R.string.backup_invalid))
            }

            require(root.optInt("version", 1) in 1..BACKUP_VERSION) { context.getString(R.string.backup_version) }
            require(json.length <= 10_000_000) { context.getString(R.string.backup_large) }
            val blockedCalls = root.optJSONArray("blocked_calls")?.let { arr ->
                (0 until arr.length()).map { i -> val obj = arr.getJSONObject(i)
                    BlockedCall(number = obj.getString("number"), reason = obj.getString("reason"),
                        riskLevel = obj.optString("riskLevel", "MANUAL"), timestamp = obj.getLong("timestamp"))
                }
            }
            val notes = root.optJSONArray("call_notes")?.let { arr ->
                (0 until arr.length()).map { i -> val obj = arr.getJSONObject(i)
                    CallNote(number = obj.getString("number"), note = obj.getString("note"), timestamp = obj.getLong("timestamp"))
                }
            }
            val manual = root.optJSONArray("blocked_numbers")?.let { arr ->
                (0 until arr.length()).map { i -> val obj = arr.getJSONObject(i)
                    fr.bonobo.phonezen.data.model.BlockedNumber(number = obj.getString("number"),
                        label = obj.optString("label", ""), timestamp = obj.getLong("timestamp"))
                }
            }
            val prefs  = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
            val db     = AppDatabase.getDatabase(context)
            val editor = prefs.edit()

            // A complete snapshot replaces this preference file. Legacy backups
            // only replace their own sections, but must reset manual countries.
            if (root.optJSONObject("all_settings") != null) {
                editor.clear()
            } else {
                prefs.all.keys.filter {
                    it == "number_country_default" || it.startsWith("number_country_account_")
                }.forEach { editor.remove(it) }
            }

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

            root.optJSONObject("all_settings")?.let { settings ->
                settings.keys().forEach { key ->
                    when (val v = settings.get(key)) {
                        is Boolean -> editor.putBoolean(key, v)
                        is Int -> if (key == "vacation_end_ts" || key.endsWith("_timestamp")) editor.putLong(key, v.toLong()) else editor.putInt(key, v)
                        is Long -> editor.putLong(key, v)
                        is Double -> editor.putFloat(key, v.toFloat())
                        is String -> editor.putString(key, v)
                        is JSONArray -> editor.putStringSet(key, (0 until v.length()).map { v.getString(it) }.toSet())
                    }
                }
            }
            db.withTransaction {
                blockedCalls?.let { rows ->
                    db.blockedCallDao().deleteAll()
                    rows.forEach { db.blockedCallDao().insert(it) }
                }
                notes?.let { rows ->
                    db.callNoteDao().deleteAll()
                    rows.forEach { db.callNoteDao().upsert(it) }
                }
                manual?.let { rows ->
                    db.blockedNumberDao().deleteAll()
                    rows.forEach { db.blockedNumberDao().insert(it) }
                }
            }
            check(editor.commit()) { context.getString(R.string.backup_preferences) }

            Log.d(TAG, "Restauration réussie depuis $uri")
            RestoreResult.Success

        } catch (e: Exception) {
            Log.e(TAG, "Erreur restauration : ${e.message}")
            RestoreResult.Error(context.getString(R.string.restore_failed_detail, e.message.orEmpty()))
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
