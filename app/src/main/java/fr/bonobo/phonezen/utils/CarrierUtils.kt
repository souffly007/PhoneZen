package fr.bonobo.phonezen.util

import android.content.Context
import android.telephony.TelephonyManager

/**
 * Numéros de messagerie vocale par opérateur
 */
private val VOICEMAIL_NUMBERS = mapOf(
    // --- OPÉRATEURS NATIONAUX ---
    "orange" to "888",
    "sosh" to "888",
    "sfr" to "123",
    "red" to "123",
    "red by sfr" to "123",
    "bouygues" to "660",
    "bouygues telecom" to "660",
    "b&you" to "660",
    "free mobile" to "666",
    "free" to "666",

    // --- MVNO RÉSEAU SFR ---
    "la poste mobile" to "123",
    "la poste" to "123",
    "prixtel" to "123",
    "coriolis" to "123",
    "réglo mobile" to "123",
    "réglo" to "123",

    // --- MVNO RÉSEAU BOUYGUES ---
    "nrj mobile" to "660",
    "nrj" to "660",
    "cic mobile" to "660",
    "crédit mutuel mobile" to "660",
    "auchan telecom" to "660",
    "auchan" to "660",

    // --- MVNO RÉSEAU ORANGE ---
    "syma mobile" to "888",
    "syma" to "888",
    "youprice" to "888",

    // --- AUTRES MVNO ---
    "lebara" to "5765",
    "lycamobile" to "121"
)

private const val VOICEMAIL_FALLBACK = "123"

/**
 * Récupère le nom de l'opérateur depuis la carte SIM
 */
fun getCarrierName(context: Context): String? {
    return try {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Essayer d'abord le nom du réseau, puis le nom de la SIM
        val networkOperator = telephonyManager.networkOperatorName
        val simOperator = telephonyManager.simOperatorName

        (networkOperator.takeIf { it.isNotBlank() } ?: simOperator.takeIf { it.isNotBlank() })
    } catch (e: Exception) {
        null
    }
}

/**
 * Récupère le numéro de messagerie vocale configuré par l'opérateur
 */
fun getSystemVoicemailNumber(context: Context): String? {
    return try {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        telephonyManager.voiceMailNumber?.takeIf { it.isNotBlank() }
    } catch (e: SecurityException) {
        // Permission READ_PHONE_STATE non accordée
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Trouve le numéro de messagerie vocale pour l'opérateur actuel
 */
fun getVoicemailNumber(context: Context): String {
    // 1. Essayer d'abord le numéro configuré par le système
    getSystemVoicemailNumber(context)?.let { systemNumber ->
        if (systemNumber.isNotBlank() && systemNumber != "null") {
            return systemNumber
        }
    }

    // 2. Sinon, chercher par nom d'opérateur
    val carrierName = getCarrierName(context)?.lowercase()?.trim()

    if (carrierName != null) {
        // Recherche exacte
        VOICEMAIL_NUMBERS[carrierName]?.let { return it }

        // Recherche partielle (contient le mot-clé)
        VOICEMAIL_NUMBERS.entries.find { (key, _) ->
            carrierName.contains(key) || key.contains(carrierName)
        }?.let { return it.value }
    }

    // 3. Fallback
    return VOICEMAIL_FALLBACK
}

/**
 * Retourne des infos de debug sur l'opérateur
 */
fun getCarrierDebugInfo(context: Context): String {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    return buildString {
        appendLine("Opérateur réseau: ${telephonyManager.networkOperatorName}")
        appendLine("Opérateur SIM: ${telephonyManager.simOperatorName}")
        appendLine("Code réseau: ${telephonyManager.networkOperator}")
        appendLine("Code SIM: ${telephonyManager.simOperator}")
        try {
            appendLine("Messagerie système: ${telephonyManager.voiceMailNumber ?: "Non défini"}")
        } catch (e: SecurityException) {
            appendLine("Messagerie système: Permission refusée")
        }
        appendLine("Numéro utilisé: ${getVoicemailNumber(context)}")
    }
}