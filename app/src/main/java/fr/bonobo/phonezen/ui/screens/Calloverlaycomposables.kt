// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.service.CallOverlayService

// ─────────────────────────────────────────────────────────────────────────────
// Mode COMPACT — carte en bas de l'écran (~120dp)
//
//  ┌─────────────────────────────────────────────┐
//  │ 👤  Jean Dupont          [✕]  [📞]  [⛶]   │
//  │     06 12 34 56 78 · Appel entrant...        │
//  └─────────────────────────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CompactCallOverlay(
    service  : CallOverlayService,
    onExpand : () -> Unit
) {
    val neonGreen = Color(0xFF22C55E)
    val neonRed   = Color(0xFFEF4444)
    val neonCyan  = Color(0xFF38BDF8)
    val bgColor   = Color(0xFF0F172A)

    val infiniteTransition = rememberInfiniteTransition(label = "compactPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label         = "pulse"
    )

    val statusColor = when (service.callStatus) {
        CallStatus.RINGING -> neonGreen
        CallStatus.ACTIVE  -> neonCyan
        else               -> Color.LightGray
    }
    val statusText = when (service.callStatus) {
        CallStatus.RINGING -> "Appel entrant..."
        CallStatus.DIALING -> "Appel en cours..."
        CallStatus.ACTIVE  -> "En communication"
        else               -> ""
    }

    Surface(
        modifier        = Modifier.fillMaxWidth(),
        color           = bgColor,
        shadowElevation = 12.dp,
        shape           = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Avatar pulsant ────────────────────────────────────────
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
                if (service.callStatus == CallStatus.RINGING) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .scale(pulse)
                            .background(neonGreen.copy(alpha = 0.2f), CircleShape)
                    )
                }
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape    = CircleShape,
                    color    = Color(0xFF1E293B)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = service.callerName.firstOrNull()?.uppercase()
                        if (!initial.isNullOrEmpty() && initial != "I") {
                            Text(initial, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // ── Nom + statut ──────────────────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = service.callerName.ifBlank { service.callerNumber },
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                if (service.callerName.isNotBlank() && service.callerNumber.isNotBlank()) {
                    Text(
                        text     = service.callerNumber,
                        fontSize = 12.sp,
                        color    = Color.LightGray.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text     = statusText,
                    fontSize = 12.sp,
                    color    = statusColor
                )
            }

            Spacer(Modifier.width(8.dp))

            // ── Boutons action ────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Refuser / Raccrocher
                IconButton(
                    onClick  = { service.reject() },
                    modifier = Modifier.size(44.dp).background(neonRed, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, "Refuser", tint = Color.White, modifier = Modifier.size(22.dp))
                }

                // Répondre (seulement si RINGING)
                if (service.callStatus == CallStatus.RINGING) {
                    IconButton(
                        onClick  = { service.answer() },
                        modifier = Modifier.size(44.dp).background(neonGreen, CircleShape)
                    ) {
                        Icon(Icons.Default.Call, "Répondre", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }

                // Agrandir → plein écran
                IconButton(
                    onClick  = onExpand,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.OpenInFull, "Agrandir", tint = neonCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode MINI — barre fine en haut (~56dp)
//
//  ┌─────────────────────────────────────────────┐
//  │ 📞 Jean Dupont · Appel entrant  [✕]  [📞]  │
//  └─────────────────────────────────────────────┘
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MiniCallOverlay(
    service  : CallOverlayService,
    onExpand : () -> Unit
) {
    val neonGreen = Color(0xFF22C55E)
    val neonRed   = Color(0xFFEF4444)
    val neonCyan  = Color(0xFF38BDF8)
    val bgColor   = Color(0xFF0F172A)

    val statusColor = when (service.callStatus) {
        CallStatus.RINGING -> neonGreen
        CallStatus.ACTIVE  -> neonCyan
        else               -> Color.LightGray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable { onExpand() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icône pulsante
            val infiniteTransition = rememberInfiniteTransition(label = "miniPulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue  = 1f,
                targetValue   = 0.3f,
                animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
                label         = "miniAlpha"
            )
            Icon(
                imageVector        = Icons.Default.Call,
                contentDescription = null,
                tint               = statusColor.copy(alpha = if (service.callStatus == CallStatus.RINGING) alpha else 1f),
                modifier           = Modifier.size(18.dp)
            )

            Spacer(Modifier.width(8.dp))

            // Nom · statut
            Text(
                text     = buildString {
                    append(service.callerName.ifBlank { service.callerNumber })
                    append(" · ")
                    append(when (service.callStatus) {
                        CallStatus.RINGING -> "Appel entrant"
                        CallStatus.ACTIVE  -> "En communication"
                        else               -> "Appel"
                    })
                },
                fontSize  = 13.sp,
                color     = Color.White,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.weight(1f)
            )

            Spacer(Modifier.width(8.dp))

            // Refuser
            Box(
                modifier         = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(neonRed)
                    .clickable { service.reject() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CallEnd, "Refuser", tint = Color.White, modifier = Modifier.size(16.dp))
            }

            // Répondre (seulement si RINGING)
            if (service.callStatus == CallStatus.RINGING) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier         = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(neonGreen)
                        .clickable { service.answer() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Call, "Répondre", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}