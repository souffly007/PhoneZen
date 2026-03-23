package fr.bonobo.phonezen.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.CallLog
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.CallGroup
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class CallFilter(val label: String, val icon: ImageVector) {
    ALL     ("Tous",     Icons.Default.List),
    MISSED  ("Manqués",  Icons.Default.CallMissed),
    INCOMING("Entrants", Icons.Default.CallReceived),
    OUTGOING("Sortants", Icons.Default.CallMade),
    BLOCKED ("Bloqués",  Icons.Default.Block)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(vm: MainViewModel, onCall: (String) -> Unit) {
    val c              = LocalColors.current
    val ctx            = LocalContext.current
    val groups         by vm.callGroups.collectAsState()
    val loading        by vm.isLoading.collectAsState()
    val hideBlocked    by vm.hideBlocked.collectAsState()
    val reportFeedback by vm.reportFeedback.collectAsState()

    var activeFilter    by remember { mutableStateOf(CallFilter.ALL) }
    var searchQuery     by remember { mutableStateOf("") }
    var showSearch      by remember { mutableStateOf(false) }

    // ── Sélection groupée ──
    var selectionMode   by remember { mutableStateOf(false) }
    val selectedNumbers = remember { mutableStateSetOf<String>() }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(reportFeedback) {
        if (reportFeedback != null) {
            snackbarHostState.showSnackbar(reportFeedback!!)
            vm.clearReportFeedback()
        }
    }

    val filtered = groups
        .filter { group ->
            if (hideBlocked && activeFilter != CallFilter.BLOCKED)
                group.lastCall.type != CallLog.Calls.BLOCKED_TYPE && !group.calls.all { it.type == CallLog.Calls.BLOCKED_TYPE }
            else true
        }
        // ── Masquer les numéros masqués/privés sauf dans le filtre Bloqués ──
        .filter { group ->
            if (activeFilter != CallFilter.BLOCKED) {
                val n = group.number.lowercase()
                n.isNotBlank() && n != "-1" && n != "-2" &&
                        !n.contains("unknown") && !n.contains("private") &&
                        !n.contains("hidden") && !n.contains("anonymous")
            } else true
        }
        .filter { group ->
            when (activeFilter) {
                CallFilter.ALL      -> true
                CallFilter.MISSED   -> group.calls.any { it.type == android.provider.CallLog.Calls.MISSED_TYPE && it.duration == 0L }
                CallFilter.INCOMING -> group.lastCall.type == CallLog.Calls.INCOMING_TYPE
                CallFilter.OUTGOING -> group.lastCall.type == CallLog.Calls.OUTGOING_TYPE
                CallFilter.BLOCKED  -> group.lastCall.type == CallLog.Calls.BLOCKED_TYPE ||
                        group.calls.any { it.type == CallLog.Calls.BLOCKED_TYPE }
            }
        }
        .filter { group ->
            if (searchQuery.isBlank()) true
            else {
                val q = searchQuery.lowercase()
                (group.name?.lowercase()?.contains(q) == true) || group.number.contains(q)
            }
        }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, containerColor = c.surfaceVar, contentColor = c.textPrimary, actionColor = c.neonCyan)
            }
        },
        containerColor = c.background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().background(c.background).padding(padding)) {

            // ── Barre de sélection OU header normal ──
            if (selectionMode) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .background(c.surfaceVar)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectionMode = false; selectedNumbers.clear() }) {
                        Icon(Icons.Default.Close, null, tint = c.textPrimary)
                    }
                    Text(
                        text       = "${selectedNumbers.size} sélectionné(s)",
                        fontSize   = 16.sp,
                        color      = c.textPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        if (selectedNumbers.size == filtered.size) selectedNumbers.clear()
                        else selectedNumbers.addAll(filtered.map { it.number })
                    }) {
                        Text(
                            if (selectedNumbers.size == filtered.size) "Désélectionner" else "Tout",
                            color = c.neonCyan, fontSize = 13.sp
                        )
                    }
                    IconButton(
                        onClick  = {
                            selectedNumbers.forEach { vm.removeCallGroup(it) }
                            selectedNumbers.clear()
                            selectionMode = false
                        },
                        enabled = selectedNumbers.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            null,
                            tint = if (selectedNumbers.isNotEmpty()) c.neonRed else c.textSecond
                        )
                    }
                }
            } else {
                Row(
                    modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = "JOURNAL",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color      = c.neonOrange,
                        modifier   = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                        Icon(if (showSearch) Icons.Default.SearchOff else Icons.Default.Search, null,
                            tint = if (showSearch) c.neonCyan else c.textSecond)
                    }
                    IconButton(onClick = { exportCsv(ctx, groups) }) {
                        Icon(Icons.Default.FileDownload, null, tint = c.neonCyan)
                    }
                    val maskedCount = groups.count { group ->
                        group.lastCall.type == CallLog.Calls.BLOCKED_TYPE ||
                                group.calls.all { it.type == CallLog.Calls.BLOCKED_TYPE }
                    }
                    if (hideBlocked && maskedCount > 0 && activeFilter != CallFilter.BLOCKED) {
                        Card(
                            shape  = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = c.neonRed.copy(alpha = 0.15f))
                        ) {
                            Text("🚫 $maskedCount", fontSize = 11.sp, color = c.neonRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            if (showSearch && !selectionMode) {
                OutlinedTextField(
                    value         = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder   = { Text("Rechercher un nom ou numéro…", color = c.textSecond) },
                    leadingIcon   = { Icon(Icons.Default.Search, null, tint = c.neonCyan) },
                    trailingIcon  = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, null, tint = c.textSecond)
                            }
                        }
                    },
                    singleLine = true,
                    colors     = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor      = c.neonCyan,
                        unfocusedBorderColor    = c.glassStroke,
                        focusedContainerColor   = c.surfaceVar,
                        unfocusedContainerColor = c.surfaceVar
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (!selectionMode) {
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CallFilter.entries) { filter ->
                        FilterChip(
                            selected    = activeFilter == filter,
                            onClick     = { activeFilter = filter },
                            label       = { Text(filter.label, fontSize = 12.sp) },
                            leadingIcon = { Icon(filter.icon, null, modifier = Modifier.size(14.dp)) },
                            colors      = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = c.neonCyan.copy(alpha = 0.2f),
                                selectedLabelColor       = c.neonCyan,
                                selectedLeadingIconColor = c.neonCyan,
                                containerColor           = c.surfaceVar,
                                labelColor               = c.textSecond,
                                iconColor                = c.textSecond
                            )
                        )
                    }
                }
                if (searchQuery.isNotBlank() || activeFilter != CallFilter.ALL) {
                    Text("${filtered.size} résultat${if (filtered.size > 1) "s" else ""}",
                        fontSize = 12.sp, color = c.textSecond,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.neonCyan)
                }
            } else if (filtered.isEmpty()) {
                EmptyState(
                    icon    = Icons.Default.History,
                    message = if (searchQuery.isNotBlank()) "Aucun résultat pour \"$searchQuery\""
                    else "Aucun appel ${activeFilter.label.lowercase()}"
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(filtered, key = { it.number }) { group ->
                        val isSelected = selectedNumbers.contains(group.number)

                        if (selectionMode) {
                            // ── Mode sélection : checkbox à gauche ──
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) c.neonCyan.copy(alpha = 0.08f) else c.background)
                                    .combinedClickable(
                                        onClick     = {
                                            if (isSelected) selectedNumbers.remove(group.number)
                                            else selectedNumbers.add(group.number)
                                        },
                                        onLongClick = {
                                            selectionMode = false
                                            selectedNumbers.clear()
                                        }
                                    )
                                    .padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked         = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedNumbers.add(group.number)
                                        else selectedNumbers.remove(group.number)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor   = c.neonCyan,
                                        uncheckedColor = c.textSecond
                                    )
                                )
                                Box(Modifier.weight(1f)) {
                                    CallGroupRow(
                                        group       = group,
                                        onCall      = onCall,
                                        onBlock     = { vm.blockNumber(it) },
                                        onReport    = { number, tag -> vm.reportNumber(number, tag) },
                                        vm          = vm,
                                        selectionMode = true,
                                        onLongClick = {}
                                    )
                                }
                            }
                        } else {
                            // ── Mode normal : swipe pour supprimer avec confirmation ──
                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            var pendingDelete     by remember { mutableStateOf("") }

                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value != SwipeToDismissBoxValue.Settled) {
                                        // Seuil élevé : on demande confirmation plutôt que supprimer direct
                                        pendingDelete    = group.number
                                        showDeleteConfirm = true
                                        false  // ← on ne supprime PAS encore, on revient à la position initiale
                                    } else false
                                },
                                positionalThreshold = { totalDistance -> totalDistance * 0.5f }  // ← 50% de l'écran minimum
                            )

                            if (showDeleteConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteConfirm = false },
                                    containerColor   = c.surfaceVar,
                                    title = {
                                        Text("Supprimer du journal ?", color = c.textPrimary, fontWeight = FontWeight.Bold)
                                    },
                                    text = {
                                        val label = group.name ?: group.number
                                        Text("L'entrée \"$label\" sera supprimée définitivement du journal.", color = c.textSecond)
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            vm.removeCallGroup(pendingDelete)
                                            showDeleteConfirm = false
                                        }) {
                                            Text("Supprimer", color = c.neonRed, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteConfirm = false }) {
                                            Text("Annuler", color = c.neonCyan)
                                        }
                                    }
                                )
                            }

                            SwipeToDismissBox(
                                state             = dismissState,
                                backgroundContent = {
                                    val progress = dismissState.progress
                                    val triggered = progress > 0.5f
                                    val color by animateColorAsState(
                                        targetValue = when {
                                            dismissState.dismissDirection == SwipeToDismissBoxValue.Settled -> c.background
                                            triggered -> c.neonRed.copy(alpha = 0.9f)
                                            else      -> c.neonRed.copy(alpha = 0.3f + progress * 0.6f)
                                        },
                                        label = "swipe_bg"
                                    )
                                    val alignment = when (dismissState.dismissDirection) {
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.CenterStart
                                    }
                                    Box(
                                        modifier         = Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                                        contentAlignment = alignment
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Default.Delete, "Supprimer", tint = Color.White, modifier = Modifier.size(28.dp))
                                            if (triggered) {
                                                Text("Relâcher pour confirmer", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            ) {
                                CallGroupRow(
                                    group         = group,
                                    onCall        = onCall,
                                    onBlock       = { vm.blockNumber(it) },
                                    onReport      = { number, tag -> vm.reportNumber(number, tag) },
                                    vm            = vm,
                                    selectionMode = false,
                                    onLongClick   = {
                                        selectionMode = true
                                        selectedNumbers.add(group.number)
                                    }
                                )
                            }
                        }
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

private fun exportCsv(ctx: Context, groups: List<CallGroup>) {
    try {
        val sdf      = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fileName = "phonezen_journal_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val sb       = StringBuilder()
        sb.appendLine("Nom,Numéro,Type,Date,Durée(s),Occurrences")
        groups.forEach { group ->
            group.calls.forEach { call ->
                val type = when (call.type) {
                    CallLog.Calls.MISSED_TYPE   -> "Manqué"
                    CallLog.Calls.INCOMING_TYPE -> "Entrant"
                    CallLog.Calls.OUTGOING_TYPE -> "Sortant"
                    CallLog.Calls.BLOCKED_TYPE  -> "Bloqué"
                    else                        -> "Inconnu"
                }
                sb.appendLine("${group.name?.replace(",", " ") ?: ""},${group.number},$type,${sdf.format(Date(call.timestamp))},${call.duration},${group.callCount}")
            }
        }
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        uri?.let {
            ctx.contentResolver.openOutputStream(it)?.use { out -> out.write(sb.toString().toByteArray(Charsets.UTF_8)) }
            Toast.makeText(ctx, "✅ Export CSV : Téléchargements/$fileName", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Toast.makeText(ctx, "❌ Erreur export : ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallGroupRow(
    group        : CallGroup,
    onCall       : (String) -> Unit,
    onBlock      : (String) -> Unit,
    onReport     : (String, String) -> Unit,
    vm           : MainViewModel,
    selectionMode: Boolean = false,
    onLongClick  : () -> Unit = {}
) {
    val c          = LocalColors.current
    var expanded   by remember { mutableStateOf(false) }
    var showMenu   by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    val reported   = vm.isReported(group.number)
    val isSuspect  = reported != null

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = { if (!selectionMode) expanded = !expanded },
                    onLongClick = onLongClick
                )
                .background(c.background)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, color) = callTypeIcon(group.lastCall.type)

            if (!group.photoUri.isNullOrBlank()) {
                ContactAvatar(name = group.name, photoUri = group.photoUri, size = 46)
            } else {
                Box(
                    modifier         = Modifier.size(46.dp).background(color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text       = group.name ?: group.number,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color      = if (group.lastCall.type == CallLog.Calls.MISSED_TYPE) c.neonRed else c.textPrimary,
                        maxLines   = 1,
                        overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f, fill = false)
                    )
                    if (group.callCount > 1) {
                        Spacer(Modifier.width(4.dp))
                        Text("(${group.callCount})", fontSize = 12.sp, color = c.neonCyan, maxLines = 1)
                    }
                    if (group.isFavorite) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Star, null, tint = c.neonOrange, modifier = Modifier.size(14.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(PhoneUtils.formatTimestamp(group.lastCall.timestamp), fontSize = 12.sp, color = c.textSecond)
                    // Badge SIM si dual SIM
                    if (group.lastCall.simSlot >= 0) {
                        Spacer(Modifier.width(4.dp))
                        Card(
                            shape  = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (group.lastCall.simSlot == 0) c.neonCyan.copy(alpha = 0.2f) else c.neonOrange.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text     = "SIM ${group.lastCall.simSlot + 1}",
                                fontSize = 9.sp,
                                color    = if (group.lastCall.simSlot == 0) c.neonCyan else c.neonOrange,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (isSuspect) {
                        Spacer(Modifier.width(6.dp))
                        Card(
                            shape  = RoundedCornerShape(6.dp),
                            colors = CardDefaults.cardColors(containerColor = c.neonRed.copy(alpha = 0.15f))
                        ) {
                            Text(
                                text     = "⚠️ ${reported!!.reports} signalement${if (reported.reports > 1) "s" else ""}",
                                fontSize = 10.sp,
                                color    = c.neonRed,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            if (!selectionMode) {
                IconButton(onClick = { onCall(group.number) }) {
                    Icon(Icons.Default.Call, null, tint = c.neonGreen)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = c.textSecond)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("📞 Rappeler") }, onClick = { onCall(group.number); showMenu = false })
                        DropdownMenuItem(text = { Text("🚫 Bloquer") }, onClick = { onBlock(group.number); showMenu = false })
                        DropdownMenuItem(text = { Text("⚠️ Signaler à la communauté") }, onClick = { showMenu = false; showReport = true })
                    }
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = c.textSecond, modifier = Modifier.size(18.dp)
                )
            }
        }

        if (expanded && !selectionMode) {
            group.calls.forEach { call ->
                val (icon, color) = callTypeIcon(call.type)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.surfaceVar)
                        .padding(start = 74.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(PhoneUtils.formatTimestamp(call.timestamp), fontSize = 12.sp, color = c.textSecond, modifier = Modifier.weight(1f))
                    if (call.duration > 0) Text(PhoneUtils.formatDuration(call.duration), fontSize = 12.sp, color = c.textSecond)
                }
            }
        }
    }

    if (showReport) {
        ReportDialog(
            number    = group.number,
            name      = group.name,
            onReport  = { tag -> onReport(group.number, tag); showReport = false },
            onDismiss = { showReport = false }
        )
    }
}

@Composable
private fun ReportDialog(number: String, name: String?, onReport: (String) -> Unit, onDismiss: () -> Unit) {
    val c    = LocalColors.current
    val tags = listOf("démarchage", "arnaque", "spam", "silence", "harcèlement", "autre")
    var selectedTag by remember { mutableStateOf("démarchage") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = c.surfaceVar,
        title = { Text("Signaler à la communauté", color = c.textPrimary, fontWeight = FontWeight.Bold) },
        text  = {
            Column {
                Text(name ?: number, fontSize = 14.sp, color = c.neonCyan, modifier = Modifier.padding(bottom = 12.dp))
                Text("Motif du signalement :", fontSize = 13.sp, color = c.textSecond)
                Spacer(Modifier.height(8.dp))
                tags.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick  = { selectedTag = tag },
                                label    = { Text(tag, fontSize = 11.sp) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = c.neonCyan.copy(alpha = 0.2f),
                                    selectedLabelColor     = c.neonCyan,
                                    containerColor         = c.background,
                                    labelColor             = c.textSecond
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onReport(selectedTag) }) { Text("Signaler", color = c.neonRed, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler", color = c.textSecond) } }
    )
}

@Composable
fun EmptyState(icon: ImageVector, message: String, subtitle: String = "") {
    val c = LocalColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = c.surfaceVar, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = c.textSecond, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) Text(subtitle, color = c.textSecond.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}

private fun callTypeIcon(type: Int): Pair<ImageVector, Color> = when (type) {
    CallLog.Calls.MISSED_TYPE   -> Icons.Default.CallMissed   to NeonRed
    CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade     to NeonCyan
    CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived to NeonGreen
    CallLog.Calls.BLOCKED_TYPE  -> Icons.Default.Block        to NeonRed
    else                        -> Icons.Default.Call         to TextSecond
}