// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.repository

import android.content.Context
import android.util.Log
import fr.bonobo.phonezen.BuildConfig
import fr.bonobo.phonezen.data.dao.HealthcareWhitelistDao
import fr.bonobo.phonezen.data.model.HealthcareEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Repository de la whitelist santé.
 *
 * Stratégie de données (par ordre de priorité) :
 *  1. Cache Room local  → lookup immédiat, zéro latence réseau
 *  2. Supabase REST API → refresh périodique (toutes les [SYNC_INTERVAL_MS])
 *  3. Fallback JSON assets → si Room vide ET Supabase injoignable
 *
 * Le refresh est déclenché par [HealthcareWhitelistSyncWorker] (WorkManager)
 * et non au moment du lookup pour ne jamais bloquer le screening d'appel.
 */
class HealthcareRepository(
    private val context: Context,
    private val dao    : HealthcareWhitelistDao
) {

    companion object {
        private const val TAG = "HealthcareRepo"

        // ── Supabase ──────────────────────────────────────────────────────
        // Remplace par tes vraies valeurs (ou lis-les depuis BuildConfig)
        private const val SUPABASE_URL     = BuildConfig.SUPABASE_URL
        private const val SUPABASE_API_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY
        private const val TABLE            = "healthcare_numbers"

        // Endpoint REST Supabase — récupère uniquement les entrées vérifiées
        private val ENDPOINT = "$SUPABASE_URL/rest/v1/$TABLE?verified=eq.true&select=*"

        // ── Sync ──────────────────────────────────────────────────────────
        /** Durée de validité du cache Room avant refresh : 24 h */
        const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** Nom du fichier JSON bundlé dans assets/ (fallback) */
        private const val ASSET_FILE = "hospitals_whitelist.json"
    }

    // ─────────────────────────────────────────────
    // API publique — appelée par HospitalWhitelistManager
    // ─────────────────────────────────────────────

    /**
     * Retourne toutes les entrées vérifiées depuis Room.
     * Si Room est vide, charge le JSON assets en fallback.
     */
    suspend fun getVerifiedEntries(): List<HealthcareEntry> = withContext(Dispatchers.IO) {
        val cached = dao.getAllVerifiedEntries()
        if (cached.isNotEmpty()) {
            Log.d(TAG, "Room cache : ${cached.size} entrées")
            return@withContext cached
        }
        Log.w(TAG, "Room vide — fallback sur assets/$ASSET_FILE")
        loadFromAssets()
    }

    /**
     * Retourne true si un refresh Supabase est nécessaire
     * (cache absent ou expiré).
     */
    fun needsSync(): Boolean {
        val lastSync = dao.getLastSyncTimestamp() ?: 0L
        return System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS
    }

    // ─────────────────────────────────────────────
    // Sync Supabase — appelée par le SyncWorker
    // ─────────────────────────────────────────────

    /**
     * Fetche les entrées depuis Supabase et les persiste dans Room.
     * En cas d'échec réseau, le cache Room existant est conservé.
     *
     * @return nombre d'entrées synchronisées, -1 en cas d'erreur
     */
    suspend fun syncFromSupabase(): Int = withContext(Dispatchers.IO) {
        try {
            val json    = fetchFromSupabase()
            val entries = parseSupabaseResponse(json)

            if (entries.isNotEmpty()) {
                dao.clearBySource("official")
                dao.clearBySource("community")
                dao.upsertAll(entries)
                Log.i(TAG, "Supabase sync OK : ${entries.size} entrées")
            }

            entries.size
        } catch (e: Exception) {
            Log.e(TAG, "Supabase sync échouée — cache Room conservé", e)
            -1
        }
    }

    // ─────────────────────────────────────────────
    // Réseau
    // ─────────────────────────────────────────────

    private fun fetchFromSupabase(): String {
        val url  = URL(ENDPOINT)
        val conn = url.openConnection() as HttpURLConnection
        return conn.run {
            requestMethod = "GET"
            setRequestProperty("apikey",        SUPABASE_API_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_API_KEY")
            setRequestProperty("Content-Type",  "application/json")
            connectTimeout = 10_000
            readTimeout    = 15_000
            connect()
            if (responseCode != 200) {
                throw Exception("HTTP $responseCode depuis Supabase")
            }
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .also { disconnect() }
        }
    }

    /**
     * Parse la réponse JSON array de Supabase.
     *
     * Format attendu par ligne :
     * {
     *   "id": "uuid",
     *   "number": "0140275757",   // nullable
     *   "prefix": "014427",       // nullable
     *   "name": "AP-HP",
     *   "type": "hospital",
     *   "region": "Île-de-France",
     *   "verified": true,
     *   "source": "official"
     * }
     */
    private fun parseSupabaseResponse(json: String): List<HealthcareEntry> {
        val now   = System.currentTimeMillis()
        val array = org.json.JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val obj = array.getJSONObject(i)
                HealthcareEntry(
                    id       = obj.optString("id", UUID.randomUUID().toString()),
                    number   = obj.optString("number", "").takeIf { it.isNotBlank() }
                        ?.normalizeNumber(),
                    prefix   = obj.optString("prefix", "").takeIf { it.isNotBlank() }
                        ?.normalizeNumber(),
                    name     = obj.getString("name"),
                    type     = obj.optString("type", "hospital"),
                    region   = obj.optString("region", "France"),
                    verified = obj.optBoolean("verified", true),
                    source   = obj.optString("source", "official"),
                    syncedAt = now
                )
            }.onFailure { Log.w(TAG, "Entrée ignorée à l'index $i", it) }
                .getOrNull()
        }
    }

    // ─────────────────────────────────────────────
    // Fallback assets
    // ─────────────────────────────────────────────

    /**
     * Charge et aplatit le JSON assets en liste de [HealthcareEntry].
     * Chaque entrée du JSON peut contenir plusieurs numéros ET plusieurs préfixes :
     * on crée une HealthcareEntry par (number|prefix) pour coller au modèle Room.
     */
    private fun loadFromAssets(): List<HealthcareEntry> {
        return try {
            val json = context.assets.open(ASSET_FILE)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }

            val root    = JSONObject(json)
            val entries = root.getJSONArray("entries")
            val now     = System.currentTimeMillis()
            val result  = mutableListOf<HealthcareEntry>()

            for (i in 0 until entries.length()) {
                val e      = entries.getJSONObject(i)
                val name   = e.getString("name")
                val type   = e.optString("type", "hospital")
                val region = e.optString("region", "France")

                // Numéros exacts
                e.optJSONArray("numbers")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        result += HealthcareEntry(
                            id       = UUID.randomUUID().toString(),
                            number   = arr.getString(j).normalizeNumber(),
                            prefix   = null,
                            name     = name,
                            type     = type,
                            region   = region,
                            verified = true,
                            source   = "assets",
                            syncedAt = now
                        )
                    }
                }

                // Préfixes
                e.optJSONArray("prefixes")?.let { arr ->
                    for (j in 0 until arr.length()) {
                        result += HealthcareEntry(
                            id       = UUID.randomUUID().toString(),
                            number   = null,
                            prefix   = arr.getString(j).normalizeNumber(),
                            name     = name,
                            type     = type,
                            region   = region,
                            verified = true,
                            source   = "assets",
                            syncedAt = now
                        )
                    }
                }
            }

            Log.i(TAG, "Fallback assets : ${result.size} entrées chargées")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de charger $ASSET_FILE", e)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────
    // Normalisation
    // ─────────────────────────────────────────────

    private fun String.normalizeNumber(): String =
        this.replace(Regex("[\\s\\-\\.\\(\\)/]"), "")
            .replace(Regex("^\\+33"), "0")
            .replace(Regex("^0033"), "0")
            .trim()
}