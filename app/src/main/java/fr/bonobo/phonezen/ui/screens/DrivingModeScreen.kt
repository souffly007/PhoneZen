// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class DrivingModeScreen {
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.service.DrivingModeManager
import fr.bonobo.phonezen.ui.theme.LocalColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrivingModeScreen(
    drivingModeManager: DrivingModeManager,
    onBack            : () -> Unit
) {
    val c   = LocalColors.current
    val ctx = LocalContext.current

    val isDriving       by drivingModeManager.isDriving.collectAsState()
    val isAutoEnabled   by drivingModeManager.isAutoDetectionEnabled.collectAsState()

    // Animation pulsation du bouton principal
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue   = 1f,
        targetValue    = if (isDriving) 1.06f else 1f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mode conduite",
                        color      = c.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            Spacer(Modifier.height(16.dp))

            // ── Bouton principal ON/OFF ───────────────────────────────────────
            Box(
                modifier         = Modifier
                    .size(180.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(if (isDriving) c.neonOrange else c.surface)
                    .border(
                        width = 3.dp,
                        color = if (isDriving) c.neonOrange else c.glassStroke,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick  = { drivingModeManager.setManualDriving(!isDriving) },
                    modifier = Modifier.size(180.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = "Mode conduite",
                            tint     = if (isDriving) c.background else c.textPrimary,
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            text       = if (isDriving) "ACTIF" else "INACTIF",
                            color      = if (isDriving) c.background else c.textSecond,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp
                        )
                    }
                }
            }

            // Statut textuel
            Text(
                text      = if (isDriving)
                    "Mode conduite activé\nLes appels reçoivent une réponse automatique"
                else
                    "Mode conduite désactivé\nAppuyez pour activer manuellement",
                color     = if (isDriving) c.neonOrange else c.textSecond,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            HorizontalDivider(color = c.glassStroke)

            // ── Carte détection automatique ───────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = c.surface)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Sensors, null,
                                tint     = c.neonOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    "Détection automatique",
                                    color      = c.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 15.sp
                                )
                                Text(
                                    "GPS + accéléromètre",
                                    color    = c.textSecond,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Switch(
                            checked         = isAutoEnabled,
                            onCheckedChange = {
                                drivingModeManager.setAutoDetectionEnabled(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor   = c.background,
                                checkedTrackColor   = c.neonOrange,
                                uncheckedThumbColor = c.textSecond,
                                uncheckedTrackColor = c.surfaceVar
                            )
                        )
                    }

                    AnimatedVisibility(visible = isAutoEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DetectionIndicator(
                                label  = "GPS",
                                icon   = Icons.Default.GpsFixed,
                                active = isAutoEnabled,
                                c      = c
                            )
                            DetectionIndicator(
                                label  = "Accéléromètre",
                                icon   = Icons.Default.Speed,
                                active = isAutoEnabled,
                                c      = c
                            )
                        }
                    }
                }
            }

            // ── Carte SMS automatique ─────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = c.surface)
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Sms, null,
                            tint     = c.neonOrange,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "Réponse automatique",
                            color      = c.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = CardDefaults.cardColors(containerColor = c.surfaceVar)
                    ) {
                        Text(
                            text     = DrivingModeManager.SMS_AUTO_REPLY,
                            color    = c.textPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        "Ce SMS est envoyé automatiquement à chaque appelant entrant quand le mode conduite est actif.",
                        color      = c.textSecond,
                        fontSize   = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            // ── Carte lecture vocale ──────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = c.surface)
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.RecordVoiceOver, null,
                        tint     = c.neonOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            "Lecture vocale",
                            color      = c.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp
                        )
                        Text(
                            "Le nom de l'appelant est annoncé via le haut-parleur",
                            color      = c.textSecond,
                            fontSize   = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectionIndicator(
    label : String,
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    c     : fr.bonobo.phonezen.ui.theme.AppColors
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) c.neonOrange else c.textSecond)
        )
        Icon(icon, null, tint = c.textSecond, modifier = Modifier.size(16.dp))
        Text(label, color = c.textSecond, fontSize = 13.sp)
    }
}