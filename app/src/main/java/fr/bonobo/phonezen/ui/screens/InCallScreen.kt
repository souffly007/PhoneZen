package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.InCallViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun InCallScreen(vm: InCallViewModel, onFinish: () -> Unit) {
    val state   by vm.state.collectAsState()
    val context  = LocalContext.current
    var showDialpad by remember { mutableStateOf(false) }

    val bgColor   = Color(0xFF0F172A)
    val neonCyan  = Color(0xFF38BDF8)
    val neonGreen = Color(0xFF22C55E)
    val neonRed   = Color(0xFFEF4444)

    // ── Lookup nom + photo en temps réel ──
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

    // Nom affiché : contactName du state en priorité, puis lookup, puis numéro
    val displayName = state.contactName?.takeIf { it.isNotBlank() }
        ?: resolvedName
        ?: state.number.ifEmpty { "Inconnu" }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.12f,
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
            modifier            = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))

            // ── Avatar : photo du contact ou icône générique avec pulse ──
            Box(contentAlignment = Alignment.Center) {
                if (state.status == CallStatus.RINGING || state.status == CallStatus.ACTIVE) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(pulseScale)
                            .background(
                                (if (state.status == CallStatus.RINGING) neonGreen else neonCyan).copy(alpha = 0.2f),
                                CircleShape
                            )
                    )
                }

                if (!resolvedPhoto.isNullOrBlank()) {
                    // Photo du contact
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedPhoto)
                            .crossfade(true)
                            .build(),
                        contentDescription = displayName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                    )
                } else {
                    // Fallback initiale
                    Surface(
                        modifier = Modifier.size(100.dp),
                        shape    = CircleShape,
                        color    = Color(0xFF1E293B),
                        border   = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (state.status == CallStatus.ACTIVE) neonCyan else Color.Transparent
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val initial = displayName.firstOrNull()?.uppercase()
                            if (initial != null && initial != "I") {
                                // "I" = "Inconnu", on garde l'icône dans ce cas
                                Text(initial, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            } else {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.padding(20.dp).size(60.dp),
                                    tint     = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Nom du contact (résolu) ──
            Text(
                text       = displayName,
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White
            )

            // ── Numéro sous le nom si on a un nom ──
            if (resolvedName != null && state.number.isNotBlank()) {
                Text(
                    text     = state.number,
                    fontSize = 15.sp,
                    color    = Color.LightGray.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = when (state.status) {
                    CallStatus.RINGING      -> "Appel entrant..."
                    CallStatus.DIALING      -> "Appel en cours..."
                    CallStatus.ACTIVE       -> formatDuration(state.durationSec)
                    CallStatus.DISCONNECTED -> "Appel terminé"
                    else                    -> "Connexion..."
                },
                fontSize = 18.sp,
                color    = if (state.status == CallStatus.ACTIVE) neonCyan else Color.LightGray,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.weight(1f))

            // ── Contrôles appel actif ──
            if (state.status == CallStatus.ACTIVE || state.status == CallStatus.DIALING) {
                if (!showDialpad) {
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(3),
                        modifier              = Modifier.fillMaxWidth().padding(bottom = 40.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement   = Arrangement.spacedBy(24.dp)
                    ) {
                        item { ControlCircleBtn(Icons.Default.MicOff,   "Muet",    state.isMuted)   { vm.toggleMute() } }
                        item { ControlCircleBtn(Icons.Default.Dialpad,  "Clavier", false)            { showDialpad = true } }
                        item { ControlCircleBtn(Icons.Default.VolumeUp, "HP",      state.isSpeaker) { vm.toggleSpeaker() } }
                        item { ControlCircleBtn(Icons.Default.Pause,    "Attente", state.isOnHold)  { vm.toggleHold() } }
                        item { ControlCircleBtn(Icons.Default.Add,      "Ajouter", false)            { } }
                        item { ControlCircleBtn(Icons.Default.Videocam, "Vidéo",   false)            { } }
                    }
                } else {
                    DtmfKeypad(
                        onClose    = { showDialpad = false },
                        onKeyClick = { vm.playDtmf(it) }
                    )
                }

                IconButton(
                    onClick  = { vm.hangUp() },
                    modifier = Modifier
                        .padding(bottom = 60.dp)
                        .size(72.dp)
                        .background(neonRed, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }

                // ── Appel entrant ──
            } else if (state.status == CallStatus.RINGING) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 80.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick  = { vm.hangUp() },
                            modifier = Modifier.size(72.dp).background(neonRed, CircleShape)
                        ) {
                            Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Text("Refuser", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick  = { vm.answer() },
                            modifier = Modifier.size(72.dp).background(neonGreen, CircleShape)
                        ) {
                            Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Text("Répondre", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ControlCircleBtn(
    icon: ImageVector,
    label: String,
    isToggled: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(
                    if (isToggled) Color.White else Color.White.copy(alpha = 0.1f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = if (isToggled) Color.Black else Color.White)
        }
        Text(text = label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun DtmfKeypad(onClose: () -> Unit, onKeyClick: (Char) -> Unit) {
    val keys = listOf('1','2','3','4','5','6','7','8','9','*','0','#')
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Default.Close, null, tint = Color.White)
        }
        LazyVerticalGrid(
            columns        = GridCells.Fixed(3),
            modifier       = Modifier.height(300.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(keys) { key ->
                TextButton(onClick = { onKeyClick(key) }) {
                    Text(key.toString(), fontSize = 24.sp, color = Color.White)
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}