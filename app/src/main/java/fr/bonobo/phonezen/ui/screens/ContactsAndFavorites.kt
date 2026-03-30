package fr.bonobo.phonezen.ui.screens

import android.content.ContentUris
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.style.TextOverflow
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
    val contacts by vm.contacts.collectAsState()

    val filtered = remember(contacts, query) {
        val q = query.lowercase().trim()
        if (q.isEmpty()) contacts
        else contacts.filter {
            it.name.lowercase().contains(q) ||
                    it.phoneNumbers.any { num -> num.contains(q) }
        }
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

                    // On prend les favoris
                    items(
                        items = sorted.take(favCount),
                        // CLÉ SÉCURISÉE : "fav_" + ID + Numéro (pour éviter le crash -2)
                        key = { it.contactId.toString() + "_fav_" + (it.phoneNumbers.firstOrNull() ?: "") }
                    ) { contact ->
                        ContactRow(contact = contact, onCall = onCall, vm = vm)
                        HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                    }

                    item { SectionLabel("👤 Tous les contacts") }
                }

                // On prend le reste
                items(
                    items = sorted.drop(favCount),
                    // CLÉ SÉCURISÉE : "all_" + ID + Numéro
                    key = { it.contactId.toString() + "_all_" + (it.phoneNumbers.firstOrNull() ?: "") }
                ) { contact ->
                    ContactRow(contact = contact, onCall = onCall, vm = vm)
                    HorizontalDivider(color = c.glassStroke, thickness = 0.5.dp)
                }
            }
        }
    }
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
        }
        if (favorites.isEmpty()) {
            EmptyState(icon = Icons.Default.StarBorder, message = "Aucun favori")
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
    val isWhitelisted = whitelist.contains(PhoneUtils.normalizeNumber(contact.phoneNumbers.firstOrNull() ?: ""))
    var isExpanded    by remember { mutableStateOf(false) }
    var showWlDialog  by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier          = Modifier
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 46)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontSize = 16.sp,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // --- ZONE DES NUMÉROS CLIQUABLES ---
                contact.phoneNumbers.forEachIndexed { index, num ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCall(num) } // Appelle le numéro spécifique touché
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = c.neonGreen.copy(alpha = 0.7f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = num,
                            fontSize = 13.sp,
                            color = if (index == 0) c.textSecond else c.textSecond.copy(alpha = 0.6f)
                        )
                        if (index == 0 && contact.isFavorite && contact.callCount > 0) {
                            Text("  📞 ${contact.callCount}", fontSize = 11.sp, color = c.neonOrange)
                        }
                    }
                }
            }

            // Actions rapides (Favoris & Bouclier)
            Row {
                IconButton(onClick = { contact.phoneNumbers.firstOrNull()?.let { vm.toggleFavorite(it) } }) {
                    Icon(
                        imageVector = if (contact.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (contact.isFavorite) c.neonOrange else c.textSecond
                    )
                }
                IconButton(onClick = {
                    if (isWhitelisted) showWlDialog = true
                    else contact.phoneNumbers.firstOrNull()?.let { vm.addToWhitelist(it) }
                }) {
                    Icon(
                        imageVector = if (isWhitelisted) Icons.Default.Shield else Icons.Outlined.Shield,
                        contentDescription = "Liste blanche",
                        tint = if (isWhitelisted) c.neonCyan else c.textSecond
                    )
                }
            }
        }

        // Options "Modifier / Détails" quand on clique sur le nom
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surfaceVar.copy(alpha = 0.2f))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_EDIT).apply {
                        data = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.contactId)
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp), tint = c.neonCyan)
                    Text(" Modifier", color = c.neonCyan, fontSize = 13.sp)
                }
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.contactId)
                    }
                    context.startActivity(intent)
                }) {
                    Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = c.textSecond)
                    Text(" Détails", color = c.textSecond, fontSize = 13.sp)
                }
            }
        }
    }

    if (showWlDialog) {
        AlertDialog(
            onDismissRequest = { showWlDialog = false },
            containerColor = c.surfaceVar,
            title = { Text("Retirer ?", color = c.textPrimary) },
            text = { Text("Enlever ${contact.name} de la liste blanche ?", color = c.textSecond) },
            confirmButton = {
                TextButton(onClick = {
                    contact.phoneNumbers.firstOrNull()?.let { vm.removeFromWhitelist(it) }
                    showWlDialog = false
                }) { Text("Retirer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showWlDialog = false }) { Text("Annuler", color = c.neonCyan) }
            }
        )
    }
}

@Composable
fun ContactAvatar(name: String?, photoUri: String?, size: Int) {
    val c = LocalColors.current
    val modifier = Modifier.size(size.dp).clip(CircleShape)
    if (!photoUri.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(photoUri).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(modifier = modifier.background(c.neonCyan.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Text(name?.firstOrNull()?.uppercase() ?: "?", color = c.neonCyan, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalColors.current
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = c.neonCyan.copy(alpha = 0.8f),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    val c = LocalColors.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = c.textSecond.copy(alpha = 0.3f))
        Spacer(Modifier.height(8.dp))
        Text(message, color = c.textSecond.copy(alpha = 0.5f))
    }
}