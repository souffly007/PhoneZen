// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoicemailScreen {
package fr.bonobo.phonezen.voicemail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.bonobo.phonezen.ui.theme.AppColors
import fr.bonobo.phonezen.ui.theme.LocalColors
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreen(
    onSettingsClick: () -> Unit,
    onBack         : () -> Unit,
    viewModel      : VoicemailViewModel = viewModel()
) {
    val c         = LocalColors.current
    val messages  by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedMessage by remember { mutableStateOf<VoicemailMessage?>(null) }
    var messageToDelete by remember { mutableStateOf<VoicemailMessage?>(null) }

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Messagerie vocale",
                        color      = c.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Retour", tint = c.neonOrange)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
                actions = {
                    IconButton(onClick = { viewModel.loadMessages() }) {
                        Icon(Icons.Default.Refresh, "Actualiser", tint = c.neonOrange)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, "Réglages IMAP", tint = c.neonOrange)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(c.background)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = c.neonOrange
                    )
                }
                messages.isEmpty() -> {
                    VoicemailEmptyState(
                        modifier = Modifier.align(Alignment.Center),
                        c        = c
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            AnimatedVisibility(
                                visible = true,
                                enter   = fadeIn() + slideInVertically()
                            ) {
                                VoicemailCard(
                                    message    = msg,
                                    c          = c,
                                    isSelected = selectedMessage?.id == msg.id,
                                    onClick    = {
                                        selectedMessage =
                                            if (selectedMessage?.id == msg.id) null else msg
                                        if (!msg.isRead) viewModel.markAsRead(msg)
                                    },
                                    onDelete = { messageToDelete = msg }
                                )
                            }
                        }
                    }
                }
            }

            // Lecteur audio flottant en bas
            AnimatedVisibility(
                visible  = selectedMessage != null && selectedMessage!!.hasContent,
                enter    = slideInVertically { it },
                exit     = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedMessage?.let { msg ->
                    VoicemailAudioPlayer(
                        message = msg,
                        onClose = { selectedMessage = null }
                    )
                }
            }
        }
    }

    // Dialog suppression
    messageToDelete?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            containerColor   = c.surface,
            title  = { Text("Supprimer ?", color = c.textPrimary) },
            text   = { Text("Ce message vocal sera supprimé définitivement.", color = c.textSecond) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msg)
                    if (selectedMessage?.id == msg.id) selectedMessage = null
                    messageToDelete = null
                }) { Text("Supprimer", color = c.neonRed) }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Annuler", color = c.neonOrange)
                }
            }
        )
    }
}

@Composable
private fun VoicemailCard(
    message   : VoicemailMessage,
    c         : AppColors,
    isSelected: Boolean,
    onClick   : () -> Unit,
    onDelete  : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = c.surface),
        elevation = CardDefaults.cardElevation(if (isSelected) 8.dp else 2.dp),
        border    = BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = if (isSelected) c.neonOrange else c.surface
        )
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (!message.isRead) c.neonOrange else c.surfaceVar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Voicemail,
                    contentDescription = null,
                    tint               = if (!message.isRead) c.background else c.textSecond,
                    modifier           = Modifier.size(24.dp)
                )
            }

            // Infos
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = message.number,
                        color      = c.textPrimary,
                        fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.Normal,
                        fontSize   = 16.sp,
                        modifier   = Modifier.weight(1f)
                    )
                    Text(
                        text     = formatDate(message.date),
                        color    = c.textSecond,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Timer, null,
                        tint     = c.textSecond,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(formatDuration(message.duration), color = c.textSecond, fontSize = 13.sp)
                    if (!message.hasContent) {
                        Text("•",                   color = c.textSecond,  fontSize = 13.sp)
                        Text("Téléchargement...",   color = c.neonOrange,  fontSize = 12.sp)
                    }
                }
                if (message.transcription.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text     = message.transcription,
                        color    = c.textSecond,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "Supprimer", tint = c.textSecond)
            }
        }
    }
}

@Composable
private fun VoicemailEmptyState(modifier: Modifier, c: AppColors) {
    Column(
        modifier              = modifier,
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Voicemail, null,
            tint     = c.textSecond,
            modifier = Modifier.size(64.dp)
        )
        Text("Aucun message vocal",               color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Les nouveaux messages apparaîtront ici", color = c.textSecond,  fontSize = 14.sp)
    }
}

// ─── Utilitaires ──────────────────────────────────────────────────────────────

internal fun formatDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> "À l'instant"
        diff < 3_600_000L   -> "${diff / 60_000}min"
        diff < 86_400_000L  -> "${diff / 3_600_000}h"
        diff < 604_800_000L -> SimpleDateFormat("EEE", Locale.FRENCH).format(Date(timestamp))
        else                -> SimpleDateFormat("dd/MM/yy", Locale.FRENCH).format(Date(timestamp))
    }
}

internal fun formatDuration(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return if (m > 0) "${m}min ${s}s" else "${s}s"
}