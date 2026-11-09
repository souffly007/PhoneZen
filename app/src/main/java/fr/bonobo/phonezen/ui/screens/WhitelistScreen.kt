package fr.bonobo.phonezen.ui.screens

import fr.bonobo.phonezen.R
import androidx.compose.ui.platform.LocalContext

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistScreen(vm: MainViewModel, onBack: () -> Unit = {}) {
    val c           = LocalColors.current
    val strings = LocalContext.current.resources
    val whitelist   by vm.whitelist.collectAsState()
    val contacts    by vm.contacts.collectAsState()
    var inputNumber by remember { mutableStateOf("") }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    // ── Snackbar pour confirmer l'ajout depuis une notification ──
    val snackbarHostState = remember { SnackbarHostState() }

    val listState      = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val numberToName = remember(contacts) { vm.buildNumberToNameMap() }

    // Taille précédente de la whitelist pour détecter un ajout externe (depuis notification)
    var prevSize by remember { mutableIntStateOf(whitelist.size) }
    LaunchedEffect(whitelist.size) {
        if (whitelist.size > prevSize) {
            // Un numéro a été ajouté depuis l'extérieur (notification "Ne plus bloquer")
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message  = strings.getString(R.string.allow_added),
                    duration = SnackbarDuration.Short
                )
            }
        }
        prevSize = whitelist.size
    }

    fun tryAdd() {
        val cleaned = inputNumber.replace(Regex("[\\s.\\-()]"), "")
        when {
            cleaned.isEmpty()           -> errorMsg = strings.getString(R.string.allow_enter)
            cleaned.length < 6          -> errorMsg = strings.getString(R.string.allow_short)
            vm.isWhitelisted(cleaned) -> errorMsg = strings.getString(R.string.allow_duplicate)
            fr.bonobo.phonezen.utils.InternationalNumbers.e164(cleaned, fr.bonobo.phonezen.utils.PhoneUtils.regionProvider()) == null ->
                errorMsg = strings.getString(R.string.country_number_needed)
            else -> {
                vm.addToWhitelist(cleaned)
                inputNumber = ""
                errorMsg = null
                coroutineScope.launch {
                    listState.animateScrollToItem(whitelist.size)
                }
            }
        }
    }

    Scaffold(
        containerColor  = c.background,
        snackbarHost    = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().background(c.background)) {

            // ── TopBar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = c.neonCyan)
                }
                Text(
                    text       = strings.getString(R.string.allow_title),
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
                        strings.getString(R.string.allow_manual),
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = c.neonCyan,
                        modifier   = Modifier.padding(bottom = 8.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value           = inputNumber,
                            onValueChange   = { inputNumber = it; errorMsg = null },
                            placeholder     = { Text(strings.getString(R.string.allow_example), color = c.textSecond) },
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
                            strings.getString(R.string.allow_contacts_tip),
                            fontSize = 11.sp,
                            color    = c.textSecond.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // ── Liste ──
            if (whitelist.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlaylistAddCheck, null, tint = c.neonCyan.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(strings.getString(R.string.allow_empty), fontSize = 16.sp, color = c.textSecond, fontWeight = FontWeight.Medium)
                        Text(
                            strings.getString(R.string.allow_empty_summary),
                            fontSize = 13.sp,
                            color    = c.textSecond.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                Text(
                    strings.getQuantityString(R.plurals.protected_numbers, whitelist.size, whitelist.size),
                    fontSize = 12.sp,
                    color    = c.textSecond,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(whitelist.toList().sorted()) { number ->
                        WhitelistEntry(
                            number      = number,
                            contactName = numberToName[number],
                            onRemove    = { vm.removeFromWhitelist(number) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WhitelistEntry(number: String, contactName: String?, onRemove: () -> Unit) {
    val c             = LocalColors.current
    val strings = LocalContext.current.resources
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
                    Text(text = number, style = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr),      fontSize = 12.sp, color = c.textSecond)
                } else {
                    Text(text = number, style = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), fontSize = 16.sp, color = c.textPrimary, fontWeight = FontWeight.Medium)
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
            title = { Text(strings.getString(R.string.allow_remove_title), color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text  = {
                val label = if (!contactName.isNullOrBlank()) "$contactName ($number)" else number
                Text(strings.getString(R.string.allow_remove_message, label), color = c.textSecond)
            },
            confirmButton = {
                TextButton(onClick = { onRemove(); confirmDelete = false }) {
                    Text(strings.getString(R.string.allow_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(strings.getString(R.string.submenu_cancel), color = c.neonCyan) }
            }
        )
    }
}
