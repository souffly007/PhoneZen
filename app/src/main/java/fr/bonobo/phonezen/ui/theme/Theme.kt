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
//  PALETTE CYANOGEN (hommage à CyanogenMod)
//  Noir profond + cyan électrique signature "Cid",
//  accent orange façon anciens settings CM.
// ──────────────────────────────────────────
private val _CyanoBackground  = Color(0xFF000000)
private val _CyanoSurface     = Color(0xFF0A0A0A)
private val _CyanoSurfaceVar  = Color(0xFF141414)
private val _CyanoNeonCyan    = Color(0xFF00BCD4)
private val _CyanoNeonOrange  = Color(0xFFFF6D00)
private val _CyanoNeonGreen   = Color(0xFF64DD17)
private val _CyanoNeonRed     = Color(0xFFE53935)
private val _CyanoNeonYellow  = Color(0xFFFFD600)
private val _CyanoGradStart   = Color(0xFF006064)
private val _CyanoTextPrimary = Color(0xFFECEFF1)
private val _CyanoTextSecond  = Color(0xFF80CBC4)
private val _CyanoGlassStroke = Color(0xFF1A3A3D)

private val Cyanogen = darkColorScheme(
    primary          = _CyanoNeonCyan,
    onPrimary        = _CyanoBackground,
    primaryContainer = _CyanoGradStart,
    secondary        = _CyanoNeonOrange,
    onSecondary      = _CyanoBackground,
    background       = _CyanoBackground,
    onBackground     = _CyanoTextPrimary,
    surface          = _CyanoSurface,
    onSurface        = _CyanoTextPrimary,
    surfaceVariant   = _CyanoSurfaceVar,
    onSurfaceVariant = _CyanoTextSecond,
    error            = _CyanoNeonRed,
    outline          = _CyanoGlassStroke,
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

private val CyanogenColors = AppColors(
    background  = _CyanoBackground,
    surface     = _CyanoSurface,
    surfaceVar  = _CyanoSurfaceVar,
    neonCyan    = _CyanoNeonCyan,
    neonOrange  = _CyanoNeonOrange,
    neonGreen   = _CyanoNeonGreen,
    neonRed     = _CyanoNeonRed,
    neonYellow  = _CyanoNeonYellow,
    gradStart   = _CyanoGradStart,
    textPrimary = _CyanoTextPrimary,
    textSecond  = _CyanoTextSecond,
    glassStroke = _CyanoGlassStroke,
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

// Couleurs Cyanogen exposées pour ThemeSelectorScreen
val CyanoNeonCyan    = _CyanoNeonCyan
val CyanoNeonOrange  = _CyanoNeonOrange
val CyanoBackground  = _CyanoBackground
val CyanoSurfaceVar  = _CyanoSurfaceVar
val CyanoGlassStroke = _CyanoGlassStroke
val CyanoTextPrimary = _CyanoTextPrimary
val CyanoTextSecond  = _CyanoTextSecond

// ──────────────────────────────────────────
//  ENUM & COMPOSABLE PRINCIPAL
// ──────────────────────────────────────────
enum class AppTheme { CYBER_DARK, ZEN_LIGHT, CYANOGEN }

@Composable
fun PhoneZenTheme(
    appTheme: AppTheme = AppTheme.CYBER_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.CYBER_DARK -> CyberDark
        AppTheme.ZEN_LIGHT  -> ZenLight
        AppTheme.CYANOGEN   -> Cyanogen
    }
    val appColors = when (appTheme) {
        AppTheme.CYBER_DARK -> DarkColors
        AppTheme.ZEN_LIGHT  -> LightColors
        AppTheme.CYANOGEN   -> CyanogenColors
    }
    CompositionLocalProvider(LocalColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}