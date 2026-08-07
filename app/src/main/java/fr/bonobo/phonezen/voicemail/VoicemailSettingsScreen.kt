// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoicemailSettingsScreen {
// SPDX-License-Identifier: GPL-3.0-or-later
package fr.bonobo.phonezen.voicemail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.AppColors
import fr.bonobo.phonezen.ui.theme.LocalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailSettingsScreen(onBack: () -> Unit) {
    val context  = LocalContext.current
    val c        = LocalColors.current
    val manager  = remember { VvmCredentialsManager(context) }
    val existing = remember { manager.load() }

    var host         by remember { mutableStateOf(existing?.host          ?: "") }
    var port         by remember { mutableStateOf(existing?.port?.toString() ?: "993") }
    var user         by remember { mutableStateOf(existing?.user          ?: "") }
    var password     by remember { mutableStateOf(existing?.password      ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var saved        by remember { mutableStateOf(false) }
    var errorMsg     by remember { mutableStateOf("") }

    val presets = listOf(
        Triple("Orange",   "imap.orange.fr", 993),
        Triple("SFR",      "imap.sfr.fr",    993),
        Triple("Bouygues", "imap.bbox.fr",   993),
        Triple("Free",     "imap.free.fr",   993)
    )

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Réglages messagerie vocale", color = c.textPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Retour", tint = c.neonOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Opérateurs prédéfinis
            SectionCard(title = "Opérateur", c = c) {
                Text(
                    "Sélectionne ton opérateur pour remplir automatiquement",
                    color = c.textSecond, fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (label, presetHost, presetPort) ->
                        FilterChip(
                            selected = host == presetHost,
                            onClick  = { host = presetHost; port = presetPort.toString() },
                            label    = { Text(label, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.neonOrange,
                                selectedLabelColor     = c.background,
                                containerColor         = c.surfaceVar,
                                labelColor             = c.textSecond
                            )
                        )
                    }
                }
            }

            // Serveur IMAP
            SectionCard(title = "Serveur IMAP", c = c) {
                VvmTextField(
                    value = host, onValueChange = { host = it; saved = false },
                    label = "Hôte IMAP", placeholder = "imap.orange.fr",
                    icon  = Icons.Default.Dns, c = c, keyboardType = KeyboardType.Uri
                )
                VvmTextField(
                    value = port, onValueChange = { port = it; saved = false },
                    label = "Port", placeholder = "993",
                    icon  = Icons.Default.Router, c = c, keyboardType = KeyboardType.Number
                )
            }

            // Compte
            SectionCard(title = "Compte", c = c) {
                VvmTextField(
                    value = user, onValueChange = { user = it; saved = false },
                    label = "Identifiant", placeholder = "0612345678 ou email",
                    icon  = Icons.Default.Person, c = c, keyboardType = KeyboardType.Email
                )
                OutlinedTextField(
                    value         = password,
                    onValueChange = { password = it; saved = false },
                    label         = { Text("Mot de passe", color = c.textSecond) },
                    placeholder   = { Text("••••••••",     color = c.textSecond) },
                    leadingIcon   = { Icon(Icons.Default.Lock, null, tint = c.neonOrange) },
                    trailingIcon  = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                "Voir", tint = c.textSecond
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier        = Modifier.fillMaxWidth(),
                    shape           = RoundedCornerShape(12.dp),
                    colors          = vvmFieldColors(c)
                )
            }

            // Feedback
            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = c.neonRed, fontSize = 13.sp)
            }
            if (saved) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = c.neonOrange, modifier = Modifier.size(18.dp))
                    Text("Credentials sauvegardés", color = c.neonOrange, fontSize = 13.sp)
                }
            }

            // Bouton Sauvegarder
            Button(
                onClick = {
                    errorMsg = ""
                    val portInt = port.toIntOrNull()
                    when {
                        host.isBlank()     -> errorMsg = "L'hôte IMAP est requis"
                        portInt == null    -> errorMsg = "Port invalide"
                        else -> {
                            manager.save(VvmCredentialsManager.ImapCredentials(
                                host = host.trim(), port = portInt,
                                user = user.trim(), password = password
                            ))
                            saved = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = c.neonOrange)
            ) {
                Icon(Icons.Default.Save, null, tint = c.background)
                Spacer(Modifier.width(8.dp))
                Text("Sauvegarder", color = c.background, fontWeight = FontWeight.Bold)
            }

            // Bouton Supprimer
            if (manager.hasCredentials()) {
                OutlinedButton(
                    onClick  = {
                        manager.clear()
                        host = ""; port = "993"; user = ""; password = ""; saved = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    border   = BorderStroke(1.dp, c.neonRed),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = c.neonRed)
                ) {
                    Icon(Icons.Default.DeleteForever, null, tint = c.neonRed)
                    Spacer(Modifier.width(8.dp))
                    Text("Supprimer les credentials", color = c.neonRed)
                }
            }

            // Note sécurité
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = c.surfaceVar)
            ) {
                Row(
                    modifier              = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Icon(Icons.Default.Security, null, tint = c.neonOrange, modifier = Modifier.size(20.dp))
                    Text(
                        "Le mot de passe est chiffré via Android Keystore (AES-256-GCM) et ne quitte jamais l'appareil.",
                        color = c.textSecond, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

// ─── Composables utilitaires ──────────────────────────────────────────────────

@Composable
private fun SectionCard(
    title  : String,
    c      : AppColors,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = c.surface)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, color = c.neonOrange, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            content()
        }
    }
}

@Composable
private fun VvmTextField(
    value        : String,
    onValueChange: (String) -> Unit,
    label        : String,
    placeholder  : String,
    icon         : androidx.compose.ui.graphics.vector.ImageVector,
    c            : AppColors,
    keyboardType : KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        label           = { Text(label,       color = c.textSecond) },
        placeholder     = { Text(placeholder, color = c.textSecond) },
        leadingIcon     = { Icon(icon, null,  tint  = c.neonOrange) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(12.dp),
        colors          = vvmFieldColors(c)
    )
}

@Composable
private fun vvmFieldColors(c: AppColors) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = c.neonOrange,
    unfocusedBorderColor = c.glassStroke,
    focusedTextColor     = c.textPrimary,
    unfocusedTextColor   = c.textPrimary,
    cursorColor          = c.neonOrange,
    focusedLabelColor    = c.neonOrange
)