// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.

package fr.bonobo.phonezen.ui.screens

import android.telephony.TelephonyManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import fr.bonobo.phonezen.viewmodel.MainViewModel
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.*

// ── Clés du clavier ──
data class Key(val main: String, val sub: String = "")
val DIAL_KEYS = listOf(
    Key("1", "RÉP"), Key("2", "ABC"), Key("3", "DEF"),
    Key("4", "GHI"), Key("5", "JKL"), Key("6", "MNO"),
    Key("7", "PQRS"), Key("8", "TUV"), Key("9", "WXYZ"),
    Key("*"), Key("0", "+"), Key("#")
)

// ── Numéros internationaux (+33 sans 0) ──
private val VOICEMAIL_NUMBERS = mapOf(
    "orange" to "+33608080808",
    "sosh" to "+33608080808",
    "sfr" to "+33612000123",
    "red" to "+33612000123",
    "bouygues" to "+33660660001",
    "bouygues telecom" to "+33660660001",
    "b&you" to "+33660660001",
    "free mobile" to "+33695600012",
    "free" to "+33695600012",
    "la poste mobile" to "+33612000123",
    "prixtel" to "+33612000123",
    "nrj mobile" to "+33771212777",
    "lycamobile" to "+33751000121",
    "lebara" to "+33680802345"
)

// ── Codes courts (France) ──
private val VOICEMAIL_SHORTCODES = mapOf(
    "orange" to "888",
    "sfr" to "123",
    "bouygues" to "660",
    "free mobile" to "666",
    "nrj mobile" to "777",
    "lycamobile" to "121",
    "lebara" to "2345"
)

private const val VOICEMAIL_FALLBACK = "123"
private const val INTERNATIONAL_FALLBACK = "+33612000123"

// ── Helpers ──
private fun isInFrance(context: android.content.Context): Boolean {
    return try {
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        tm?.networkCountryIso?.lowercase() == "fr"
    } catch (e: Exception) {
        false
    }
}

fun detectOperatorName(context: android.content.Context): String {
    return try {
        val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        tm?.networkOperatorName?.ifBlank { "Opérateur inconnu" } ?: "Opérateur inconnu"
    } catch (e: Exception) {
        "Opérateur inconnu"
    }
}

private fun formatPhoneNumber(number: String): String {
    if (number.length <= 3) return number
    if (!number.startsWith("+33")) return number
    val digits = number.drop(3)
    return "+33 " + digits.chunked(2).joinToString(" ")
}

private fun isUssdCode(number: String): Boolean =
    number.matches(Regex("^[*#][0-9*#]+#?$")) || number.startsWith("*#") || number.startsWith("#")

private fun isValidNumber(number: String): Boolean {
    val cleaned = number.replace(Regex("[\\s.\\-()]"), "")
    return cleaned.matches(Regex("^[+]?[0-9]{6,15}$"))
}

fun detectVoicemailNumber(
    context: android.content.Context,
    forceInternational: Boolean = false
): String {
    val inFrance = isInFrance(context) && !forceInternational
    val tm = context.getSystemService(android.telephony.TelephonyManager::class.java)
        ?: return if (inFrance) VOICEMAIL_FALLBACK else INTERNATIONAL_FALLBACK

    var operatorName = tm.networkOperatorName?.lowercase()?.trim()?.replace(Regex("\\s+"), " ") ?: ""
    val key = VOICEMAIL_NUMBERS.keys.firstOrNull { operatorName.contains(it) }
        ?: VOICEMAIL_SHORTCODES.keys.firstOrNull { operatorName.contains(it) }

    if (key != null) {
        return if (inFrance) (VOICEMAIL_SHORTCODES[key] ?: VOICEMAIL_FALLBACK)
        else (VOICEMAIL_NUMBERS[key] ?: INTERNATIONAL_FALLBACK)
    }

    val mnc = tm.networkOperator
    return if (inFrance) {
        when {
            mnc.startsWith("2080") -> "888"
            mnc.startsWith("2081") -> "123"
            mnc.startsWith("2082") -> "660"
            mnc == "20815" -> "666"
            else -> VOICEMAIL_FALLBACK
        }
    } else {
        when {
            mnc.startsWith("2080") -> "+33608080808"
            mnc.startsWith("2081") -> "+33612000123"
            mnc.startsWith("2082") -> "+33660660001"
            mnc == "20815" -> "+33695600012"
            else -> INTERNATIONAL_FALLBACK
        }
    }
}

// ── Composant Clé ──
@Composable
fun DialKey(
    key: Key,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = LocalColors.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
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
            Text(
                text = key.main,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = c.neonOrange
            )
            if (key.sub.isNotEmpty()) {
                Text(
                    text = key.sub,
                    fontSize = 9.sp,
                    color = c.textSecond
                )
            }
        }
    }
}

// ── Écran Principal ──
@Composable
fun KeypadScreen(
    vm: MainViewModel, // Intégration du ViewModel pour le tri
    onCall: (String) -> Unit,
    onVoicemail: () -> Unit = {}
) {
    val c = LocalColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var number by remember { mutableStateOf("") }
    var forceInternational by remember { mutableStateOf(false) }

    val isUssd = remember(number) { isUssdCode(number) }
    val voicemailNumber = remember(forceInternational) {
        detectVoicemailNumber(context, forceInternational)
    }
    val operatorName = remember { detectOperatorName(context) }
    val formattedVoicemail = remember(voicemailNumber) {
        formatPhoneNumber(voicemailNumber)
    }
    val isFranceDetected = remember { isInFrance(context) }

    val isSmallScreen = configuration.screenHeightDp < 700
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fabSizeDp = if (isSmallScreen) 72.dp else 80.dp

    val modeText = when {
        forceInternational -> "International forcé"
        isFranceDetected -> "France (code court)"
        else -> "International"
    }

    // Fonction d'appel centralisée pour gérer le tri
    fun handleCallAction(targetNumber: String) {
        if (targetNumber.isEmpty()) return
        // On incrémente le compteur de ce numéro dans le ViewModel pour le tri des favoris
        vm.incrementCallCount(targetNumber)
        onCall(targetNumber)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isUssd) "CODE USSD" else "CLAVIER",
            style = MaterialTheme.typography.titleLarge,
            color = if (isUssd) c.neonCyan else c.neonOrange,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUssd) c.neonCyan.copy(0.08f) else c.surface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.ifEmpty { "Composez un numéro" },
                    fontSize = if (number.isEmpty()) 16.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        number.isEmpty() -> c.textSecond
                        isUssd -> c.neonCyan
                        else -> c.textPrimary
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Grille de touches
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            DIAL_KEYS.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { key ->
                        DialKey(
                            key = key,
                            onTap = { number += key.main },
                            onLongPress = {
                                when (key.main) {
                                    "0" -> number += "+"
                                    "1" -> handleCallAction(voicemailNumber)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Boutons d'action (Backspace, Call, Clear)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = navBarPadding.coerceAtLeast(8.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                modifier = Modifier.size(60.dp)
            ) {
                Icon(Icons.Default.Backspace, null, tint = c.neonRed)
            }

            FloatingActionButton(
                onClick = { handleCallAction(number) },
                modifier = Modifier.size(fabSizeDp),
                containerColor = if (isUssd) c.neonCyan else c.neonGreen,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            FilledIconButton(
                onClick = { number = "" },
                modifier = Modifier.size(60.dp)
            ) {
                Text("C", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.neonOrange)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Contrôle Messagerie
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Messagerie : $modeText", fontSize = 12.sp, color = c.textSecond)
            Switch(
                checked = forceInternational,
                onCheckedChange = { forceInternational = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.neonCyan,
                    checkedTrackColor = c.neonCyan.copy(alpha = 0.5f)
                )
            )
        }

        Spacer(Modifier.height(8.dp))

        // Bouton Messagerie
        OutlinedButton(
            onClick = { handleCallAction(voicemailNumber) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Voicemail, null, tint = c.neonCyan)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("MESSAGERIE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("$operatorName · $formattedVoicemail", fontSize = 10.sp, color = c.textSecond)
            }
        }
    }
}