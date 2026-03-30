package fr.bonobo.phonezen.ui.screens

import android.provider.CallLog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.bonobo.phonezen.data.model.CallGroup
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class CallFilter(val label: String, val icon: ImageVector) {
    ALL("Tous", Icons.Default.List),
    MISSED("Manqués", Icons.Default.CallMissed),
    INCOMING("Entrants", Icons.Default.CallReceived),
    OUTGOING("Sortants", Icons.Default.CallMade),
    BLOCKED("Bloqués", Icons.Default.Block)
}

// ── Couleur entrant visible sur blanc ET noir ──
val IncomingColor = Color(0xFFFFAB40) // Orange clair/ambre

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(vm: MainViewModel, onCall: (String) -> Unit) {
    val c = LocalColors.current
    val groups by vm.callGroups.collectAsState()
    val loading by vm.isLoading.collectAsState()
    val hideBlocked by vm.hideBlocked.collectAsState()
    val notes by vm.notes.collectAsState()

    var activeFilter by remember { mutableStateOf(CallFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    val selectedNumbers = remember { mutableStateSetOf<String>() }

    // État pour gérer l'affichage avec un petit délai
    var showContent by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val filtered by remember(groups, activeFilter, searchQuery, hideBlocked) {
        derivedStateOf {
            groups.filter { group ->
                val isBlocked = group.lastCall.type == CallLog.Calls.BLOCKED_TYPE
                if (hideBlocked && activeFilter != CallFilter.BLOCKED && isBlocked) false
                else when (activeFilter) {
                    CallFilter.ALL -> true
                    CallFilter.MISSED -> group.calls.any { it.type == CallLog.Calls.MISSED_TYPE }
                    CallFilter.INCOMING -> group.lastCall.type == CallLog.Calls.INCOMING_TYPE
                    CallFilter.OUTGOING -> group.lastCall.type == CallLog.Calls.OUTGOING_TYPE
                    CallFilter.BLOCKED -> isBlocked
                }
            }.filter { group ->
                if (searchQuery.isBlank()) true
                else (group.name?.contains(searchQuery, true) == true) || group.number.contains(searchQuery)
            }
        }
    }

    LaunchedEffect(activeFilter) {
        listState.scrollToItem(0)
    }

    // Gestion du délai d'affichage pour laisser l'UI respirer
    LaunchedEffect(loading) {
        if (!loading) {
            // Petit délai pour laisser l'UI respirer sur Realme
            delay(50)
            showContent = true
        } else {
            showContent = false
        }
    }

    // Fonction pour supprimer les appels sélectionnés
    fun deleteSelectedCalls() {
        selectedNumbers.forEach { number ->
            vm.removeCallGroup(number)
        }
        selectedNumbers.clear()
        selectionMode = false
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            if (selectionMode) {
                // Barre d'action en mode sélection
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = c.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Bouton pour fermer le mode sélection
                        IconButton(onClick = {
                            selectionMode = false
                            selectedNumbers.clear()
                        }) {
                            Icon(Icons.Default.Close, null, tint = c.textPrimary)
                        }

                        // Compteur des éléments sélectionnés
                        Text(
                            text = "${selectedNumbers.size} sélectionné${if (selectedNumbers.size > 1) "s" else ""}",
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.textPrimary
                        )

                        // Bouton de suppression (désactivé si rien n'est sélectionné)
                        IconButton(
                            onClick = { deleteSelectedCalls() },
                            enabled = selectedNumbers.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                null,
                                tint = if (selectedNumbers.isNotEmpty()) c.neonRed else c.textSecond
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // --- HEADER (caché en mode sélection) ---
            if (!selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "JOURNAL",
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.neonOrange
                    )

                    val blockedCount = remember(groups) {
                        groups.count { it.lastCall.type == CallLog.Calls.BLOCKED_TYPE }
                    }
                    if (hideBlocked && blockedCount > 0 && activeFilter != CallFilter.BLOCKED) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = c.neonRed.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                "🚫 $blockedCount",
                                fontSize = 11.sp,
                                color = c.neonRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) searchQuery = ""
                    }) {
                        Icon(
                            if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                            null,
                            tint = if (showSearch) c.neonCyan else c.textSecond
                        )
                    }
                    IconButton(onClick = { /* Export CSV logic */ }) {
                        Icon(Icons.Default.FileDownload, null, tint = c.neonCyan)
                    }
                }

                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Rechercher...", color = c.textSecond) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.neonCyan,
                            unfocusedBorderColor = c.glassStroke
                        )
                    )
                }

                // --- TABS FILTRES ---
                LazyRow(
                    contentPadding = PaddingValues(16.dp, 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CallFilter.entries) { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = {
                                activeFilter = filter
                                coroutineScope.launch { listState.scrollToItem(0) }
                            },
                            label = { Text(filter.label, fontSize = 12.sp) },
                            leadingIcon = { Icon(filter.icon, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.neonCyan.copy(0.2f),
                                selectedLabelColor = c.neonCyan,
                                selectedLeadingIconColor = c.neonCyan
                            )
                        )
                    }
                }
            }

            // ── AFFICHAGE AVEC DÉLAI POUR LAISSER L'UI RESPIRER ──
            if (loading || !showContent) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.neonCyan)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Limiter le nombre d'items initialement affichés pour les performances
                    val itemsToShow = filtered.take(50)
                    val remainingCount = filtered.size - itemsToShow.size

                    items(itemsToShow, key = { it.number }) { group ->
                        CallGroupRow(
                            group = group,
                            note = notes[PhoneUtils.normalizeNumber(group.number)],
                            onCall = onCall,
                            vm = vm,
                            selectionMode = selectionMode,
                            isSelected = selectedNumbers.contains(group.number),
                            onToggleSelection = {
                                if (selectedNumbers.contains(group.number)) {
                                    selectedNumbers.remove(group.number)
                                    if (selectedNumbers.isEmpty()) {
                                        selectionMode = false
                                    }
                                } else {
                                    selectedNumbers.add(group.number)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedNumbers.add(group.number)
                            }
                        )
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }

                    // Bouton pour charger plus si nécessaire
                    if (remainingCount > 0) {
                        item {
                            Button(
                                onClick = {
                                    // Pour l'instant on recharge tout, mais idéalement on ferait du pagination
                                    coroutineScope.launch {
                                        // On pourrait implémenter un chargement progressif ici
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.surface,
                                    contentColor = c.neonCyan
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Charger ${remainingCount} élément${if (remainingCount > 1) "s" else ""} restant${if (remainingCount > 1) "s" else ""}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallGroupRow(
    group: CallGroup,
    note: String?,
    onCall: (String) -> Unit,
    vm: MainViewModel,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onLongClick: () -> Unit
) {
    val c = LocalColors.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    val isBlocked = group.lastCall.type == CallLog.Calls.BLOCKED_TYPE
    val whitelisted = vm.isWhitelisted(group.number)
    val isKnown = group.name != null
    val hasPhoto = !group.photoUri.isNullOrBlank()

    // ── Type et couleur (Orange pour entrants) ──
    val callType = group.lastCall.type
    val (typeIcon, typeColor) = when (callType) {
        CallLog.Calls.BLOCKED_TYPE -> Icons.Default.Block to c.neonRed
        CallLog.Calls.MISSED_TYPE -> Icons.Default.CallMissed to c.neonRed
        CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade to c.neonGreen
        CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived to IncomingColor
        else -> Icons.Default.CallReceived to IncomingColor
    }

    // ── Couleur du nom (Orange pour entrants) ──
    val nameColor = when (callType) {
        CallLog.Calls.BLOCKED_TYPE -> c.neonRed
        CallLog.Calls.MISSED_TYPE -> c.neonRed
        CallLog.Calls.OUTGOING_TYPE -> c.textPrimary
        CallLog.Calls.INCOMING_TYPE -> IncomingColor
        else -> c.textPrimary
    }

    // ── Compteurs par type (optimisés avec remember) ──
    val missedCount = remember(group) { group.calls.count { it.type == CallLog.Calls.MISSED_TYPE } }
    val outgoingCount = remember(group) { group.calls.count { it.type == CallLog.Calls.OUTGOING_TYPE } }
    val incomingCount = remember(group) { group.calls.count { it.type == CallLog.Calls.INCOMING_TYPE } }
    val blockedCount = remember(group) { group.calls.count { it.type == CallLog.Calls.BLOCKED_TYPE } }

    // ── Calcul période (20 jours) optimisé ──
    val periodDays = remember(group) {
        if (group.calls.size < 2) 0
        else {
            val oldest = group.calls.minOf { it.timestamp }
            val newest = group.calls.maxOf { it.timestamp }
            ((newest - oldest) / (1000 * 60 * 60 * 24)).toInt().coerceAtMost(20)
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) c.neonCyan.copy(alpha = 0.1f) else Color.Transparent)
                .combinedClickable(
                    onClick = {
                        when {
                            selectionMode -> onToggleSelection()
                            !isBlocked -> onCall(group.number)
                        }
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = c.neonCyan)
                )
                Spacer(Modifier.width(8.dp))
            }

            // ── AVATAR avec photo ou icône ──
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasPhoto) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(group.photoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .background(c.background, CircleShape)
                            .padding(2.dp)
                            .background(typeColor.copy(0.9f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(typeColor.copy(0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // --- INFOS ---
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name ?: group.number,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = nameColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (group.callCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "(${group.callCount})",
                            fontSize = 13.sp,
                            color = typeColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isKnown) {
                    Text(
                        text = group.number,
                        fontSize = 12.sp,
                        color = c.textSecond.copy(alpha = 0.7f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        formatTimestampFull(group.lastCall.timestamp),
                        fontSize = 11.sp,
                        color = c.textSecond
                    )

                    Spacer(Modifier.width(8.dp))

                    if (outgoingCount > 0) {
                        Icon(Icons.Default.CallMade, null, tint = c.neonGreen, modifier = Modifier.size(12.dp))
                        Text("$outgoingCount", fontSize = 10.sp, color = c.neonGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }

                    if (incomingCount > 0) {
                        Icon(Icons.Default.CallReceived, null, tint = IncomingColor, modifier = Modifier.size(12.dp))
                        Text("$incomingCount", fontSize = 10.sp, color = IncomingColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }

                    if (missedCount > 0) {
                        Icon(Icons.Default.CallMissed, null, tint = c.neonRed, modifier = Modifier.size(12.dp))
                        Text("$missedCount", fontSize = 10.sp, color = c.neonRed, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }

                    if (blockedCount > 0) {
                        Icon(Icons.Default.Block, null, tint = c.neonRed, modifier = Modifier.size(12.dp))
                        Text("$blockedCount", fontSize = 10.sp, color = c.neonRed, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                    }

                    if (!isBlocked && group.lastCall.simSlot >= 0) {
                        Text("SIM${group.lastCall.simSlot + 1}", fontSize = 9.sp, color = c.textSecond)
                    }

                    if (!note.isNullOrBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Text("📝", fontSize = 10.sp)
                    }

                    if (whitelisted) {
                        Spacer(Modifier.width(4.dp))
                        Text("🛡️", fontSize = 10.sp)
                    }
                }
            }

            // --- ACTIONS ---
            if (!selectionMode) {
                if (group.callCount > 1) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = c.textSecond
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = c.textSecond)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(c.surfaceVar)
                    ) {
                        if (!isBlocked) {
                            DropdownMenuItem(
                                text = { Text("📞 Appeler", color = c.neonGreen) },
                                onClick = {
                                    onCall(group.number)
                                    showMenu = false
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("📝 Note / Commentaire", color = c.textPrimary) },
                            onClick = { showMenu = false }
                        )

                        if (!whitelisted) {
                            DropdownMenuItem(
                                text = { Text("🛡️ Liste Blanche", color = c.neonCyan) },
                                onClick = {
                                    vm.addToWhitelist(group.number)
                                    showMenu = false
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("🔓 Retirer Liste Blanche", color = c.textPrimary) },
                                onClick = {
                                    vm.removeFromWhitelist(group.number)
                                    showMenu = false
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("⚠️ Signaler & Bloquer", color = c.neonOrange) },
                            onClick = {
                                vm.reportNumber(group.number, "Indésirable")
                                showMenu = false
                            }
                        )

                        HorizontalDivider(color = c.glassStroke)

                        DropdownMenuItem(
                            text = { Text("🗑️ Supprimer", color = c.neonRed) },
                            onClick = {
                                vm.removeCallGroup(group.number)
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }

        // ── DÉTAILS EXPANDABLES (historique sur 20 jours) ──
        if (expanded && group.calls.size > 1) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface.copy(alpha = 0.5f))
                    .padding(start = 76.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            ) {
                if (periodDays > 0) {
                    Text(
                        "📊 Sur ${periodDays} jour${if (periodDays > 1) "s" else ""} : " +
                                "${outgoingCount} sortant${if (outgoingCount > 1) "s" else ""}, " +
                                "${incomingCount} entrant${if (incomingCount > 1) "s" else ""}, " +
                                "${missedCount} manqué${if (missedCount > 1) "s" else ""}",
                        fontSize = 11.sp,
                        color = c.textSecond,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                group.calls.take(10).forEach { call ->
                    val (icon, color) = when (call.type) {
                        CallLog.Calls.OUTGOING_TYPE -> Icons.Default.CallMade to c.neonGreen
                        CallLog.Calls.INCOMING_TYPE -> Icons.Default.CallReceived to IncomingColor
                        CallLog.Calls.MISSED_TYPE -> Icons.Default.CallMissed to c.neonRed
                        CallLog.Calls.BLOCKED_TYPE -> Icons.Default.Block to c.neonRed
                        else -> Icons.Default.Call to c.textSecond
                    }

                    val typeLabel = when (call.type) {
                        CallLog.Calls.OUTGOING_TYPE -> "Sortant"
                        CallLog.Calls.INCOMING_TYPE -> "Entrant"
                        CallLog.Calls.MISSED_TYPE -> "Manqué"
                        CallLog.Calls.BLOCKED_TYPE -> "Bloqué"
                        else -> "Appel"
                    }

                    val duration = if (call.duration > 0) {
                        val min = call.duration / 60
                        val sec = call.duration % 60
                        if (min > 0) "${min}m${sec}s" else "${sec}s"
                    } else ""

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            formatTimestampDetail(call.timestamp),
                            fontSize = 11.sp,
                            color = c.textSecond
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            typeLabel,
                            fontSize = 11.sp,
                            color = color,
                            fontWeight = FontWeight.Medium
                        )
                        if (duration.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "($duration)",
                                fontSize = 10.sp,
                                color = c.textSecond
                            )
                        }
                        if (call.simSlot >= 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "SIM${call.simSlot + 1}",
                                fontSize = 9.sp,
                                color = c.textSecond.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                if (group.calls.size > 10) {
                    Text(
                        "... et ${group.calls.size - 10} autre${if (group.calls.size - 10 > 1) "s" else ""}",
                        fontSize = 10.sp,
                        color = c.textSecond,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestampFull(ts: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(ts))
}

private fun formatTimestampDetail(ts: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ts
    val sdf = when {
        diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault())
        diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        else -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
    return sdf.format(Date(ts))
}