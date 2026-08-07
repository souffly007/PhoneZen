// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.police

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * PoliceLookupHelper
 * ------------------
 * Lookup offline des commissariats de Police Nationale accueillant du public.
 *
 * Source : Ministère de l'Intérieur (data.gouv.fr), mis à jour juin 2026.
 * 14 commissariats référencés avec numéro + nom + commune.
 *
 * La base [police_whitelist.db] doit être placée dans :
 *   app/src/main/assets/police_whitelist.db
 */
class PoliceLookupHelper private constructor(context: Context) {

    companion object {
        private const val TAG        = "PoliceLookup"
        private const val DB_ASSET   = "police_whitelist.db"
        private const val DB_VERSION = 1

        @Volatile
        private var INSTANCE: PoliceLookupHelper? = null

        fun getInstance(context: Context): PoliceLookupHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PoliceLookupHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Base de données ───────────────────────────────────────────────────────

    private val db: SQLiteDatabase? by lazy {
        try { openOrCopyDatabase(context) }
        catch (e: Exception) { Log.e(TAG, "Erreur ouverture base Police", e); null }
    }

    private fun openOrCopyDatabase(context: Context): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_ASSET)
        val prefs  = context.getSharedPreferences("police_prefs", Context.MODE_PRIVATE)
        if (!dbFile.exists() || prefs.getInt("db_version", 0) < DB_VERSION) {
            Log.i(TAG, "Copie de la base Police depuis les assets...")
            context.assets.open(DB_ASSET).use { i -> FileOutputStream(dbFile).use { o -> i.copyTo(o) } }
            prefs.edit().putInt("db_version", DB_VERSION).apply()
            Log.i(TAG, "Base Police copiée : ${dbFile.length() / 1024} Ko")
        }
        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    // ── Modèle ────────────────────────────────────────────────────────────────

    data class PoliceInfo(
        val nom        : String,
        val commune    : String,
        val departement: String,
    ) {
        val displaySummary: String
            get() = "Commissariat · $commune"
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    fun lookup(rawNumber: String): PoliceInfo? {
        val numero   = normalizeNumber(rawNumber) ?: return null
        val database = db ?: return null
        return try {
            database.rawQuery(
                "SELECT nom, commune, departement FROM police_numbers WHERE numero = ? LIMIT 1",
                arrayOf(numero)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    PoliceInfo(
                        nom         = cursor.getString(0),
                        commune     = cursor.getString(1) ?: "",
                        departement = cursor.getString(2) ?: "",
                    )
                } else null
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Erreur lookup Police", e)
            null
        }
    }

    fun isPoliceNationale(rawNumber: String): Boolean = lookup(rawNumber) != null

    private fun normalizeNumber(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("33") && digits.length == 11 -> "0" + digits.substring(2)
            digits.startsWith("0")  && digits.length == 10 -> digits
            else -> null
        }
    }

    fun close() { db?.close(); INSTANCE = null }
}