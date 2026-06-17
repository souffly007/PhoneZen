// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import fr.bonobo.phonezen.ui.screens.MainScreen
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.utils.ContactCache
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel
import fr.bonobo.phonezen.ui.screens.InCallActivity
import fr.bonobo.phonezen.service.VoicemailListener
import fr.bonobo.phonezen.utils.VoicemailNotificationHelper

import fr.bonobo.phonezen.data.model.CallStatus
import android.telecom.Call
import fr.bonobo.phonezen.service.CallManager

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val themeVm: ThemeViewModel by viewModels()
    private var rolesRequested = false

    // Listener pour masquer l'UI principale dès qu'un appel sortant démarre
    private val callListener: (Call?, CallStatus) -> Unit = { _, status ->
        if (status == CallStatus.DIALING) {
            Log.d("MainActivity", "Statut DIALING détecté → masquage de l'UI principale")
            moveTaskToBack(true)
        }
    }

    // ── États onboarding (Compose observe ces variables) ──
    private var isDialerGranted    by mutableStateOf(false)
    private var isScreeningGranted by mutableStateOf(false)
    private var isContactsGranted  by mutableStateOf(false)

    // Liste complète des permissions nécessaires
    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.VIBRATE,
        Manifest.permission.BLUETOOTH,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                Log.d("MainActivity", "Toutes les permissions accordées")
            } else {
                Log.w("MainActivity", "Certaines permissions sont refusées")
            }
            proceedAfterPermissions()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d("MainActivity", "Rôle demandé, résultat: ${result.resultCode}")
            refreshRoleStates()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate")

        VoicemailNotificationHelper.createChannel(this)

        // Calcul initial des états avant le premier setContent
        refreshRoleStates()

        setContent {
            val appTheme by themeVm.theme.collectAsState()
            PhoneZenTheme(appTheme = appTheme) {
                MainScreen(
                    vm                 = vm,
                    themeVm            = themeVm,
                    onCall             = { number -> launchCall(number) },
                    onCallWithSim      = { number, subscriptionId -> launchCallWithSim(number, subscriptionId) },
                    onVoicemail        = { launchVoicemail() },
                    isDialerGranted    = isDialerGranted,
                    isScreeningGranted = isScreeningGranted,
                    isContactsGranted  = isContactsGranted,
                    onRequestDialer    = { requestDialerRole() },
                    onRequestScreening = { requestScreeningRole() },
                    onRequestContacts  = { requestContactsPermission() }
                )
            }
        }

        checkPermissions()
        handleDialIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        CallManager.addListener(callListener)
    }

    override fun onStop() {
        super.onStop()
        CallManager.removeListener(callListener)
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")

        // Met à jour les états onboarding quand l'utilisateur revient
        // d'un dialog système (rôle accordé ou refusé)
        refreshRoleStates()

        if (rolesReady()) {
            vm.loadData(this)
        }

        checkForOngoingCall()
        checkVoicemailStatus()
    }

    // ══════════════════════════════════════════════════════════════
    // INTENT tel: (lien depuis navigateur ou autre app)
    // ══════════════════════════════════════════════════════════════

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent")
        setIntent(intent)
        handleDialIntent(intent)
    }

    private fun handleDialIntent(intent: Intent?) {
        val number = getNumberFromIntent(intent) ?: return
        if (number.isBlank()) return
        Log.d("MainActivity", "Intent tel reçu: $number")
        vm.setDialpadNumber(number)
    }

    private fun getNumberFromIntent(intent: Intent?): String? {
        val data = intent?.data
        return when {
            data?.scheme == "tel" -> data.schemeSpecificPart
            intent?.action == Intent.ACTION_DIAL && data != null -> data.schemeSpecificPart
            else -> null
        }
    }

    // ══════════════════════════════════════════════════════════════
    // APPEL NORMAL (mono SIM ou SIM par défaut)
    // ══════════════════════════════════════════════════════════════

    fun launchCall(number: String) {
        Log.d("MainActivity", "launchCall: $number")
        try {
            cacheContactName(number)
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.fromParts("tel", number, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            // Masque immédiatement l'UI principale pour éviter l'effet de gel
            moveTaskToBack(true)
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Permission CALL_PHONE manquante, ouverture du dialer")
            startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.fromParts("tel", number, null)
            })
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur appel: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // APPEL AVEC SIM SPÉCIFIQUE (dual SIM)
    // ══════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    fun launchCallWithSim(number: String, subscriptionId: Int) {
        Log.d("MainActivity", "launchCallWithSim: $number sur SIM $subscriptionId")
        cacheContactName(number)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || subscriptionId < 0) {
            launchCall(number)
            return
        }

        try {
            val telecomManager     = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val phoneAccountHandle = getPhoneAccountHandle(subscriptionId)

            if (phoneAccountHandle != null) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.fromParts("tel", number, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                }
                startActivity(intent)
                // Masque immédiatement l'UI principale pour éviter l'effet de gel
                moveTaskToBack(true)
                Log.d("MainActivity", "Appel via SIM $subscriptionId lancé")
            } else {
                Log.w("MainActivity", "PhoneAccountHandle non trouvé pour SIM $subscriptionId")
                launchCall(number)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur appel SIM spécifique: ${e.message}")
            launchCall(number)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPhoneAccountHandle(subscriptionId: Int): PhoneAccountHandle? {
        return try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.callCapablePhoneAccounts?.firstOrNull { handle ->
                val account = telecomManager.getPhoneAccount(handle)
                val extras  = account?.extras
                val subId   = extras?.getInt("android.telecom.extra.SUBSCRIPTION_ID", -1) ?: -1
                subId == subscriptionId
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur getPhoneAccountHandle: ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CACHE DES CONTACTS
    // ══════════════════════════════════════════════════════════════

    private fun cacheContactName(number: String) {
        if (ContactCache.get(number) != null) return
        val name = lookupContactName(number)
        if (name != null) {
            ContactCache.put(number, name)
            Log.d("MainActivity", "Contact mis en cache: $name → $number")
        }
    }

    private fun lookupContactName(number: String): String? {
        val normalizedNumber = PhoneUtils.normalizeNumber(number)
        val uri = Uri.withAppendedPath(
            android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedNumber)
        )
        val projection = arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME)
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun preloadContactCache() {
        Thread {
            try {
                ContactCache.preloadFromContacts(contentResolver)
                Log.d("MainActivity", "ContactCache préchargé")
            } catch (e: Exception) {
                Log.e("MainActivity", "Erreur préchargement cache: ${e.message}")
            }
        }.start()
    }

    // ══════════════════════════════════════════════════════════════
    // MESSAGERIE VOCALE
    // ══════════════════════════════════════════════════════════════

    private fun launchVoicemail() {
        val number = getVoicemailNumber()
        Log.d("MainActivity", "Appel messagerie vocale: $number")
        launchCall(number)
    }

    private fun getVoicemailNumber(): String {
        getSystemVoicemailNumber()?.let { return it }
        val carrier = getCarrierName()?.lowercase()?.trim() ?: return "123"
        VOICEMAIL_NUMBERS[carrier]?.let { return it }
        VOICEMAIL_NUMBERS.entries.find { (key, _) ->
            carrier.contains(key) || key.contains(carrier)
        }?.let { return it.value }
        return "123"
    }

    private fun getSystemVoicemailNumber(): String? {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.voiceMailNumber?.takeIf { it.isNotBlank() && it != "null" }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur getSystemVoicemailNumber: ${e.message}")
            null
        }
    }

    private fun getCarrierName(): String? {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName.takeIf { it.isNotBlank() }
                ?: tm.simOperatorName.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur getCarrierName: ${e.message}")
            null
        }
    }

    private fun checkVoicemailStatus() {
        try {
            val listener = VoicemailListener(this)
            listener.checkCurrentMwi()
        } catch (e: Exception) {
            Log.e("MainActivity", "Erreur checkVoicemailStatus: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PERMISSIONS & RÔLES
    // ══════════════════════════════════════════════════════════════

    /**
     * Met à jour les états observables isDialerGranted / isScreeningGranted / isContactsGranted.
     * Appelé dans onCreate, onResume et après chaque résultat de roleLauncher.
     */
    private fun refreshRoleStates() {
        isContactsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager    = getSystemService(Context.ROLE_SERVICE) as RoleManager
            isDialerGranted    = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            isScreeningGranted = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } else {
            // Avant Android 10, les rôles n'existent pas → on considère tout OK
            isDialerGranted    = true
            isScreeningGranted = true
        }

        Log.d("MainActivity", "refreshRoleStates → dialer=$isDialerGranted " +
                "screening=$isScreeningGranted contacts=$isContactsGranted")
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            Log.d("MainActivity", "Toutes les permissions sont déjà accordées")
            proceedAfterPermissions()
        } else {
            Log.d("MainActivity", "Permissions manquantes: ${missing.joinToString()}")
            permLauncher.launch(missing.toTypedArray())
        }
    }

    private fun proceedAfterPermissions() {
        Log.d("MainActivity", "proceedAfterPermissions")
        vm.loadData(this)
        preloadContactCache()
        refreshRoleStates()
    }

    // ── Demandes individuelles (appelées depuis l'onboarding) ──

    private fun requestDialerRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            Log.d("MainActivity", "Demande du rôle DIALER")
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    private fun requestScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Log.d("MainActivity", "Demande du rôle CALL_SCREENING")
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun requestContactsPermission() {
        permLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS
            )
        )
    }

    private fun rolesReady(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    // ══════════════════════════════════════════════════════════════
    // APPEL EN COURS (reconnexion après kill)
    // ══════════════════════════════════════════════════════════════

    @SuppressLint("MissingPermission")
    private fun checkForOngoingCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (telecomManager.isInCall) {
                Log.d("MainActivity", "Appel en cours détecté, relance InCallActivity")
                val intent = Intent(this, InCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "checkForOngoingCall error: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // CONSTANTES
    // ══════════════════════════════════════════════════════════════

    companion object {
        private val VOICEMAIL_NUMBERS = mapOf(
            "orange"              to "888",
            "sosh"                to "888",
            "sfr"                 to "123",
            "red"                 to "123",
            "red by sfr"          to "123",
            "bouygues"            to "660",
            "bouygues telecom"    to "660",
            "b&you"               to "660",
            "free"                to "666",
            "free mobile"         to "666",
            "syma"                to "888",
            "syma mobile"         to "888",
            "youprice"            to "888",
            "la poste mobile"     to "123",
            "la poste"            to "123",
            "prixtel"             to "123",
            "coriolis"            to "123",
            "réglo mobile"        to "123",
            "réglo"               to "123",
            "nrj mobile"          to "660",
            "nrj"                 to "660",
            "cic mobile"          to "660",
            "crédit mutuel mobile" to "660",
            "auchan telecom"      to "660",
            "cdiscount mobile"    to "660",
            "lebara"              to "5765",
            "lycamobile"          to "121"
        )
    }
}