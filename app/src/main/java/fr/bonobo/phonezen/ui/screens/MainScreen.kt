// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import fr.bonobo.phonezen.ui.theme.LocalColors
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel
//import fr.bonobo.phonezen.voicemail.VoicemailScreen
//import fr.bonobo.phonezen.voicemail.VoicemailSettingsScreen

enum class Screen(val title: String, val icon: ImageVector) {
    Onboarding("Bienvenue", Icons.Default.Shield),
    Recents("Journal", Icons.Default.History),
    Keypad("Clavier", Icons.Default.Dialpad),
    //Voicemail("Répondeur", Icons.Default.Voicemail),
    Contacts("Contacts", Icons.Default.Contacts),
    Settings("Réglages", Icons.Default.Settings)
}

private val navScreens = listOf(
    Screen.Recents,
    Screen.Keypad,
    //Screen.Voicemail,
    Screen.Contacts,
    Screen.Settings
)

private const val ROUTE_MAIN = "main"
private const val ROUTE_VOICEMAIL_SETTINGS = "voicemail_settings"

@Composable
fun MainScreen(
    vm: MainViewModel,
    themeVm: ThemeViewModel,
    onCall: (String) -> Unit,
    onCallWithSim: (String, Int) -> Unit = { number, _ -> onCall(number) },
    onVoicemail: () -> Unit,
    isDialerGranted: Boolean = true,
    isScreeningGranted: Boolean = true,
    isContactsGranted: Boolean = true,
    onRequestDialer: () -> Unit = {},
    onRequestScreening: () -> Unit = {},
    onRequestContacts: () -> Unit = {}
) {
    val c = LocalColors.current
    val context = LocalContext.current

    val startScreen =
        if (!isDialerGranted || !isScreeningGranted || !isContactsGranted) {
            Screen.Onboarding
        } else {
            Screen.Keypad
        }

    var currentScreen by remember { mutableStateOf(startScreen) }
    var currentRoute by remember { mutableStateOf(ROUTE_MAIN) }

    var showWhitelist by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showTopReported by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    var showEditContact by remember { mutableStateOf(false) }
    var currentContactIdToEdit by remember { mutableStateOf<Long?>(null) }
    var prefillNumber by remember { mutableStateOf("") }

    var showVoicemail by remember { mutableStateOf(false) }

    val dialpadNumber by vm.dialpadNumber.collectAsState()

    LaunchedEffect(dialpadNumber) {
        if (dialpadNumber.isNotEmpty()) {
            currentScreen = Screen.Keypad
            currentRoute = ROUTE_MAIN
        }
    }

    //if (currentRoute == ROUTE_VOICEMAIL_SETTINGS) {
    //    VoicemailSettingsScreen(
    //        onBack = { currentRoute = ROUTE_MAIN }
    //    )
    //    return
    //}

    //if (showVoicemail) {
    //    VoicemailScreen(
    //        onSettingsClick = { currentRoute = ROUTE_VOICEMAIL_SETTINGS },
    //        onBack = { showVoicemail = false }
    //    )
    //    return
    //}

    if (showWhitelist) {
        WhitelistScreen(
            vm = vm,
            onBack = { showWhitelist = false }
        )
        return
    }

    if (showTheme) {
        ThemeSelectorScreen(
            themeVm = themeVm,
            onBack = { showTheme = false }
        )
        return
    }

    if (showTopReported) {
        TopReportedScreen(
            vm = vm,
            onBack = { showTopReported = false }
        )
        return
    }

    if (showAddContact) {
        AddContactScreen(
            prefillNumber = prefillNumber,
            onNavigateBack = {
                showAddContact = false
                prefillNumber = ""
                vm.forceReload(context)
            }
        )
        return
    }

    if (showEditContact && currentContactIdToEdit != null) {
        AddContactScreen(
            contactId = currentContactIdToEdit!!,
            onNavigateBack = {
                showEditContact = false
                currentContactIdToEdit = null
                vm.forceReload(context)
            }
        )
        return
    }

    if (showProfiles) {
        ProfileScreen(
            vm = vm,
            onBack = { showProfiles = false }
        )
        return
    }

    if (currentScreen == Screen.Onboarding) {
        OnboardingScreen(
            isDialerGranted = isDialerGranted,
            isScreeningGranted = isScreeningGranted,
            isContactsGranted = isContactsGranted,
            onRequestDialer = onRequestDialer,
            onRequestScreening = onRequestScreening,
            onRequestContacts = onRequestContacts,
            onFinish = {
                currentScreen = Screen.Keypad
                currentRoute = ROUTE_MAIN
            }
        )
        return
    }

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(
                containerColor = c.surface,
                contentColor = c.neonOrange
            ) {
                navScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen && currentRoute == ROUTE_MAIN,
                        onClick = {
                            currentScreen = screen
                            currentRoute = ROUTE_MAIN
                        },
                        label = {
                            Text(
                                text = screen.title,
                                color = if (currentScreen == screen) {
                                    c.neonOrange
                                } else {
                                    c.textSecond
                                }
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title,
                                tint = if (currentScreen == screen) {
                                    c.neonOrange
                                } else {
                                    c.textSecond
                                }
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = c.surfaceVar,
                            selectedIconColor = c.neonOrange,
                            unselectedIconColor = c.textSecond,
                            selectedTextColor = c.neonOrange,
                            unselectedTextColor = c.textSecond
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            when (currentScreen) {
                Screen.Recents -> RecentsScreen(
                    vm = vm,
                    onCall = onCall,
                    onAddContact = { number ->
                        prefillNumber = number
                        showAddContact = true
                    },
                    onEditContact = { contactId ->
                        currentContactIdToEdit = contactId
                        showEditContact = true
                    }
                )

                Screen.Keypad -> KeypadScreen(
                    onCall = onCall,
                    onCallWithSim = onCallWithSim,
                    onVoicemail = { showVoicemail = true },
                    vm = vm
                )

                //Screen.Voicemail -> VoicemailScreen(
                 //   onSettingsClick = {
                 //       currentRoute = ROUTE_VOICEMAIL_SETTINGS
                //    },
                //    onBack = {
                //        currentScreen = Screen.Keypad
                //        currentRoute = ROUTE_MAIN
                //   }
                //)

                Screen.Contacts -> ContactsScreen(
                    vm = vm,
                    onCall = onCall,
                    onAddContact = {
                        prefillNumber = ""
                        showAddContact = true
                    }
                )

                Screen.Settings -> SettingsScreen(
                    vm = vm,
                    themeVm = themeVm,
                    onNavigateToWhitelist = { showWhitelist = true },
                    onNavigateToTheme = { showTheme = true },
                    onNavigateToTopReported = { showTopReported = true },
                    onNavigateToProfiles = { showProfiles = true }
                )

                Screen.Onboarding -> Unit
            }
        }
    }
}