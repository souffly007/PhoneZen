// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.BlockingProfile
import fr.bonobo.phonezen.data.model.VacationConfig
import fr.bonobo.phonezen.ui.theme.LocalColors
import fr.bonobo.phonezen.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProfileScreen(vm: MainViewModel, onBack: () -> Unit = {}) {
    val c              = LocalColors.current
    val activeProfile  by vm.activeProfile.collectAsState()
    val vacationConfig by vm.vacationConfig.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
    ) {
        // ── TopBar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = c.neonCyan)
            }
            Text(
                text       = "Profils de blocage",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonCyan,
                modifier   = Modifier.padding(start = 4.dp)
            )
        }

        // ── Info contexte ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = c.neonCyan.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = c.neonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Le profil actif remplace les autres règles de blocage. " +
                            "La liste blanche reste toujours prioritaire.",
                    fontSize = 12.sp,
                    color    = c.neonCyan
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Les 3 profils ──
        ProfileCard(
            profile  = BlockingProfile.WORK,
            isActive = activeProfile == BlockingProfile.WORK,
            onSelect = { vm.setActiveProfile(BlockingProfile.WORK) }
        ) {
            ProfileDetail("✅ Autorisés",  "Contacts, favoris, numéros 08/09 non-surtaxés")
            ProfileDetail("🚫 Bloqués",    "Démarchage ARCEP, signalements communautaires, masqués")
            ProfileDetail("💡 Idéal pour", "Heures de bureau, attente de rappels professionnels")
        }

        ProfileCard(
            profile  = BlockingProfile.HOME,
            isActive = activeProfile == BlockingProfile.HOME,
            onSelect = { vm.setActiveProfile(BlockingProfile.HOME) }
        ) {
            ProfileDetail("✅ Autorisés",  "Contacts et favoris uniquement")
            ProfileDetail("🚫 Bloqués",    "Tout le reste (inconnus, démarchage, pro...)")
            ProfileDetail("💡 Idéal pour", "Soirées et week-ends à la maison")
        }

        ProfileCard(
            profile  = BlockingProfile.VACATION,
            isActive = activeProfile == BlockingProfile.VACATION,
            onSelect = { vm.setActiveProfile(BlockingProfile.VACATION) }
        ) {
            ProfileDetail("✅ Autorisés",  "Favoris et liste blanche uniquement")
            ProfileDetail("🚫 Bloqués",    "Tout le reste, y compris contacts non-favoris")
            ProfileDetail("💡 Idéal pour", "Vacances, week-ends prolongés, repos total")

            AnimatedVisibility(visible = activeProfile == BlockingProfile.VACATION) {
                VacationConfigSection(
                    config  = vacationConfig,
                    onSave  = { vm.saveVacationConfig(it) },
                    onClear = { vm.clearVacationEndDate() }
                )
            }
        }

        // ── Résumé du profil actif ──
        Spacer(Modifier.height(8.dp))
        ActiveProfileSummary(activeProfile, vacationConfig)
        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────
// CARD PROFIL
// ─────────────────────────────────────────────
@Composable
private fun ProfileCard(
    profile  : BlockingProfile,
    isActive : Boolean,
    onSelect : () -> Unit,
    content  : @Composable ColumnScope.() -> Unit
) {
    val c           = LocalColors.current
    val borderColor = if (isActive) c.neonCyan else c.glassStroke
    val bgColor     = if (isActive) c.neonCyan.copy(alpha = 0.06f) else c.surfaceVar

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isActive) 2.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onSelect() },
        shape  = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text       = profile.label,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (isActive) c.neonCyan else c.textPrimary
                    )
                    Text(
                        text     = profile.subtitle,
                        fontSize = 12.sp,
                        color    = c.textSecond
                    )
                }
                if (isActive) {
                    Surface(shape = RoundedCornerShape(20.dp), color = c.neonCyan) {
                        Text(
                            text       = "ACTIF",
                            fontSize   = 10.sp,
                            color      = c.background,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ProfileDetail(label: String, value: String) {
    val c = LocalColors.current
    Row(
        modifier          = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            fontSize   = 12.sp,
            color      = c.textSecond,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.width(90.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 12.sp, color = c.textPrimary, modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────
// CONFIGURATION VACANCES
// Base : ton code coulissant (pas de chevauchement)
// Ajout : sélecteur "Profil de retour" sous la date
// ─────────────────────────────────────────────
@Composable
private fun VacationConfigSection(
    config  : VacationConfig,
    onSave  : (VacationConfig) -> Unit,
    onClear : () -> Unit
) {
    val c = LocalColors.current
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 12.dp),
            color    = c.glassStroke
        )

        Text(
            "⚙️ Options vacances",
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
            color      = c.neonCyan,
            modifier   = Modifier.padding(bottom = 8.dp)
        )

        // ── 1. DATE DE RETOUR ──────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(10.dp),
            colors   = CardDefaults.cardColors(containerColor = c.background.copy(alpha = 0.4f))
        ) {
            Row(
                modifier          = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.EventAvailable, null, tint = c.neonCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Date de retour automatique",
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color      = c.textPrimary
                    )
                    val dateTxt = if (config.hasEndDate)
                        SimpleDateFormat("EEE dd MMMM yyyy", Locale.FRANCE).format(Date(config.endTimestamp))
                    else "Désactivation manuelle"
                    Text(
                        dateTxt,
                        fontSize = 11.sp,
                        color    = if (config.hasEndDate) c.neonCyan else c.textSecond
                    )
                }
                if (config.hasEndDate) {
                    IconButton(onClick = onClear, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, null, tint = c.neonRed, modifier = Modifier.size(16.dp))
                    }
                }
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        if (config.hasEndDate) "Modifier" else "Définir",
                        color    = c.neonCyan,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ── 2. PROFIL DE RETOUR (coulisse sous la date si une date est définie) ──
        AnimatedVisibility(
            visible = config.hasEndDate,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Column {
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = CardDefaults.cardColors(containerColor = c.background.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                null,
                                tint     = c.neonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Profil à la fin des vacances",
                                    fontSize   = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color      = c.textPrimary
                                )
                                Text(
                                    "Basculera automatiquement le jour du retour",
                                    fontSize = 11.sp,
                                    color    = c.textSecond
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Sélecteur 2 boutons — Domicile / Travail
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(BlockingProfile.HOME, BlockingProfile.WORK).forEach { profile ->
                                val isSelected = config.returnProfile == profile
                                OutlinedButton(
                                    onClick  = { onSave(config.copy(returnProfile = profile)) },
                                    modifier = Modifier.weight(1f),
                                    shape    = RoundedCornerShape(10.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isSelected) c.neonCyan.copy(alpha = 0.15f) else c.surfaceVar,
                                        contentColor   = if (isSelected) c.neonCyan else c.textSecond
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) c.neonCyan else c.glassStroke
                                    )
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(profile.emoji, fontSize = 18.sp)
                                        Text(
                                            profile.label,
                                            fontSize   = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 3. NE PAS DÉRANGER LA NUIT ────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NightlightRound, null, tint = c.neonCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Ne pas déranger la nuit",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = c.textPrimary
                    )
                    Text("Silence total programmé", fontSize = 11.sp, color = c.textSecond)
                }
            }
            Switch(
                checked         = config.autoNightDnd,
                onCheckedChange = { onSave(config.copy(autoNightDnd = it)) },
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = c.background,
                    checkedTrackColor   = c.neonCyan,
                    uncheckedThumbColor = c.textSecond,
                    uncheckedTrackColor = c.glassStroke
                )
            )
        }

        // ── 4. HORAIRES (coulisse sous le switch si DND activé) ──
        AnimatedVisibility(
            visible = config.autoNightDnd,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = c.neonCyan.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DÉBUT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textSecond)
                            Text(
                                "${config.nightStart.toString().padStart(2, '0')}h",
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = c.neonCyan
                            )
                        }
                        Icon(Icons.Default.ArrowForward, null, tint = c.neonCyan.copy(0.3f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.textSecond)
                            Text(
                                "${config.nightEnd.toString().padStart(2, '0')}h",
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = c.neonCyan
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = c.neonOrange, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Blocage strict de ${config.nightStart}h à ${config.nightEnd}h",
                            fontSize   = 11.sp,
                            color      = c.neonOrange,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        VacationDatePickerDialog(
            currentTs = config.endTimestamp,
            onConfirm = { ts ->
                onSave(config.copy(endTimestamp = ts))
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

// ─────────────────────────────────────────────
// DATE PICKER
// ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacationDatePickerDialog(
    currentTs : Long,
    onConfirm : (Long) -> Unit,
    onDismiss : () -> Unit
) {
    val c     = LocalColors.current
    val state = rememberDatePickerState(
        initialSelectedDateMillis = if (currentTs > 0) currentTs
        else System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                utcTimeMillis > System.currentTimeMillis()
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton    = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(it) }
            }) { Text("OK", color = c.neonCyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler", color = c.textSecond) }
        },
        colors = DatePickerDefaults.colors(containerColor = c.surfaceVar)
    ) {
        DatePicker(
            state  = state,
            colors = DatePickerDefaults.colors(
                containerColor             = c.surfaceVar,
                titleContentColor          = c.neonCyan,
                headlineContentColor       = c.neonCyan,
                weekdayContentColor        = c.textSecond,
                subheadContentColor        = c.textSecond,
                navigationContentColor     = c.neonCyan,
                yearContentColor           = c.textPrimary,
                currentYearContentColor    = c.neonCyan,
                selectedYearContentColor   = c.background,
                selectedYearContainerColor = c.neonCyan,
                dayContentColor            = c.textPrimary,
                selectedDayContentColor    = c.background,
                selectedDayContainerColor  = c.neonCyan,
                todayContentColor          = c.neonCyan,
                todayDateBorderColor       = c.neonCyan
            )
        )
    }
}

// ─────────────────────────────────────────────
// RÉSUMÉ DU PROFIL ACTIF
// ─────────────────────────────────────────────
@Composable
private fun ActiveProfileSummary(profile: BlockingProfile, vacationConfig: VacationConfig) {
    val c = LocalColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.surfaceVar)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Résumé du profil actif",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonCyan,
                modifier   = Modifier.padding(bottom = 8.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(profile.emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "${profile.label} actif",
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color      = c.textPrimary
                    )
                    val msg = when (profile) {
                        BlockingProfile.WORK     -> "Contacts, favoris et numéros pro passent librement."
                        BlockingProfile.HOME     -> "Seuls vos contacts et favoris peuvent vous joindre."
                        BlockingProfile.VACATION -> {
                            if (vacationConfig.hasEndDate && !vacationConfig.isExpired) {
                                val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
                                val returnLabel = vacationConfig.returnProfile.label
                                "Favoris uniquement · Retour le ${fmt.format(Date(vacationConfig.endTimestamp))} → $returnLabel"
                            } else {
                                "Favoris uniquement · Aucune date de retour définie."
                            }
                        }
                    }
                    Text(msg, fontSize = 12.sp, color = c.textSecond)
                }
            }

            if (profile == BlockingProfile.VACATION && vacationConfig.autoNightDnd) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NightlightRound, null, tint = c.neonOrange, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "DND nocturne actif : ${vacationConfig.nightStart}h–${vacationConfig.nightEnd}h",
                        fontSize = 11.sp,
                        color    = c.neonOrange
                    )
                }
            }
        }
    }
}