// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.blocking

import android.util.Log
import fr.bonobo.phonezen.data.model.HealthcareEntry
import fr.bonobo.phonezen.data.repository.HealthcareRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking

/**
 * Gestionnaire de la whitelist des établissements de santé français.
 *
 * Le cache mémoire est chargé de façon SYNCHRONE au premier accès
 * pour garantir qu'aucun appel entrant n'est analysé avec un cache vide.
 *
 * Priorité absolue : un numéro reconnu ici ne peut jamais être bloqué.
 */
class HospitalWhitelistManager(
    private val repository: HealthcareRepository
) {

    companion object {
        private const val TAG = "HospitalWhitelist"
    }

    // Cache mémoire
    @Volatile private var numberSet : Set<String>         = emptySet()
    @Volatile private var prefixList: List<String>        = emptyList()
    @Volatile private var entryMap  : Map<String, String> = emptyMap()
    @Volatile private var totalCount: Int                 = 0
    @Volatile private var isReady   : Boolean             = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Chargement synchrone bloquant au démarrage —
        // indispensable pour que le cache soit prêt avant le premier appel entrant
        runBlocking(Dispatchers.IO) {
            try {
                val entries = repository.getVerifiedEntries()
                buildCache(entries)
                Log.i(TAG, "Cache mémoire prêt (sync) : $totalCount entrées")
            } catch (e: Exception) {
                Log.e(TAG, "Erreur chargement initial", e)
            }
        }
    }

    // ─────────────────────────────────────────────
    // Rechargement (après sync Supabase)
    // ─────────────────────────────────────────────

    /**
     * Recharge le cache mémoire depuis Room.
     * Appelé par [HealthcareWhitelistSyncWorker] après une sync Supabase réussie.
     */
    suspend fun reload() {
        val entries = repository.getVerifiedEntries()
        buildCache(entries)
        Log.i(TAG, "Cache mémoire rechargé : $totalCount entrées")
    }

    private fun buildCache(entries: List<HealthcareEntry>) {
        val numbers  = mutableSetOf<String>()
        val prefixes = mutableListOf<String>()
        val nameMap  = mutableMapOf<String, String>()

        entries.forEach { e ->
            e.number?.let { n ->
                numbers  += n
                nameMap[n] = e.name
            }
            e.prefix?.let { p ->
                prefixes += p
                nameMap[p] = e.name
            }
        }

        numberSet  = numbers
        prefixList = prefixes
        entryMap   = nameMap
        totalCount = entries.size
        isReady    = true
    }

    // ─────────────────────────────────────────────
    // API publique — synchrone, safe depuis n'importe quel thread
    // ─────────────────────────────────────────────

    /**
     * Retourne true si le numéro appartient à un établissement de santé connu.
     *
     * Si le cache n'est pas encore prêt (cas extrême), force un chargement
     * synchrone bloquant plutôt que de laisser passer une fausse réponse.
     */
    fun isHospitalNumber(rawNumber: String): Boolean {
        ensureCacheReady()
        val number = rawNumber.normalizeNumber()
        if (number.isBlank()) return false
        return number in numberSet || prefixList.any { number.startsWith(it) }
    }

    /**
     * Retourne le nom lisible de l'établissement si trouvé, null sinon.
     */
    fun getHospitalName(rawNumber: String): String? {
        ensureCacheReady()
        val number = rawNumber.normalizeNumber()
        if (number.isBlank()) return null
        return entryMap[number]
            ?: prefixList.firstOrNull { number.startsWith(it) }?.let { entryMap[it] }
    }

    /** Nombre total d'entrées en cache (pour les réglages). */
    fun getEntriesCount(): Int = totalCount

    // ─────────────────────────────────────────────
    // Sécurité : garantir que le cache est prêt
    // ─────────────────────────────────────────────

    /**
     * Si le cache est vide (démarrage très rapide ou erreur init),
     * force un rechargement synchrone avant tout lookup.
     */
    private fun ensureCacheReady() {
        if (!isReady || totalCount == 0) {
            Log.w(TAG, "Cache vide au moment du lookup — rechargement forcé")
            runBlocking(Dispatchers.IO) {
                try {
                    val entries = repository.getVerifiedEntries()
                    buildCache(entries)
                    Log.i(TAG, "Cache rechargé en urgence : $totalCount entrées")
                } catch (e: Exception) {
                    Log.e(TAG, "Échec rechargement d'urgence", e)
                }
            }
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