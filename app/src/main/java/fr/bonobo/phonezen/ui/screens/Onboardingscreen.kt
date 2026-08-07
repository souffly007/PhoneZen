// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class Onboardingscreen {
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.LocalColors
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    isDialerGranted    : Boolean,
    isScreeningGranted : Boolean,
    isContactsGranted  : Boolean,
    onRequestDialer    : () -> Unit,
    onRequestScreening : () -> Unit,
    onRequestContacts  : () -> Unit,
    onFinish           : () -> Unit
) {
    val c = LocalColors.current

    // Dès que tout est accordé → on avance automatiquement
    LaunchedEffect(isDialerGranted, isScreeningGranted, isContactsGranted) {
        if (isDialerGranted && isScreeningGranted && isContactsGranted) {
            delay(600) // petite pause pour que l'utilisateur voie le ✅ final
            onFinish()
        }
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); visible = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // ── Logo / Titre ──
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { -40 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🛡️", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "PhoneZen",
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color      = c.neonCyan
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Votre bouclier anti-spam\npour appels et contacts",
                    fontSize  = 15.sp,
                    color     = c.textSecond,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        // ── Explication ──
        AnimatedVisibility(visible = visible, enter = fadeIn() + slideInVertically { 40 }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = c.neonCyan.copy(alpha = 0.08f))
            ) {
                Text(
                    "Pour vous protéger efficacement, PhoneZen a besoin de quelques autorisations. " +
                            "Chaque bouton ci-dessous vous explique pourquoi avant de vous la demander.",
                    modifier  = Modifier.padding(16.dp),
                    fontSize  = 13.sp,
                    color     = c.neonCyan,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Bouton 1 : Rôle Dialer ──
        OnboardingButton(
            visible     = visible,
            delayMs     = 0,
            icon        = Icons.Default.Phone,
            emoji       = "📞",
            title       = "Activer le bouclier anti-spam",
            description = "Permet à PhoneZen de gérer vos appels et de bloquer automatiquement les numéros spam avant qu'ils ne sonnent.",
            buttonLabel = if (isDialerGranted) "✅ Activé" else "Activer maintenant",
            isGranted   = isDialerGranted,
            onClick     = { if (!isDialerGranted) onRequestDialer() },
            accentColor = c.neonCyan
        )

        Spacer(Modifier.height(16.dp))

        // ── Bouton 2 : Rôle Call Screening ──
        OnboardingButton(
            visible     = visible,
            delayMs     = 150,
            icon        = Icons.Default.Shield,
            emoji       = "🔍",
            title       = "Filtrage intelligent des appels",
            description = "Analyse les appels entrants en temps réel pour identifier les démarcheurs et les arnaques avant même que votre téléphone sonne.",
            buttonLabel = if (isScreeningGranted) "✅ Activé" else "Activer le filtrage",
            isGranted   = isScreeningGranted,
            onClick     = { if (!isScreeningGranted) onRequestScreening() },
            accentColor = c.neonOrange
        )

        Spacer(Modifier.height(16.dp))

        // ── Bouton 3 : Contacts ──
        OnboardingButton(
            visible     = visible,
            delayMs     = 300,
            icon        = Icons.Default.Contacts,
            emoji       = "👥",
            title       = "Protéger mes proches",
            description = "Accède à vos contacts pour ne jamais bloquer vos proches, afficher leurs noms dans le journal d'appels et faciliter leur gestion.",
            buttonLabel = if (isContactsGranted) "✅ Autorisé" else "Autoriser l'accès",
            isGranted   = isContactsGranted,
            onClick     = { if (!isContactsGranted) onRequestContacts() },
            accentColor = c.neonGreen
        )

        Spacer(Modifier.height(32.dp))

        // ── Bouton "Continuer" (disponible même sans tout accorder) ──
        AnimatedVisibility(visible = visible, enter = fadeIn()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = onFinish,
                    shape   = RoundedCornerShape(12.dp),
                    colors  = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecond),
                    border  = androidx.compose.foundation.BorderStroke(1.dp, c.glassStroke)
                ) {
                    Text("Continuer sans tout activer", fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Certaines fonctionnalités seront limitées",
                    fontSize = 11.sp,
                    color    = c.textSecond.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────
// COMPOSANT CARTE BOUTON D'ONBOARDING
// ─────────────────────────────────────────────
@Composable
private fun OnboardingButton(
    visible     : Boolean,
    delayMs     : Int,
    icon        : ImageVector,
    emoji       : String,
    title       : String,
    description : String,
    buttonLabel : String,
    isGranted   : Boolean,
    onClick     : () -> Unit,
    accentColor : androidx.compose.ui.graphics.Color
) {
    val c = LocalColors.current
    var itemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) { delay(delayMs.toLong()); itemVisible = true }
    }

    AnimatedVisibility(visible = itemVisible, enter = fadeIn() + slideInVertically { 60 }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = CardDefaults.cardColors(
                containerColor = if (isGranted)
                    accentColor.copy(alpha = 0.08f)
                else
                    c.surfaceVar
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isGranted) accentColor else c.textPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    description,
                    fontSize = 13.sp,
                    color    = c.textSecond
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick  = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    enabled  = !isGranted,
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = accentColor,
                        contentColor           = c.background,
                        disabledContainerColor = accentColor.copy(alpha = 0.3f),
                        disabledContentColor   = accentColor
                    )
                ) {
                    Icon(icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}