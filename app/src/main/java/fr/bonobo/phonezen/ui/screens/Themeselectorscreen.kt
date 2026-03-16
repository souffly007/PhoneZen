package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.ThemeViewModel

@Composable
fun ThemeSelectorScreen(
    themeVm: ThemeViewModel,
    onBack: () -> Unit = {}
) {
    val current by themeVm.theme.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // ── TopBar ──
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = NeonCyan)
            }
            Text(
                text       = "Thème",
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                color      = NeonCyan,
                modifier   = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Carte Cyber Dark ──
        ThemeCard(
            label       = "Cyber Dark",
            description = "Thème sombre haute contraste",
            selected    = current == AppTheme.CYBER_DARK,
            preview     = {
                CyberDarkPreview()
            },
            onClick     = { themeVm.setTheme(AppTheme.CYBER_DARK) }
        )

        Spacer(Modifier.height(16.dp))

        // ── Carte Zen Clair ──
        ThemeCard(
            label       = "Zen Clair",
            description = "Thème lumineux inspiré de l'icône",
            selected    = current == AppTheme.ZEN_LIGHT,
            preview     = {
                ZenLightPreview()
            },
            onClick     = { themeVm.setTheme(AppTheme.ZEN_LIGHT) }
        )
    }
}

@Composable
private fun ThemeCard(
    label: String,
    description: String,
    selected: Boolean,
    preview: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val borderColor = if (selected) NeonCyan else GlassStroke
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .background(SurfaceVar)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Miniature preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                preview()
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text       = label,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color      = if (selected) NeonCyan else TextPrimary
                    )
                    Text(
                        text     = description,
                        fontSize = 12.sp,
                        color    = TextSecond
                    )
                }
                if (selected) {
                    Box(
                        modifier         = Modifier
                            .size(28.dp)
                            .background(NeonCyan, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint     = Background,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Miniature Cyber Dark ──
@Composable
private fun CyberDarkPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF010203))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Fausse barre de titre
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(50))
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(80.dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            // Fausses lignes de liste
            repeat(3) {
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFF161B22), RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .fillMaxWidth(0.6f)
                            .background(Color(0xFF263238), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
        // Accent cyan en bas à droite
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(28.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF00E5FF).copy(alpha = 0.5f), Color.Transparent)
                    ),
                    RoundedCornerShape(50)
                )
        )
    }
}

// ── Miniature Zen Clair ──
@Composable
private fun ZenLightPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFB8D4D8), Color(0xFFF0F6F7))
                )
            )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Fausse barre de titre
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF1DB87A).copy(alpha = 0.2f), RoundedCornerShape(50))
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(80.dp)
                        .background(Color(0xFF1DB87A).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                )
            }
            Spacer(Modifier.height(8.dp))
            // Fausses lignes de liste
            repeat(3) {
                Row(
                    modifier = Modifier.padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFFFFFFFF), RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .fillMaxWidth(0.6f)
                            .background(Color(0xFFCADEE2), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
        // Accent vert en bas à droite
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(28.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF1DB87A).copy(alpha = 0.4f), Color.Transparent)
                    ),
                    RoundedCornerShape(50)
                )
        )
    }
}