package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.LocalColors
import fr.bonobo.phonezen.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*
import fr.bonobo.phonezen.data.model.BlockedNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(vm: MainViewModel, onBack: () -> Unit) {
    val c = LocalColors.current
    // ✅ Maintenant on observe la vraie liste noire
    val blockedList by vm.blockedNumbers.collectAsState()

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "LISTE NOIRE PERSONNELLE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.neonRed
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = c.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Ces numéros sont bloqués directement sur votre appareil.",
                fontSize = 12.sp,
                color = c.textSecond,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (blockedList.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Shield, null, modifier = Modifier.size(48.dp), tint = c.textSecond.copy(0.3f))
                        Spacer(Modifier.height(8.dp))
                        Text("Aucun numéro bloqué", color = c.textSecond)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    items(
                        items = blockedList,
                        key = { it.id }   // ✅ id unique suffit ici
                    ) { blocked ->
                        BlockedNumberRow(
                            blocked = blocked,
                            onDelete = { vm.unblockNumber(blocked) }  // ✅ bonne fonction
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedNumberRow(blocked: BlockedNumber, onDelete: () -> Unit) {
    val c = LocalColors.current
    val date = remember(blocked.timestamp) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(blocked.timestamp))
    }

    Surface(
        color = c.surfaceVar,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.glassStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(c.neonRed.copy(0.1f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🚫", fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = blocked.number,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                // ✅ Affiche le label ET la date
                if (blocked.label.isNotBlank()) {
                    Text(
                        text = blocked.label,
                        color = c.textSecond,
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = date,
                    color = c.textSecond.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Débloquer",
                    tint = c.neonRed
                )
            }
        }
    }
}