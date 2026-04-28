// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import android.accounts.Account
import android.content.ContentUris
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.bonobo.phonezen.ui.theme.AppColors
import fr.bonobo.phonezen.ui.theme.LocalColors
import fr.bonobo.phonezen.viewmodel.AddContactUiState
import fr.bonobo.phonezen.viewmodel.AddContactViewModel

/**
 * Écran création / modification de contact.
 *
 * @param prefillNumber  Numéro pré-rempli (depuis le journal)
 * @param contactId      Si non null → mode modification (ouvre l'éditeur natif Android)
 * @param onNavigateBack Callback retour
 */
@Composable
fun AddContactScreen(
    prefillNumber  : String  = "",
    contactId      : Long?   = null,       // ← NOUVEAU : null = création, sinon modification
    onNavigateBack : () -> Unit
) {
    val context = LocalContext.current
    val vm: AddContactViewModel = viewModel(
        factory = AddContactViewModel.Factory(context)
    )

    val c             = LocalColors.current
    val uiState       by vm.uiState.collectAsState()
    val availAccounts by vm.availableAccounts.collectAsState()

    // ── Mode modification : on délègue à l'app contacts native ──────────────
    // Plus simple, plus fiable, et l'utilisateur peut choisir son compte
    // directement dans l'interface Android standard.
    if (contactId != null) {
        LaunchedEffect(contactId) {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                data  = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI,
                    contactId
                )
                // Permet de revenir dans PhoneZen après modification
                putExtra("finishActivityOnSaveCompleted", true)
            }
            context.startActivity(intent)
            onNavigateBack()   // revenir à l'écran précédent pendant que l'éditeur s'ouvre
        }
        return
    }

    // ── Mode création ────────────────────────────────────────────────────────
    var firstName           by remember { mutableStateOf("") }
    var lastName            by remember { mutableStateOf("") }
    var phones              by remember { mutableStateOf(listOf(prefillNumber)) }
    var emails              by remember { mutableStateOf(listOf("")) }
    var selectedAccount     by remember { mutableStateOf<Account?>(null) }
    var accountMenuExpanded by remember { mutableStateOf(false) }

    // Auto-sélectionner le premier compte Google disponible
    LaunchedEffect(availAccounts) {
        if (selectedAccount == null) {
            selectedAccount = availAccounts.firstOrNull { it.type == "com.google" }
                ?: availAccounts.firstOrNull()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is AddContactUiState.Success) onNavigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
    ) {

        // ── TopBar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = c.neonCyan)
            }
            Text(
                text       = "Nouveau contact",
                color      = c.textPrimary,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, top = 48.dp)
            )
            TextButton(
                onClick  = { vm.saveContact(firstName, lastName, phones, emails, selectedAccount) },
                enabled  = uiState !is AddContactUiState.Loading
            ) {
                Text(
                    text       = "Enregistrer",
                    color      = c.neonCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp
                )
            }
        }

        // ── Formulaire ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(c.surface)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint     = c.neonCyan,
                    modifier = Modifier.size(48.dp)
                )
            }

            // ── Identité ────────────────────────────────────────────────────
            SectionLabel("Identité", c)
            ContactField(value = firstName, onValueChange = { firstName = it }, label = "Prénom", c = c)
            ContactField(value = lastName,  onValueChange = { lastName  = it }, label = "Nom",    c = c)

            // ── Téléphones ──────────────────────────────────────────────────
            SectionLabel("Téléphones", c)
            phones.forEachIndexed { i, phone ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactField(
                        value         = phone,
                        onValueChange = { v -> phones = phones.toMutableList().also { it[i] = v } },
                        label         = "Numéro ${i + 1}",
                        keyboardType  = KeyboardType.Phone,
                        c             = c,
                        modifier      = Modifier.weight(1f)
                    )
                    if (phones.size > 1) {
                        IconButton(onClick = { phones = phones.toMutableList().also { it.removeAt(i) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = c.neonRed)
                        }
                    }
                }
            }
            AddFieldBtn("Ajouter un numéro", c) { phones = phones + "" }

            // ── Emails ──────────────────────────────────────────────────────
            SectionLabel("Emails", c)
            emails.forEachIndexed { i, email ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContactField(
                        value         = email,
                        onValueChange = { v -> emails = emails.toMutableList().also { it[i] = v } },
                        label         = "Email ${i + 1}",
                        keyboardType  = KeyboardType.Email,
                        c             = c,
                        modifier      = Modifier.weight(1f)
                    )
                    if (emails.size > 1) {
                        IconButton(onClick = { emails = emails.toMutableList().also { it.removeAt(i) } }) {
                            Icon(Icons.Default.Close, contentDescription = "Supprimer", tint = c.neonRed)
                        }
                    }
                }
            }
            AddFieldBtn("Ajouter un email", c) { emails = emails + "" }

            // ── Compte Android ──────────────────────────────────────────────
            if (availAccounts.isNotEmpty()) {
                SectionLabel("Enregistrer dans", c)

                // Info compte Google sélectionné
                if (selectedAccount?.type == "com.google") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(8.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = c.neonCyan.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier          = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Cloud,
                                null,
                                tint     = c.neonCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Ce contact sera sauvegardé sur Google et synchronisé sur tous vos appareils.",
                                fontSize = 11.sp,
                                color    = c.neonCyan
                            )
                        }
                    }
                }

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surface)
                            .clickable { accountMenuExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text     = if (selectedAccount?.type == "com.google") "Google"
                                else selectedAccount?.type?.substringAfterLast('.') ?: "Téléphone (local)",
                                color    = c.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (selectedAccount != null) {
                                Text(
                                    text     = selectedAccount!!.name,
                                    color    = c.textSecond,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Text("▾", color = c.neonCyan, fontSize = 16.sp)
                    }
                    DropdownMenu(
                        expanded         = accountMenuExpanded,
                        onDismissRequest = { accountMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text("Téléphone (local)") },
                            onClick = { selectedAccount = null; accountMenuExpanded = false }
                        )
                        availAccounts.forEach { account ->
                            val label = when (account.type) {
                                "com.google" -> "Google · ${account.name}"
                                else         -> "${account.type.substringAfterLast('.')} · ${account.name}"
                            }
                            DropdownMenuItem(
                                text    = { Text(label) },
                                onClick = { selectedAccount = account; accountMenuExpanded = false }
                            )
                        }
                    }
                }
            }

            // ── Erreur / Loading ────────────────────────────────────────────
            if (uiState is AddContactUiState.Error) {
                Text(
                    text     = (uiState as AddContactUiState.Error).message,
                    color    = c.neonRed,
                    fontSize = 13.sp
                )
            }
            if (uiState is AddContactUiState.Loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.neonCyan, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

// ── Composables privés ───────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, c: AppColors) {
    Text(
        text          = text.uppercase(),
        color         = c.neonCyan,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun ContactField(
    value         : String,
    onValueChange : (String) -> Unit,
    label         : String,
    keyboardType  : KeyboardType = KeyboardType.Text,
    c             : AppColors,
    modifier      : Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label, fontSize = 13.sp) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier        = modifier,
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = c.neonCyan,
            unfocusedBorderColor    = c.glassStroke,
            focusedLabelColor       = c.neonCyan,
            unfocusedLabelColor     = c.textSecond,
            cursorColor             = c.neonCyan,
            focusedTextColor        = c.textPrimary,
            unfocusedTextColor      = c.textPrimary,
            focusedContainerColor   = c.surface,
            unfocusedContainerColor = c.surface
        ),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun AddFieldBtn(label: String, c: AppColors, onClick: () -> Unit) {
    TextButton(
        onClick        = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = c.neonCyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = c.neonCyan, fontSize = 14.sp)
    }
}