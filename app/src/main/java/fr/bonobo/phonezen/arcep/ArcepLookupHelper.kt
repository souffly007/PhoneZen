package fr.bonobo.phonezen.arcep

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * ArcepLookupHelper
 * -----------------
 * Lookup offline O(1) des numéros de téléphone français via la base ARCEP.
 *
 * La base [arcep_lookup.db] doit être placée dans :
 *   app/src/main/assets/arcep_lookup.db
 *
 * Elle est copiée automatiquement dans le dossier interne de l'app au premier lancement.
 *
 * Usage :
 *   val helper = ArcepLookupHelper.getInstance(context)
 *   val info   = helper.lookup("0612345678")
 *   // → ArcepNumberInfo(operateur="SFR", categorie="Mobile", territoire="Métropole", ...)
 */
class ArcepLookupHelper private constructor(context: Context) {

    companion object {
        private const val TAG         = "ArcepLookup"
        private const val DB_ASSET    = "arcep_lookup.db"
        private const val DB_VERSION  = 1           // Incrémenter si tu régénères la base

        @Volatile
        private var INSTANCE: ArcepLookupHelper? = null

        fun getInstance(context: Context): ArcepLookupHelper =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArcepLookupHelper(context.applicationContext).also { INSTANCE = it }
            }
    }

    // -------------------------------------------------------------------------
    // Base de données
    // -------------------------------------------------------------------------

    private val db: SQLiteDatabase? by lazy {
        try {
            openOrCopyDatabase(context)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur ouverture base ARCEP", e)
            null
        }
    }

    /**
     * Copie la base depuis les assets vers le stockage interne si nécessaire,
     * puis l'ouvre en lecture seule.
     */
    private fun openOrCopyDatabase(context: Context): SQLiteDatabase {
        val dbFile = File(context.filesDir, DB_ASSET)

        // Vérifier si la base doit être (re)copiée
        val prefs      = context.getSharedPreferences("arcep_prefs", Context.MODE_PRIVATE)
        val storedVer  = prefs.getInt("db_version", 0)
        if (!dbFile.exists() || storedVer < DB_VERSION) {
            Log.i(TAG, "Copie de la base ARCEP depuis les assets...")
            context.assets.open(DB_ASSET).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putInt("db_version", DB_VERSION).apply()
            Log.i(TAG, "Base ARCEP copiée : ${dbFile.length() / 1024} Ko")
        }

        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
    }

    // -------------------------------------------------------------------------
    // Modèle de résultat
    // -------------------------------------------------------------------------

    data class ArcepNumberInfo(
        /** Numéro normalisé passé en entrée */
        val numero: String,
        /** Nom complet de l'opérateur attributaire (ex: "Orange", "SFR") */
        val operateur: String,
        /** Code interne ARCEP 4 lettres (ex: "FRTE", "SFR0") */
        val mnemo: String,
        /** Catégorie lisible (ex: "Mobile", "Fixe - Île-de-France", "Numéro vert (gratuit)") */
        val categorie: String,
        /** Territoire de couverture (ex: "Métropole", "National", "Martinique") */
        val territoire: String,
        /** true si c'est un numéro court (< 6 chiffres) */
        val isShortNumber: Boolean = false,
        /** true si aucune tranche trouvée */
        val isUnknown: Boolean = false,
    ) {
        /** Résumé compact pour affichage inline dans l'UI d'appel */
        val displaySummary: String
            get() = when {
                isUnknown     -> "Numéro inconnu"
                isShortNumber -> "Numéro court · $operateur"
                else          -> "$operateur · $categorie"
            }

        /** Indicateur de risque heuristique basé sur le type de numéro */
        val isSuspiciousType: Boolean
            get() = categorie.contains("surtaxé", ignoreCase = true) ||
                    categorie.contains("spécial", ignoreCase = true)
    }

    // -------------------------------------------------------------------------
    // Lookup principal
    // -------------------------------------------------------------------------

    /**
     * Cherche les informations ARCEP pour un numéro français.
     *
     * @param rawNumber Numéro brut (tous formats acceptés : +33612..., 0612..., 612...)
     * @return [ArcepNumberInfo] ou null si la base n'est pas disponible
     */
    fun lookup(rawNumber: String): ArcepNumberInfo? {
        val numero = normalizeNumber(rawNumber) ?: return null
        val database = db ?: return null

        // Cas numéros courts (≤ 4 chiffres) → table arcep_sdt
        if (numero.length <= 4) {
            return lookupShortNumber(database, numero)
        }

        // Cas standard : lookup par tranche (length = 10)
        val result = lookupByTranche(database, numero)
        if (result != null) return result

        // Fallback : numéro court spécial dans arcep_sdt
        return lookupShortNumber(database, numero)
            ?: ArcepNumberInfo(
                numero      = numero,
                operateur   = "Inconnu",
                mnemo       = "",
                categorie   = guessCategoryFromPrefix(numero),
                territoire  = "",
                isUnknown   = true,
            )
    }

    /**
     * Lookup par tranche E.164 (table arcep_tranches).
     * Stratégie : tranche de 10 chiffres en priorité, puis tranches plus courtes.
     */
    private fun lookupByTranche(db: SQLiteDatabase, numero: String): ArcepNumberInfo? {
        // 1. Recherche exacte sur tranches de 10 chiffres
        queryTranche(db, numero, numero)?.let { return it }

        // 2. Fallback : tranches plus courtes (4 à 9 chiffres)
        for (len in 9 downTo 4) {
            if (len > numero.length) continue
            val prefix = numero.substring(0, len)
            queryTranche(db, prefix, prefix)?.let { return it }
        }
        return null
    }

    private fun queryTranche(db: SQLiteDatabase, start: String, end: String): ArcepNumberInfo? {
        return try {
            db.rawQuery(
                """
                SELECT tranche_debut, tranche_fin, mnemo, operateur_nom, territoire, categorie
                FROM arcep_tranches
                WHERE tranche_debut <= ? AND tranche_fin >= ?
                  AND length(tranche_debut) = 10
                ORDER BY tranche_debut DESC
                LIMIT 1
                """.trimIndent(),
                arrayOf(start, end)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    ArcepNumberInfo(
                        numero     = start,
                        operateur  = cursor.getString(3),
                        mnemo      = cursor.getString(2),
                        categorie  = cursor.getString(5),
                        territoire = cursor.getString(4),
                    )
                } else null
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Erreur query tranche", e)
            null
        }
    }

    private fun lookupShortNumber(db: SQLiteDatabase, numero: String): ArcepNumberInfo? {
        return try {
            db.rawQuery(
                "SELECT ressource, mnemo, operateur_nom FROM arcep_sdt WHERE ressource = ? LIMIT 1",
                arrayOf(numero)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    ArcepNumberInfo(
                        numero        = numero,
                        operateur     = cursor.getString(2),
                        mnemo         = cursor.getString(1),
                        categorie     = "Numéro court",
                        territoire    = "National",
                        isShortNumber = true,
                    )
                } else null
            }
        } catch (e: SQLiteException) {
            Log.e(TAG, "Erreur query short", e)
            null
        }
    }

    // -------------------------------------------------------------------------
    // Normalisation
    // -------------------------------------------------------------------------

    /**
     * Normalise un numéro vers le format français 10 chiffres (0XXXXXXXXX).
     * Retourne null si le numéro n'est pas un numéro français valide.
     */
    fun normalizeNumber(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("33") && digits.length == 11 -> "0" + digits.substring(2)
            digits.startsWith("0")  && digits.length == 10 -> digits
            digits.length in 3..4                          -> digits  // numéros courts
            else                                           -> null
        }
    }

    /**
     * Heuristique de catégorie depuis le préfixe quand aucune tranche n'est trouvée.
     */
    private fun guessCategoryFromPrefix(numero: String): String {
        if (numero.length < 2) return "Inconnu"
        return when (numero.substring(0, 2)) {
            "01" -> "Fixe - Île-de-France"
            "02" -> "Fixe - Nord-Ouest"
            "03" -> "Fixe - Nord-Est"
            "04" -> "Fixe - Sud-Est"
            "05" -> "Fixe - Sud-Ouest"
            "06", "07" -> "Mobile"
            "08" -> when {
                numero.startsWith("0800") -> "Numéro vert (gratuit)"
                numero.startsWith("081")  -> "Numéro azur"
                numero.startsWith("089")  -> "Numéro surtaxé"
                else -> "Numéro spécial"
            }
            "09" -> "Fixe / VoIP"
            else -> "Autre"
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    fun close() {
        db?.close()
        INSTANCE = null
    }
}