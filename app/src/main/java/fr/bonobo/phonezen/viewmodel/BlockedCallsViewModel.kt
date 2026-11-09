// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.local.AppDatabase
import fr.bonobo.phonezen.data.local.BlockedCall
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel pour la gestion de l'historique des appels bloqués.
 * 
 * Ce ViewModel expose la liste des appels bloqués et permet de les supprimer.
 * 
 * @param application Contexte de l'application
 */
class BlockedCallsViewModel(application: Application) : AndroidViewModel(application) {

    private val db: AppDatabase = AppDatabase.getDatabase(application)
    
    /**
     * Flux des appels bloqués, triés par date décroissante.
     */
    val blockedCalls: StateFlow<List<BlockedCall>> = 
        db.blockedCallDao().getAllBlockedCalls()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    /**
     * Supprime un appel bloqué par son ID.
     * 
     * @param callId ID de l'appel bloqué à supprimer
     */
    fun deleteBlockedCall(callId: Int) {
        viewModelScope.launch {
            db.blockedCallDao().deleteById(callId)
        }
    }

    /**
     * Supprime tous les appels bloqués.
     */
    fun deleteAllBlockedCalls() {
        viewModelScope.launch {
            db.blockedCallDao().deleteAll()
        }
    }

    /**
     * Obtient le nombre total d'appels bloqués.
     * 
     * @return Nombre d'appels bloqués
     */
    fun getBlockedCallsCount(): Int {
        return blockedCalls.value.size
    }

    /**
     * Filtre les appels bloqués par niveau de risque.
     * 
     * @param riskLevel Niveau de risque à filtrer
     * @return Liste des appels bloqués avec ce niveau de risque
     */
    fun getBlockedCallsByRiskLevel(riskLevel: String): List<BlockedCall> {
        return blockedCalls.value.filter { it.riskLevel == riskLevel }
    }

    /**
     * Filtre les appels bloqués par raison.
     * 
     * @param reason Raison à filtrer
     * @return Liste des appels bloqués avec cette raison
     */
    fun getBlockedCallsByReason(reason: String): List<BlockedCall> {
        return blockedCalls.value.filter { it.reason.contains(reason, ignoreCase = true) }
    }

    /**
     * Recherche des appels bloqués par numéro.
     * 
     * @param query Numéro à rechercher
     * @return Liste des appels bloqués correspondant à la recherche
     */
    fun searchBlockedCalls(query: String): List<BlockedCall> {
        return blockedCalls.value.filter {
            it.number.contains(query, ignoreCase = true)
        }
    }
}
