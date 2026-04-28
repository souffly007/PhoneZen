package fr.bonobo.phonezen.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed // Ajouté pour le correctif
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

val IncomingColor = Color(0xFFFFAB40)

private val SAFE_BLOCKED_TYPE: Int  = try { CallLog.Calls.BLOCKED_TYPE  } catch (e: Throwable) { 6 }
private val SAFE_MISSED_TYPE: Int   = try { CallLog.Calls.MISSED_TYPE   } catch (e: Throwable) { 3 }
private val SAFE_INCOMING_TYPE: Int = try { CallLog.Calls.INCOMING_TYPE } catch (e: Throwable) { 1 }
private val SAFE_OUTGOING_TYPE: Int = try { CallLog.Calls.OUTGOING_TYPE } catch (e: Throwable) { 2 }

private fun safeIsBlocked(type: Int)              = try { type == SAFE_BLOCKED_TYPE  } catch (e: Throwable) { false }
private fun safeTypeEquals(type: Int, ref: Int)   = try { type == ref               } catch (e: Throwable) { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    vm: MainViewModel,
    onCall: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onEditContact: (Long) -> Unit
) {
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
    var showContent by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val filtered by remember(groups, activeFilter, searchQuery, hideBlocked) {
        derivedStateOf {
            try {
                groups.filter { group ->
                    val isBlocked = safeIsBlocked(group.lastCall.type)
                    if (hideBlocked && activeFilter != CallFilter.BLOCKED && isBlocked) return@filter false
                    when (activeFilter) {
                        CallFilter.ALL      -> true
                        CallFilter.MISSED   -> group.calls.any { safeTypeEquals(it.type, SAFE_MISSED_TYPE) }
                        CallFilter.INCOMING -> safeTypeEquals(group.lastCall.type, SAFE_INCOMING_TYPE)
                        CallFilter.OUTGOING -> safeTypeEquals(group.lastCall.type, SAFE_OUTGOING_TYPE)
                        CallFilter.BLOCKED  -> isBlocked
                    }
                }.filter { group ->
                    if (searchQuery.isBlank()) true
                    else (group.name?.contains(searchQuery, true) == true) ||
                            group.number.contains(searchQuery)
                }
            } catch (e: Throwable) {
                emptyList()
            }
        }
    }

    LaunchedEffect(activeFilter) { listState.scrollToItem(0) }

    LaunchedEffect(loading) {
        if (!loading) { delay(50); showContent = true }
        else showContent = false
    }

    fun deleteSelectedCalls() {
        selectedNumbers.forEach { vm.removeCallGroup(it) }
        selectedNumbers.clear()
        selectionMode = false
    }

    Scaffold(
        containerColor = c.background,
        topBar = {
            if (selectionMode) {
                Surface(modifier = Modifier.fillMaxWidth(), color = c.surface, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectionMode = false; selectedNumbers.clear() }) {
                            Icon(Icons.Default.Close, null, tint = c.textPrimary)
                        }
                        Text(
                            "${selectedNumbers.size} sélectionné${if (selectedNumbers.size > 1) "s" else ""}",
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.textPrimary
                        )
                        IconButton(onClick = { deleteSelectedCalls() }, enabled = selectedNumbers.isNotEmpty()) {
                            Icon(
                                Icons.Default.Delete, null,
                                tint = if (selectedNumbers.isNotEmpty()) c.neonRed else c.textSecond
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (!selectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("JOURNAL", modifier = Modifier.weight(1f), fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold, color = c.neonOrange)

                    val blockedCount = remember(groups) {
                        try { groups.count { safeIsBlocked(it.lastCall.type) } } catch (e: Throwable) { 0 }
                    }
                    if (hideBlocked && blockedCount > 0 && activeFilter != CallFilter.BLOCKED) {
                        Surface(shape = RoundedCornerShape(8.dp), color = c.neonRed.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 8.dp)) {
                            Text("🚫 $blockedCount", fontSize = 11.sp, color = c.neonRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold)
                        }
                    }

                    IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                        Icon(if (showSearch) Icons.Default.SearchOff else Icons.Default.Search, null,
                            tint = if (showSearch) c.neonCyan else c.textSecond)
                    }
                    IconButton(onClick = { /* Export CSV */ }) {
                        Icon(Icons.Default.FileDownload, null, tint = c.neonCyan)
                    }
                }

                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Rechercher...", color = c.textSecond) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.neonCyan, unfocusedBorderColor = c.glassStroke)
                    )
                }

                LazyRow(contentPadding = PaddingValues(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CallFilter.entries) { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { activeFilter = filter; coroutineScope.launch { listState.scrollToItem(0) } },
                            label = { Text(filter.label, fontSize = 12.sp) },
                            leadingIcon = { Icon(filter.icon, null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = c.neonCyan.copy(0.2f),
                                selectedLabelColor = c.neonCyan,
                                selectedLeadingIconColor = c.neonCyan)
                        )
                    }
                }
            }

            if (loading || !showContent) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = c.neonCyan)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    val itemsToShow = filtered.take(50)
                    val remainingCount = filtered.size - itemsToShow.size

                    // --- CORRECTIF ICI : Utilisation de itemsIndexed et index pour l'unicité des clés ---
                    itemsIndexed(
                        items = itemsToShow,
                        key = { index, group -> "${group.number}_$index" }
                    ) { index, group ->
                        CallGroupRow(
                            group = group,
                            note = notes[PhoneUtils.normalizeNumber(group.number)],
                            onCall = onCall,
                            onAddContact = onAddContact,
                            onEditContact = onEditContact,
                            vm = vm,
                            selectionMode = selectionMode,
                            isSelected = selectedNumbers.contains(group.number),
                            onToggleSelection = {
                                if (selectedNumbers.contains(group.number)) {
                                    selectedNumbers.remove(group.number)
                                    if (selectedNumbers.isEmpty()) selectionMode = false
                                } else {
                                    selectedNumbers.add(group.number)
                                }
                            },
                            onLongClick = { selectionMode = true; selectedNumbers.add(group.number) }
                        )
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }

                    if (remainingCount > 0) {
                        item {
                            Button(
                                onClick = { coroutineScope.launch { } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.surface, contentColor = c.neonCyan),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Charger $remainingCount élément${if (remainingCount > 1) "s" else ""} restant${if (remainingCount > 1) "s" else ""}")
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
    onAddContact: (String) -> Unit,
    onEditContact: (Long) -> Unit,
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

    val isBlocked = remember(group.lastCall.type) { safeIsBlocked(group.lastCall.type) }
    val whitelisted = vm.isWhitelisted(group.number)
    val isKnown = group.name != null

    val hasPhoto = remember(group.photoUri) {
        try {
            !group.photoUri.isNullOrBlank() && Uri.parse(group.photoUri).scheme != null
        } catch (e: Throwable) { false }
    }

    val contactId = remember(group.number) {
        try {
            if (isKnown && group.number.isNotBlank()) PhoneUtils.lookupContactId(context, group.number)
            else null
        } catch (e: Throwable) { null }
    }

    val callType = group.lastCall.type

    val (typeIcon, typeColor) = remember(callType) {
        when {
            safeIsBlocked(callType)                       -> Icons.Default.Block        to Color(0xFFFF5252)
            safeTypeEquals(callType, SAFE_MISSED_TYPE)    -> Icons.Default.CallMissed   to Color(0xFFFF5252)
            safeTypeEquals(callType, SAFE_OUTGOING_TYPE)  -> Icons.Default.CallMade     to Color(0xFF69FF47)
            safeTypeEquals(callType, SAFE_INCOMING_TYPE)  -> Icons.Default.CallReceived to IncomingColor
            else                                           -> Icons.Default.CallReceived to IncomingColor
        }
    }

    val nameColor = remember(callType) {
        when {
            safeIsBlocked(callType)                       -> Color(0xFFFF5252)
            safeTypeEquals(callType, SAFE_MISSED_TYPE)    -> Color(0xFFFF5252)
            safeTypeEquals(callType, SAFE_OUTGOING_TYPE)  -> Color.Unspecified
            else                                           -> IncomingColor
        }
    }

    val missedCount   = remember(group) { try { group.calls.count { safeTypeEquals(it.type, SAFE_MISSED_TYPE) }   } catch (e: Throwable) { 0 } }
    val outgoingCount = remember(group) { try { group.calls.count { safeTypeEquals(it.type, SAFE_OUTGOING_TYPE) } } catch (e: Throwable) { 0 } }
    val incomingCount = remember(group) { try { group.calls.count { safeTypeEquals(it.type, SAFE_INCOMING_TYPE) } } catch (e: Throwable) { 0 } }
    val blockedCount  = remember(group) { try { group.calls.count { safeIsBlocked(it.type) }                      } catch (e: Throwable) { 0 } }

    val periodDays = remember(group) {
        try {
            if (group.calls.size < 2) 0
            else {
                val oldest = group.calls.minOf { it.timestamp }
                val newest = group.calls.maxOf { it.timestamp }
                ((newest - oldest) / (1000L * 60 * 60 * 24)).toInt().coerceAtMost(20)
            }
        } catch (e: Throwable) { 0 }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) c.neonCyan.copy(alpha = 0.1f) else Color.Transparent)
                .combinedClickable(
                    onClick = { if (selectionMode) onToggleSelection() else if (!isBlocked) onCall(group.number) },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() },
                    colors = CheckboxDefaults.colors(checkedColor = c.neonCyan))
                Spacer(Modifier.width(8.dp))
            }

            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (hasPhoto) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(group.photoUri).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier.align(Alignment.BottomEnd).size(18.dp)
                            .background(c.background, CircleShape).padding(2.dp)
                            .background(typeColor.copy(0.9f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier.size(48.dp).background(typeColor.copy(0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(typeIcon, null, tint = typeColor, modifier = Modifier.size(22.dp))
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = group.name ?: group.number,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = nameColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (group.callCount > 1) {
                        Spacer(Modifier.width(6.dp))
                        Text("(${group.callCount})", fontSize = 13.sp, color = typeColor, fontWeight = FontWeight.Bold)
                    }
                }
                if (isKnown) {
                    Text(group.number, fontSize = 12.sp, color = c.textSecond.copy(alpha = 0.7f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Text(formatTimestampFull(group.lastCall.timestamp), fontSize = 11.sp, color = c.textSecond)
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
                    if (!note.isNullOrBlank()) { Spacer(Modifier.width(4.dp)); Text("📝", fontSize = 10.sp) }
                    if (whitelisted) { Spacer(Modifier.width(4.dp)); Text("🛡️", fontSize = 10.sp) }
                }
            }

            if (!selectionMode) {
                if (group.callCount > 1) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, tint = c.textSecond)
                    }
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = c.textSecond)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(c.surfaceVar)) {
                        if (!isBlocked) {
                            DropdownMenuItem(
                                text = { Text("📞 Appeler", color = c.neonGreen) },
                                onClick = { onCall(group.number); showMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("💬 Envoyer un SMS", color = c.neonCyan) },
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("smsto:${group.number}")
                                        })
                                    } catch (e: Throwable) { }
                                    showMenu = false
                                }
                            )
                        }
                        if (!isKnown) {
                            DropdownMenuItem(
                                text = { Text("Ajouter aux contacts", color = c.textPrimary) },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, null, tint = c.neonCyan) },
                                onClick = { onAddContact(group.number); showMenu = false }
                            )
                        }
                        if (contactId != null) {
                            DropdownMenuItem(
                                text = { Text("✏️ Modifier le contact", color = c.textPrimary) },
                                onClick = { onEditContact(contactId); showMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("📝 Note / Commentaire", color = c.textPrimary) },
                            onClick = { showMenu = false }
                        )
                        if (!whitelisted) {
                            DropdownMenuItem(
                                text = { Text("🛡️ Liste Blanche", color = c.neonCyan) },
                                onClick = { vm.addToWhitelist(group.number); showMenu = false }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("🔓 Retirer Liste Blanche", color = c.textPrimary) },
                                onClick = { vm.removeFromWhitelist(group.number); showMenu = false }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("⚠️ Signaler & Bloquer", color = c.neonOrange) },
                            onClick = { vm.reportNumber(group.number, "Indésirable"); showMenu = false }
                        )
                        HorizontalDivider(color = c.glassStroke)
                        DropdownMenuItem(
                            text = { Text("🗑️ Supprimer", color = c.neonRed) },
                            onClick = { vm.removeCallGroup(group.number); showMenu = false }
                        )
                    }
                }
            }
        }

        if (expanded && group.calls.size > 1) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(c.surface.copy(alpha = 0.5f))
                    .padding(start = 76.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            ) {
                if (periodDays > 0) {
                    Text(
                        "📊 Sur ${periodDays} jour${if (periodDays > 1) "s" else ""} : " +
                                "${outgoingCount} sortant${if (outgoingCount > 1) "s" else ""}, " +
                                "${incomingCount} entrant${if (incomingCount > 1) "s" else ""}, " +
                                "${missedCount} manqué${if (missedCount > 1) "s" else ""}",
                        fontSize = 11.sp, color = c.textSecond, modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                group.calls.take(10).forEach { call ->
                    val (icon, color) = when {
                        safeTypeEquals(call.type, SAFE_OUTGOING_TYPE) -> Icons.Default.CallMade     to c.neonGreen
                        safeTypeEquals(call.type, SAFE_INCOMING_TYPE) -> Icons.Default.CallReceived to IncomingColor
                        safeTypeEquals(call.type, SAFE_MISSED_TYPE)   -> Icons.Default.CallMissed   to c.neonRed
                        safeIsBlocked(call.type)                       -> Icons.Default.Block        to c.neonRed
                        else                                            -> Icons.Default.Call         to c.textSecond
                    }
                    val typeLabel = when {
                        safeTypeEquals(call.type, SAFE_OUTGOING_TYPE) -> "Sortant"
                        safeTypeEquals(call.type, SAFE_INCOMING_TYPE) -> "Entrant"
                        safeTypeEquals(call.type, SAFE_MISSED_TYPE)   -> "Manqué"
                        safeIsBlocked(call.type)                       -> "Bloqué"
                        else                                            -> "Appel"
                    }
                    val duration = try {
                        if (call.duration > 0) {
                            val min = call.duration / 60; val sec = call.duration % 60
                            if (min > 0) "${min}m${sec}s" else "${sec}s"
                        } else ""
                    } catch (e: Throwable) { "" }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(formatTimestampDetail(call.timestamp), fontSize = 11.sp, color = c.textSecond)
                        Spacer(Modifier.width(8.dp))
                        Text(typeLabel, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
                        if (duration.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("($duration)", fontSize = 10.sp, color = c.textSecond)
                        }
                        if (call.simSlot >= 0) {
                            Spacer(Modifier.width(6.dp))
                            Text("SIM${call.simSlot + 1}", fontSize = 9.sp, color = c.textSecond.copy(alpha = 0.6f))
                        }
                    }
                }

                if (group.calls.size > 10) {
                    Text(
                        "... et ${group.calls.size - 10} autre${if (group.calls.size - 10 > 1) "s" else ""}",
                        fontSize = 10.sp, color = c.textSecond, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestampFull(ts: Long): String = try {
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(ts))
} catch (e: Throwable) { "--/-- --:--" }

private fun formatTimestampDetail(ts: Long): String = try {
    val diff = System.currentTimeMillis() - ts
    val sdf = when {
        diff < 24 * 60 * 60 * 1000L     -> SimpleDateFormat("HH:mm", Locale.getDefault())
        diff < 7 * 24 * 60 * 60 * 1000L -> SimpleDateFormat("EEE HH:mm", Locale.getDefault())
        else                              -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
    sdf.format(Date(ts))
} catch (e: Throwable) { "--:--" }