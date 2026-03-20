package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.util.getCarrierName
import fr.bonobo.phonezen.util.getVoicemailNumber
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
    key        : Key,
    onTap      : () -> Unit,
    onLongPress: (() -> Unit)? = null,
    size       : Dp = 72.dp,
    fontSize   : Float = 26f
) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .pointerInput(key.main) {
                detectTapGestures(
                    onTap       = { onTap() },
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
    onCall     : (String) -> Unit,
    onVoicemail: () -> Unit,
    vm         : MainViewModel
) {
    val c               = LocalColors.current
    val context         = LocalContext.current
    var number          by remember { mutableStateOf("") }

    val voicemailNumber = remember { getVoicemailNumber(context) }
    val operatorName    = remember { getCarrierName(context) ?: "Messagerie" }
    val suggestions     = vm.getSuggestions(number)
    val isUssd          = remember(number) { isUssdCode(number) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 16.dp)
    ) {
        val screenH = maxHeight

        // ── Calcul adaptatif selon la hauteur disponible ──
        val isSmall  = screenH < 600.dp
        val isMedium = screenH < 750.dp

        val keySize      = when { isSmall -> 58.dp;  isMedium -> 66.dp;  else -> 74.dp }
        val keyFontSize  = when { isSmall -> 22f;    isMedium -> 24f;    else -> 27f   }
        val fabSize      = when { isSmall -> 60.dp;  isMedium -> 68.dp;  else -> 74.dp }
        val actionSize   = when { isSmall -> 48.dp;  isMedium -> 52.dp;  else -> 56.dp }
        val displayH     = when { isSmall -> 50.dp;  isMedium -> 58.dp;  else -> 64.dp }
        val titleSize    = when { isSmall -> 14f;    isMedium -> 15f;    else -> 16f   }
        val spacingOuter = when { isSmall -> 4.dp;   isMedium -> 8.dp;   else -> 12.dp }
        val spacingInner = when { isSmall -> 2.dp;   isMedium -> 4.dp;   else -> 6.dp  }

        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {

            // ── Bloc 1 : Titre + numéro ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = if (isUssd) "CODE USSD" else "CLAVIER",
                    fontSize   = titleSize.sp,
                    color      = if (isUssd) c.neonCyan else c.neonOrange,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(spacingInner))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = CardDefaults.cardColors(
                        containerColor = if (isUssd) c.neonCyan.copy(0.1f) else c.surface
                    )
                ) {
                    Box(
                        modifier         = Modifier.fillMaxWidth().height(displayH).padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = number.ifEmpty { "Composez un numéro" },
                            fontSize   = if (number.isEmpty()) 15.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            color      = if (number.isEmpty()) c.textSecond else if (isUssd) c.neonCyan else c.textPrimary,
                            textAlign  = TextAlign.Center,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }
                // Suggestions
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(spacingInner))
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        suggestions.forEach { contact ->
                            Surface(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.dp, c.neonCyan.copy(0.4f), RoundedCornerShape(14.dp))
                                    .clickable { onCall(contact.phoneNumber) },
                                color = c.neonCyan.copy(0.12f)
                            ) {
                                Text(
                                    text       = contact.name,
                                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = c.neonCyan
                                )
                            }
                        }
                    }
                }
            }

            // ── Bloc 2 : Pavé numérique ──
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DIAL_KEYS.chunked(3).forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            DialKey(
                                key         = key,
                                onTap       = { number += key.main },
                                onLongPress = {
                                    if (key.main == "0") number += "+"
                                    if (key.main == "1") onCall(voicemailNumber)
                                },
                                size      = keySize,
                                fontSize  = keyFontSize
                            )
                        }
                    }
                    Spacer(Modifier.height(spacingInner))
                }
            }

            // ── Bloc 3 : Actions + Messagerie ──
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick  = { if (number.isNotEmpty()) number = number.dropLast(1) },
                        modifier = Modifier.size(actionSize),
                        colors   = IconButtonDefaults.filledIconButtonColors(containerColor = c.surface)
                    ) {
                        Icon(Icons.Default.Backspace, null, tint = c.neonRed)
                    }

                    FloatingActionButton(
                        onClick        = { if (number.isNotEmpty()) onCall(number) },
                        modifier       = Modifier.size(fabSize),
                        containerColor = if (isUssd) c.neonCyan else c.neonGreen,
                        shape          = CircleShape
                    ) {
                        Icon(Icons.Default.Call, null, tint = Color.White,
                            modifier = Modifier.size((fabSize.value * 0.47f).dp))
                    }

                    FilledIconButton(
                        onClick  = { number = "" },
                        modifier = Modifier.size(actionSize),
                        colors   = IconButtonDefaults.filledIconButtonColors(containerColor = c.surface)
                    ) {
                        Text("C", fontSize = (actionSize.value * 0.38f).sp,
                            fontWeight = FontWeight.Bold, color = c.neonOrange)
                    }
                }

                Spacer(Modifier.height(spacingOuter))

                OutlinedButton(
                    onClick  = { onCall(voicemailNumber) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        containerColor = c.surface,
                        contentColor   = c.textPrimary
                    )
                ) {
                    Icon(Icons.Default.Voicemail, null, tint = c.neonCyan,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("MESSAGERIE", fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, color = c.textPrimary)
                        Text("$operatorName · $voicemailNumber",
                            fontSize = 11.sp, color = c.textSecond,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}