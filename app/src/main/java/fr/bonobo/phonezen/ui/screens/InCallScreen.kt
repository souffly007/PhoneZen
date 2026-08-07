// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.PhonePaused
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.bonobo.phonezen.data.model.ArcepInfo
import fr.bonobo.phonezen.data.model.AudioRoute
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.data.model.CallTrustLevel
import fr.bonobo.phonezen.data.model.RiskLevel
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.InCallViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// InCallScreen principal
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InCallScreen(
    vm                   : InCallViewModel,
    onFinish             : () -> Unit,
    secondCallName       : String?  = null,
    secondCallNumber     : String?  = null,
    hasSecondCall        : Boolean  = false,
    onAnswerSecondCall   : () -> Unit = {},
    onRejectSecondCall   : () -> Unit = {},
    onStayOnFirstCall    : () -> Unit = {},
    onSwapCalls          : () -> Unit = {}
) {
    val state   by vm.state.collectAsState()
    val context = LocalContext.current
    var showDialpad by remember { mutableStateOf(false) }

    val bgColor   = Color(0xFF0F172A)
    val neonCyan  = Color(0xFF38BDF8)
    val neonGreen = Color(0xFF22C55E)
    val neonRed   = Color(0xFFEF4444)

    var resolvedName  by remember { mutableStateOf<String?>(null) }
    var resolvedPhoto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.number) {
        if (state.number.isNotBlank()) {
            val (name, photo) = withContext(Dispatchers.IO) {
                PhoneUtils.lookupContact(context, state.number)
            }
            resolvedName  = name
            resolvedPhoto = photo
        }
    }

    val displayName = state.contactName?.takeIf { it.isNotBlank() }
        ?: resolvedName
        ?: state.number.ifEmpty { "Inconnu" }

    val waitingDisplayName = secondCallName ?: secondCallNumber ?: "Numéro inconnu"

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 1.12f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    LaunchedEffect(state.status) {
        if (state.status == CallStatus.DISCONNECTED) {
            delay(1200)
            onFinish()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(bgColor, Color.Black)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val topSpacing = if (hasSecondCall) 140.dp else 80.dp
            Spacer(Modifier.height(topSpacing))

            // ── Avatar ────────────────────────────────────────────────────
            Box(contentAlignment = Alignment.Center) {
                if (state.status == CallStatus.RINGING || state.status == CallStatus.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .background(
                                (if (state.status == CallStatus.RINGING) neonGreen else neonCyan)
                                    .copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                }
                if (!resolvedPhoto.isNullOrBlank()) {
                    AsyncImage(
                        model              = ImageRequest.Builder(context)
                            .data(resolvedPhoto).crossfade(true).build(),
                        contentDescription = displayName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.size(100.dp).clip(CircleShape)
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape    = CircleShape,
                        color    = Color(0xFF1E293B),
                        border   = BorderStroke(
                            2.dp,
                            when (state.trustLevel) {
                                CallTrustLevel.Trusted    -> neonGreen
                                CallTrustLevel.Suspicious -> Color(0xFFF97316)
                                CallTrustLevel.Spam       -> neonRed
                                CallTrustLevel.Unknown    ->
                                    if (state.status == CallStatus.ACTIVE) neonCyan
                                    else Color.Transparent
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val initial = displayName.firstOrNull()?.uppercase()
                            if (initial != null && initial != "I") {
                                Text(
                                    initial,
                                    fontSize   = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White
                                )
                            } else {
                                Icon(
                                    Icons.Default.Person, null,
                                    modifier = Modifier.padding(20.dp).size(60.dp),
                                    tint     = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Nom ───────────────────────────────────────────────────────
            Text(
                displayName,
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )

            // ── Numéro (si contact résolu) ────────────────────────────────
            if (resolvedName != null && state.number.isNotBlank()) {
                Text(
                    state.number,
                    fontSize = 15.sp,
                    color    = Color.LightGray.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ── Badge ARCEP ───────────────────────────────────────────────
            AnimatedVisibility(
                visible  = state.arcepInfo != null && resolvedName == null,
                enter    = fadeIn() + expandVertically(),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                state.arcepInfo?.let { ArcepBadge(it) }
            }

            // ── Statut / chrono ───────────────────────────────────────────
            Text(
                text = when (state.status) {
                    CallStatus.RINGING      -> "Appel entrant..."
                    CallStatus.DIALING      -> "Appel en cours..."
                    CallStatus.ACTIVE       -> if (state.isOnHold) "En attente" else formatDuration(state.durationSec)
                    CallStatus.ON_HOLD      -> "En attente"
                    CallStatus.DISCONNECTED -> "Appel terminé"
                    else                    -> "Connexion..."
                },
                fontSize = 18.sp,
                color    = if (state.status == CallStatus.ACTIVE && !state.isOnHold) neonCyan else Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )

            // ── Badge de confiance ────────────────────────────────────────
            AnimatedVisibility(
                visible  = state.trustLevel != CallTrustLevel.Unknown,
                enter    = fadeIn() + expandVertically(),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                TrustBadge(trustLevel = state.trustLevel)
            }

            // ── Bandeau Double Appel ──────────────────────────────────────
            AnimatedVisibility(
                visible  = hasSecondCall && state.isOnHold,
                enter    = fadeIn() + expandVertically(),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                SecondCallBanner(
                    displayName     = waitingDisplayName,
                    number          = secondCallNumber,
                    color           = neonCyan,
                    neonGreen       = neonGreen,
                    neonRed         = neonRed,
                    isPrimaryOnHold = state.isOnHold,
                    onAnswer        = onAnswerSecondCall,
                    onReject        = onRejectSecondCall,
                    onSwap          = onSwapCalls
                )
            }

            // ── Bandeau spam ──────────────────────────────────────────────
            AnimatedVisibility(
                visible  = state.isSpam &&
                        (state.status == CallStatus.RINGING || state.status == CallStatus.ACTIVE),
                enter    = fadeIn() + expandVertically(),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                RiskBanner(
                    riskLevel = if (state.isSpam) RiskLevel.HIGH else RiskLevel.NONE,
                    score     = if (state.isSpam) 100 else 0,
                    reason    = state.spamReason
                )
            }

            Spacer(Modifier.weight(1f))

            // ── Contrôles (appel actif / en attente / numérotation) ───────
            if (state.status == CallStatus.ACTIVE || state.status == CallStatus.ON_HOLD || state.status == CallStatus.DIALING) {
                if (!showDialpad) {
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(3),
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement   = Arrangement.spacedBy(24.dp),
                        userScrollEnabled     = false
                    ) {
                        item {
                            ControlCircleBtn(
                                icon        = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label       = if (state.isMuted) "Micro off" else "Muet",
                                isToggled   = state.isMuted,
                                activeColor = neonCyan
                            ) { vm.toggleMute() }
                        }
                        item {
                            ControlCircleBtn(Icons.Default.Dialpad, "Clavier") {
                                showDialpad = true
                            }
                        }
                        item {
                            ControlCircleBtn(
                                icon        = if (state.isOnHold) Icons.Default.CheckCircle else Icons.Default.Pause,
                                label       = if (state.isOnHold) "Reprendre" else "Attente",
                                isToggled   = state.isOnHold,
                                activeColor = if (state.isOnHold) neonGreen else neonCyan
                            ) { vm.toggleHold() }
                        }
                        item {
                            ControlCircleBtn(Icons.Default.Add, "Ajouter") {}
                        }
                        item {
                            ControlCircleBtn(Icons.Default.Videocam, "Vidéo") {}
                        }
                        item {
                            AudioRouteButton(
                                currentRoute    = state.audioRoute,
                                isBtAvailable   = state.isBtAvailable,
                                neonCyan        = neonCyan,
                                onRouteSelected = { vm.setAudioRoute(it) }
                            )
                        }
                    }
                } else {
                    DtmfKeypad(
                        onClose    = { showDialpad = false },
                        onKeyClick = { vm.playDtmf(it) }
                    )
                }

                HangupButton(
                    hasSecondCall = hasSecondCall,
                    neonRed       = neonRed,
                    neonCyan      = neonCyan,
                    onHangup      = { vm.hangUp() },
                    onSwap        = onSwapCalls
                )

                // ── Appel entrant : swipe pour décrocher / raccrocher ─────────
            } else if (state.status == CallStatus.RINGING) {
                SwipeToAnswerReject(
                    neonGreen = neonGreen,
                    neonRed   = neonRed,
                    onAnswer  = { vm.answer() },
                    onReject  = { vm.hangUp() },
                    modifier  = Modifier.padding(bottom = 80.dp)
                )
            }
        }

        // ── Overlay 2e appel ENTRANT ──────────────────────────────────────
        SecondCallIncomingOverlay(
            visible           = hasSecondCall && !state.isOnHold,
            callerName        = waitingDisplayName,
            callerNumber      = secondCallNumber,
            neonCyan          = neonCyan,
            neonGreen         = neonGreen,
            neonRed           = neonRed,
            onAnswer          = onAnswerSecondCall,
            onDecline         = onRejectSecondCall,
            onStayOnFirstCall = onStayOnFirstCall,
            modifier          = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .align(Alignment.TopCenter)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SwipeToAnswerReject
// ─────────────────────────────────────────────────────────────────────────────
//
//   ◀ ───────── [ 📞 ] ───────── ▶
//  Refuser                   Répondre
//
// Swipe droite (+) → Répondre  — fond vert qui s'étale
// Swipe gauche (-) → Refuser   — fond rouge qui s'étale
// Seuil : 38 % de la largeur du rail
// Haptic au déclenchement
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SwipeToAnswerReject(
    neonGreen : Color,
    neonRed   : Color,
    onAnswer  : () -> Unit,
    onReject  : () -> Unit,
    modifier  : Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    var dragOffset        by remember { mutableFloatStateOf(0f) }
    var triggered         by remember { mutableStateOf(false) }
    var containerWidthPx  by remember { mutableFloatStateOf(1f) }

    // ratio [-1 ; +1]
    val ratio = (dragOffset / (containerWidthPx / 2f)).coerceIn(-1f, 1f)
    val threshold = 0.38f

    // fond coloré dynamique
    val bgColor = when {
        ratio >  0.05f -> neonGreen.copy(alpha = ratio.coerceIn(0f, 1f) * 0.4f)
        ratio < -0.05f -> neonRed.copy(alpha   = (-ratio).coerceIn(0f, 1f) * 0.4f)
        else           -> Color.Transparent
    }

    // déplacement plafonné de la poignée
    val handleOffsetPx = (dragOffset * 0.65f).coerceIn(
        -containerWidthPx * 0.33f,
        containerWidthPx * 0.33f
    )

    // opacité animée des labels
    val answerAlpha by animateFloatAsState(
        targetValue   = if (ratio > 0.05f) ratio.coerceIn(0.3f, 1f) else 0.3f,
        animationSpec = tween(80), label = "answerAlpha"
    )
    val rejectAlpha by animateFloatAsState(
        targetValue   = if (ratio < -0.05f) (-ratio).coerceIn(0.3f, 1f) else 0.3f,
        animationSpec = tween(80), label = "rejectAlpha"
    )

    Column(
        modifier            = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Hint ──────────────────────────────────────────────────────────
        Text(
            text     = "Glisser pour répondre ou refuser",
            color    = Color.White.copy(alpha = 0.4f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        // ── Rail de swipe ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(Color.White.copy(alpha = 0.07f))
                .background(bgColor)          // fond coloré par-dessus
                .pointerInput(Unit) {
                    containerWidthPx = size.width.toFloat()
                    detectHorizontalDragGestures(
                        onDragEnd    = { dragOffset = 0f; triggered = false },
                        onDragCancel = { dragOffset = 0f; triggered = false },
                        onHorizontalDrag = { _, delta ->
                            if (triggered) return@detectHorizontalDragGestures
                            dragOffset += delta
                            val r = (dragOffset / (containerWidthPx / 2f)).coerceIn(-1f, 1f)
                            when {
                                r >= threshold -> {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAnswer()
                                }
                                r <= -threshold -> {
                                    triggered = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onReject()
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Label gauche — Refuser
            Text(
                text       = "✕  Refuser",
                color      = neonRed.copy(alpha = rejectAlpha),
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.align(Alignment.CenterStart).padding(start = 20.dp)
            )

            // Label droite — Répondre
            Text(
                text       = "Répondre  ✓",
                color      = neonGreen.copy(alpha = answerAlpha),
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp)
            )

            // Poignée centrale qui suit le doigt
            Box(
                modifier = Modifier
                    .offset { IntOffset(handleOffsetPx.roundToInt(), 0) }
                    .size(68.dp)
                    .background(
                        color = when {
                            ratio >  0.08f -> neonGreen
                            ratio < -0.08f -> neonRed
                            else           -> Color(0xFF1E3A5F)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = when {
                        ratio < -0.08f -> Icons.Default.CallEnd
                        else           -> Icons.Default.Call
                    },
                    contentDescription = "Swipe",
                    tint               = Color.White,
                    modifier           = Modifier.size(30.dp)
                )
            }
        }

        // ── Légendes sous le rail ─────────────────────────────────────────
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("◀  Refuser",   color = neonRed.copy(alpha   = 0.5f), fontSize = 11.sp)
            Text("Répondre  ▶", color = neonGreen.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ArcepBadge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ArcepBadge(info: ArcepInfo) {
    val accentColor = when {
        info.isSuspiciousType                              -> Color(0xFFF97316)
        info.categorie.contains("vert",   ignoreCase=true) -> Color(0xFF22C55E)
        info.categorie.contains("Mobile", ignoreCase=true) -> Color(0xFF38BDF8)
        else                                               -> Color(0xFF94A3B8)
    }
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier              = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Icon(Icons.Default.SimCard, null, tint = accentColor, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(5.dp))
        Text(info.displayLine, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        info.displayTerritory?.let { terr ->
            Text(" · $terr", color = accentColor.copy(alpha = 0.75f), fontSize = 11.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TrustBadge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TrustBadge(trustLevel: CallTrustLevel) {
    val style = when (trustLevel) {
        CallTrustLevel.Trusted    -> TrustStyle(Color(0xFF0D2B1A), Color(0xFF22C55E), Icons.Default.VerifiedUser, "Appelant de confiance", null)
        CallTrustLevel.Suspicious -> TrustStyle(Color(0xFF2D1F00), Color(0xFFF97316), Icons.Default.Warning,      "Numéro suspect",        "Ne dites pas « Oui » — risque d'enregistrement vocal")
        CallTrustLevel.Spam       -> TrustStyle(Color(0xFF3B0000), Color(0xFFEF4444), Icons.Default.GppBad,       "Spam / Fraude",         null)
        CallTrustLevel.Unknown    -> return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "trustPulse")
    val iconAlpha by if (trustLevel == CallTrustLevel.Suspicious || trustLevel == CallTrustLevel.Spam) {
        infiniteTransition.animateFloat(1f, 0.4f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), "trustIconAlpha")
    } else remember { mutableStateOf(1f) }

    Row(
        modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(style.bg).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(style.icon, style.label, tint = style.border.copy(alpha = iconAlpha), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(style.label, color = style.border, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (style.sublabel != null)
                Text(style.sublabel, color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private data class TrustStyle(val bg: Color, val border: Color, val icon: ImageVector, val label: String, val sublabel: String?)

// ─────────────────────────────────────────────────────────────────────────────
// SecondCallIncomingOverlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecondCallIncomingOverlay(
    visible           : Boolean,
    callerName        : String,
    callerNumber      : String?,
    neonCyan          : Color,
    neonGreen         : Color,
    neonRed           : Color,
    onAnswer          : () -> Unit,
    onDecline         : () -> Unit,
    onStayOnFirstCall : () -> Unit,
    modifier          : Modifier = Modifier
) {
    AnimatedVisibility(
        visible  = visible,
        enter    = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)),
        exit     = slideOutVertically(targetOffsetY  = { -it }, animationSpec = spring(stiffness = Spring.StiffnessHigh)),
        modifier = modifier
    ) {
        Surface(
            shape           = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            color           = Color(0xFF0F2538),
            shadowElevation = 16.dp,
            modifier        = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Appel entrant", style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = neonCyan, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(callerName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!callerNumber.isNullOrBlank() && callerNumber != callerName)
                    Text(callerNumber, fontSize = 13.sp, color = Color.LightGray.copy(alpha = 0.75f))
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    OverlayActionButton(Icons.Default.CallEnd,     "Refuser",  neonRed,   onDecline)
                    OverlayActionButton(Icons.Rounded.PhonePaused, "Rester",   neonCyan,  onStayOnFirstCall)
                    OverlayActionButton(Icons.Default.Call,        "Répondre", neonGreen, onAnswer)
                }
            }
        }
    }
}

@Composable
private fun OverlayActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = onClick, modifier = Modifier.size(52.dp).clip(CircleShape).background(color)) {
            Icon(icon, label, tint = Color.Black, modifier = Modifier.size(24.dp))
        }
        Text(label, fontSize = 11.sp, color = Color.LightGray)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HangupButton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HangupButton(
    hasSecondCall : Boolean,
    neonRed       : Color,
    neonCyan      : Color,
    onHangup      : () -> Unit,
    onSwap        : () -> Unit
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(bottom = 60.dp),
        horizontalArrangement = if (hasSecondCall) Arrangement.SpaceEvenly else Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        if (hasSecondCall) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onSwap, modifier = Modifier.size(64.dp).background(neonCyan, CircleShape)) {
                    Icon(Icons.Default.SwapCalls, "Permuter", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                Text("Permuter", color = neonCyan, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onHangup, modifier = Modifier.size(72.dp).background(neonRed, CircleShape)) {
                Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            if (hasSecondCall)
                Text("Raccrocher", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SecondCallBanner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SecondCallBanner(
    displayName     : String,
    number          : String?,
    color           : Color,
    neonGreen       : Color,
    neonRed         : Color,
    isPrimaryOnHold : Boolean,
    onAnswer        : () -> Unit,
    onReject        : () -> Unit,
    onSwap          : () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0F2538)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(42.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Call, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (isPrimaryOnHold) "Appel en attente" else "Double appel", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (!number.isNullOrBlank() && number != displayName)
                    Text(number, color = Color.LightGray.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (isPrimaryOnHold) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onSwap, modifier = Modifier.size(52.dp).background(color, CircleShape)) {
                        Icon(Icons.Default.SwapCalls, "Permuter", tint = Color.Black, modifier = Modifier.size(24.dp))
                    }
                    Text("Permuter", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onReject, modifier = Modifier.size(52.dp).background(neonRed, CircleShape)) {
                        Icon(Icons.Default.CallEnd, "Refuser second", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Text("Refuser", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onAnswer, modifier = Modifier.size(52.dp).background(neonGreen, CircleShape)) {
                        Icon(Icons.Default.Call, "Répondre second", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Text("Répondre", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AudioRouteButton
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AudioRouteButton(
    currentRoute    : AudioRoute,
    isBtAvailable   : Boolean,
    neonCyan        : Color,
    onRouteSelected : (AudioRoute) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val (activeIcon, activeLabel) = when (currentRoute) {
        AudioRoute.SPEAKER   -> Icons.Default.VolumeUp  to "HP"
        AudioRoute.BLUETOOTH -> Icons.Default.Bluetooth to "Bluetooth"
        AudioRoute.EARPIECE  -> Icons.Default.Headset   to "Écouteur"
    }
    val options = listOf(
        Triple(AudioRoute.EARPIECE,  Icons.Default.Headset,   "Écouteur"),
        Triple(AudioRoute.SPEAKER,   Icons.Default.VolumeUp,  "Haut-parleur"),
        Triple(AudioRoute.BLUETOOTH, Icons.Default.Bluetooth, "Bluetooth")
    )
    Box {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { expanded = true }) {
            Box(
                modifier         = Modifier.size(64.dp).background(if (currentRoute != AudioRoute.EARPIECE) neonCyan else Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(activeIcon, activeLabel, tint = if (currentRoute != AudioRoute.EARPIECE) Color.Black else Color.White, modifier = Modifier.size(26.dp))
            }
            Text(activeLabel, color = if (currentRoute != AudioRoute.EARPIECE) neonCyan else Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF1E293B))) {
            options.forEach { (route, icon, label) ->
                val isSelected  = route == currentRoute
                val isAvailable = route != AudioRoute.BLUETOOTH || isBtAvailable
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(icon, label, tint = when { isSelected -> neonCyan; !isAvailable -> Color.White.copy(alpha = 0.3f); else -> Color.White }, modifier = Modifier.size(20.dp))
                            Text(label, color = when { isSelected -> neonCyan; !isAvailable -> Color.White.copy(alpha = 0.3f); else -> Color.White }, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp)
                        }
                    },
                    onClick = { if (isAvailable) { onRouteSelected(route); expanded = false } },
                    enabled = isAvailable,
                    colors  = MenuDefaults.itemColors(textColor = Color.White, disabledTextColor = Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RiskBanner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RiskBanner(riskLevel: RiskLevel, score: Int, reason: String?) {
    val (bgColor, borderColor, icon, label) = when (riskLevel) {
        RiskLevel.CRITICAL -> RiskStyle(Color(0xFF3B0000), Color(0xFFEF4444), Icons.Default.GppBad,        "Danger critique")
        RiskLevel.HIGH     -> RiskStyle(Color(0xFF2D1000), Color(0xFFEF4444), Icons.Default.Warning,       "Risque élevé")
        RiskLevel.MEDIUM   -> RiskStyle(Color(0xFF2D1F00), Color(0xFFF97316), Icons.Default.ReportProblem, "Risque modéré")
        RiskLevel.LOW      -> RiskStyle(Color(0xFF1A2300), Color(0xFFEAB308), Icons.Default.Info,          "Risque faible")
        RiskLevel.NONE     -> RiskStyle(Color.Transparent, Color.Transparent, Icons.Default.CheckCircle,   "")
    }
    Row(
        modifier          = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bgColor).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "riskPulse")
        val iconAlpha by if (riskLevel == RiskLevel.CRITICAL || riskLevel == RiskLevel.HIGH) {
            infiniteTransition.animateFloat(1f, 0.4f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), "iconAlpha")
        } else remember { mutableStateOf(1f) }
        Icon(icon, label, tint = borderColor.copy(alpha = iconAlpha), modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = borderColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (!reason.isNullOrBlank())
                Text(reason, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (score > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(borderColor.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                Text("$score", color = borderColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private data class RiskStyle(val bg: Color, val border: Color, val icon: ImageVector, val label: String)

// ─────────────────────────────────────────────────────────────────────────────
// ControlCircleBtn
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ControlCircleBtn(
    icon        : ImageVector,
    label       : String,
    isToggled   : Boolean = false,
    activeColor : Color   = Color.White,
    onClick     : () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(
            modifier         = Modifier.size(64.dp).background(if (isToggled) activeColor else Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (isToggled) Color.Black else Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, color = if (isToggled) activeColor else Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DtmfKeypad
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DtmfKeypad(onClose: () -> Unit, onKeyClick: (Char) -> Unit) {
    val keys = listOf('1','2','3','4','5','6','7','8','9','*','0','#')
    Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.8f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(300.dp), contentPadding = PaddingValues(16.dp)) {
            items(keys) { key ->
                TextButton(onClick = { onKeyClick(key) }) {
                    Text(key.toString(), fontSize = 24.sp, color = Color.White)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Utilitaires
// ─────────────────────────────────────────────────────────────────────────────

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}