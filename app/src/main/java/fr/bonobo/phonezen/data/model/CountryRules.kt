// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

/**
 * Représente les règles de blocage pour un pays spécifique.
 * 
 * @param countryCode Code ISO du pays (ex: "FR", "DE", "BE")
 * @param countryName Nom du pays en français
 * @param blockedPrefixes Liste des préfixes à bloquer systématiquement
 * @param telemarketingPrefixes Liste des préfixes de télémarketing
 * @param emergencyNumbers Liste des numéros d'urgence à ne jamais bloquer
 * @param neverBlock Liste des numéros/préfixes à toujours autoriser
 */
data class CountryRules(
    val countryCode: String,
    val countryName: String,
    val blockedPrefixes: List<String> = emptyList(),
    val telemarketingPrefixes: List<String> = emptyList(),
    val emergencyNumbers: List<String> = emptyList(),
    val neverBlock: List<String> = emptyList()
) {
    
    /**
     * Vérifie si un numéro est un numéro d'urgence pour ce pays.
     * 
     * @param number Le numéro à vérifier (peut être local ou international)
     * @return true si c'est un numéro d'urgence
     */
    fun isEmergency(number: String): Boolean {
        if (number.isBlank()) return false
        return emergencyNumbers.any { 
            number == it || 
            number.endsWith(it) || 
            number.contains(it) ||
            (it.startsWith("+") && number == it.substring(1)) ||
            (number.startsWith("+") && number == "+$it")
        }
    }
    
    /**
     * Vérifie si un numéro doit être bloqué selon les préfixes bloqués.
     * 
     * @param number Le numéro à vérifier (normalisé)
     * @return true si le numéro doit être bloqué
     */
    fun shouldBlock(number: String): Boolean {
        if (number.isBlank()) return false
        return blockedPrefixes.any { prefix ->
            // Vérifier si le numéro commence par le préfixe
            number.startsWith(prefix)
        } || telemarketingPrefixes.any { prefix ->
            number.startsWith(prefix)
        }
    }
    
    /**
     * Vérifie si un numéro doit toujours être autorisé.
     * 
     * @param number Le numéro à vérifier
     * @return true si le numéro doit être autorisé
     */
    fun shouldAllow(number: String): Boolean {
        if (number.isBlank()) return false
        return neverBlock.any { allowed ->
            number == allowed || 
            number.endsWith(allowed) ||
            number.startsWith(allowed)
        }
    }
    
    /**
     * Obtient tous les préfixes bloqués (blocked + telemarketing).
     */
    val allBlockedPrefixes: List<String> get() = blockedPrefixes + telemarketingPrefixes
}
