package fr.bonobo.phonezen.utils

import android.content.Context
import fr.bonobo.phonezen.data.model.RiskLevel
import fr.bonobo.phonezen.data.model.SpamResult
import org.json.JSONObject
import android.util.Log
import java.util.Calendar

class SpamDetector(context: Context) {

    private val prefs = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)

    companion object {
        private val alwaysBlockPrefixes   = mutableListOf<String>()
        private val telemarketingPrefixes = mutableListOf<String>()
        private val alwaysBlockPatterns   = mutableListOf<Regex>()
        private val neverBlock            = mutableListOf<String>()
        private var isLoaded = false

        /** Seuil de signalements communautaires pour blocage automatique */
        const val COMMUNITY_BLOCK_THRESHOLD = 10L

        private fun loadJson(context: Context) {
            if (isLoaded) return
            try {
                val json = context.assets.open("prefixes_blocked_fr.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                alwaysBlockPrefixes.clear(); telemarketingPrefixes.clear()
                alwaysBlockPatterns.clear(); neverBlock.clear()
                root.optJSONArray("always_block_prefixes")?.let  { arr -> for (i in 0 until arr.length()) alwaysBlockPrefixes.add(arr.getString(i)) }
                root.optJSONArray("telemarketing_prefixes")?.let { arr -> for (i in 0 until arr.length()) telemarketingPrefixes.add(arr.getString(i)) }
                root.optJSONArray("always_block_patterns")?.let  { arr -> for (i in 0 until arr.length()) alwaysBlockPatterns.add(Regex(arr.getString(i))) }
                root.optJSONArray("never_block")?.let            { arr -> for (i in 0 until arr.length()) neverBlock.add(arr.getString(i)) }
                isLoaded = true
                Log.d("SpamDetector", "Base ARCEP chargée")
            } catch (e: Exception) {
                Log.e("SpamDetector", "Erreur assets: ${e.message}")
            }
        }
    }

    init { loadJson(context) }

    // ─────────────────────────────────────────────
    // ANALYSE PRINCIPALE
    // ─────────────────────────────────────────────
    fun analyze(rawNumber: String?): SpamResult {

        // 1. Numéro masqué
        if (rawNumber.isNullOrBlank() || isAnonymous(rawNumber)) {
            val block = isBlockPrivateEnabled()
            return SpamResult(
                isSpam    = block,
                isPrivate = true,
                reason    = "Appel masqué",
                riskLevel = if (block) RiskLevel.HIGH else RiskLevel.NONE
            )
        }

        val clean  = rawNumber.replace(Regex("[^0-9+]"), "")
        val local  = toLocalFormat(clean)
        val intl   = toInternationalFormat(clean)

        // 2. Liste blanche → jamais bloquer (priorité absolue)
        if (getWhitelist().any { normalize(it) == normalize(local) || normalize(it) == normalize(intl) }) {
            return SpamResult(isSpam = false, reason = "Liste blanche", riskLevel = RiskLevel.NONE)
        }

        // 3. Services d'urgence → jamais bloquer
        if (neverBlock.any { local == it || local == "0$it" }) {
            return SpamResult(isSpam = false, reason = "Numéro protégé", riskLevel = RiskLevel.NONE)
        }

        // 4. Mode Ne pas déranger → bloque tout (sauf liste blanche et urgences déjà vérifiés)
        if (isDoNotDisturbEnabled()) {
            return SpamResult(isSpam = true, reason = "Mode Ne pas déranger actif", riskLevel = RiskLevel.HIGH)
        }

        // 5. Spoofing
        if (clean.filter { it.isDigit() }.length > 15) {
            return SpamResult(isSpam = true, reason = "Numéro invalide (Spoofing)", riskLevel = RiskLevel.CRITICAL)
        }

        // 6. Patterns regex
        for (pattern in alwaysBlockPatterns) {
            if (pattern.containsMatchIn(clean)) {
                return SpamResult(isSpam = true, reason = "Pattern suspect", riskLevel = RiskLevel.CRITICAL)
            }
        }

        // 7. Préfixes ARCEP
        val targets = listOf(local, intl)
        for (target in targets) {
            if (alwaysBlockPrefixes.any { target.startsWith(it) })
                return SpamResult(isSpam = true, reason = "Numéro frauduleux connu", riskLevel = RiskLevel.HIGH)
            if (telemarketingPrefixes.any { target.startsWith(it) })
                return SpamResult(isSpam = true, reason = "Démarchage commercial", riskLevel = RiskLevel.MEDIUM)
        }

        // 8. Horaires de blocage → inconnus non-spam hors plage
        if (isInBlockingSchedule()) {
            return SpamResult(isSpam = true, reason = "Hors horaires autorisés", riskLevel = RiskLevel.MEDIUM)
        }

        return SpamResult(isSpam = false, riskLevel = RiskLevel.NONE)
    }

    // ─────────────────────────────────────────────
    // BLOCAGE COMMUNAUTAIRE
    // ─────────────────────────────────────────────

    /**
     * Vérifie si le blocage automatique communautaire est activé.
     */
    fun isCommunityBlockEnabled(): Boolean =
        prefs.getBoolean("community_block_enabled", true)

    fun setCommunityBlockEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("community_block_enabled", enabled).apply()

    /**
     * Cache local des numéros bloqués par la communauté.
     * Mis à jour par MainViewModel.checkReportedNumbers() au chargement.
     * Utilisé par le CallScreeningService pour éviter un appel Firestore
     * à chaque appel entrant.
     */
    fun getCommunityBlockedNumbers(): Set<String> =
        prefs.getStringSet("community_blocked", emptySet()) ?: emptySet()

    fun setCommunityBlockedNumbers(numbers: Set<String>) =
        prefs.edit().putStringSet("community_blocked", numbers).apply()

    fun addCommunityBlocked(number: String) {
        val set = getCommunityBlockedNumbers().toMutableSet()
        set.add(normalize(number))
        prefs.edit().putStringSet("community_blocked", set).apply()
    }

    fun removeCommunityBlocked(number: String) {
        val set = getCommunityBlockedNumbers().toMutableSet()
        set.remove(normalize(number))
        prefs.edit().putStringSet("community_blocked", set).apply()
    }

    /**
     * Vérifie si un numéro est dans le cache communautaire local.
     */
    fun isCommunityBlocked(number: String): Boolean {
        if (!isCommunityBlockEnabled()) return false
        val clean = normalize(toLocalFormat(number.replace(Regex("[^0-9+]"), "")))
        val intl  = normalize(toInternationalFormat(number.replace(Regex("[^0-9+]"), "")))
        return getCommunityBlockedNumbers().any { it == clean || it == intl }
    }

    // ─────────────────────────────────────────────
    // LISTE BLANCHE
    // ─────────────────────────────────────────────
    fun getWhitelist(): Set<String> =
        prefs.getStringSet("whitelist", emptySet()) ?: emptySet()

    fun addToWhitelist(number: String) {
        val list = getWhitelist().toMutableSet()
        list.add(normalize(number))
        prefs.edit().putStringSet("whitelist", list).apply()
    }

    fun removeFromWhitelist(number: String) {
        val list = getWhitelist().toMutableSet()
        list.remove(normalize(number))
        prefs.edit().putStringSet("whitelist", list).apply()
    }

    fun isWhitelisted(number: String): Boolean {
        val n = normalize(toLocalFormat(number.replace(Regex("[^0-9+]"), "")))
        return getWhitelist().any { normalize(it) == n }
    }

    // ─────────────────────────────────────────────
    // HORAIRES DE BLOCAGE
    // ─────────────────────────────────────────────
    fun setScheduleEnabled(enabled: Boolean) =
        prefs.edit().putBoolean("schedule_enabled", enabled).apply()
    fun isScheduleEnabled(): Boolean =
        prefs.getBoolean("schedule_enabled", false)

    fun setScheduleStartHour(h: Int)   = prefs.edit().putInt("schedule_start_hour", h).apply()
    fun getScheduleStartHour(): Int    = prefs.getInt("schedule_start_hour", 22)
    fun setScheduleStartMinute(m: Int) = prefs.edit().putInt("schedule_start_minute", m).apply()
    fun getScheduleStartMinute(): Int  = prefs.getInt("schedule_start_minute", 0)

    fun setScheduleEndHour(h: Int)     = prefs.edit().putInt("schedule_end_hour", h).apply()
    fun getScheduleEndHour(): Int      = prefs.getInt("schedule_end_hour", 8)
    fun setScheduleEndMinute(m: Int)   = prefs.edit().putInt("schedule_end_minute", m).apply()
    fun getScheduleEndMinute(): Int    = prefs.getInt("schedule_end_minute", 0)

    fun isInBlockingSchedule(): Boolean {
        if (!isScheduleEnabled()) return false
        val now    = Calendar.getInstance()
        val nowM   = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startM = getScheduleStartHour() * 60 + getScheduleStartMinute()
        val endM   = getScheduleEndHour()   * 60 + getScheduleEndMinute()
        return if (startM > endM) nowM >= startM || nowM < endM
        else nowM in startM until endM
    }

    // ─────────────────────────────────────────────
    // MODE NE PAS DÉRANGER
    // ─────────────────────────────────────────────
    fun setDoNotDisturb(enabled: Boolean) =
        prefs.edit().putBoolean("do_not_disturb", enabled).apply()
    fun isDoNotDisturbEnabled(): Boolean =
        prefs.getBoolean("do_not_disturb", false)

    // ─────────────────────────────────────────────
    // NUMÉROS PRIVÉS
    // ─────────────────────────────────────────────
    fun setBlockPrivateNumbers(block: Boolean) =
        prefs.edit().putBoolean("block_private_numbers", block).apply()
    fun isBlockPrivateEnabled(): Boolean =
        prefs.getBoolean("block_private_numbers", false)

    // ─────────────────────────────────────────────
    // UTILITAIRES
    // ─────────────────────────────────────────────
    private fun isAnonymous(num: String): Boolean {
        val n = num.lowercase()
        return n == "-1" || n == "-2" || n == "unknown" || n == "private" || n == "hidden" || n.contains("anonymous")
    }
    private fun toLocalFormat(num: String): String {
        var n = num
        if (n.startsWith("+33")) n = "0" + n.substring(3)
        if (n.startsWith("0033")) n = "0" + n.substring(4)
        return n
    }
    private fun toInternationalFormat(num: String): String {
        if (num.startsWith("0") && !num.startsWith("00")) return "+33" + num.substring(1)
        return num
    }
    private fun normalize(num: String): String = num.replace(Regex("[\\s.\\-()]"), "")
}