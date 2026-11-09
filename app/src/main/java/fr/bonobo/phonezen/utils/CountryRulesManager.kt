// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.content.Context
import android.util.Log
import fr.bonobo.phonezen.data.model.CountryRules
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Gestionnaire des règles de blocage par pays.
 * 
 * Ce singleton charge et cache les règles de blocage pour chaque pays.
 * Les règles sont chargées à la demande (lazy loading) et mises en cache.
 */
object CountryRulesManager {

    private val TAG = "CountryRulesManager"
    
    /** Cache des règles par code de pays */
    private val rulesCache = mutableMapOf<String, CountryRules>()
    
    /** Verrou pour la synchronization */
    private val loadLock = Any()
    
    /** Liste des pays avec règles disponibles */
    private val availableCountries = mutableListOf<String>()
    
    /**
     * Charge les règles pour un pays donné.
     * 
     * @param countryCode Code ISO du pays (ex: "FR", "DE")
     * @param context Contexte Android
     * @return Les règles pour ce pays, ou des règles vides si non trouvé
     */
    fun loadRules(countryCode: String, context: Context): CountryRules {
        val normalizedCode = countryCode.uppercase()
        
        synchronized(loadLock) {
            // Retourner depuis le cache si déjà chargé
            if (rulesCache.containsKey(normalizedCode)) {
                return rulesCache[normalizedCode]!!
            }
            
            // Charger depuis les assets
            val rules = tryLoadRules(normalizedCode, context)
            rulesCache[normalizedCode] = rules
            
            // Mettre à jour la liste des pays disponibles
            if (!availableCountries.contains(normalizedCode)) {
                availableCountries.add(normalizedCode)
                availableCountries.sort()
            }
            
            Log.d(TAG, "Règles chargées pour $normalizedCode: ${rules.blockedPrefixes.size} préfixes bloqués, ${rules.emergencyNumbers.size} numéros d'urgence")
            
            return rules
        }
    }
    
    /**
     * Tente de charger les règles depuis les assets.
     */
    private fun tryLoadRules(countryCode: String, context: Context): CountryRules {
        return try {
            // Essayer le nouveau format : assets/rules/{countryCode}/blocked_prefixes.json
            val newPath = "rules/$countryCode/blocked_prefixes.json"
            val json = context.assets.open(newPath).bufferedReader().use { it.readText() }
            parseRules(countryCode, json)
        } catch (e: Exception) {
            try {
                // Essayer l'ancien format : assets/prefixes_blocked_{countryCode}.json
                val oldPath = "prefixes_blocked_$countryCode.json"
                val json = context.assets.open(oldPath).bufferedReader().use { it.readText() }
                parseLegacyRules(countryCode, json)
            } catch (e2: Exception) {
                Log.w(TAG, "Aucun fichier de règles trouvé pour $countryCode")
                // Retourner des règles vides avec le bon nom de pays
                CountryRules(
                    countryCode = countryCode,
                    countryName = getCountryName(countryCode)
                )
            }
        }
    }
    
    /**
     * Parse les règles depuis un JSON au format moderne.
     */
    private fun parseRules(countryCode: String, json: String): CountryRules {
        val root = JSONObject(json)
        val countryName = root.optString("country_name", getCountryName(countryCode))
        
        return CountryRules(
            countryCode = countryCode,
            countryName = countryName,
            blockedPrefixes = parseStringList(root.optJSONArray("blocked_prefixes")),
            telemarketingPrefixes = parseStringList(root.optJSONArray("telemarketing_prefixes")),
            emergencyNumbers = parseStringList(root.optJSONArray("emergency_numbers")),
            neverBlock = parseStringList(root.optJSONArray("never_block"))
        )
    }
    
    /**
     * Parse les règles depuis un JSON au format ancien (pour compatibilité).
     */
    private fun parseLegacyRules(countryCode: String, json: String): CountryRules {
        val root = JSONObject(json)
        val countryName = getCountryName(countryCode)
        
        // Récupérer depuis les sections PRIORITY_*
        val blockedPrefixes = mutableListOf<String>()
        val telemarketingPrefixes = mutableListOf<String>()
        val neverBlock = mutableListOf<String>()
        
        root.optJSONObject("PRIORITY_2_NPV_ARCEP")?.optJSONArray("prefixes")?.let { arr ->
            for (i in 0 until arr.length()) {
                blockedPrefixes.add(arr.getString(i))
            }
        }
        
        root.optJSONObject("PRIORITY_3_COMMERCIAL_FLOTTE")?.optJSONArray("prefixes")?.let { arr ->
            for (i in 0 until arr.length()) {
                telemarketingPrefixes.add(arr.getString(i))
            }
        }
        
        root.optJSONObject("PRIORITY_1_SAFE_LIST")?.let { safeList ->
            safeList.optJSONArray("numbers")?.let { arr ->
                for (i in 0 until arr.length()) {
                    neverBlock.add(arr.getString(i))
                }
            }
            safeList.optJSONArray("prefixes_gratuits")?.let { arr ->
                for (i in 0 until arr.length()) {
                    neverBlock.add(arr.getString(i))
                }
            }
        }
        
        // Numéros d'urgence - à définir par pays
        val emergencyNumbers = when (countryCode) {
            "FR" -> listOf("15", "17", "18", "112", "114", "119", "196")
            "DE" -> listOf("110", "112", "115")
            "BE" -> listOf("100", "101", "102", "103", "104", "105", "106", "107", "112")
            "ES" -> listOf("112", "091", "061", "062", "080")
            "IT" -> listOf("112", "113", "115", "118")
            "CH" -> listOf("112", "117", "118", "144", "145", "111")
            "GB" -> listOf("999", "112", "101", "102", "103", "104", "105", "106", "111")
            "NL" -> listOf("112", "100", "101", "102", "103", "104", "105", "106", "107", "108", "109")
            "PT" -> listOf("112", "113", "115", "118", "119")
            "US" -> listOf("911", "112")
            "CA" -> listOf("911", "112")
            else -> listOf("112") // Numéro d'urgence international
        }
        
        return CountryRules(
            countryCode = countryCode,
            countryName = countryName,
            blockedPrefixes = blockedPrefixes,
            telemarketingPrefixes = telemarketingPrefixes,
            emergencyNumbers = emergencyNumbers,
            neverBlock = neverBlock
        )
    }
    
    /**
     * Parse un JSONArray de strings en List<String>.
     */
    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { i -> array.getString(i) }
    }
    
    /**
     * Obtient le nom du pays à partir de son code ISO.
     */
    fun getCountryName(countryCode: String): String {
        return try {
            Locale("", countryCode).displayCountry
        } catch (e: Exception) {
            countryCode
        }
    }
    
    /**
     * Obtient la liste des pays avec des règles disponibles.
     */
    fun getAvailableCountries(): List<String> {
        synchronized(loadLock) {
            return availableCountries.toList()
        }
    }
    
    /**
     * Vérifie si des règles existent pour un pays.
     */
    fun hasRules(countryCode: String): Boolean {
        synchronized(loadLock) {
            return rulesCache.containsKey(countryCode.uppercase())
        }
    }
    
    /**
     * Efface le cache (utile pour les tests ou le rechargement).
     */
    fun clearCache() {
        synchronized(loadLock) {
            rulesCache.clear()
            availableCountries.clear()
        }
    }
    
    /**
     * Précharge les règles pour plusieurs pays.
     */
    fun preloadRules(countries: List<String>, context: Context) {
        synchronized(loadLock) {
            countries.forEach { country ->
                loadRules(country, context)
            }
            Log.d(TAG, "Pré-chargement terminé pour ${countries.size} pays")
        }
    }
}
