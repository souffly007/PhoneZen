// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import fr.bonobo.phonezen.data.model.HealthcareEntry

/**
 * DAO Room pour la whitelist des établissements de santé.
 *
 * Toutes les requêtes de lookup sont synchrones (appelées depuis [HospitalWhitelistManager]
 * qui gère déjà son propre thread via coroutines).
 */
@Dao
interface HealthcareWhitelistDao {

    // ─────────────────────────────────────────────
    // Lecture
    // ─────────────────────────────────────────────

    /**
     * Vérifie si un numéro exact est dans la whitelist (verified uniquement).
     */
    @Query("SELECT COUNT(*) FROM healthcare_whitelist WHERE number = :number AND verified = 1")
    fun isNumberWhitelisted(number: String): Int

    /**
     * Retourne toutes les entrées dont le préfixe correspond au début du numéro.
     * On récupère la liste et on filtre en Kotlin via startsWith (Room ne supporte
     * pas nativement le "numéro LIKE prefix%").
     */
    @Query("SELECT * FROM healthcare_whitelist WHERE prefix IS NOT NULL AND verified = 1")
    fun getAllPrefixEntries(): List<HealthcareEntry>

    /**
     * Retourne le nom de l'établissement pour un numéro exact, null si absent.
     */
    @Query("""
        SELECT name FROM healthcare_whitelist
        WHERE number = :number AND verified = 1
        LIMIT 1
    """)
    fun getNameForNumber(number: String): String?

    /**
     * Nombre total d'entrées vérifiées en cache (pour les réglages).
     */
    @Query("SELECT COUNT(*) FROM healthcare_whitelist WHERE verified = 1")
    fun countVerifiedEntries(): Int

    /**
     * Timestamp de la dernière synchro (pour décider si un refresh est nécessaire).
     */
    @Query("SELECT MAX(syncedAt) FROM healthcare_whitelist")
    fun getLastSyncTimestamp(): Long?

    /**
     * Toutes les entrées — utilisé pour le fallback et les stats.
     */
    @Query("SELECT * FROM healthcare_whitelist WHERE verified = 1")
    fun getAllVerifiedEntries(): List<HealthcareEntry>

    // ─────────────────────────────────────────────
    // Écriture
    // ─────────────────────────────────────────────

    /**
     * Insère ou remplace les entrées récupérées depuis Supabase.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<HealthcareEntry>)

    /**
     * Supprime toutes les entrées (avant un refresh complet depuis Supabase).
     */
    @Query("DELETE FROM healthcare_whitelist")
    fun clearAll()

    /**
     * Supprime uniquement les entrées d'une source donnée
     * (ex: nettoyer les entrées "community" sans toucher les "official").
     */
    @Query("DELETE FROM healthcare_whitelist WHERE source = :source")
    fun clearBySource(source: String)
}