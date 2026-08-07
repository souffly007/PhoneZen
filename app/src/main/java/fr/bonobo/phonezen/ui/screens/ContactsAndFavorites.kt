package fr.bonobo.phonezen.ui.screens

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.bonobo.phonezen.data.model.Contact
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel
import kotlinx.coroutines.launch

// ── Alphabet complet ──────────────────────────────────────────────────────────
private val ALPHABET = ('A'..'Z').map { it.toString() } + listOf("#")

private sealed class ListItem {
    data class Header(val letter: String) : ListItem()
    data class ContactItem(val contact: Contact) : ListItem()
}

// ── Couleur verte WhatsApp ────────────────────────────────────────────────────
private val WhatsAppGreen = Color(0xFF25D366)

// ── Dialog sélection numéro ──────────────────────────────────────────────────
@Composable
fun PhoneNumberPickerDialog(
    contactName: String,
    numbers: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = c.surfaceVar,
        title            = { Text("Appeler $contactName", color = c.textPrimary) },
        text             = {
            Column {
                numbers.forEach { num ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(num) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Call,
                            contentDescription = null,
                            tint               = c.neonGreen,
                            modifier           = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text     = num.replace(" ", "\u00A0"),
                            color    = c.textPrimary,
                            fontSize = 15.sp
                        )
                    }
                    if (num != numbers.last()) {
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = c.neonCyan)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    vm: MainViewModel,
    onCall: (String) -> Unit,
    onAddContact: () -> Unit
) {
    val c        = LocalColors.current
    val query    by vm.searchQuery.collectAsState()
    val loading  by vm.isLoading.collectAsState()
    val contacts by vm.contacts.collectAsState()

    // Gestion du picker pour les contacts à multiples numéros
    val pendingCallContact by vm.pendingCallContact.collectAsState()
    pendingCallContact?.let { contact ->
        PhoneNumberPickerDialog(
            contactName = contact.name,
            numbers     = contact.phoneNumbers,
            onSelect    = { num ->
                vm.dismissPendingCall()
                onCall(num)
            },
            onDismiss = { vm.dismissPendingCall() }
        )
    }

    val sorted = remember(contacts, query) {
        val q = query.lowercase().trim()
        if (q.isEmpty()) {
            contacts.sortedWith(
                compareByDescending<Contact> { it.isFavorite }
                    .thenBy { it.name.lowercase() }
            )
        } else {
            contacts
                .filter { contact ->
                    contact.name.lowercase().contains(q) ||
                            contact.phoneNumbers.any { num -> num.contains(q) }
                }
                .sortedWith(
                    compareByDescending<Contact> { it.isFavorite }
                        .thenBy { !it.name.lowercase().startsWith(q) }
                        .thenBy { it.name.lowercase() }
                )
        }
    }

    Scaffold(
        containerColor = c.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onAddContact,
                containerColor = c.neonCyan,
                contentColor   = c.background,
                shape          = CircleShape,
                modifier       = Modifier.padding(end = 20.dp, bottom = 16.dp)
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Nouveau contact")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
                .padding(bottom = paddingValues.calculateBottomPadding())
        ) {
            Text(
                text       = "Contacts",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonCyan,
                modifier   = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )

            OutlinedTextField(
                value         = query,
                onValueChange = vm::setSearchQuery,
                placeholder   = { Text("Rechercher…", color = c.textSecond) },
                leadingIcon   = { Icon(Icons.Default.Search, null, tint = c.neonCyan) },
                singleLine    = true,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = c.neonCyan,
                    unfocusedBorderColor    = c.glassStroke,
                    focusedContainerColor   = c.surface.copy(alpha = 0.5f),
                    unfocusedContainerColor = c.surface.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(4.dp))

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = c.neonCyan)
                }
            } else if (sorted.isEmpty()) {
                EmptyState(icon = Icons.Default.PersonSearch, message = "Aucun contact")
            } else {
                val showAlpha = query.isBlank()
                ContactListWithAlpha(
                    sorted    = sorted,
                    showAlpha = showAlpha,
                    onCall    = { num ->
                        val contact = contacts.find { c -> c.phoneNumbers.contains(num) }
                        if (contact != null && contact.phoneNumbers.size > 1) {
                            vm.requestCall(contact) { onCall(it) }
                        } else {
                            onCall(num)
                        }
                    },
                    vm        = vm
                )
            }
        }
    }
}

@Composable
private fun ContactListWithAlpha(
    sorted: List<Contact>,
    showAlpha: Boolean,
    onCall: (String) -> Unit,
    vm: MainViewModel
) {
    val c            = LocalColors.current
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<String?>(null) }

    val favCount = sorted.count { it.isFavorite }
    val favs     = sorted.take(favCount)
    val rest     = sorted.drop(favCount)

    val flatItems = remember(sorted) {
        buildList {
            if (favs.isNotEmpty()) {
                add(ListItem.Header("⭐"))
                favs.forEach { add(ListItem.ContactItem(it)) }
                add(ListItem.Header("👤"))
            }
            var currentLetter = ""
            rest.forEach { contact ->
                val letter = contact.name
                    .uppercase()
                    .firstOrNull()
                    ?.let { if (it.isLetter()) it.toString() else "#" }
                    ?: "#"
                if (letter != currentLetter) {
                    currentLetter = letter
                    add(ListItem.Header(letter))
                }
                add(ListItem.ContactItem(contact))
            }
        }
    }

    val letterIndexMap = remember(flatItems) {
        buildMap {
            flatItems.forEachIndexed { index, item ->
                if (item is ListItem.Header &&
                    item.letter != "⭐" && item.letter != "👤"
                ) {
                    putIfAbsent(item.letter, index)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state    = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (showAlpha) 36.dp else 0.dp)
        ) {
            flatItems.forEachIndexed { _, item ->
                when (item) {
                    is ListItem.Header -> item {
                        AlphaHeader(
                            letter  = item.letter,
                            isAlpha = item.letter != "⭐" && item.letter != "👤"
                        )
                    }
                    is ListItem.ContactItem -> item(
                        key = item.contact.contactId.toString() + "_" +
                                (item.contact.phoneNumbers.firstOrNull() ?: "")
                    ) {
                        ContactRow(contact = item.contact, onCall = onCall, vm = vm)
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }
                }
            }
        }

        if (showAlpha) {
            AlphaBar(
                letters      = ALPHABET,
                activeLetter = activeLetter,
                modifier     = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp),
                onLetterSelected = { letter ->
                    activeLetter = letter
                    val idx = letterIndexMap[letter]
                        ?: letterIndexMap.entries
                            .sortedBy { it.key }
                            .lastOrNull { it.key <= letter }?.value
                    if (idx != null) {
                        scope.launch { listState.scrollToItem(idx) }
                    }
                },
                onDragEnd = { activeLetter = null }
            )
        }

        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.Center)
                    .background(c.neonCyan.copy(alpha = 0.9f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = letter,
                    fontSize   = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color      = c.background,
                    textAlign  = TextAlign.Center
                )
            }
        }
    }
}

// ── Barre alphabet améliorée ──────────────────────────────────────────────────
@Composable
private fun AlphaBar(
    letters: List<String>,
    activeLetter: String?,
    modifier: Modifier = Modifier,
    onLetterSelected: (String) -> Unit,
    onDragEnd: () -> Unit
) {
    val c      = LocalColors.current
    val haptic = LocalHapticFeedback.current
    var barHeightPx by remember { mutableStateOf(1f) }
    var lastLetter  by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .width(32.dp)
            .onGloballyPositioned { coords ->
                barHeightPx = coords.size.height.toFloat()
            }
            .pointerInput(letters) {
                awaitPointerEventScope {
                    while (true) {
                        val event   = awaitPointerEvent()
                        val pressed = event.changes.any { it.pressed }

                        if (!pressed) {
                            lastLetter = null
                            onDragEnd()
                            continue
                        }

                        event.changes.forEach { change ->
                            change.consume()
                            val relY   = change.position.y.coerceIn(0f, barHeightPx)
                            val idx    = ((relY / barHeightPx) * letters.size)
                                .toInt()
                                .coerceIn(0, letters.lastIndex)
                            val letter = letters[idx]

                            if (letter != lastLetter) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                lastLetter = letter
                                onLetterSelected(letter)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                val isActive = letter == activeLetter
                val isDark   = c.background.luminance() < 0.5f

                Box(
                    modifier = Modifier
                        .size(if (isActive) 22.dp else 18.dp)
                        .then(
                            if (isActive) Modifier.background(c.neonCyan, CircleShape) else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = if (letter == "#") "•" else letter,
                        fontSize   = if (isActive) 13.sp else 12.sp,
                        fontWeight = if (!isDark) FontWeight.ExtraBold else FontWeight.Bold,
                        color      = when {
                            isActive -> c.background
                            isDark   -> c.neonOrange
                            else     -> Color.Black
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphaHeader(letter: String, isAlpha: Boolean) {
    val c = LocalColors.current
    if (isAlpha) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .background(c.background)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = letter,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonCyan,
            )
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(
                modifier  = Modifier.weight(1f),
                color     = c.neonCyan.copy(alpha = 0.2f),
                thickness = 0.5.dp
            )
        }
    } else {
        Text(
            text       = letter,
            fontSize   = 11.sp,
            fontWeight = FontWeight.Bold,
            color      = c.neonCyan.copy(alpha = 0.8f),
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun FavoritesScreen(vm: MainViewModel, onCall: (String) -> Unit) {
    val c         = LocalColors.current
    val favorites by vm.favorites.collectAsState()

    val pendingCallContact by vm.pendingCallContact.collectAsState()
    pendingCallContact?.let { contact ->
        PhoneNumberPickerDialog(
            contactName = contact.name,
            numbers     = contact.phoneNumbers,
            onSelect    = { num ->
                vm.dismissPendingCall()
                onCall(num)
            },
            onDismiss = { vm.dismissPendingCall() }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "Favoris",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonOrange,
                modifier   = Modifier.weight(1f)
            )
        }
        if (favorites.isEmpty()) {
            EmptyState(icon = Icons.Default.StarBorder, message = "Aucun favori")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(
                    items = favorites,
                    key   = { it.contactId.takeIf { it != 0L }?.toString() ?: "fav-${it.hashCode()}" }
                ) { contact ->
                    ContactRow(
                        contact = contact,
                        onCall  = { num ->
                            if (contact.phoneNumbers.size > 1) {
                                vm.requestCall(contact) { onCall(it) }
                            } else {
                                onCall(num)
                            }
                        },
                        vm = vm
                    )
                    HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun ContactRow(contact: Contact, onCall: (String) -> Unit, vm: MainViewModel) {
    val c             = LocalColors.current
    val context       = LocalContext.current
    val whitelist    by vm.whitelist.collectAsState()
    val isWhitelisted = whitelist.contains(
        PhoneUtils.normalizeNumber(contact.phoneNumbers.firstOrNull() ?: "")
    )
    var showMenu         by remember { mutableStateOf(false) }
    var showWlDialog     by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val handleRowTap = {
        when (contact.phoneNumbers.size) {
            0    -> Unit
            1    -> onCall(contact.phoneNumbers.first())
            else -> vm.requestCall(contact) { onCall(it) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier          = Modifier
                .clickable { handleRowTap() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 46)
            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp)
            ) {
                Text(
                    text       = contact.name,
                    fontSize   = 15.sp,
                    color      = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 3,
                    softWrap   = true,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                contact.phoneNumbers.forEachIndexed { index, num ->
                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .clickable { onCall(num) }
                            .padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Call,
                            contentDescription = null,
                            tint               = c.neonGreen.copy(alpha = 0.7f),
                            modifier           = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text     = num.replace(" ", "\u00A0"),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color    = if (index == 0) c.textSecond else c.textSecond.copy(alpha = 0.6f)
                        )
                        if (index == 0 && contact.isFavorite && contact.callCount > 0) {
                            Text("  📞 ${contact.callCount}", fontSize = 11.sp, color = c.neonOrange)
                        }
                    }
                }
            }

            // Boutons icônes TRÈS RESSERRÉS (collés les uns aux autres)
            Row(
                modifier = Modifier.wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Favori (étoile)
                IconButton(
                    onClick = { contact.phoneNumbers.firstOrNull()?.let { vm.toggleFavorite(it) } },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (contact.isFavorite) c.neonOrange else c.textSecond,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Liste blanche (bouclier)
                IconButton(
                    onClick = {
                        if (isWhitelisted) showWlDialog = true
                        else contact.phoneNumbers.firstOrNull()?.let { vm.addToWhitelist(it) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isWhitelisted) Icons.Default.Shield else Icons.Outlined.Shield,
                        contentDescription = "Liste blanche",
                        tint = if (isWhitelisted) c.neonCyan else c.textSecond,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Menu 3 points
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = c.textSecond,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    // DropdownMenu (en dehors du Row pour éviter les problèmes d'affichage)
    DropdownMenu(
        expanded         = showMenu,
        onDismissRequest = { showMenu = false },
        containerColor   = c.surfaceVar
    ) {
        // 1. Modifier
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = c.neonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Modifier", color = c.textPrimary)
                }
            },
            onClick = {
                showMenu = false
                val intent = Intent(Intent.ACTION_EDIT).apply {
                    data = ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contact.contactId
                    )
                }
                context.startActivity(intent)
            }
        )

        // 2. SMS
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Message, null, modifier = Modifier.size(18.dp), tint = c.neonCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("SMS", color = c.textPrimary)
                }
            },
            onClick = {
                showMenu = false
                val num = contact.phoneNumbers.firstOrNull() ?: return@DropdownMenuItem
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$num")
                }
                context.startActivity(intent)
            }
        )

        // 3. Détails
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = c.textSecond)
                    Spacer(Modifier.width(8.dp))
                    Text("Détails", color = c.textPrimary)
                }
            },
            onClick = {
                showMenu = false
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contact.contactId
                    )
                }
                context.startActivity(intent)
            }
        )

        // 4. WhatsApp
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = WhatsAppGreen
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("WhatsApp", color = WhatsAppGreen)
                }
            },
            onClick = {
                showMenu = false
                val num        = contact.phoneNumbers.firstOrNull() ?: return@DropdownMenuItem
                val digitsOnly = num.replace(Regex("[^0-9]"), "")
                val finalNumber = when {
                    digitsOnly.startsWith("0")  -> "33" + digitsOnly.drop(1)
                    digitsOnly.startsWith("33") -> digitsOnly
                    else                        -> "33$digitsOnly"
                }
                val uri    = Uri.parse("https://wa.me/$finalNumber")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        )

        // 5. Supprimer (avec séparateur)
        HorizontalDivider(color = c.glassStroke)
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            onClick = {
                showMenu = false
                showDeleteDialog = true
            }
        )
    }

    // Dialog : retirer de la liste blanche
    if (showWlDialog) {
        AlertDialog(
            onDismissRequest = { showWlDialog = false },
            containerColor   = c.surfaceVar,
            title            = { Text("Retirer ?", color = c.textPrimary) },
            text             = { Text("Enlever ${contact.name} de la liste blanche ?", color = c.textSecond) },
            confirmButton    = {
                TextButton(onClick = {
                    contact.phoneNumbers.firstOrNull()?.let { vm.removeFromWhitelist(it) }
                    showWlDialog = false
                }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWlDialog = false }) {
                    Text("Annuler", color = c.neonCyan)
                }
            }
        )
    }

    // Dialog : confirmation suppression
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor   = c.surfaceVar,
            title            = { Text("Supprimer ?", color = c.textPrimary) },
            text             = {
                Text(
                    text  = "Supprimer définitivement ${contact.name} de vos contacts ?",
                    color = c.textSecond
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val uri = ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contact.contactId
                    )
                    context.contentResolver.delete(uri, null, null)
                    showDeleteDialog = false
                    Toast.makeText(context, "${contact.name} supprimé", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Supprimer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Annuler", color = c.neonCyan)
                }
            }
        )
    }
}

@Composable
fun ContactAvatar(name: String?, photoUri: String?, size: Int) {
    val c        = LocalColors.current
    val modifier = Modifier.size(size.dp).clip(CircleShape)
    if (!photoUri.isNullOrBlank()) {
        AsyncImage(
            model              = ImageRequest.Builder(LocalContext.current)
                .data(photoUri).crossfade(true).build(),
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = modifier
        )
    } else {
        Box(
            modifier         = modifier.background(c.neonCyan.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name?.firstOrNull()?.uppercase() ?: "?",
                color      = c.neonCyan,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    val c = LocalColors.current
    Column(
        modifier            = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = c.textSecond.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Text(message, color = c.textSecond.copy(alpha = 0.5f))
    }
}