// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.dila

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * DilaLookupHelper
 * ----------------
 * Lookup offline O(1) des numéros de téléphone du service public français
 * via la base DILA (annuaire service-public.gouv.fr).
 *
 * 66 000+ numéros : mairies, gendarmeries, commissariats, CAF, CPAM,
 * France Travail, impôts, tribunaux, France Services...
 *
 * La base [dila_whitelist.db] doit être placée dans :
 *   app/src/main/assets/dila_whitelist.db
 *
 * Usage :
 *   val helper = DilaLookupHelper.getInstance(context)
 *   val info   = helper.lookup("0164383052")
 *   // → DilaInfo(nom="Mairie - Fontaine-le-Port", categorie="mairie", commune="77188")
 */
class DilaLookupHelper private constructor(context: Context) {

    companion object {
        private const val TAG        = "DilaLookup"
        private const val DB_ASSET   = "dila_whitelist.db"
        private const val DB_VERSION = 2  // Incrémenté 13/08/2026 : base régénérée (66 069 numéros)

        @Volatile
        private var INSTANCE: DilaLookupHelper? = null

        fun getInstance(context: Context): DilaLookupHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: DilaLookupHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Base de données ───────────────────────────────────────────────────────

    private val db: SQLiteDatabase? by lazy {
        try {
            openOrCopyDatabase(context)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur ouverture base DILA", e)
            null
        }
    }

    private fun openOrCopyDatabase(context: Context): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_ASSET)
        val prefs  = context.getSharedPreferences("dila_prefs", Context.MODE_PRIVATE)

        if (!dbFile.exists() || prefs.getInt("db_version", 0) < DB_VERSION) {
            Log.i(TAG, "Copie de la base DILA depuis les assets...")
            context.assets.open(DB_ASSET).use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
            prefs.edit().putInt("db_version", DB_VERSION).apply()
            Log.i(TAG, "Base DILA copiée : ${dbFile.length() / 1024} Ko")
        }

        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    // ── Modèle ────────────────────────────────────────────────────────────────

    data class DilaInfo(
        /** Ex: "Mairie - Fontaine-le-Port" */
        val nom       : String,
        /** Ex: "mairie", "gendarmerie", "commissariat_police" */
        val categorie : String,
        /** Code INSEE commune (ex: "77188") */
        val commune   : String,
    ) {
        /**
         * Résumé compact pour l'UI — affiché sous le nom dans InCallScreen.
         * Ex: "📍 mairie · Fontaine-le-Port"
         */
        val displaySummary: String get() = "$categorie · $nom"

        /** Ce numéro appartient à un service de sécurité */
        val isSecurity: Boolean
            get() = categorie.contains("Gendarmerie",  ignoreCase = true) ||
                    categorie.contains("Commissariat", ignoreCase = true) ||
                    categorie.contains("Police",       ignoreCase = true)

        /** Ce numéro appartient à un service social/santé */
        val isSocial: Boolean
            get() = categorie.contains("CPAM",    ignoreCase = true) ||
                    categorie.contains("CAF",     ignoreCase = true) ||
                    categorie.contains("PMI",     ignoreCase = true) ||
                    categorie.contains("CCAS",    ignoreCase = true) ||
                    categorie.contains("Travail", ignoreCase = true)
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Cherche un numéro dans la base DILA.
     *
     * @param rawNumber Numéro brut (0XXXXXXXXX, +33XXXXXXXXX, etc.)
     * @return [DilaInfo] si trouvé, null sinon (numéro non référencé ou base indisponible)
     */
    fun lookup(rawNumber: String): DilaInfo? {
        val numero   = normalizeNumber(rawNumber) ?: return null
        val database = db ?: return null

        return try {
            database.rawQuery(
                "SELECT nom, categorie, commune FROM dila_numbers WHERE numero = ? LIMIT 1",
                arrayOf(numero)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    DilaInfo(
                        nom       = cursor.getString(0),
                        categorie = cursor.getString(1),
                        commune   = cursor.getString(2) ?: "",
                    )
                } else null
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Erreur lookup DILA", e)
            null
        }
    }

    /** true si le numéro est dans la base DILA (service public confirmé) */
    fun isPublicService(rawNumber: String): Boolean = lookup(rawNumber) != null

    // ── Normalisation ─────────────────────────────────────────────────────────

    private fun normalizeNumber(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("33") && digits.length == 11 -> "0" + digits.substring(2)
            digits.startsWith("0")  && digits.length == 10 -> digits
            else -> null
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun close() {
        db?.close()
        INSTANCE = null
    }
}