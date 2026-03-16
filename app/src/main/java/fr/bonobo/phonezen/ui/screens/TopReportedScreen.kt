package fr.bonobo.phonezen.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.data.model.ReportedNumber
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel

@Composable
fun TopReportedScreen(
    vm    : MainViewModel,
    onBack: () -> Unit = {}
) {
    val c       = LocalColors.current
    var loading by remember { mutableStateOf(true) }
    var numbers by remember { mutableStateOf<List<ReportedNumber>>(emptyList()) }

    // Charge le top au lancement
    LaunchedEffect(Unit) {
        numbers = vm.getTopReported()
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(c.background)) {

        // ── TopBar ──
        Row(
            modifier          = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
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
                    text     = "Données communautaires · 40 jours glissants",
                    fontSize = 11.sp,
                    color    = c.textSecond
                )
            }
            // Bouton rafraîchir
            IconButton(onClick = {
                loading = true
                /* Le LaunchedEffect ne se re-déclenche pas, on recharge via un flag */
            }) {
                Icon(Icons.Default.Refresh, null, tint = c.textSecond)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = c.neonCyan)
                    Spacer(Modifier.height(12.dp))
                    Text("Chargement…", fontSize = 13.sp, color = c.textSecond)
                }
            }
        } else if (numbers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ThumbUp,
                        null,
                        tint     = c.neonCyan.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Aucun numéro signalé", fontSize = 16.sp, color = c.textSecond, fontWeight = FontWeight.Medium)
                    Text("La communauté n'a rien signalé pour l'instant", fontSize = 13.sp, color = c.textSecond.copy(alpha = 0.7f))
                }
            }
        } else {
            // Compteur
            Text(
                "${numbers.size} numéro(s) signalé(s) par la communauté",
                fontSize = 12.sp,
                color    = c.textSecond,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
            )

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(numbers) { index, reported ->
                    ReportedNumberCard(index = index, reported = reported)
                }
            }
        }
    }
}

@Composable
private fun ReportedNumberCard(index: Int, reported: ReportedNumber) {
    val c = LocalColors.current

    // Couleur du rang selon la position
    val rankColor = when (index) {
        0    -> c.neonRed
        1    -> c.neonOrange
        2    -> c.neonYellow
        else -> c.textSecond
    }

    Card(
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = c.surfaceVar)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Rang ──
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
                                colors = CardDefaults.cardColors(containerColor = c.neonCyan.copy(alpha = 0.1f))
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

            // ── Compteur de signalements ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = "${reported.reports}",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = rankColor
                )
                Text(
                    text     = "signal.",
                    fontSize = 10.sp,
                    color    = c.textSecond
                )
            }
        }
    }
}