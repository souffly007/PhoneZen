package fr.bonobo.phonezen.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

// ── Numéros de messagerie vocale par opérateur ──
private val VOICEMAIL_NUMBERS = mapOf(
    // Opérateurs nationaux
    "orange"               to "888",
    "sosh"                 to "888",
    "sfr"                  to "123",
    "red"                  to "123",
    "red by sfr"           to "123",
    "bouygues"             to "660",
    "bouygues telecom"     to "660",
    "b&you"                to "660",
    "free mobile"          to "666",
    "free"                 to "666",
    // MVNO SFR
    "la poste mobile"      to "123",
    "la poste"             to "123",
    "prixtel"              to "123",
    "coriolis"             to "123",
    "réglo mobile"         to "123",
    "réglo"                to "123",
    // MVNO Bouygues
    "nrj mobile"           to "660",
    "nrj"                  to "660",
    "cic mobile"           to "660",
    "crédit mutuel mobile" to "660",
    "auchan telecom"       to "660",
    "auchan"               to "660",
    // MVNO Orange
    "syma mobile"          to "888",
    "syma"                 to "888",
    "youprice"             to "888",
    // Autres MVNO
    "lebara"               to "5765",
    "lycamobile"           to "121"
)

private const val VOICEMAIL_FALLBACK = "123"

// ──────────────────────────────────────────
//  Modèle SIM
// ──────────────────────────────────────────
data class SimInfo(
    val slotIndex   : Int,      // 0 = SIM 1, 1 = SIM 2
    val label       : String,   // "SIM 1" ou "SIM 2"
    val carrierName : String,
    val voicemail   : String,
    val subscriptionId: Int = -1
)

// ──────────────────────────────────────────
//  Détection dual SIM
// ──────────────────────────────────────────

/**
 * Retourne la liste des SIMs actives (1 ou 2).
 * Nécessite READ_PHONE_STATE.
 */
@SuppressLint("MissingPermission")
fun getActiveSims(context: Context): List<SimInfo> {
    val sims = mutableListOf<SimInfo>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptions = sm.activeSubscriptionInfoList ?: emptyList()

            subscriptions.forEach { sub ->
                val tm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                        .createForSubscriptionId(sub.subscriptionId)
                } else {
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                }

                val carrierName = sub.carrierName?.toString()?.ifBlank { null }
                    ?: tm.networkOperatorName?.ifBlank { null }
                    ?: tm.simOperatorName?.ifBlank { null }
                    ?: "Opérateur inconnu"

                val voicemail = resolveVoicemail(context, tm, carrierName)

                sims.add(
                    SimInfo(
                        slotIndex      = sub.simSlotIndex,
                        label          = "SIM ${sub.simSlotIndex + 1}",
                        carrierName    = carrierName,
                        voicemail      = voicemail,
                        subscriptionId = sub.subscriptionId
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback mono-SIM
        }
    }

    // Fallback si SubscriptionManager indisponible ou vide
    if (sims.isEmpty()) {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val carrier = tm.networkOperatorName?.ifBlank { null }
            ?: tm.simOperatorName?.ifBlank { null }
            ?: "Opérateur inconnu"
        sims.add(
            SimInfo(
                slotIndex   = 0,
                label       = "SIM 1",
                carrierName = carrier,
                voicemail   = resolveVoicemail(context, tm, carrier)
            )
        )
    }

    return sims.sortedBy { it.slotIndex }
}

/**
 * Résout le numéro de messagerie pour un TelephonyManager donné.
 */
private fun resolveVoicemail(context: Context, tm: TelephonyManager, carrierName: String): String {
    // 1. Numéro système configuré par l'opérateur
    try {
        val systemNumber = tm.voiceMailNumber
        if (!systemNumber.isNullOrBlank() && systemNumber != "null") return systemNumber
    } catch (e: Exception) { /* permission refusée */ }

    // 2. Map par nom d'opérateur
    val name = carrierName.lowercase().trim()
    VOICEMAIL_NUMBERS[name]?.let { return it }
    VOICEMAIL_NUMBERS.entries.find { (key, _) ->
        name.contains(key) || key.contains(name)
    }?.let { return it.value }

    // 3. Fallback
    return VOICEMAIL_FALLBACK
}

// ──────────────────────────────────────────
//  API compatibilité mono-SIM (inchangée)
// ──────────────────────────────────────────

fun getCarrierName(context: Context): String? {
    return try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.networkOperatorName?.ifBlank { null } ?: tm.simOperatorName?.ifBlank { null }
    } catch (e: Exception) { null }
}

fun getSystemVoicemailNumber(context: Context): String? {
    return try {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tm.voiceMailNumber?.takeIf { it.isNotBlank() }
    } catch (e: Exception) { null }
}

fun getVoicemailNumber(context: Context): String {
    getSystemVoicemailNumber(context)?.let { if (it != "null") return it }
    val carrier = getCarrierName(context)?.lowercase()?.trim() ?: return VOICEMAIL_FALLBACK
    VOICEMAIL_NUMBERS[carrier]?.let { return it }
    VOICEMAIL_NUMBERS.entries.find { (key, _) ->
        carrier.contains(key) || key.contains(carrier)
    }?.let { return it.value }
    return VOICEMAIL_FALLBACK
}

fun getCarrierDebugInfo(context: Context): String {
    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val sims = getActiveSims(context)
    return buildString {
        appendLine("=== Dual SIM Info ===")
        appendLine("Nombre de SIMs actives : ${sims.size}")
        sims.forEach { sim ->
            appendLine("--- ${sim.label} ---")
            appendLine("  Opérateur : ${sim.carrierName}")
            appendLine("  Messagerie : ${sim.voicemail}")
        }
        appendLine("=== TelephonyManager ===")
        appendLine("Réseau : ${tm.networkOperatorName}")
        appendLine("SIM : ${tm.simOperatorName}")
        try { appendLine("Messagerie système : ${tm.voiceMailNumber ?: "Non défini"}") }
        catch (e: Exception) { appendLine("Messagerie système : Permission refusée") }
    }
}