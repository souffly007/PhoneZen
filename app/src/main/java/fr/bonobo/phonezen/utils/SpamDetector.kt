package fr.bonobo.phonezen.utils

import android.content.Context
import fr.bonobo.phonezen.data.model.RiskLevel
import fr.bonobo.phonezen.data.model.SpamResult
import org.json.JSONObject
import android.util.Log
import java.util.Calendar

class SpamDetector(context: Context) {

    private val prefs          = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
    private val profileManager = ProfileManager(context)

    companion object {
        private val alwaysBlockPrefixes   = mutableListOf<String>()
        private val telemarketingPrefixes = mutableListOf<String>()
        private val alwaysBlockPatterns   = mutableListOf<Regex>()
        private val neverBlock            = mutableListOf<String>()
        private var isLoaded = false

        const val COMMUNITY_BLOCK_THRESHOLD = 10L

        private fun loadJson(context: Context) {
            if (isLoaded) return
            try {
                val json = context.assets.open("prefixes_blocked_fr.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(json)
                alwaysBlockPrefixes.clear()
                telemarketingPrefixes.clear()
                alwaysBlockPatterns.clear()
                neverBlock.clear()

                // ── PRIORITY_1 : Urgences & numéros protégés → never_block ──
                root.optJSONObject("PRIORITY_1_SAFE_LIST")?.let { p1 ->
                    p1.optJSONArray("numbers")?.let { arr ->
                        for (i in 0 until arr.length()) neverBlock.add(arr.getString(i))
                    }
                    p1.optJSONArray("prefixes_gratuits")?.let { arr ->
                        for (i in 0 until arr.length()) neverBlock.add(arr.getString(i))
                    }
                }

                // ── PRIORITY_2 : NPV ARCEP stricts → always_block ──
                root.optJSONObject("PRIORITY_2_NPV_ARCEP")?.let { p2 ->
                    p2.optJSONArray("prefixes")?.let { arr ->
                        for (i in 0 until arr.length()) alwaysBlockPrefixes.add(arr.getString(i))
                    }
                }

                // ── PRIORITY_3 : VoIP télémarketing → telemarketing ──
                root.optJSONObject("PRIORITY_3_COMMERCIAL_FLOTTE")?.let { p3 ->
                    p3.optJSONArray("prefixes")?.let { arr ->
                        for (i in 0 until arr.length()) telemarketingPrefixes.add(arr.getString(i))
                    }
                }

                // ── Ancienne structure conservée pour rétrocompatibilité ──
                root.optJSONArray("always_block_prefixes")?.let { arr ->
                    for (i in 0 until arr.length()) alwaysBlockPrefixes.add(arr.getString(i))
                }
                root.optJSONArray("telemarketing_prefixes")?.let { arr ->
                    for (i in 0 until arr.length()) telemarketingPrefixes.add(arr.getString(i))
                }
                root.optJSONArray("always_block_patterns")?.let { arr ->
                    for (i in 0 until arr.length()) alwaysBlockPatterns.add(Regex(arr.getString(i)))
                }
                root.optJSONArray("never_block")?.let { arr ->
                    for (i in 0 until arr.length()) neverBlock.add(arr.getString(i))
                }

                isLoaded = true
                Log.d("SpamDetector", "Base ARCEP chargée : " +
                        "${alwaysBlockPrefixes.size} bloqués stricts, " +
                        "${telemarketingPrefixes.size} télémarketing, " +
                        "${neverBlock.size} protégés, " +
                        "${alwaysBlockPatterns.size} patterns")
            } catch (e: Exception) {
                Log.e("SpamDetector", "Erreur assets: ${e.message}")
            }
        }
    }

    init { loadJson(context) }

    // ─────────────────────────────────────────────
    // NORMALISATION UNIFIÉE
    // ─────────────────────────────────────────────
    private fun norm(number: String?): String =
        PhoneUtils.normalizeNumber(number)

    // ─────────────────────────────────────────────
    // ANALYSE PRINCIPALE
    // Ordre de priorité :
    //   1. Numéro masqué
    //   2. Normalisation
    //   3. Liste blanche (priorité absolue)
    //   4. Services d'urgence
    //   5. Blocage communautaire (signalé ≥ 10×)   ← AJOUTÉ
    //   6. DND nocturne automatique (profil Vacances)
    //   7. Profil de blocage (contact ? favori ? pro ?)
    //   8. Mode Ne pas déranger
    //   9. Spoofing / patterns
    //  10. Préfixes ARCEP
    //  11. Horaires de blocage
    // ─────────────────────────────────────────────
    fun analyze(rawNumber: String?, isContact: Boolean = false, isFavorite: Boolean = false): SpamResult {

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

        val normalized = norm(rawNumber)
        val local      = normalized
        val intl       = toInternationalFormat(normalized)

        // 2. Liste blanche → jamais bloquer (priorité absolue)
        if (getWhitelist().any { norm(it) == local || norm(it) == intl }) {
            return SpamResult(isSpam = false, reason = "Liste blanche", riskLevel = RiskLevel.NONE)
        }

        // 3. Services d'urgence → jamais bloquer
        if (neverBlock.any { local == it || local == "0$it" || local.startsWith(it) }) {
            return SpamResult(isSpam = false, reason = "Numéro protégé", riskLevel = RiskLevel.NONE)
        }

        // 4. Blocage communautaire (signalé ≥ COMMUNITY_BLOCK_THRESHOLD fois)
        //    Placé après la safe list pour ne jamais bloquer un numéro d'urgence,
        //    mais avant le profil pour bloquer même si le profil serait permissif.
        if (isCommunityBlocked(normalized)) {
            return SpamResult(
                isSpam    = true,
                reason    = "Signalé par la communauté",
                riskLevel = RiskLevel.HIGH
            )
        }

        // 5. DND nocturne automatique (profil Vacances uniquement)
        if (profileManager.isAutoNightDndActive()) {
            return SpamResult(isSpam = true, reason = "Mode nuit vacances actif", riskLevel = RiskLevel.HIGH)
        }

        // 6. Logique profil de blocage
        val isProNumber     = profileManager.isProNumber(local)
        val profileDecision = profileManager.isAllowedByProfile(
            isContact   = isContact,
            isFavorite  = isFavorite,
            isProNumber = isProNumber
        )
        when (profileDecision) {
            true  -> return SpamResult(isSpam = false, reason = "Autorisé par profil ${profileManager.getActiveProfile().label}", riskLevel = RiskLevel.NONE)
            false -> return SpamResult(isSpam = true,  reason = "Bloqué par profil ${profileManager.getActiveProfile().label}",  riskLevel = RiskLevel.MEDIUM)
            null  -> { /* continuer l'analyse normale */ }
        }

        // 7. Mode Ne pas déranger global
        if (isDoNotDisturbEnabled()) {
            return SpamResult(isSpam = true, reason = "Mode Ne pas déranger actif", riskLevel = RiskLevel.HIGH)
        }

        // 8. Spoofing — numéro anormalement long
        val digits = normalized.filter { it.isDigit() }
        if (digits.length > 15) {
            return SpamResult(isSpam = true, reason = "Numéro invalide (Spoofing)", riskLevel = RiskLevel.CRITICAL)
        }

        // 9. Patterns regex
        for (pattern in alwaysBlockPatterns) {
            if (pattern.containsMatchIn(normalized)) {
                return SpamResult(isSpam = true, reason = "Pattern suspect", riskLevel = RiskLevel.CRITICAL)
            }
        }

        // 10. Préfixes ARCEP — on teste les deux formats (local 0X et international +33X)
        for (target in listOf(local, intl)) {
            if (alwaysBlockPrefixes.any { target.startsWith(it) })
                return SpamResult(isSpam = true, reason = "Numéro frauduleux connu (ARCEP)", riskLevel = RiskLevel.HIGH)
            if (telemarketingPrefixes.any { target.startsWith(it) })
                return SpamResult(isSpam = true, reason = "Démarchage commercial", riskLevel = RiskLevel.MEDIUM)
        }

        // 11. Horaires de blocage
        if (isInBlockingSchedule()) {
            return SpamResult(isSpam = true, reason = "Hors horaires autorisés", riskLevel = RiskLevel.MEDIUM)
        }

        return SpamResult(isSpam = false, riskLevel = RiskLevel.NONE)
    }

    // ─────────────────────────────────────────────
    // BLOCAGE COMMUNAUTAIRE
    // ─────────────────────────────────────────────
    fun isCommunityBlockEnabled(): Boolean   = prefs.getBoolean("community_block_enabled", true)
    fun setCommunityBlockEnabled(e: Boolean) = prefs.edit().putBoolean("community_block_enabled", e).apply()

    fun getCommunityBlockedNumbers(): Set<String> =
        prefs.getStringSet("community_blocked", emptySet()) ?: emptySet()

    fun setCommunityBlockedNumbers(numbers: Set<String>) =
        prefs.edit().putStringSet("community_blocked", numbers).apply()

    fun addCommunityBlocked(number: String) {
        val set = getCommunityBlockedNumbers().toMutableSet()
        set.add(norm(number))
        prefs.edit().putStringSet("community_blocked", set).apply()
    }

    fun removeCommunityBlocked(number: String) {
        val set = getCommunityBlockedNumbers().toMutableSet()
        set.remove(norm(number))
        prefs.edit().putStringSet("community_blocked", set).apply()
    }

    fun isCommunityBlocked(number: String): Boolean {
        if (!isCommunityBlockEnabled()) return false
        val n = norm(number)
        return getCommunityBlockedNumbers().any { norm(it) == n }
    }

    // ─────────────────────────────────────────────
    // LISTE BLANCHE
    // ─────────────────────────────────────────────
    fun getWhitelist(): Set<String> =
        prefs.getStringSet("whitelist", emptySet()) ?: emptySet()

    fun addToWhitelist(number: String) {
        val list = getWhitelist().toMutableSet()
        list.add(norm(number))
        prefs.edit().putStringSet("whitelist", list).apply()
    }

    fun removeFromWhitelist(number: String) {
        val list = getWhitelist().toMutableSet()
        list.remove(norm(number))
        prefs.edit().putStringSet("whitelist", list).apply()
    }

    fun isWhitelisted(number: String): Boolean {
        val n = norm(number)
        return getWhitelist().any { norm(it) == n }
    }

    // ─────────────────────────────────────────────
    // HORAIRES DE BLOCAGE
    // ─────────────────────────────────────────────
    fun setScheduleEnabled(enabled: Boolean)  = prefs.edit().putBoolean("schedule_enabled", enabled).apply()
    fun isScheduleEnabled(): Boolean          = prefs.getBoolean("schedule_enabled", false)
    fun setScheduleStartHour(h: Int)          = prefs.edit().putInt("schedule_start_hour", h).apply()
    fun getScheduleStartHour(): Int           = prefs.getInt("schedule_start_hour", 22)
    fun setScheduleStartMinute(m: Int)        = prefs.edit().putInt("schedule_start_minute", m).apply()
    fun getScheduleStartMinute(): Int         = prefs.getInt("schedule_start_minute", 0)
    fun setScheduleEndHour(h: Int)            = prefs.edit().putInt("schedule_end_hour", h).apply()
    fun getScheduleEndHour(): Int             = prefs.getInt("schedule_end_hour", 8)
    fun setScheduleEndMinute(m: Int)          = prefs.edit().putInt("schedule_end_minute", m).apply()
    fun getScheduleEndMinute(): Int           = prefs.getInt("schedule_end_minute", 0)

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
    fun setDoNotDisturb(enabled: Boolean) = prefs.edit().putBoolean("do_not_disturb", enabled).apply()
    fun isDoNotDisturbEnabled(): Boolean  = prefs.getBoolean("do_not_disturb", false)

    // ─────────────────────────────────────────────
    // NUMÉROS PRIVÉS
    // ─────────────────────────────────────────────
    fun setBlockPrivateNumbers(block: Boolean) = prefs.edit().putBoolean("block_private_numbers", block).apply()
    fun isBlockPrivateEnabled(): Boolean       = prefs.getBoolean("block_private_numbers", false)

    // ─────────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ─────────────────────────────────────────────
    private fun isAnonymous(num: String): Boolean {
        val n = num.lowercase()
        return n == "-1" || n == "-2" || n == "unknown" || n == "private" ||
                n == "hidden" || n.contains("anonymous")
    }

    private fun toInternationalFormat(num: String): String {
        if (num.startsWith("0") && !num.startsWith("00")) return "+33" + num.substring(1)
        return num
    }
}