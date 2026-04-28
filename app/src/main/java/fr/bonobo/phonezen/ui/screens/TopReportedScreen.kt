package fr.bonobo.phonezen.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.ReportedNumber
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun TopReportedScreen(
    vm    : MainViewModel,
    onBack: () -> Unit = {}
) {
    val c           = LocalColors.current
    var loading     by remember { mutableStateOf(true) }
    var numbers     by remember { mutableStateOf<List<ReportedNumber>>(emptyList()) }
    var refreshKey  by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        loading = true
        numbers = vm.getTopReported()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {

        // ── TopBar ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 42.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = c.neonCyan)
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text       = "Numéros signalés",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = c.neonCyan
                )
                Text(
                    text     = "Données communautaires · Expiration dynamique",
                    fontSize = 11.sp,
                    color    = c.textSecond
                )
            }
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Default.Refresh, null, tint = c.textSecond)
            }
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = c.neonCyan)
                        Spacer(Modifier.height(12.dp))
                        Text("Chargement…", fontSize = 13.sp, color = c.textSecond)
                    }
                }
            }

            numbers.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ThumbUp, null,
                            tint     = c.neonCyan.copy(alpha = 0.3f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Aucun numéro signalé", fontSize = 16.sp, color = c.textSecond, fontWeight = FontWeight.Medium)
                        Text("La communauté n'a rien signalé pour l'instant", fontSize = 13.sp, color = c.textSecond.copy(alpha = 0.7f))
                    }
                }
            }

            else -> {
                Text(
                    "${numbers.size} numéro(s) signalé(s) par la communauté",
                    fontSize = 12.sp,
                    color    = c.textSecond,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(numbers) { index, reported ->
                        ReportedNumberCard(index = index, reported = reported)
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// CARD NUMÉRO SIGNALÉ
// ─────────────────────────────────────────────
@Composable
private fun ReportedNumberCard(index: Int, reported: ReportedNumber) {
    val c = LocalColors.current

    val rankColor = when (index) {
        0    -> c.neonRed
        1    -> c.neonOrange
        2    -> c.neonYellow
        else -> c.textSecond
    }

    // Calcul expiration
    val expirationInfo = remember(reported.expiresAt) {
        computeExpiration(reported.expiresAt, reported.reports)
    }

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.surfaceVar)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {

            // ── Ligne principale ──
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Rang
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .background(rankColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "#${index + 1}",
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color      = rankColor
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text       = reported.number,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = c.textPrimary
                    )
                    // Tags
                    if (reported.tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            reported.tags.take(3).forEach { tag ->
                                Card(
                                    shape  = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = c.neonCyan.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Text(
                                        text     = tag,
                                        fontSize = 10.sp,
                                        color    = c.neonCyan,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Compteur signalements
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = "${reported.reports}",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = rankColor
                    )
                    Text(text = "signal.", fontSize = 10.sp, color = c.textSecond)
                }
            }

            // ── Bloc expiration ──
            if (expirationInfo != null) {
                Spacer(Modifier.height(10.dp))
                ExpirationBlock(info = expirationInfo)
            }
        }
    }
}

// ─────────────────────────────────────────────
// BLOC EXPIRATION — barre de vie + date
// ─────────────────────────────────────────────
@Composable
private fun ExpirationBlock(info: ExpirationInfo) {
    val c = LocalColors.current

    // Couleur de la barre selon le temps restant
    val barColor = when {
        info.fractionRemaining > 0.5f -> c.neonGreen
        info.fractionRemaining > 0.25f -> c.neonOrange
        else -> c.neonRed
    }

    // Animation de la barre au premier affichage
    val animatedFraction by animateFloatAsState(
        targetValue  = info.fractionRemaining,
        animationSpec = tween(durationMillis = 600),
        label        = "expiration_bar"
    )

    Column {
        // Ligne date + jours restants
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    null,
                    tint     = barColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text     = "Expire le ${info.expiresDateFormatted}",
                    fontSize = 11.sp,
                    color    = c.textSecond
                )
            }
            // Badge jours restants
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = barColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text     = info.daysRemainingLabel,
                    fontSize = 10.sp,
                    color    = barColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Barre de vie
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(c.glassStroke, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(barColor, RoundedCornerShape(2.dp))
            )
        }

        // Légende durée totale
        Text(
            text     = info.totalDurationLabel,
            fontSize = 10.sp,
            color    = c.textSecond.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// ─────────────────────────────────────────────
// MODÈLE + CALCUL EXPIRATION
// ─────────────────────────────────────────────

private data class ExpirationInfo(
    val expiresDateFormatted : String,
    val daysRemainingLabel   : String,
    val fractionRemaining    : Float,
    val totalDurationLabel   : String
)

private fun computeExpiration(expiresAt: String, reports: Long): ExpirationInfo? {
    if (expiresAt.isBlank()) return null

    return try {
        val expiresInstant = Instant.parse(expiresAt)
        val now            = Instant.now()
        val daysRemaining  = ChronoUnit.DAYS.between(now, expiresInstant)

        if (daysRemaining < 0) return null  // déjà expiré (ne devrait pas arriver — filtré par Supabase)

        // Durée totale selon le palier de signalements
        val totalDays = when {
            reports >= 100 -> 180L
            reports >= 50  -> 90L
            reports >= 20  -> 60L
            else           -> 30L
        }

        val fraction = (daysRemaining.toFloat() / totalDays.toFloat()).coerceIn(0f, 1f)

        val formatter = DateTimeFormatter
            .ofPattern("dd MMM yyyy", Locale.FRANCE)
            .withZone(ZoneId.systemDefault())

        val daysLabel = when {
            daysRemaining == 0L  -> "Expire aujourd'hui"
            daysRemaining == 1L  -> "1 jour restant"
            daysRemaining <= 7L  -> "$daysRemaining jours restants"
            else                 -> "$daysRemaining j. restants"
        }

        ExpirationInfo(
            expiresDateFormatted = formatter.format(expiresInstant),
            daysRemainingLabel   = daysLabel,
            fractionRemaining    = fraction,
            totalDurationLabel   = "Durée totale : $totalDays jours · ${reports} signalement${if (reports > 1) "s" else ""}"
        )
    } catch (e: Exception) {
        null
    }
}