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
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import fr.bonobo.phonezen.ui.screens.MainScreen
import fr.bonobo.phonezen.ui.theme.PhoneZenTheme
import fr.bonobo.phonezen.utils.ContactCache
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.viewmodel.MainViewModel
import fr.bonobo.phonezen.viewmodel.ThemeViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val themeVm: ThemeViewModel by viewModels()
    private var rolesRequested = false

    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.all { it.value }) proceedAfterPermissions()
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appTheme by themeVm.theme.collectAsState()
            PhoneZenTheme(appTheme = appTheme) {
                MainScreen(
                    vm             = vm,
                    themeVm        = themeVm,
                    onCall         = { number -> launchCall(number) },
                    onCallWithSim  = { number, subscriptionId -> launchCallWithSim(number, subscriptionId) },
                    onVoicemail    = { launchVoicemail() }
                )
            }
        }

        checkPermissions()

        // Récupère le numéro si lancé depuis un lien tel: (navigateur, site web, etc.)
        handleDialIntent(intent)
    }

    // ══════════════════════════════════════════════════════════════
    // GESTION DE L'INTENT tel: (lien depuis navigateur ou autre app)
    // ══════════════════════════════════════════════════════════════

    /**
     * Indispensable avec launchMode="singleTask" :
     * si l'app est déjà ouverte, onCreate n'est pas rappelé,
     * Android passe par onNewIntent à la place.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // met à jour l'intent courant
        handleDialIntent(intent)
    }

    /**
     * Extrait le numéro d'un intent ACTION_DIAL / ACTION_VIEW avec scheme "tel"
     * et le pousse vers le ViewModel pour préremplir le dialpad.
     */
    private fun handleDialIntent(intent: Intent?) {
        val number = getNumberFromIntent(intent) ?: return
        if (number.isBlank()) return
        Log.d("PhoneZen", "Intent tel: reçu → $number")
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
        try {
            cacheContactName(number)

            val intent = Intent(Intent.ACTION_CALL).apply {
                data  = Uri.fromParts("tel", number, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: SecurityException) {
            startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.fromParts("tel", number, null)
            })
        } catch (e: Exception) {
            Log.e("PhoneZen", "Erreur appel : ${e.message}")
            e.printStackTrace()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // APPEL AVEC SIM SPÉCIFIQUE (dual SIM)
    // ══════════════════════════════════════════════════════════════
    @SuppressLint("MissingPermission")
    fun launchCallWithSim(number: String, subscriptionId: Int) {
        cacheContactName(number)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || subscriptionId < 0) {
            launchCall(number)
            return
        }
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val phoneAccountHandle = getPhoneAccountHandle(subscriptionId)

            if (phoneAccountHandle != null) {
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.fromParts("tel", number, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                }
                startActivity(intent)
                Log.d("PhoneZen", "Appel via SIM subscriptionId=$subscriptionId : $number")
            } else {
                launchCall(number)
            }
        } catch (e: Exception) {
            Log.e("PhoneZen", "Erreur appel SIM spécifique : ${e.message}")
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
                val subId   = extras?.getInt(
                    "android.telecom.extra.SUBSCRIPTION_ID", -1
                ) ?: -1
                subId == subscriptionId
            }
        } catch (e: Exception) {
            Log.e("PhoneZen", "Erreur getPhoneAccountHandle : ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    // GESTION DU CACHE DES CONTACTS
    // ══════════════════════════════════════════════════════════════
    private fun cacheContactName(number: String) {
        if (ContactCache.get(number) != null) return
        val name = lookupContactName(number)
        if (name != null) {
            ContactCache.put(number, name)
            Log.d("PhoneZen", "Contact mis en cache: $name → $number")
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
                Log.d("PhoneZen", "ContactCache préchargé avec succès")
            } catch (e: Exception) {
                Log.e("PhoneZen", "Erreur préchargement cache : ${e.message}")
            }
        }.start()
    }

    // ══════════════════════════════════════════════════════════════
    // MESSAGERIE VOCALE
    // ══════════════════════════════════════════════════════════════
    private fun launchVoicemail() {
        val number = getVoicemailNumber()
        Log.d("PhoneZen", "Messagerie: $number (${getCarrierName()})")
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
            Log.e("PhoneZen", "Erreur getSystemVoicemailNumber : ${e.message}")
            null
        }
    }

    private fun getCarrierName(): String? {
        return try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName.takeIf { it.isNotBlank() }
                ?: tm.simOperatorName.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("PhoneZen", "Erreur getCarrierName : ${e.message}")
            null
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PERMISSIONS & RÔLES
    // ══════════════════════════════════════════════════════════════
    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) proceedAfterPermissions()
        else permLauncher.launch(missing.toTypedArray())
    }

    private fun proceedAfterPermissions() {
        vm.loadData(this)
        preloadContactCache()
        checkAndRequestRoles()
    }

    private fun checkAndRequestRoles() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (rolesRequested) return
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            rolesRequested = true
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            return
        }
        if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            rolesRequested = true
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    override fun onResume() {
        super.onResume()
        if (rolesReady()) {
            vm.loadData(this)
        }
    }

    private fun rolesReady(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    companion object {
        private val VOICEMAIL_NUMBERS = mapOf(
            "orange" to "888", "sosh" to "888",
            "sfr" to "123", "red" to "123", "red by sfr" to "123",
            "bouygues" to "660", "bouygues telecom" to "660", "b&you" to "660",
            "free" to "666", "free mobile" to "666",
            "syma" to "888", "syma mobile" to "888", "youprice" to "888",
            "la poste mobile" to "123", "la poste" to "123",
            "prixtel" to "123", "coriolis" to "123",
            "réglo mobile" to "123", "réglo" to "123",
            "nrj mobile" to "660", "nrj" to "660",
            "cic mobile" to "660", "crédit mutuel mobile" to "660",
            "auchan telecom" to "660", "cdiscount mobile" to "660",
            "lebara" to "5765", "lycamobile" to "121"
        )
    }
}