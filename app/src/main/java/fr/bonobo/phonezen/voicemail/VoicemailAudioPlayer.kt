// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoicemailAudioPlayer {
// SPDX-License-Identifier: GPL-3.0-or-later
package fr.bonobo.phonezen.voicemail

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.LocalColors
import kotlinx.coroutines.delay

@Composable
fun VoicemailAudioPlayer(
    message: VoicemailMessage,
    onClose: () -> Unit
) {
    val c       = LocalColors.current
    val context = LocalContext.current

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying   by remember { mutableStateOf(false) }
    var position    by remember { mutableStateOf(0f) }
    var elapsed     by remember { mutableStateOf(0L) }
    var isPrepared  by remember { mutableStateOf(false) }

    LaunchedEffect(message.uri) {
        try {
            val mp = MediaPlayer().apply {
                setDataSource(context, message.uri)
                setOnPreparedListener  { isPrepared = true }
                setOnCompletionListener {
                    isPlaying = false; position = 0f; elapsed = 0L; seekTo(0)
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            android.util.Log.e("VoicemailPlayer", "Erreur MediaPlayer", e)
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { mp ->
                if (mp.duration > 0) {
                    position = mp.currentPosition.toFloat() / mp.duration
                    elapsed  = (mp.currentPosition / 1000).toLong()
                }
            }
            delay(200)
        }
    }

    DisposableEffect(message.uri) {
        onDispose { mediaPlayer?.release(); mediaPlayer = null }
    }

    // ─── UI ──────────────────────────────────────────────────────────────────

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(c.surface)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // En-tête
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(message.number,               color = c.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(formatDuration(message.duration), color = c.textSecond,  fontSize = 13.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.KeyboardArrowDown, "Fermer", tint = c.textSecond)
                }
            }

            // Slider
            Column {
                Slider(
                    value         = position,
                    onValueChange = { newPos ->
                        position = newPos
                        mediaPlayer?.takeIf { isPrepared }?.let { mp ->
                            mp.seekTo((newPos * mp.duration).toInt())
                            elapsed = (mp.currentPosition / 1000).toLong()
                        }
                    },
                    colors   = SliderDefaults.colors(
                        thumbColor         = c.neonOrange,
                        activeTrackColor   = c.neonOrange,
                        inactiveTrackColor = c.surfaceVar
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(elapsed),           color = c.textSecond, fontSize = 11.sp)
                    Text(formatDuration(message.duration),  color = c.textSecond, fontSize = 11.sp)
                }
            }

            // Contrôles
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    mediaPlayer?.takeIf { isPrepared }?.let { mp ->
                        val p = maxOf(0, mp.currentPosition - 10_000)
                        mp.seekTo(p); elapsed = (p / 1000).toLong(); position = p.toFloat() / mp.duration
                    }
                }) {
                    Icon(Icons.Default.Replay10, "−10s", tint = c.textSecond, modifier = Modifier.size(28.dp))
                }

                // Bouton Play/Pause
                Box(
                    modifier         = Modifier.size(56.dp).clip(CircleShape).background(c.neonOrange),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = {
                        val mp = mediaPlayer ?: return@IconButton
                        if (!isPrepared) return@IconButton
                        if (isPlaying) { mp.pause(); isPlaying = false }
                        else           { mp.start(); isPlaying = true  }
                    }) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Lecture",
                            tint               = c.background,
                            modifier           = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(onClick = {
                    mediaPlayer?.takeIf { isPrepared }?.let { mp ->
                        val p = minOf(mp.duration, mp.currentPosition + 10_000)
                        mp.seekTo(p); elapsed = (p / 1000).toLong(); position = p.toFloat() / mp.duration
                    }
                }) {
                    Icon(Icons.Default.Forward10, "+10s", tint = c.textSecond, modifier = Modifier.size(28.dp))
                }
            }

            // Transcription
            if (message.transcription.isNotBlank()) {
                HorizontalDivider(color = c.surfaceVar)
                Text(
                    "\"${message.transcription}\"",
                    color      = c.textSecond,
                    fontSize   = 13.sp,
                    modifier   = Modifier.fillMaxWidth()
                )
            }
        }
    }
}