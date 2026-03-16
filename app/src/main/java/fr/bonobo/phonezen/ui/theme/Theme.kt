package fr.bonobo.phonezen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ──────────────────────────────────────────
//  PALETTE CYBER DARK
// ──────────────────────────────────────────
private val _DarkBackground  = Color(0xFF010203)
private val _DarkSurface     = Color(0xFF0D1117)
private val _DarkSurfaceVar  = Color(0xFF161B22)
private val _DarkNeonCyan    = Color(0xFF00E5FF)
private val _DarkNeonOrange  = Color(0xFFFF9800)
private val _DarkNeonGreen   = Color(0xFF4CAF50)
private val _DarkNeonRed     = Color(0xFFF44336)
private val _DarkNeonYellow  = Color(0xFFFFEB3B)
private val _DarkGradStart   = Color(0xFF1A237E)
private val _DarkTextPrimary = Color(0xFFE0E0E0)
private val _DarkTextSecond  = Color(0xFF9E9E9E)
private val _DarkGlassStroke = Color(0xFF263238)

private val CyberDark = darkColorScheme(
    primary          = _DarkNeonCyan,
    onPrimary        = _DarkBackground,
    primaryContainer = _DarkGradStart,
    secondary        = _DarkNeonOrange,
    onSecondary      = _DarkBackground,
    background       = _DarkBackground,
    onBackground     = _DarkTextPrimary,
    surface          = _DarkSurface,
    onSurface        = _DarkTextPrimary,
    surfaceVariant   = _DarkSurfaceVar,
    onSurfaceVariant = _DarkTextSecond,
    error            = _DarkNeonRed,
    outline          = _DarkGlassStroke,
)

// ──────────────────────────────────────────
//  PALETTE ZEN CLAIR
// ──────────────────────────────────────────
private val _ZenGreen        = Color(0xFF1DB87A)
private val _ZenGreenLight   = Color(0xFF4ECFA0)
private val _ZenSkyBlue      = Color(0xFFB8D4D8)
private val _ZenSkyBlueDark  = Color(0xFF7BA8B0)
private val _ZenBackground   = Color(0xFFF0F6F7)
private val _ZenSurface      = Color(0xFFFFFFFF)
private val _ZenSurfaceVar   = Color(0xFFE8F2F4)
private val _ZenStroke       = Color(0xFFCADEE2)
private val _ZenTextPrimary  = Color(0xFF1A2E32)
private val _ZenTextSecond   = Color(0xFF5A7A80)
private val _ZenError        = Color(0xFFD64C3A)

private val ZenLight = lightColorScheme(
    primary          = _ZenGreen,
    onPrimary        = Color.White,
    primaryContainer = _ZenGreenLight.copy(alpha = 0.25f),
    secondary        = _ZenSkyBlueDark,
    onSecondary      = Color.White,
    background       = _ZenBackground,
    onBackground     = _ZenTextPrimary,
    surface          = _ZenSurface,
    onSurface        = _ZenTextPrimary,
    surfaceVariant   = _ZenSurfaceVar,
    onSurfaceVariant = _ZenTextSecond,
    error            = _ZenError,
    outline          = _ZenStroke,
)

// ──────────────────────────────────────────
//  AppColors : objet porté par CompositionLocal
// ──────────────────────────────────────────
@Immutable
data class AppColors(
    val background  : Color,
    val surface     : Color,
    val surfaceVar  : Color,
    val neonCyan    : Color,
    val neonOrange  : Color,
    val neonGreen   : Color,
    val neonRed     : Color,
    val neonYellow  : Color,
    val gradStart   : Color,
    val textPrimary : Color,
    val textSecond  : Color,
    val glassStroke : Color,
)

private val DarkColors = AppColors(
    background  = _DarkBackground,
    surface     = _DarkSurface,
    surfaceVar  = _DarkSurfaceVar,
    neonCyan    = _DarkNeonCyan,
    neonOrange  = _DarkNeonOrange,
    neonGreen   = _DarkNeonGreen,
    neonRed     = _DarkNeonRed,
    neonYellow  = _DarkNeonYellow,
    gradStart   = _DarkGradStart,
    textPrimary = _DarkTextPrimary,
    textSecond  = _DarkTextSecond,
    glassStroke = _DarkGlassStroke,
)

private val LightColors = AppColors(
    background  = _ZenBackground,
    surface     = _ZenSurface,
    surfaceVar  = _ZenSurfaceVar,
    neonCyan    = _ZenGreen,
    neonOrange  = Color(0xFFE07B2A),
    neonGreen   = _ZenGreen,
    neonRed     = _ZenError,
    neonYellow  = Color(0xFFD4A017),
    gradStart   = _ZenGreenLight,
    textPrimary = _ZenTextPrimary,
    textSecond  = _ZenTextSecond,
    glassStroke = _ZenStroke,
)

val LocalColors = staticCompositionLocalOf { DarkColors }

// ──────────────────────────────────────────
//  Alias statiques — compatibilité compile
//  (à remplacer progressivement par LocalColors.current.xxx)
// ──────────────────────────────────────────
val Background  = _DarkBackground
val Surface     = _DarkSurface
val SurfaceVar  = _DarkSurfaceVar
val Primary     = _DarkNeonCyan
val NeonCyan    = _DarkNeonCyan
val NeonOrange  = _DarkNeonOrange
val NeonGreen   = _DarkNeonGreen
val NeonRed     = _DarkNeonRed
val NeonYellow  = _DarkNeonYellow
val GradStart   = _DarkGradStart
val TextPrimary = _DarkTextPrimary
val TextSecond  = _DarkTextSecond
val GlassStroke = _DarkGlassStroke

// Couleurs Zen exposées pour ThemeSelectorScreen
val ZenGreen       = _ZenGreen
val ZenGreenLight  = _ZenGreenLight
val ZenSkyBlue     = _ZenSkyBlue
val ZenSkyBlueDark = _ZenSkyBlueDark
val ZenBackground  = _ZenBackground
val ZenSurfaceVar  = _ZenSurfaceVar
val ZenStroke      = _ZenStroke
val ZenTextPrimary = _ZenTextPrimary
val ZenTextSecond  = _ZenTextSecond

// ──────────────────────────────────────────
//  ENUM & COMPOSABLE PRINCIPAL
// ──────────────────────────────────────────
enum class AppTheme { CYBER_DARK, ZEN_LIGHT }

@Composable
fun PhoneZenTheme(
    appTheme: AppTheme = AppTheme.CYBER_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.CYBER_DARK -> CyberDark
        AppTheme.ZEN_LIGHT  -> ZenLight
    }
    val appColors = when (appTheme) {
        AppTheme.CYBER_DARK -> DarkColors
        AppTheme.ZEN_LIGHT  -> LightColors
    }
    CompositionLocalProvider(LocalColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}