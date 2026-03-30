package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.util.SimInfo
import fr.bonobo.phonezen.util.getActiveSims
import fr.bonobo.phonezen.viewmodel.MainViewModel

data class Key(val main: String, val sub: String = "")

val DIAL_KEYS = listOf(
    Key("1", "RÉP"), Key("2", "ABC"), Key("3", "DEF"),
    Key("4", "GHI"), Key("5", "JKL"), Key("6", "MNO"),
    Key("7", "PQRS"), Key("8", "TUV"), Key("9", "WXYZ"),
    Key("*"), Key("0", "+"), Key("#")
)

private fun isUssdCode(number: String): Boolean =
    number.matches(Regex("^[*#][0-9*#]+#?$")) ||
            number.startsWith("*#") || number.startsWith("#")

@Composable
fun DialKey(
    key: Key,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    size: Dp = 72.dp,
    fontSize: Float = 26f
) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .pointerInput(key.main) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(key.main, fontSize = fontSize.sp, fontWeight = FontWeight.Bold, color = c.neonOrange)
            if (key.sub.isNotEmpty()) {
                Text(key.sub, fontSize = (fontSize * 0.35f).sp, color = c.textSecond)
            }
        }
    }
}

@Composable
fun KeypadScreen(
    onCall: (String) -> Unit,
    onCallWithSim: (String, Int) -> Unit = { n, _ -> onCall(n) },
    onVoicemail: () -> Unit,
    vm: MainViewModel
) {
    val c = LocalColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var number by remember { mutableStateOf("") }

    val sims = remember { getActiveSims(context) }
    val isDualSim = sims.size >= 2
    val isUssd = remember(number) { isUssdCode(number) }

    // ── Préremplissage depuis un intent tel: (lien navigateur) ──
    val dialpadNumber by vm.dialpadNumber.collectAsState()
    LaunchedEffect(dialpadNumber) {
        if (dialpadNumber.isNotEmpty()) {
            number = dialpadNumber
            vm.clearDialpadNumber() // consommé, on remet à zéro
        }
    }

    // ── Suggestions réactives ──
    val contactsList by vm.contacts.collectAsState()
    val suggestions = remember(number, contactsList) {
        vm.getSuggestions(number)
    }

    // ── Dialog choix SIM ──
    var showSimDialog by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf("") }

    // ── Menu contextuel coller ──
    var showPasteMenu by remember { mutableStateOf(false) }

    fun handleCall() {
        if (number.isEmpty()) return
        if (isDualSim) {
            pendingNumber = number
            showSimDialog = true
        } else {
            onCall(number)
        }
    }

    fun pasteFromClipboard() {
        val text = clipboardManager.getText()?.text ?: return
        val cleaned = text.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleaned.isNotEmpty()) number = cleaned
    }

    // ── Dialog sélection SIM ──
    if (showSimDialog) {
        SimSelectionDialog(
            sims = sims,
            number = pendingNumber,
            onSelect = { sim ->
                showSimDialog = false
                onCallWithSim(pendingNumber, sim.subscriptionId)
            },
            onDismiss = { showSimDialog = false }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 16.dp)
    ) {
        val screenH = maxHeight
        val isSmall = screenH < 600.dp
        val isMedium = screenH < 750.dp

        val keySize = when { isSmall -> 58.dp; isMedium -> 66.dp; else -> 74.dp }
        val keyFontSize = when { isSmall -> 22f; isMedium -> 24f; else -> 27f }
        val fabSize = when { isSmall -> 60.dp; isMedium -> 68.dp; else -> 74.dp }
        val actionSize = when { isSmall -> 48.dp; isMedium -> 52.dp; else -> 56.dp }
        val displayH = when { isSmall -> 50.dp; isMedium -> 58.dp; else -> 64.dp }
        val titleSize = when { isSmall -> 14f; isMedium -> 15f; else -> 16f }
        val spacingIn = when { isSmall -> 2.dp; isMedium -> 4.dp; else -> 6.dp }
        val spacingOut = when { isSmall -> 4.dp; isMedium -> 8.dp; else -> 12.dp }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // ── Bloc 1 : Titre + numéro + suggestions ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isUssd) "CODE USSD" else "CLAVIER",
                    fontSize = titleSize.sp,
                    color = if (isUssd) c.neonCyan else c.neonOrange,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(spacingIn))

                // ── Zone numéro ──
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUssd) c.neonCyan.copy(0.1f) else c.surface
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(displayH)
                            .padding(horizontal = 16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = { showPasteMenu = true }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (number.isEmpty()) {
                            Text(
                                text = "Composez un numéro",
                                fontSize = 15.sp,
                                color = c.textSecond,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            BasicTextField(
                                value = number,
                                onValueChange = { new ->
                                    number = new.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUssd) c.neonCyan else c.textPrimary,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(c.neonCyan),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // ── Menu contextuel Coller ──
                    DropdownMenu(
                        expanded = showPasteMenu,
                        onDismissRequest = { showPasteMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Coller") },
                            onClick = {
                                pasteFromClipboard()
                                showPasteMenu = false
                            }
                        )
                        if (number.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Effacer tout") },
                                onClick = {
                                    number = ""
                                    showPasteMenu = false
                                }
                            )
                        }
                    }
                }

                // ── Suggestions de contacts ──
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(spacingIn))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        suggestions.forEach { contact ->
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, c.neonCyan.copy(0.4f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        contact.phoneNumbers.firstOrNull()?.let { num ->
                                            number = num
                                        }
                                    },
                                color = c.neonCyan.copy(0.12f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = contact.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = c.neonCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.phoneNumbers.firstOrNull() ?: "",
                                        fontSize = 9.sp,
                                        color = c.textSecond,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Bloc 2 : Pavé numérique ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DIAL_KEYS.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            DialKey(
                                key = key,
                                onTap = { number += key.main },
                                onLongPress = {
                                    if (key.main == "0") number += "+"
                                    if (key.main == "1") sims.firstOrNull()?.let {
                                        onCallWithSim(it.voicemail, it.subscriptionId)
                                    }
                                },
                                size = keySize,
                                fontSize = keyFontSize
                            )
                        }
                    }
                    Spacer(Modifier.height(spacingIn))
                }
            }

            // ── Bloc 3 : Actions + Messagerie ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                        modifier = Modifier.size(actionSize),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surface)
                    ) {
                        Icon(Icons.Default.Backspace, null, tint = c.neonRed)
                    }

                    FloatingActionButton(
                        onClick = { handleCall() },
                        modifier = Modifier.size(fabSize),
                        containerColor = if (isUssd) c.neonCyan else c.neonGreen,
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.Call, null, tint = Color.White,
                            modifier = Modifier.size((fabSize.value * 0.47f).dp)
                        )
                    }

                    FilledIconButton(
                        onClick = { number = "" },
                        modifier = Modifier.size(actionSize),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surface)
                    ) {
                        Text(
                            "C", fontSize = (actionSize.value * 0.38f).sp,
                            fontWeight = FontWeight.Bold, color = c.neonOrange
                        )
                    }
                }

                Spacer(Modifier.height(spacingOut))

                // ── Messagerie ──
                if (isDualSim) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sims.forEach { sim ->
                            SimVoicemailButton(
                                sim = sim,
                                modifier = Modifier.weight(1f),
                                onClick = { onCallWithSim(sim.voicemail, sim.subscriptionId) }
                            )
                        }
                    }
                } else {
                    sims.firstOrNull()?.let { sim ->
                        OutlinedButton(
                            onClick = { onCall(sim.voicemail) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = c.surface,
                                contentColor = c.textPrimary
                            )
                        ) {
                            Icon(
                                Icons.Default.Voicemail, null,
                                tint = c.neonCyan, modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    "MESSAGERIE",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = c.textPrimary
                                )
                                Text(
                                    "${sim.carrierName} · ${sim.voicemail}",
                                    fontSize = 11.sp, color = c.textSecond,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Dialog sélection SIM ──
@Composable
private fun SimSelectionDialog(
    sims: List<SimInfo>,
    number: String,
    onSelect: (SimInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceVar,
        title = {
            Text("Choisir la SIM", color = c.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Appeler $number avec :",
                    fontSize = 13.sp,
                    color = c.textSecond
                )
                sims.forEach { sim ->
                    val simColor = if (sim.slotIndex == 0) c.neonCyan else c.neonOrange
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(sim) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = simColor.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SimCard, null,
                                tint = simColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = sim.label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = simColor
                                )
                                Text(
                                    text = sim.carrierName,
                                    fontSize = 12.sp,
                                    color = c.textSecond
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = c.textSecond)
            }
        }
    )
}

@Composable
private fun SimVoicemailButton(sim: SimInfo, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val simColor = if (sim.slotIndex == 0) c.neonCyan else c.neonOrange
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = c.surface,
            contentColor = c.textPrimary
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Voicemail, null,
                    tint = simColor, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(sim.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = simColor)
            }
            Text(
                sim.carrierName, fontSize = 10.sp, color = c.textSecond,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(sim.voicemail, fontSize = 10.sp, color = c.textSecond, maxLines = 1)
        }
    }
}