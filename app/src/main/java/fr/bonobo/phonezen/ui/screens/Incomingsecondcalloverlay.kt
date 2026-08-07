// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class Incomingsecondcalloverlay {
package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.CallInfo
import fr.bonobo.phonezen.data.model.CallState

/**
 * Overlay affiché en haut de l'écran d'appel quand un second appel arrive.
 *
 * Propose trois actions :
 * - Répondre  → [onAnswer]   : accepte le 2e appel, met le 1er en HOLD
 * - Refuser   → [onDecline]  : rejette le 2e appel, reste sur le 1er
 * - Rester    → [onStayOnFirstCall] : ferme l'overlay sans rejeter (sonnerie continue côté réseau)
 *
 * L'overlay glisse depuis le haut avec une animation ressort.
 */
@Composable
fun IncomingSecondCallOverlay(
    visible: Boolean,
    secondCall: CallInfo?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onStayOnFirstCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && secondCall != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(stiffness = Spring.StiffnessHigh)
        ),
        modifier = modifier
    ) {
        secondCall?.let { call ->
            SecondCallCard(
                call = call,
                onAnswer = onAnswer,
                onDecline = onDecline,
                onStayOnFirstCall = onStayOnFirstCall
            )
        }
    }
}

@Composable
private fun SecondCallCard(
    call: CallInfo,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onStayOnFirstCall: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicateur visuel
            Text(
                text = "Appel entrant",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nom / numéro
            Text(
                text = call.displayName.ifBlank { call.phoneNumber },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (call.displayName.isNotBlank() && call.phoneNumber.isNotBlank()) {
                Text(
                    text = call.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Boutons d'action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refuser
                CallActionButton(
                    icon = { Icon(Icons.Rounded.CallEnd, contentDescription = "Refuser") },
                    label = "Refuser",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onDecline
                )

                // Rester sur l'appel 1
                CallActionButton(
                    icon = { Icon(Icons.Rounded.PhonePaused, contentDescription = "Rester") },
                    label = "Rester",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onStayOnFirstCall
                )

                // Répondre
                CallActionButton(
                    icon = { Icon(Icons.Rounded.Call, contentDescription = "Répondre") },
                    label = "Répondre",
                    containerColor = Color(0xFF2E7D32), // vert accessible
                    contentColor = Color.White,
                    onClick = onAnswer
                )
            }
        }
    }
}

@Composable
private fun CallActionButton(
    icon: @Composable () -> Unit,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
        ) {
            // Forcer la couleur du contenu
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides contentColor
            ) {
                icon()
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

// ─── Prévisualisation ─────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF1A1A2E)
@Composable
private fun IncomingSecondCallOverlayPreview() {
    MaterialTheme {
        IncomingSecondCallOverlay(
            visible = true,
            secondCall = CallInfo(
                id = "preview",
                displayName = "Marie Dupont",
                phoneNumber = "+33 6 12 34 56 78",
                state = CallState.INCOMING_SECOND
            ),
            onAnswer = {},
            onDecline = {},
            onStayOnFirstCall = {}
        )
    }
}