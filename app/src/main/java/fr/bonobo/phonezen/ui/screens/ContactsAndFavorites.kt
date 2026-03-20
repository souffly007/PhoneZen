package fr.bonobo.phonezen.ui.screens

import android.content.ContentUris
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.bonobo.phonezen.data.model.Contact
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel

@Composable
fun ContactsScreen(vm: MainViewModel, onCall: (String) -> Unit) {
    val c       = LocalColors.current
    val query   by vm.searchQuery.collectAsState()
    val loading by vm.isLoading.collectAsState()

    // ── FIX étoile : on observe contacts directement via StateFlow ──
    val contacts by vm.contacts.collectAsState()
    val filtered = remember(contacts, query) {
        val q = query.lowercase().trim()
        if (q.isEmpty()) contacts
        else contacts.filter { it.name.lowercase().contains(q) || it.phoneNumber.contains(q) }
    }
    val sorted = remember(filtered) {
        filtered.sortedWith(compareByDescending<Contact> { it.isFavorite }.thenBy { it.name })
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {
        Text(
            text       = "Contacts",
            fontSize   = 24.sp,
            fontWeight = FontWeight.Bold,
            color      = c.neonCyan,
            modifier   = Modifier.padding(16.dp)
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = c.neonCyan)
            }
        } else if (sorted.isEmpty()) {
            EmptyState(icon = Icons.Default.PersonSearch, message = "Aucun contact")
        } else {
            val favCount = sorted.count { it.isFavorite }
            LazyColumn(Modifier.fillMaxSize()) {
                if (favCount > 0) {
                    item { SectionLabel("⭐ Favoris") }
                    items(sorted.take(favCount), key = { it.contactId }) { contact ->
                        ContactRow(contact = contact, onCall = onCall, vm = vm)
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }
                    item { SectionLabel("👤 Tous les contacts") }
                }
                items(sorted.drop(favCount), key = { it.contactId }) { contact ->
                    ContactRow(contact = contact, onCall = onCall, vm = vm)
                    HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalColors.current
    Text(
        text       = text,
        fontSize   = 12.sp,
        fontWeight = FontWeight.Bold,
        color      = c.neonCyan,
        modifier   = Modifier
            .fillMaxWidth()
            .background(c.background)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
fun FavoritesScreen(vm: MainViewModel, onCall: (String) -> Unit) {
    val c         = LocalColors.current
    val favorites by vm.favorites.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "Favoris",
                fontSize   = 24.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonOrange,
                modifier   = Modifier.weight(1f)
            )
            if (favorites.isNotEmpty()) {
                Text("Triés par appels", fontSize = 11.sp, color = c.textSecond)
                Icon(Icons.Default.ArrowDownward, null, tint = c.textSecond, modifier = Modifier.size(12.dp))
            }
        }
        if (favorites.isEmpty()) {
            EmptyState(icon = Icons.Default.StarBorder, message = "Aucun favori", subtitle = "Appuyez sur ⭐ dans la liste")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(favorites, key = { it.contactId }) { contact ->
                    ContactRow(contact = contact, onCall = onCall, vm = vm)
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
    val whitelist     by vm.whitelist.collectAsState()
    val isWhitelisted  = whitelist.contains(PhoneUtils.normalizeNumber(contact.phoneNumber))
    var isExpanded    by remember { mutableStateOf(false) }
    var showWlDialog  by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 46)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(contact.name, fontSize = 16.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.phoneNumber, fontSize = 13.sp, color = c.textSecond)
                    if (contact.isFavorite && contact.callCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text     = "📞 ${contact.callCount}",
                            fontSize = 11.sp,
                            color    = c.neonOrange.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Favori
            IconButton(onClick = { vm.toggleFavorite(contact.phoneNumber) }) {
                Icon(
                    imageVector        = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "Favori",
                    tint               = if (contact.isFavorite) c.neonOrange else c.textSecond
                )
            }

            // Liste blanche
            IconButton(onClick = {
                if (isWhitelisted) showWlDialog = true
                else vm.addToWhitelist(contact.phoneNumber)
            }) {
                Icon(
                    imageVector        = if (isWhitelisted) Icons.Default.Shield else Icons.Outlined.Shield,
                    contentDescription = "Liste blanche",
                    tint               = if (isWhitelisted) c.neonCyan else c.textSecond
                )
            }

            // Appel
            IconButton(onClick = { onCall(contact.phoneNumber) }) {
                Icon(Icons.Default.Call, contentDescription = "Appeler", tint = c.neonGreen)
            }
        }

        if (isExpanded) {
            Row(
                modifier              = Modifier.fillMaxWidth().background(c.surfaceVar.copy(alpha = 0.3f)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_EDIT).apply {
                        data = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.contactId)
                    })
                }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = c.neonCyan)
                    Text(" Modifier", color = c.neonCyan)
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.contactId)
                    })
                }) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp), tint = c.textSecond)
                    Text(" Détails", color = c.textSecond)
                }
            }
        }
    }

    if (showWlDialog) {
        AlertDialog(
            onDismissRequest = { showWlDialog = false },
            containerColor   = c.surfaceVar,
            title = { Text("Retirer de la liste blanche ?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("${contact.name} (${contact.phoneNumber}) pourra à nouveau être bloqué.", color = c.textSecond) },
            confirmButton = {
                TextButton(onClick = { vm.removeFromWhitelist(contact.phoneNumber); showWlDialog = false }) {
                    Text("Retirer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWlDialog = false }) { Text("Annuler", color = c.neonCyan) }
            }
        )
    }
}

@Composable
fun ContactAvatar(name: String?, photoUri: String?, size: Int = 46) {
    val c      = LocalColors.current
    val sizeDp = size.dp
    if (!photoUri.isNullOrBlank()) {
        AsyncImage(
            model              = ImageRequest.Builder(LocalContext.current).data(photoUri).crossfade(true).build(),
            contentDescription = name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.size(sizeDp).clip(CircleShape)
        )
    } else {
        Box(
            modifier         = Modifier.size(sizeDp).background(c.neonCyan.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = name?.firstOrNull()?.uppercase() ?: "?",
                color      = c.neonCyan,
                fontWeight = FontWeight.Bold,
                fontSize   = (size * 0.4f).sp
            )
        }
    }
}