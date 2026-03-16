package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel

@Composable
fun WhitelistScreen(vm: MainViewModel, onBack: () -> Unit = {}) {
    val c           = LocalColors.current
    val whitelist   by vm.whitelist.collectAsState()
    val contacts    by vm.contacts.collectAsState()
    var inputNumber by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    val numberToName = remember(contacts) { vm.buildNumberToNameMap() }

    fun tryAdd() {
        val cleaned = inputNumber.replace(Regex("[\\s.\\-()]"), "")
        when {
            cleaned.isEmpty()           -> errorMsg = "Entrez un numéro"
            cleaned.length < 6          -> errorMsg = "Numéro trop court"
            whitelist.contains(cleaned) -> errorMsg = "Déjà dans la liste blanche"
            else -> { vm.addToWhitelist(cleaned); inputNumber = ""; errorMsg = null }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {

        // ── TopBar ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = c.neonCyan)
            }
            Text(
                text       = "Liste blanche",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = c.neonCyan,
                modifier   = Modifier.padding(start = 4.dp)
            )
        }

        // ── Champ d'ajout manuel ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape    = RoundedCornerShape(12.dp),
            colors   = CardDefaults.cardColors(containerColor = c.surfaceVar)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Ajouter un numéro manuellement",
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color      = c.neonCyan,
                    modifier   = Modifier.padding(bottom = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value           = inputNumber,
                        onValueChange   = { inputNumber = it; errorMsg = null },
                        placeholder     = { Text("Ex: 0612345678", color = c.textSecond) },
                        singleLine      = true,
                        modifier        = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { tryAdd() }),
                        isError         = errorMsg != null,
                        colors          = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = c.neonCyan,
                            unfocusedBorderColor = c.glassStroke,
                            focusedTextColor     = c.textPrimary,
                            unfocusedTextColor   = c.textPrimary,
                            errorBorderColor     = MaterialTheme.colorScheme.error,
                            cursorColor          = c.neonCyan
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick  = { tryAdd() },
                        modifier = Modifier.background(c.neonCyan, RoundedCornerShape(8.dp)).size(48.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = c.background)
                    }
                }
                if (errorMsg != null) {
                    Text(errorMsg!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = c.textSecond.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Vous pouvez aussi utiliser l'icône 🛡 depuis l'onglet Contacts",
                        fontSize = 11.sp,
                        color    = c.textSecond.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // ── Liste vide ──
        if (whitelist.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlaylistAddCheck, null, tint = c.neonCyan.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Liste blanche vide", fontSize = 16.sp, color = c.textSecond, fontWeight = FontWeight.Medium)
                    Text(
                        "Les numéros ajoutés ici ne seront\njamais bloqués par PhoneZen",
                        fontSize = 13.sp,
                        color    = c.textSecond.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Text(
                "${whitelist.size} numéro(s) protégé(s)",
                fontSize = 12.sp,
                color    = c.textSecond,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(whitelist.toList().sorted()) { number ->
                    WhitelistEntry(number = number, contactName = numberToName[number], onRemove = { vm.removeFromWhitelist(number) })
                }
            }
        }
    }
}

@Composable
private fun WhitelistEntry(number: String, contactName: String?, onRemove: () -> Unit) {
    val c             = LocalColors.current
    var confirmDelete by remember { mutableStateOf(false) }

    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = c.surfaceVar)) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, null, tint = c.neonCyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (!contactName.isNullOrBlank()) {
                    Text(text = contactName, fontSize = 15.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                    Text(text = number,      fontSize = 12.sp, color = c.textSecond)
                } else {
                    Text(text = number, fontSize = 16.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.DeleteOutline, null, tint = c.textSecond)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor   = c.surfaceVar,
            title = { Text("Retirer de la liste blanche ?", color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                val label = if (!contactName.isNullOrBlank()) "$contactName ($number)" else number
                Text("$label pourra à nouveau être bloqué.", color = c.textSecond)
            },
            confirmButton = {
                TextButton(onClick = { onRemove(); confirmDelete = false }) {
                    Text("Retirer", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler", color = c.neonCyan) }
            }
        )
    }
}