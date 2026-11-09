package fr.bonobo.phonezen.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** All values live in the existing backed-up preferences. No GPS or language inference. */
class CountrySettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
    private val telecom get() = context.getSystemService(TelecomManager::class.java)
    private fun key(handle: PhoneAccountHandle?) = if (handle == null) "number_country_default"
        else "number_country_account_${handle.componentName.flattenToString()}/${handle.id}"

    fun manual(handle: PhoneAccountHandle? = null): String? = InternationalNumbers.region(prefs.getString(key(handle), null))
    fun set(handle: PhoneAccountHandle?, country: String?) {
        val value = InternationalNumbers.region(country)
        prefs.edit().apply { if (value == null) remove(key(handle)) else putString(key(handle), value) }.apply()
    }

    data class Line(val handle: PhoneAccountHandle, val label: String, val country: String?)

    @SuppressLint("MissingPermission")
    fun lines(): List<Line> = runCatching {
        telecom.callCapablePhoneAccounts.orEmpty().mapIndexed { i, handle ->
            Line(handle, telecom.getPhoneAccount(handle)?.label?.toString().orEmpty().ifBlank { "SIM ${i + 1}" }, forAccount(handle))
        }
    }.getOrDefault(emptyList())

    @SuppressLint("MissingPermission")
    fun forAccount(handle: PhoneAccountHandle?): String? {
        if (handle == null) return defaultRegion()
        manual(handle)?.let { return it }
        val detected = runCatching {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val scoped = if (Build.VERSION.SDK_INT >= 26) tm.createForPhoneAccountHandle(handle)
                else telecom.getPhoneAccount(handle)?.extras
                    ?.getInt("android.telecom.extra.SUBSCRIPTION_ID", -1)
                    ?.takeIf { it >= 0 }?.let { tm.createForSubscriptionId(it) }
            InternationalNumbers.region(scoped?.simCountryIso)
        }.getOrNull()
        return CountrySelection.forLine(manual(handle), detected, manual())
    }

    @SuppressLint("MissingPermission")
    fun defaultRegion(): String? {
        manual()?.let { return it }
        // Without a line, infer only when all active subscriptions agree.
        val countries = runCatching {
            context.getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList.orEmpty()
                .map { InternationalNumbers.region(it.countryIso) }
        }.getOrDefault(emptyList())
        return CountrySelection.withoutLine(null, countries)
    }

    fun forLog(accountId: String?, component: String?): String? {
        if (accountId.isNullOrBlank()) return defaultRegion()
        val candidates = lines().filter { it.handle.id == accountId &&
            (component.isNullOrBlank() || it.handle.componentName.flattenToString() == component) }
        return candidates.singleOrNull()?.country ?: manual()
    }
}
