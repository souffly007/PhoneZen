package fr.bonobo.phonezen.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    themeVm: ThemeViewModel,
    onNavigateToWhitelist: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {}
) {
    val c                = LocalColors.current
    val ctx              = LocalContext.current
    val blockPrivate     by vm.blockPrivate.collectAsState()
    val hideBlocked      by vm.hideBlocked.collectAsState()
    val doNotDisturb     by vm.doNotDisturb.collectAsState()
    val scheduleEnabled  by vm.scheduleEnabled.collectAsState()
    val scheduleStartH   by vm.scheduleStartHour.collectAsState()
    val scheduleStartM   by vm.scheduleStartMinute.collectAsState()
    val scheduleEndH     by vm.scheduleEndHour.collectAsState()
    val scheduleEndM     by vm.scheduleEndMinute.collectAsState()
    val currentTheme     by themeVm.theme.collectAsState()

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsTopBar("Réglages")

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
            subtitle = "prefixes_blocked_fr.json v4.1 (2026-02-25)",
            onClick  = {}
        )

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

        SectionHeader("✅ Liste blanche")

        SettingItem(
            icon     = Icons.Default.PlaylistAddCheck,
            title    = "Gérer la liste blanche",
            subtitle = "Numéros jamais bloqués, même en mode NePasDéranger",
            onClick  = onNavigateToWhitelist
        )

        SectionHeader("🎨 Apparence")

        SettingItem(
            icon     = Icons.Default.Palette,
            title    = "Thème de l'application",
            subtitle = when (currentTheme) {
                AppTheme.CYBER_DARK -> "Cyber Dark (actif)"
                AppTheme.ZEN_LIGHT  -> "Zen Clair (actif)"
            },
            onClick  = onNavigateToTheme
        )

        SectionHeader("📱 Application par défaut")

        SettingItem(
            icon     = Icons.Default.Phone,
            title    = "Définir comme application téléphone",
            subtitle = "Requis pour gérer les appels entrants",
            onClick  = {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (e: Exception) {
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

        SectionHeader("💡 Conseils sécurité")

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = c.surfaceVar.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                listOf(
                    "❌ Ne rappelez jamais un numéro inconnu surtaxé",
                    "❌ Ne donnez jamais vos codes bancaires",
                    "✅ Signalez les arnaques sur signal-spam.fr",
                    "✅ 33700 pour signaler un SMS spam"
                ).forEach { tip ->
                    Text(tip, fontSize = 13.sp, color = c.textSecond, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }

        SectionHeader("ℹ️ À propos")

        SettingItem(
            icon     = Icons.Default.Info,
            title    = "PhoneZen v1.0.0",
            subtitle = "Développé par Franck R-F (souffly007) · GPL v3",
            onClick  = {}
        )

        Spacer(Modifier.height(32.dp))
    }

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
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
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
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
                checked         = checked,
                onCheckedChange = onToggle,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor   = c.background,
                    checkedTrackColor   = c.neonCyan,
                    uncheckedThumbColor = c.textSecond,
                    uncheckedTrackColor = c.glassStroke
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