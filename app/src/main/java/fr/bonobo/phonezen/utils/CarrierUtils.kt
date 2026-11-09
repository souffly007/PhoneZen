package fr.bonobo.phonezen.util

import fr.bonobo.phonezen.R

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.InputType
import android.widget.EditText
import android.widget.Toast

data class SimInfo(
    val slotIndex: Int,
    val label: String,
    val carrierName: String,
    val voicemail: String,
    val subscriptionId: Int = -1
)

@SuppressLint("MissingPermission")
fun getActiveSims(context: Context): List<SimInfo> = try {
    val sm = context.getSystemService(SubscriptionManager::class.java)
    val tm = context.getSystemService(TelephonyManager::class.java)
    sm?.activeSubscriptionInfoList.orEmpty().map { sub ->
        val scoped = tm.createForSubscriptionId(sub.subscriptionId)
        val carrier = sub.carrierName?.toString().orEmpty().ifBlank { context.getString(R.string.carrier) }
        val manual = context.getSharedPreferences("voicemail_numbers", Context.MODE_PRIVATE)
            .getString("sub_${sub.subscriptionId}", null)
        val detected = runCatching { scoped.voiceMailNumber }.getOrNull()
            ?.takeUnless { it.isBlank() || it == "null" }.orEmpty()
        SimInfo(sub.simSlotIndex, "SIM ${sub.simSlotIndex + 1}", carrier,
            manual ?: detected, sub.subscriptionId)
    }.sortedBy { it.slotIndex }
} catch (_: Exception) { emptyList() }

@SuppressLint("MissingPermission")
fun findPhoneAccount(context: Context, subscriptionId: Int): PhoneAccountHandle? {
    if (subscriptionId < 0) return null
    val telecom = context.getSystemService(TelecomManager::class.java)
    val tm = context.getSystemService(TelephonyManager::class.java)
    return runCatching {
        telecom.callCapablePhoneAccounts.firstOrNull { handle ->
            if (Build.VERSION.SDK_INT >= 30) {
                tm.createForPhoneAccountHandle(handle)?.subscriptionId == subscriptionId
            } else {
                telecom.getPhoneAccount(handle)?.extras
                    ?.getInt("android.telecom.extra.SUBSCRIPTION_ID", -1) == subscriptionId
            }
        }
    }.getOrNull()
}

fun configureVoicemail(context: Context, sim: SimInfo, onSaved: () -> Unit = {}) {
    val input = EditText(context).apply {
        inputType = InputType.TYPE_CLASS_PHONE
        hint = context.getString(R.string.vm_number)
        setText(sim.voicemail)
    }
    val prefs = context.getSharedPreferences("voicemail_numbers", Context.MODE_PRIVATE)
    val dialog = AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.vm_title, sim.label, sim.carrierName))
        .setMessage(context.getString(R.string.vm_explanation))
        .setView(input)
        .setPositiveButton(context.getString(R.string.save), null)
        .setNeutralButton(context.getString(R.string.automatic)) { _, _ ->
            prefs.edit().remove("sub_${sim.subscriptionId}").apply()
            onSaved()
        }
        .setNegativeButton(context.getString(R.string.cancel), null).create()
    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val number = input.text.toString().replace(Regex("[\\s().-]"), "")
            if (!number.matches(Regex("\\+?[0-9]{1,20}"))) {
                input.error = context.getString(R.string.invalid_number)
            } else {
                prefs.edit().putString("sub_${sim.subscriptionId}", number).apply()
                onSaved()
                dialog.dismiss()
            }
        }
    }
    dialog.show()
}

fun showVoicemailSettings(context: Context, onSaved: () -> Unit = {}) {
    val sims = getActiveSims(context)
    if (sims.isEmpty()) {
        Toast.makeText(context, context.getString(R.string.no_sim), Toast.LENGTH_LONG).show()
        return
    }
    if (sims.size == 1) configureVoicemail(context, sims.first(), onSaved)
    else AlertDialog.Builder(context).setTitle(context.getString(R.string.configure_vm))
        .setItems(sims.map { "${it.label} · ${it.carrierName}" }.toTypedArray()) { _, i ->
            configureVoicemail(context, sims[i], onSaved)
        }.show()
}

fun callVoicemail(context: Context, sim: SimInfo, call: (String, Int) -> Unit) {
    // Re-read after settings, permissions or eSIM changes; never use an obsolete snapshot.
    val current = getActiveSims(context).firstOrNull { it.subscriptionId == sim.subscriptionId }
    if (current == null) {
        Toast.makeText(context, context.getString(R.string.sim_gone), Toast.LENGTH_LONG).show()
    } else if (current.voicemail.isBlank()) configureVoicemail(context, current)
    else call(current.voicemail, current.subscriptionId)
}

fun getCarrierName(context: Context): String? = getActiveSims(context).firstOrNull()?.carrierName
fun getSystemVoicemailNumber(context: Context): String? = runCatching {
    context.getSystemService(TelephonyManager::class.java).voiceMailNumber
}.getOrNull()?.takeUnless { it.isBlank() || it == "null" }
fun getVoicemailNumber(context: Context): String = getActiveSims(context).firstOrNull()?.voicemail.orEmpty()
fun getCarrierDebugInfo(context: Context): String = getActiveSims(context).joinToString("\n") {
    "${it.label} · ${it.carrierName} · ${it.voicemail.ifBlank { context.getString(R.string.not_configured) }}"
}
