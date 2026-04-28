// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import fr.bonobo.phonezen.ui.theme.*
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel

enum class Screen(val title: String, val icon: ImageVector) {
    Onboarding("Bienvenue",  Icons.Default.Shield),   // ← pas dans la nav bar
    Recents   ("Journal",    Icons.Default.History),
    Keypad    ("Clavier",    Icons.Default.Dialpad),
    Contacts  ("Contacts",   Icons.Default.Contacts),
    Settings  ("Réglages",   Icons.Default.Settings)
}

// Seuls ces écrans apparaissent dans la barre de navigation
private val navScreens = listOf(Screen.Recents, Screen.Keypad, Screen.Contacts, Screen.Settings)

@Composable
fun MainScreen(
    vm                 : MainViewModel,
    themeVm            : ThemeViewModel,
    onCall             : (String) -> Unit,
    onCallWithSim      : (String, Int) -> Unit = { number, _ -> onCall(number) },
    onVoicemail        : () -> Unit,
    // ── Callbacks onboarding (états et launchers venant de MainActivity) ──
    isDialerGranted    : Boolean = true,
    isScreeningGranted : Boolean = true,
    isContactsGranted  : Boolean = true,
    onRequestDialer    : () -> Unit = {},
    onRequestScreening : () -> Unit = {},
    onRequestContacts  : () -> Unit = {}
) {
    val c       = LocalColors.current
    val context = LocalContext.current

    // ── Écran initial : Onboarding si des rôles/permissions manquent ──
    val startScreen = if (!isDialerGranted || !isScreeningGranted || !isContactsGranted)
        Screen.Onboarding else Screen.Keypad

    var currentScreen by remember { mutableStateOf(startScreen) }

    var showWhitelist          by remember { mutableStateOf(false) }
    var showTheme              by remember { mutableStateOf(false) }
    var showTopReported        by remember { mutableStateOf(false) }
    var showAddContact         by remember { mutableStateOf(false) }
    var showProfiles           by remember { mutableStateOf(false) }
    var showEditContact        by remember { mutableStateOf(false) }
    var currentContactIdToEdit by remember { mutableStateOf<Long?>(null) }
    var prefillNumber          by remember { mutableStateOf("") }

    // ── Si un numéro arrive via intent tel:, on force l'onglet Clavier ──
    val dialpadNumber by vm.dialpadNumber.collectAsState()
    LaunchedEffect(dialpadNumber) {
        if (dialpadNumber.isNotEmpty()) currentScreen = Screen.Keypad
    }

    // ── Sous-écrans modaux (inchangés) ──
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
    if (showAddContact) {
        AddContactScreen(
            prefillNumber  = prefillNumber,
            onNavigateBack = {
                showAddContact = false
                prefillNumber  = ""
                vm.forceReload(context)
            }
        )
        return
    }
    if (showEditContact && currentContactIdToEdit != null) {
        AddContactScreen(
            contactId      = currentContactIdToEdit!!,
            onNavigateBack = {
                showEditContact        = false
                currentContactIdToEdit = null
                vm.forceReload(context)
            }
        )
        return
    }
    if (showProfiles) {
        ProfileScreen(vm = vm, onBack = { showProfiles = false })
        return
    }

    // ── Onboarding : plein écran sans nav bar ──
    if (currentScreen == Screen.Onboarding) {
        OnboardingScreen(
            isDialerGranted    = isDialerGranted,
            isScreeningGranted = isScreeningGranted,
            isContactsGranted  = isContactsGranted,
            onRequestDialer    = onRequestDialer,
            onRequestScreening = onRequestScreening,
            onRequestContacts  = onRequestContacts,
            onFinish           = { currentScreen = Screen.Keypad }
        )
        return
    }

    // ── Interface principale avec nav bar ──
    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(
                containerColor = c.surface,
                contentColor   = c.neonOrange
            ) {
                navScreens.forEach { screen ->
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
                            indicatorColor      = c.surfaceVar,
                            selectedIconColor   = c.neonOrange,
                            unselectedIconColor = c.textSecond,
                            selectedTextColor   = c.neonOrange,
                            unselectedTextColor = c.textSecond
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                Screen.Recents  -> RecentsScreen(
                    vm           = vm,
                    onCall       = onCall,
                    onAddContact = { number ->
                        prefillNumber  = number
                        showAddContact = true
                    },
                    onEditContact = { contactId ->
                        currentContactIdToEdit = contactId
                        showEditContact        = true
                    }
                )
                Screen.Keypad   -> KeypadScreen(
                    onCall        = onCall,
                    onCallWithSim = onCallWithSim,
                    onVoicemail   = onVoicemail,
                    vm            = vm
                )
                Screen.Contacts -> ContactsScreen(
                    vm           = vm,
                    onCall       = onCall,
                    onAddContact = {
                        prefillNumber  = ""
                        showAddContact = true
                    }
                )
                Screen.Settings -> SettingsScreen(
                    vm                      = vm,
                    themeVm                 = themeVm,
                    onNavigateToWhitelist   = { showWhitelist = true },
                    onNavigateToTheme       = { showTheme = true },
                    onNavigateToTopReported = { showTopReported = true },
                    onNavigateToProfiles    = { showProfiles = true }
                )
                else -> {} // Screen.Onboarding géré au-dessus
            }
        }
    }
}