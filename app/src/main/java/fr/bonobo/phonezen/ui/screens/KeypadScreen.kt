// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.

package fr.bonobo.phonezen.ui.screens

import android.telephony.TelephonyManager
import android.util.Log
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

// ── Numéros internationaux (+33 sans 0 initial) ──
private val VOICEMAIL_NUMBERS = mapOf(
    "orange" to "+33608080808",
    "sosh" to "+33608080808",
    "sfr" to "+33612000123",
    "red" to "+33612000123",
    "red by sfr" to "+33612000123",
    "bouygues" to "+33660660001",
    "bouygues telecom" to "+33660660001",
    "b&you" to "+33660660001",
    "free mobile" to "+33695600012",  // Officiel Free 2026 (assistance.free.fr)
    "free" to "+33695600012",
    "la poste mobile" to "+33612000123",
    "la poste" to "+33612000123",
    "prixtel" to "+33612000123",
    "coriolis" to "+33612000123",
    "réglo mobile" to "+33612000123",
    "nrj mobile" to "+33771212777",
    "nrj" to "+33771212777",
    "cic mobile" to "+33771212777",
    "auchan telecom" to "+33771212777",
    "syma mobile" to "+33608080808",
    "youprice" to "+33608080808",
    "lebara" to "+33680802345",
    "lycamobile" to "+33751000121"
)

// ── Codes courts (en France) ──
private val VOICEMAIL_SHORTCODES = mapOf(
    "orange" to "888",
    "sosh" to "888",
    "sfr" to "123",
    "red" to "123",
    "bouygues" to "660",
    "bouygues telecom" to "660",
    "b&you" to "660",
    "free mobile" to "666",
    "free" to "666",
    "nrj mobile" to "777",
    "lycamobile" to "121",
    "lebara" to "2345"
)

private const val VOICEMAIL_FALLBACK = "123"
private const val INTERNATIONAL_FALLBACK = "+33612000123"  // SFR-like

// ── Helpers ──
private fun isInFrance(context: android.content.Context): Boolean {
    return try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        tm?.networkCountryIso?.lowercase() == "fr"
    } catch (e: Exception) {
        false
    }
}

fun detectVoicemailNumber(
    context: android.content.Context,
    forceInternational: Boolean = false
): String {
    val inFrance = isInFrance(context) && !forceInternational

    try {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return if (inFrance) VOICEMAIL_FALLBACK else INTERNATIONAL_FALLBACK
        var operatorName = tm.networkOperatorName?.lowercase()?.trim()?.replace(Regex("\\s+"), " ") ?: ""

        // Recherche par nom (priorité shortcode si en France)
        val key = VOICEMAIL_NUMBERS.keys.firstOrNull { operatorName.contains(it) }
            ?: VOICEMAIL_SHORTCODES.keys.firstOrNull { operatorName.contains(it) }

        if (key != null) {
            return if (inFrance) VOICEMAIL_SHORTCODES[key] ?: VOICEMAIL_FALLBACK
            else VOICEMAIL_NUMBERS[key] ?: INTERNATIONAL_FALLBACK
        }

        // Fallback MCC/MNC
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
    } catch (e: Exception) {
        // Log.e("PhoneZen", "Erreur détection voicemail", e)
        return if (inFrance) VOICEMAIL_FALLBACK else INTERNATIONAL_FALLBACK
    }
}

fun detectOperatorName(context: android.content.Context): String {
    return try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        tm?.networkOperatorName?.ifBlank { "Opérateur inconnu" } ?: "Opérateur inconnu"
    } catch (e: Exception) {
        "Opérateur inconnu"
    }
}

private fun formatPhoneNumber(number: String): String {
    if (number.length <= 3) return number  // Code court
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

// ── UI ──
@Composable
fun DialKey(
    key: Key,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .size(72.dp)
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

@Composable
fun KeypadScreen(
    onCall: (String) -> Unit,
    onVoicemail: () -> Unit  // Peut être utilisé si besoin ailleurs
) {
    val c = LocalColors.current
    val context = LocalContext.current

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

    val modeText = when {
        forceInternational -> "International forcé (+33...)"
        isFranceDetected -> "France (code court)"
        else -> "International (détection)"
    }

    fun handleCall() {
        if (number.isEmpty()) return
        if (isUssd || isValidNumber(number) || number.length >= 3) {
            onCall(number)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isUssd) "CODE MMI / USSD" else "CLAVIER",
            style = MaterialTheme.typography.titleLarge,
            color = if (isUssd) c.neonCyan else c.neonOrange,
            modifier = Modifier.padding(vertical = 16.dp),
            fontWeight = FontWeight.ExtraBold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUssd) c.neonCyan.copy(alpha = 0.08f) else c.surface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.ifEmpty { "Composez un numéro" },
                    fontSize = if (number.isEmpty()) 16.sp else 28.sp,
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

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            DIAL_KEYS.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        DialKey(
                            key = key,
                            onTap = { number += key.main },
                            onLongPress = {
                                when (key.main) {
                                    "0" -> number += "+"
                                    "1" -> onCall(voicemailNumber)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { if (number.isNotEmpty()) number = number.dropLast(1) },
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surfaceVar)
            ) {
                Icon(Icons.Default.Backspace, contentDescription = "Effacer", tint = c.neonRed)
            }

            FloatingActionButton(
                onClick = { handleCall() },
                modifier = Modifier.size(80.dp),
                containerColor = if (isUssd) c.neonCyan else c.neonGreen,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.Default.Call, contentDescription = "Appeler", tint = Color.White, modifier = Modifier.size(38.dp))
            }

            FilledIconButton(
                onClick = { number = "" },
                modifier = Modifier.size(60.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.surfaceVar)
            ) {
                Text("C", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.neonOrange)
            }
        }

        // Toggle Mode France / International
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messagerie : $modeText",
                fontSize = 12.sp,
                color = c.textSecond
            )
            Switch(
                checked = forceInternational,
                onCheckedChange = { forceInternational = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.neonCyan,
                    checkedTrackColor = c.neonCyan.copy(alpha = 0.5f)
                )
            )
        }

        // Bouton Messagerie Vocale
        OutlinedButton(
            onClick = { onCall(voicemailNumber) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, c.glassStroke),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = c.surface,
                contentColor = c.textPrimary
            )
        ) {
            Icon(Icons.Default.Voicemail, contentDescription = null, tint = c.neonCyan)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "MESSAGERIE VOCALE",
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    "$operatorName · $formattedVoicemail",
                    fontSize = 10.sp,
                    color = c.textSecond
                )
            }
        }
    }
}
