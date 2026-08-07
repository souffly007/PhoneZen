// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.BlockingProfile
import androidx.core.content.FileProvider
import fr.bonobo.phonezen.data.model.CallPopupMode
import fr.bonobo.phonezen.utils.CrashHandler
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.utils.BackupManager
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    themeVm: ThemeViewModel,
    onNavigateToWhitelist  : () -> Unit = {},
    onNavigateToTheme      : () -> Unit = {},
    onNavigateToTopReported: () -> Unit = {},
    onNavigateToProfiles   : () -> Unit = {}
) {
    val c                = LocalColors.current
    val ctx              = LocalContext.current
    val scope            = rememberCoroutineScope()
    val blockPrivate     by vm.blockPrivate.collectAsState()
    val hideBlocked      by vm.hideBlocked.collectAsState()
    val doNotDisturb     by vm.doNotDisturb.collectAsState()
    val scheduleEnabled  by vm.scheduleEnabled.collectAsState()
    val scheduleStartH   by vm.scheduleStartHour.collectAsState()
    val scheduleStartM   by vm.scheduleStartMinute.collectAsState()
    val scheduleEndH     by vm.scheduleEndHour.collectAsState()
    val scheduleEndM     by vm.scheduleEndMinute.collectAsState()
    val currentTheme     by themeVm.theme.collectAsState()
    val activeProfile    by vm.activeProfile.collectAsState()
    val vacationConfig   by vm.vacationConfig.collectAsState()
    val callPopupMode    by vm.callPopupMode.collectAsState()

    val hospitalEnabled  by vm.hospitalWhitelistEnabled.collectAsState()
    val hospitalCount    by vm.hospitalEntriesCount.collectAsState()

    var showStartPicker  by remember { mutableStateOf(false) }
    var showEndPicker    by remember { mutableStateOf(false) }

    var backupMessage      by remember { mutableStateOf<String?>(null) }
    var isBackupLoading    by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var pendingRestoreUri  by remember { mutableStateOf<Uri?>(null) }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri  = uri
            showRestoreConfirm = true
        }
    }

    // Vérification permission overlay (SYSTEM_ALERT_WINDOW)
    val canDrawOverlays = remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    val hasCrashReport  = remember { mutableStateOf(CrashHandler.hasCrashReports(ctx)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsTopBar("Réglages")

        // ══════════════════════════════════════════════════════════════
        // PROFILS DE BLOCAGE
        // ══════════════════════════════════════════════════════════════
        SectionHeader("👤 Profil de blocage")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceVar),
            onClick = onNavigateToProfiles
        ) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(activeProfile.emoji, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Profil : ${activeProfile.label}",
                            fontSize   = 15.sp,
                            color      = c.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when (activeProfile) {
                                BlockingProfile.WORK     -> c.neonCyan.copy(alpha = 0.2f)
                                BlockingProfile.HOME     -> c.neonOrange.copy(alpha = 0.2f)
                                BlockingProfile.VACATION -> c.neonGreen.copy(alpha = 0.2f)
                            }
                        ) {
                            Text(
                                text     = "ACTIF",
                                fontSize = 9.sp,
                                color    = when (activeProfile) {
                                    BlockingProfile.WORK     -> c.neonCyan
                                    BlockingProfile.HOME     -> c.neonOrange
                                    BlockingProfile.VACATION -> c.neonGreen
                                },
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    val subtitle = when (activeProfile) {
                        BlockingProfile.WORK     -> "Contacts, favoris et numéros pro autorisés"
                        BlockingProfile.HOME     -> "Contacts et favoris uniquement"
                        BlockingProfile.VACATION ->
                            if (vacationConfig.hasEndDate && !vacationConfig.isExpired) {
                                val fmt = java.text.SimpleDateFormat("dd/MM", java.util.Locale.FRANCE)
                                "Favoris uniquement · Retour le ${fmt.format(java.util.Date(vacationConfig.endTimestamp))}"
                            } else {
                                "Favoris et liste blanche uniquement"
                            }
                    }
                    Text(subtitle, fontSize = 12.sp, color = c.textSecond)
                }
                Icon(Icons.Default.ChevronRight, null, tint = c.textSecond)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BlockingProfile.entries.forEach { profile ->
                val isActive = activeProfile == profile
                OutlinedButton(
                    onClick  = { vm.setActiveProfile(profile) },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isActive) c.neonCyan.copy(alpha = 0.12f) else c.surfaceVar,
                        contentColor   = if (isActive) c.neonCyan else c.textSecond
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isActive) 1.5.dp else 0.5.dp,
                        color = if (isActive) c.neonCyan else c.glassStroke
                    )
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(profile.emoji, fontSize = 18.sp)
                        Text(profile.label, fontSize = 10.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // PROTECTION
        // ══════════════════════════════════════════════════════════════
        SectionHeader("🛡️ Protection")

        SettingSwitch(
            icon     = Icons.Default.VisibilityOff,
            title    = "Bloquer numéros privés/masqués",
            subtitle = "Rejette automatiquement les appels sans numéro",
            checked  = blockPrivate,
            onToggle = { vm.setBlockPrivate(it) }
        )
        SettingSwitch(
            icon     = Icons.Default.FilterList,
            title    = "Masquer les appels bloqués",
            subtitle = "Les appels bloqués n'apparaissent pas dans les récents",
            checked  = hideBlocked,
            onToggle = { vm.setHideBlocked(it) }
        )
        SettingItem(
            icon     = Icons.Default.Block,
            title    = "Filtres anti-spam actifs",
            subtitle = "prefixes_blocked_fr.json v4.1 (2026-04-05)",
            onClick  = {}
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceVar)
        ) {
            Row(
                modifier          = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocalHospital,
                    contentDescription = null,
                    tint     = if (hospitalEnabled) c.neonCyan else c.textSecond,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Whitelist établissements de santé",
                        fontSize   = 15.sp,
                        color      = c.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        when {
                            !hospitalEnabled  -> "Désactivée — les hôpitaux peuvent être bloqués"
                            hospitalCount > 0 -> "$hospitalCount établissements · Source FINESS 2026"
                            else              -> "Chargement..."
                        },
                        fontSize = 12.sp,
                        color    = if (hospitalEnabled) c.textSecond else c.neonOrange
                    )
                }
                Switch(
                    checked         = hospitalEnabled,
                    onCheckedChange = { vm.setHospitalWhitelistEnabled(it) },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = c.background,
                        checkedTrackColor   = c.neonCyan,
                        uncheckedThumbColor = c.textSecond,
                        uncheckedTrackColor = c.glassStroke
                    )
                )
            }
        }

        if (!hospitalEnabled) {
            InfoCard("⚠️ Désactivée : les appels d'hôpitaux, SAMU et urgences peuvent être bloqués par les autres filtres.")
        }

        SettingItem(
            icon     = Icons.Default.WarningAmber,
            title    = "Top numéros signalés",
            subtitle = "Numéros les plus signalés par la communauté",
            onClick  = onNavigateToTopReported
        )
        SettingSwitch(
            icon     = Icons.Default.Groups,
            title    = "Blocage communautaire auto",
            subtitle = "Bloque automatiquement les numéros signalés 10× ou plus",
            checked  = vm.spamDetector.isCommunityBlockEnabled(),
            onToggle = { vm.spamDetector.setCommunityBlockEnabled(it) }
        )

        // ══════════════════════════════════════════════════════════════
        // NE PAS DÉRANGER
        // ══════════════════════════════════════════════════════════════
        SectionHeader("🌙 Mode Ne pas déranger")

        SettingSwitch(
            icon     = Icons.Default.DoNotDisturb,
            title    = "Ne pas déranger",
            subtitle = if (doNotDisturb)
                "Actif — tous les appels bloqués sauf liste blanche"
            else
                "Inactif — tous les appels passent normalement",
            checked  = doNotDisturb,
            onToggle = { vm.setDoNotDisturb(it) }
        )
        if (doNotDisturb) {
            InfoCard("⚠️ Seuls les numéros de votre liste blanche et les services d'urgence (15, 17, 18, 112) peuvent vous joindre.")
        }

        // ══════════════════════════════════════════════════════════════
        // HORAIRES
        // ══════════════════════════════════════════════════════════════
        SectionHeader("⏰ Horaires de blocage")

        SettingSwitch(
            icon     = Icons.Default.Schedule,
            title    = "Blocage par horaires",
            subtitle = "Bloque les inconnus/spam pendant les heures définies",
            checked  = scheduleEnabled,
            onToggle = { vm.setScheduleEnabled(it) }
        )
        if (scheduleEnabled) {
            TimePickerCard(label = "Début du blocage", hour = scheduleStartH, minute = scheduleStartM, onClick = { showStartPicker = true })
            TimePickerCard(label = "Fin du blocage",   hour = scheduleEndH,   minute = scheduleEndM,   onClick = { showEndPicker = true })
            val startStr = "%02d:%02d".format(scheduleStartH, scheduleStartM)
            val endStr   = "%02d:%02d".format(scheduleEndH,   scheduleEndM)
            InfoCard("📋 Les inconnus et numéros spam seront bloqués de $startStr à $endStr.")
        }

        // ══════════════════════════════════════════════════════════════
        // LISTE BLANCHE
        // ══════════════════════════════════════════════════════════════
        SectionHeader("✅ Liste blanche")

        SettingItem(
            icon     = Icons.Default.PlaylistAddCheck,
            title    = "Gérer la liste blanche",
            subtitle = "Numéros jamais bloqués, même en mode NePasDéranger",
            onClick  = onNavigateToWhitelist
        )

        // ══════════════════════════════════════════════════════════════
        // AFFICHAGE DES APPELS
        // ══════════════════════════════════════════════════════════════
        SectionHeader("📞 Affichage des appels")

        // Avertissement permission si mode non-fullscreen sélectionné
        // et permission pas encore accordée
        if (callPopupMode != CallPopupMode.FULLSCREEN && !canDrawOverlays.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = c.neonOrange.copy(alpha = 0.1f)),
                onClick = {
                    ctx.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${ctx.packageName}")
                        )
                    )
                }
            ) {
                Row(
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, null, tint = c.neonOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Permission requise",
                            fontSize   = 13.sp,
                            color      = c.neonOrange,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Appuyez ici pour autoriser l'affichage par-dessus les autres applis",
                            fontSize = 12.sp,
                            color    = c.neonOrange.copy(alpha = 0.8f)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = c.neonOrange)
                }
            }
        }

        // Sélecteur de mode : 3 boutons
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape  = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = c.surfaceVar)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Mode d'affichage lors d'un appel",
                    fontSize   = 15.sp,
                    color      = c.textPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Choisir comment l'écran d'appel apparaît",
                    fontSize = 12.sp,
                    color    = c.textSecond,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CallPopupMode.entries.forEach { mode ->
                        val isActive = callPopupMode == mode
                        OutlinedButton(
                            onClick  = {
                                if (mode != CallPopupMode.FULLSCREEN && !Settings.canDrawOverlays(ctx)) {
                                    // Demander la permission d'abord
                                    ctx.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${ctx.packageName}")
                                        )
                                    )
                                } else {
                                    vm.setCallPopupMode(mode)
                                    canDrawOverlays.value = Settings.canDrawOverlays(ctx)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isActive) c.neonCyan.copy(alpha = 0.12f) else c.surfaceVar,
                                contentColor   = if (isActive) c.neonCyan else c.textSecond
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isActive) 1.5.dp else 0.5.dp,
                                color = if (isActive) c.neonCyan else c.glassStroke
                            )
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier            = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(mode.emoji, fontSize = 20.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    mode.label,
                                    fontSize   = 10.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                // Description du mode actif
                Text(
                    text = when (callPopupMode) {
                        CallPopupMode.FULLSCREEN -> "📱 L'appel prend tout l'écran (comportement par défaut)"
                        CallPopupMode.COMPACT    -> "🪟 Carte en bas de l'écran, vous pouvez continuer à utiliser votre téléphone"
                        CallPopupMode.MINI       -> "➖ Fine barre en haut de l'écran, discret et minimal"
                    },
                    fontSize = 12.sp,
                    color    = c.neonCyan.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }

        // ══════════════════════════════════════════════════════════════
        // SAUVEGARDE
        // ══════════════════════════════════════════════════════════════
        SectionHeader("💾 Sauvegarde & Restauration")

        backupMessage?.let { msg -> InfoCard(msg) }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = c.surfaceVar),
            onClick  = {
                if (!isBackupLoading) {
                    isBackupLoading = true
                    backupMessage   = null
                    scope.launch {
                        val uri = BackupManager.createBackup(ctx)
                        isBackupLoading = false
                        if (uri != null) {
                            backupMessage = "✅ Sauvegarde créée"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            shareLauncher.launch(Intent.createChooser(shareIntent, "Partager la sauvegarde"))
                        } else {
                            backupMessage = "❌ Erreur lors de la sauvegarde"
                        }
                    }
                }
            }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isBackupLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = c.neonCyan, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.SaveAlt, null, tint = c.neonCyan, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sauvegarder", fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    Text("Exporte favoris, liste blanche, blocages et paramètres", fontSize = 12.sp, color = c.textSecond)
                }
                Icon(Icons.Default.ChevronRight, null, tint = c.textSecond)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = c.surfaceVar),
            onClick  = { backupMessage = null; restoreLauncher.launch("application/json") }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RestorePage, null, tint = c.neonOrange, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Restaurer", fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    Text("Importe une sauvegarde PhoneZen existante", fontSize = 12.sp, color = c.textSecond)
                }
                Icon(Icons.Default.ChevronRight, null, tint = c.textSecond)
            }
        }

        // ══════════════════════════════════════════════════════════════
        // APPARENCE
        // ══════════════════════════════════════════════════════════════
        SectionHeader("🎨 Apparence")

        SettingItem(
            icon     = Icons.Default.Palette,
            title    = "Thème de l'application",
            subtitle = when (currentTheme) {
                AppTheme.CYBER_DARK -> "Cyber Dark (actif)"
                AppTheme.ZEN_LIGHT  -> "Zen Clair (actif)"
                AppTheme.CYANOGEN   -> "Cyanogen (actif)"
            },
            onClick = onNavigateToTheme
        )

        // ══════════════════════════════════════════════════════════════
        // APP PAR DÉFAUT
        // ══════════════════════════════════════════════════════════════
        SectionHeader("📱 Application par défaut")

        SettingItem(
            icon     = Icons.Default.Phone,
            title    = "Définir comme application téléphone",
            subtitle = "Requis pour gérer les appels entrants",
            onClick  = {
                try { ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                    })
                }
            }
        )
        SettingItem(
            icon     = Icons.Default.Security,
            title    = "Définir comme service de filtrage",
            subtitle = "Requis pour bloquer les appels spam",
            onClick  = {
                try {
                    ctx.startActivity(Intent("android.telecom.action.CHANGE_CALL_SCREENING_APP").apply {
                        putExtra("android.telecom.extra.CALL_SCREENING_APP_PACKAGE_NAME", ctx.packageName)
                    })
                } catch (e: Exception) {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            }
        )

        // ══════════════════════════════════════════════════════════════
        // CONSEILS
        // ══════════════════════════════════════════════════════════════
        SectionHeader("💡 Conseils sécurité")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = c.surfaceVar.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                listOf(
                    " ❌ Ne rappelez jamais un numéro inconnu après un appel en absence (arnaque ping call)",
                    " ❌ Ne donnez jamais vos codes bancaires ou identifiants par téléphone",
                    " ❌ Méfiez-vous des numéros commençant par 08 ou très courts",
                    " ✅ Inscrivez-vous sur Bloctel pour réduire le démarchage",
                    " ✅ Signalez les appels abusifs au 33700 (SMS ou site officiel)"
                ).forEach { tip ->
                    Text(tip, fontSize = 13.sp, color = c.textSecond, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }


        // ══════════════════════════════════════════════════════════════
        // DIAGNOSTIC
        // ══════════════════════════════════════════════════════════════
        SectionHeader("🐛 Diagnostic")

        if (hasCrashReport.value) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = c.neonOrange.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier          = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BugReport, null, tint = c.neonOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Un rapport de crash est disponible",
                        fontSize   = 13.sp,
                        color      = c.neonOrange,
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.weight(1f)
                    )
                }
            }
        }

        // Partager le dernier rapport
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = c.surfaceVar),
            onClick  = {
                val reports = CrashHandler.getCrashReports(ctx)
                if (reports.isEmpty()) return@Card
                val uri = FileProvider.getUriForFile(
                    ctx,
                    "${ctx.packageName}.fileprovider",
                    reports.first()
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "PhoneZen — Rapport de crash")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(android.content.Intent.createChooser(intent, "Partager le rapport"))
            }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Share,
                    null,
                    tint     = if (hasCrashReport.value) c.neonCyan else c.textSecond,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Partager le dernier rapport",
                        fontSize   = 15.sp,
                        color      = c.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (hasCrashReport.value)
                            "${CrashHandler.getCrashReports(ctx).size} rapport(s) disponible(s)"
                        else
                            "Aucun crash enregistré — tout va bien !",
                        fontSize = 12.sp,
                        color    = if (hasCrashReport.value) c.neonOrange else c.textSecond
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = c.textSecond)
            }
        }

        // Supprimer tous les rapports
        if (hasCrashReport.value) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = c.surfaceVar),
                onClick  = {
                    CrashHandler.clearAll(ctx)
                    hasCrashReport.value = false
                }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, null, tint = c.neonOrange, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Effacer tous les rapports", fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Libère l'espace occupé par les fichiers de diagnostic", fontSize = 12.sp, color = c.textSecond)
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // À PROPOS
        // ══════════════════════════════════════════════════════════════
        SectionHeader("ℹ️ À propos")

        SettingItem(
            icon     = Icons.Default.Info,
            title    = "PhoneZen",
            subtitle = "Développé par Franck R-F (souffly007) · GPL v3",
            onClick  = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/souffly007/PhoneZen"))
                )
            }
        )

        Spacer(Modifier.height(32.dp))
    }

    // ── Dialogs ───────────────────────────────────────────────────────
    if (showStartPicker) {
        TimePickerDialog(
            initialHour   = scheduleStartH,
            initialMinute = scheduleStartM,
            onConfirm     = { h, m -> vm.setScheduleStartHour(h); vm.setScheduleStartMinute(m); showStartPicker = false },
            onDismiss     = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialHour   = scheduleEndH,
            initialMinute = scheduleEndM,
            onConfirm     = { h, m -> vm.setScheduleEndHour(h); vm.setScheduleEndMinute(m); showEndPicker = false },
            onDismiss     = { showEndPicker = false }
        )
    }
    if (showRestoreConfirm) {
        val c = LocalColors.current
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor   = c.surfaceVar,
            title = { Text("Restaurer la sauvegarde ?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "Cette action remplacera vos favoris, liste blanche, numéros bloqués et paramètres actuels.",
                    color = c.textSecond, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    val uri = pendingRestoreUri ?: return@TextButton
                    scope.launch {
                        val result = BackupManager.restoreBackup(ctx, uri)
                        backupMessage = when (result) {
                            is BackupManager.RestoreResult.Success -> "✅ Restauration réussie — redémarrez l'app"
                            is BackupManager.RestoreResult.Error   -> "❌ ${result.message}"
                        }
                        if (result is BackupManager.RestoreResult.Success) vm.forceReload(ctx)
                        pendingRestoreUri = null
                    }
                }) {
                    Text("Restaurer", color = c.neonOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false; pendingRestoreUri = null }) {
                    Text("Annuler", color = c.textSecond)
                }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════
// COMPOSANTS PRIVÉS
// ══════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int, initialMinute: Int,
    onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit
) {
    val c     = LocalColors.current
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = c.surfaceVar,
        title = { Text("Choisir l'heure", color = c.neonCyan, fontWeight = FontWeight.Bold) },
        text  = {
            TimePicker(
                state  = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor                       = c.background,
                    clockDialSelectedContentColor        = c.background,
                    clockDialUnselectedContentColor      = c.textPrimary,
                    selectorColor                        = c.neonCyan,
                    containerColor                       = c.surfaceVar,
                    timeSelectorSelectedContainerColor   = c.neonCyan,
                    timeSelectorUnselectedContainerColor = c.background,
                    timeSelectorSelectedContentColor     = c.background,
                    timeSelectorUnselectedContentColor   = c.textPrimary,
                )
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK", color = c.neonCyan) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.textSecond) } }
    )
}

@Composable
private fun TimePickerCard(label: String, hour: Int, minute: Int, onClick: () -> Unit) {
    val c = LocalColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = c.surfaceVar),
        onClick  = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, null, tint = c.neonCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 14.sp, color = c.textSecond)
                Text("%02d:%02d".format(hour, minute), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = c.neonCyan)
            }
            Icon(Icons.Default.Edit, null, tint = c.textSecond, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun InfoCard(text: String) {
    val c = LocalColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = c.neonCyan.copy(alpha = 0.08f))
    ) {
        Text(text = text, fontSize = 13.sp, color = c.neonCyan, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun SettingsTopBar(title: String) {
    val c = LocalColors.current
    Text(
        text       = title,
        fontSize   = 24.sp,
        fontWeight = FontWeight.Bold,
        color      = c.neonCyan,
        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun SectionHeader(title: String) {
    val c = LocalColors.current
    Text(
        text       = title,
        fontSize   = 13.sp,
        fontWeight = FontWeight.Bold,
        color      = c.neonCyan,
        modifier   = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingSwitch(
    icon: ImageVector, title: String, subtitle: String,
    checked: Boolean, onToggle: (Boolean) -> Unit
) {
    val c = LocalColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = c.surfaceVar)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.neonCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = c.textSecond)
            }
            Switch(
                checked = checked, onCheckedChange = onToggle,
                colors  = SwitchDefaults.colors(
                    checkedThumbColor   = c.background, checkedTrackColor   = c.neonCyan,
                    uncheckedThumbColor = c.textSecond, uncheckedTrackColor = c.glassStroke
                )
            )
        }
    }
}

@Composable
private fun SettingItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = c.surfaceVar),
        onClick  = onClick
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.neonCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title,    fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = c.textSecond)
            }
            Icon(Icons.Default.ChevronRight, null, tint = c.textSecond)
        }
    }
}