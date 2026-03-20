package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel

enum class Screen(val title: String, val icon: ImageVector) {
    Recents("Journal",   Icons.Default.History),
    Keypad("Clavier",    Icons.Default.Dialpad),
    Contacts("Contacts", Icons.Default.Contacts),
    Settings("Réglages", Icons.Default.Settings)
}

@Composable
fun MainScreen(
    vm         : MainViewModel,
    themeVm    : ThemeViewModel,
    onCall     : (String) -> Unit,
    onVoicemail: () -> Unit
) {
    val c             = LocalColors.current
    var currentScreen by remember { mutableStateOf(Screen.Keypad) }
    var showWhitelist    by remember { mutableStateOf(false) }
    var showTheme        by remember { mutableStateOf(false) }
    var showTopReported  by remember { mutableStateOf(false) }

    // ── Écrans plein écran sans BottomBar ──
    if (showWhitelist) {
        WhitelistScreen(vm = vm, onBack = { showWhitelist = false })
        return
    }
    if (showTheme) {
        ThemeSelectorScreen(themeVm = themeVm, onBack = { showTheme = false })
        return
    }
    if (showTopReported) {
        TopReportedScreen(vm = vm, onBack = { showTopReported = false })
        return
    }

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(
                containerColor = c.surface,
                contentColor   = c.neonOrange
            ) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick  = { currentScreen = screen },
                        label    = {
                            Text(
                                screen.title,
                                color = if (currentScreen == screen) c.neonOrange else c.textSecond
                            )
                        },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.title,
                                tint = if (currentScreen == screen) c.neonOrange else c.textSecond
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor          = c.surfaceVar,
                            selectedIconColor       = c.neonOrange,
                            unselectedIconColor     = c.textSecond,
                            selectedTextColor       = c.neonOrange,
                            unselectedTextColor     = c.textSecond
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Recents   -> RecentsScreen(vm = vm, onCall = onCall)
                Screen.Keypad    -> KeypadScreen(onCall = onCall, onVoicemail = onVoicemail, vm = vm)
                Screen.Contacts  -> ContactsScreen(vm = vm, onCall = onCall)
                Screen.Settings  -> SettingsScreen(
                    vm                     = vm,
                    themeVm                = themeVm,
                    onNavigateToWhitelist  = { showWhitelist = true },
                    onNavigateToTheme      = { showTheme = true },
                    onNavigateToTopReported = { showTopReported = true }
                )
            }
        }
    }
}