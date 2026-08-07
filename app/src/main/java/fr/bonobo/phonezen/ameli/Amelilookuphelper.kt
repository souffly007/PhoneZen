// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ameli

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * AmeliLookupHelper
 * -----------------
 * Lookup offline O(1) des professionnels de santé et centres de santé
 * via la base Ameli (annuairesante.ameli.fr).
 *
 * 271 302 numéros : médecins, infirmiers, kinés, dentistes, pharmacies,
 * laboratoires, centres de santé...
 *
 * La base [ameli_whitelist.db] doit être placée dans :
 *   app/src/main/assets/ameli_whitelist.db
 */
class AmeliLookupHelper private constructor(context: Context) {

    companion object {
        private const val TAG        = "AmeliLookup"
        private const val DB_ASSET   = "ameli_whitelist.db"
        private const val DB_VERSION = 1

        // Table des spécialités embarquée en mémoire (68 entrées, ~2 Ko)
        private val SPECIALITES = mapOf(
            0  to "Cardiologue",
            1  to "Médecin généraliste",
            2  to "Gynécologue / Obstétricien",
            3  to "Chirurgien urologue",
            4  to "Chirurgien orthopédiste",
            5  to "Endocrinologue",
            6  to "Chirurgien vasculaire",
            7  to "Anesthésiste",
            8  to "Neurologue",
            9  to "Pneumologue",
            10 to "Néphrologue",
            11 to "Médecin santé publique",
            12 to "Ophtalmologiste",
            13 to "Psychiatre",
            14 to "ORL",
            15 to "Rhumatologue",
            16 to "Stomatologiste",
            17 to "Allergologue",
            18 to "Dermatologue",
            19 to "Pédiatre",
            20 to "Médecin rééducation",
            21 to "Médecin biologiste",
            22 to "Gastro-entérologue",
            23 to "Radiologue",
            24 to "Radiothérapeute",
            25 to "Chirurgien viscéral",
            26 to "Chirurgien plasticien",
            27 to "Chirurgien maxillo-facial",
            28 to "Anatomo-Cyto-Pathologiste",
            29 to "Médecine d'urgence",
            30 to "Chirurgien général",
            31 to "Médecine vasculaire",
            32 to "Chirurgien oral",
            33 to "Médecine nucléaire",
            34 to "Cancérologue",
            35 to "Pharmacien",
            36 to "Transport sanitaire",
            37 to "Opticien",
            38 to "Audioprothésiste",
            39 to "Prestataire médical",
            40 to "Orthopédiste",
            41 to "Orthoprothésiste",
            42 to "Podo-orthésiste",
            43 to "Laboratoire",
            44 to "Chirurgien-dentiste",
            45 to "Orthodontiste",
            46 to "Sage-femme",
            47 to "Infirmier",
            48 to "Infirmier pratique avancée",
            49 to "Masseur-kinésithérapeute",
            50 to "Pédicure-podologue",
            51 to "Orthophoniste",
            52 to "Orthoptiste",
            53 to "Cancérologue radiothérapeute",
            54 to "Médecine interne",
            55 to "Hématologue",
            56 to "Chirurgien maxillo-facial",
            57 to "Neurochirurgien",
            58 to "Oculariste",
            59 to "Neuropsychiatre",
            60 to "Gériatre",
            61 to "Chirurgien thoracique",
            62 to "Chirurgien infantile",
            63 to "Maladies infectieuses",
            64 to "Réanimateur",
            65 to "Médecin généticien",
            66 to "Médecine légale",
            67 to "Professionnel de santé",
        )

        @Volatile
        private var INSTANCE: AmeliLookupHelper? = null

        fun getInstance(context: Context): AmeliLookupHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AmeliLookupHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    // ── Base de données ───────────────────────────────────────────────────────

    private val db: SQLiteDatabase? by lazy {
        try { openOrCopyDatabase(context) }
        catch (e: Exception) { Log.e(TAG, "Erreur ouverture base Ameli", e); null }
    }

    private fun openOrCopyDatabase(context: Context): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_ASSET)
        val prefs  = context.getSharedPreferences("ameli_prefs", Context.MODE_PRIVATE)
        if (!dbFile.exists() || prefs.getInt("db_version", 0) < DB_VERSION) {
            Log.i(TAG, "Copie de la base Ameli depuis les assets...")
            context.assets.open(DB_ASSET).use { i -> FileOutputStream(dbFile).use { o -> i.copyTo(o) } }
            prefs.edit().putInt("db_version", DB_VERSION).apply()
            Log.i(TAG, "Base Ameli copiée : ${dbFile.length() / 1024} Ko")
        }
        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    // ── Modèle ────────────────────────────────────────────────────────────────

    data class AmeliInfo(
        /** Ex: "Médecin généraliste", "Pharmacien", "Infirmier" */
        val specialite   : String,
        /** true = centre de santé, false = professionnel libéral */
        val isCentreSante: Boolean,
    ) {
        val displaySummary: String
            get() = if (isCentreSante) "Centre de santé · $specialite" else specialite

        /** Professionnel de santé = whitelist → Trusted */
        val isTrusted: Boolean get() = true
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    fun lookup(rawNumber: String): AmeliInfo? {
        val numero   = normalizeNumber(rawNumber) ?: return null
        val database = db ?: return null
        return try {
            database.rawQuery(
                "SELECT specialite_idx, categorie_code FROM ameli_numbers WHERE numero = ? LIMIT 1",
                arrayOf(numero)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val specIdx  = cursor.getInt(0)
                    val catCode  = cursor.getInt(1)
                    AmeliInfo(
                        specialite    = SPECIALITES[specIdx] ?: "Professionnel de santé",
                        isCentreSante = catCode == 1,
                    )
                } else null
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Erreur lookup Ameli", e)
            null
        }
    }

    fun isHealthProfessional(rawNumber: String): Boolean = lookup(rawNumber) != null

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