// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.viewmodel

import android.accounts.Account
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.repository.AddContactRepository
import fr.bonobo.phonezen.data.repository.NewContactData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AddContactUiState {
    object Idle    : AddContactUiState()
    object Loading : AddContactUiState()
    object Success : AddContactUiState()
    data class Error(val message: String) : AddContactUiState()
}

class AddContactViewModel(
    private val repository : AddContactRepository,
    private val appContext : Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddContactUiState>(AddContactUiState.Idle)
    val uiState: StateFlow<AddContactUiState> = _uiState.asStateFlow()

    private val _availableAccounts = MutableStateFlow<List<Account>>(emptyList())
    val availableAccounts: StateFlow<List<Account>> = _availableAccounts.asStateFlow()

    init { loadAccounts() }

    private fun loadAccounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _availableAccounts.value = repository.getAvailableAccounts()
        }
    }

    // ─────────────────────────────────────────────
    // CRÉATION
    // ─────────────────────────────────────────────
    fun saveContact(
        firstName       : String,
        lastName        : String,
        phones          : List<String>,
        emails          : List<String>,
        selectedAccount : Account?
    ) {
        if (firstName.isBlank() && lastName.isBlank() && phones.all { it.isBlank() }) {
            _uiState.value = AddContactUiState.Error("Veuillez renseigner au moins un nom ou un numéro.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AddContactUiState.Loading
            val success = repository.createContact(
                NewContactData(
                    firstName = firstName,
                    lastName  = lastName,
                    phones    = phones.filter { it.isNotBlank() },
                    emails    = emails.filter { it.isNotBlank() },
                    account   = selectedAccount
                )
            )
            _uiState.value = if (success) AddContactUiState.Success
            else AddContactUiState.Error("Échec de la création du contact.")
        }
    }

    // ─────────────────────────────────────────────
    // MODIFICATION — ouvre l'éditeur natif Android
    // ─────────────────────────────────────────────
    /**
     * Lance l'éditeur de contact natif Android pour modifier un contact existant.
     * Déléguer à l'app native est la meilleure approche :
     *  - L'utilisateur peut changer le compte de synchronisation
     *  - Tous les champs sont accessibles (photo, adresse, etc.)
     *  - Aucun risque de corruption des données
     *
     * @param contactId L'ID du contact dans ContentResolver (ContactsContract.Contacts._ID)
     */
    fun editContact(contactId: Long) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            data  = ContentUris.withAppendedId(
                ContactsContract.Contacts.CONTENT_URI,
                contactId
            )
            // Retour dans PhoneZen après sauvegarde
            putExtra("finishActivityOnSaveCompleted", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun resetState() { _uiState.value = AddContactUiState.Idle }

    // ─────────────────────────────────────────────
    // FACTORY
    // ─────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddContactViewModel(
                repository = AddContactRepository(context.applicationContext),
                appContext = context.applicationContext
            ) as T
    }
}