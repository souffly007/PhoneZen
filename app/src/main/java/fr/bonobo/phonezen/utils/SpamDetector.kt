// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.content.Context
import fr.bonobo.phonezen.PhoneZenApp
import fr.bonobo.phonezen.blocking.HospitalWhitelistManager
import fr.bonobo.phonezen.data.model.RiskLevel
import fr.bonobo.phonezen.data.model.SpamResult
import org.json.JSONObject
import android.util.Log
import java.util.Calendar

class SpamDetector(context: Context) {

    private val prefs                    = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)
    private val profileManager           = ProfileManager(context)
    private val hospitalWhitelistManager : HospitalWhitelistManager =
        (context.applicationContext as PhoneZenApp).hospitalWhitelistManager

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

                root.optJSONObject("PRIORITY_1_SAFE_LIST")?.let { p1 ->
                    p1.optJSONArray("numbers")?.let { arr ->
                        for (i in 0 until arr.length()) neverBlock.add(arr.getString(i))
                    }
                    p1.optJSONArray("prefixes_gratuits")?.let { arr ->
                        for (i in 0 until arr.length()) neverBlock.add(arr.getString(i))
                    }
                }
                root.optJSONObject("PRIORITY_2_NPV_ARCEP")?.let { p2 ->
                    p2.optJSONArray("prefixes")?.let { arr ->
                        for (i in 0 until arr.length()) alwaysBlockPrefixes.add(arr.getString(i))
                    }
                }
                root.optJSONObject("PRIORITY_3_COMMERCIAL_FLOTTE")?.let { p3 ->
                    p3.optJSONArray("prefixes")?.let { arr ->
                        for (i in 0 until arr.length()) telemarketingPrefixes.add(arr.getString(i))
                    }
                }
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

    private fun norm(number: String?): String = PhoneUtils.normalizeNumber(number)

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

        CrashHandler.lastAnalyzedNumber = local
        CrashHandler.lastAction         = "SpamDetector.analyze: $local"

        // 2. Whitelist santé — PRIORITÉ ABSOLUE
        CrashHandler.lastAction = "SpamDetector: whitelist santé FINESS"
        if (hospitalWhitelistManager.isHospitalNumber(local) ||
            hospitalWhitelistManager.isHospitalNumber(intl)) {
            val name = hospitalWhitelistManager.getHospitalName(local)
                ?: hospitalWhitelistManager.getHospitalName(intl)
                ?: "Établissement de santé"
            return SpamResult(
                isSpam    = false,
                reason    = "Établissement de santé : $name",
                riskLevel = RiskLevel.NONE
            )
        }

        // 3. Liste blanche utilisateur
        CrashHandler.lastAction = "SpamDetector: liste blanche utilisateur"
        if (getWhitelist().any { norm(it) == local || norm(it) == intl }) {
            return SpamResult(isSpam = false, reason = "Liste blanche", riskLevel = RiskLevel.NONE)
        }

        // 4. Services d'urgence JSON
        CrashHandler.lastAction = "SpamDetector: services urgence"
        if (neverBlock.any { local == it || local == "0$it" || local.startsWith(it) }) {
            return SpamResult(isSpam = false, reason = "Numéro protégé", riskLevel = RiskLevel.NONE)
        }

        // 5. Blocage communautaire
        CrashHandler.lastAction = "SpamDetector: blocage communautaire"
        if (isCommunityBlocked(normalized)) {
            return SpamResult(
                isSpam    = true,
                reason    = "Signalé par la communauté",
                riskLevel = RiskLevel.HIGH
            )
        }

        // 6. DND nocturne automatique (profil Vacances)
        CrashHandler.lastAction = "SpamDetector: DND nocturne vacances"
        if (profileManager.isAutoNightDndActive()) {
            return SpamResult(isSpam = true, reason = "Mode nuit vacances actif", riskLevel = RiskLevel.HIGH)
        }

        // 7. Profil de blocage
        CrashHandler.lastAction = "SpamDetector: profil de blocage"
        val isProNumber     = profileManager.isProNumber(local)
        val profileDecision = profileManager.isAllowedByProfile(
            isContact   = isContact,
            isFavorite  = isFavorite,
            isProNumber = isProNumber
        )
        when (profileDecision) {
            true  -> return SpamResult(isSpam = false, reason = "Autorisé par profil ${profileManager.getActiveProfile().label}", riskLevel = RiskLevel.NONE)
            false -> return SpamResult(isSpam = true,  reason = "Bloqué par profil ${profileManager.getActiveProfile().label}",  riskLevel = RiskLevel.MEDIUM)
            null  -> { /* continuer */ }
        }

        // 8. Mode Ne pas déranger
        CrashHandler.lastAction = "SpamDetector: mode Ne pas déranger"
        if (isDoNotDisturbEnabled()) {
            if (isDndScheduleEnabled()) {
                if (isInDndSchedule()) {
                    return SpamResult(isSpam = true, reason = "Ne pas déranger (horaires)", riskLevel = RiskLevel.HIGH)
                }
            } else {
                return SpamResult(isSpam = true, reason = "Mode Ne pas déranger actif", riskLevel = RiskLevel.HIGH)
            }
        }

        // 9. Spoofing
        CrashHandler.lastAction = "SpamDetector: détection spoofing"
        val digits = normalized.filter { it.isDigit() }
        if (digits.length > 15) {
            return SpamResult(isSpam = true, reason = "Numéro invalide (Spoofing)", riskLevel = RiskLevel.CRITICAL)
        }

        // 10. Patterns regex
        CrashHandler.lastAction = "SpamDetector: patterns regex"
        for (pattern in alwaysBlockPatterns) {
            if (pattern.containsMatchIn(normalized)) {
                return SpamResult(isSpam = true, reason = "Pattern suspect", riskLevel = RiskLevel.CRITICAL)
            }
        }

        // 11. Préfixes ARCEP
        CrashHandler.lastAction = "SpamDetector: préfixes ARCEP"
        for (target in listOf(local, intl)) {
            if (alwaysBlockPrefixes.any { target.startsWith(it) })
                return SpamResult(isSpam = true, reason = "Numéro frauduleux connu (ARCEP)", riskLevel = RiskLevel.HIGH)
            if (telemarketingPrefixes.any { target.startsWith(it) })
                return SpamResult(isSpam = true, reason = "Démarchage commercial", riskLevel = RiskLevel.MEDIUM)
        }

        // 12. Horaires de blocage
        CrashHandler.lastAction = "SpamDetector: horaires de blocage"
        if (isInBlockingSchedule()) {
            return SpamResult(isSpam = true, reason = "Hors horaires autorisés", riskLevel = RiskLevel.MEDIUM)
        }

        CrashHandler.lastAction = "SpamDetector: terminé → pas de spam"
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

    fun setDndScheduleEnabled(enabled: Boolean) = prefs.edit().putBoolean("dnd_schedule_enabled", enabled).apply()
    fun isDndScheduleEnabled(): Boolean          = prefs.getBoolean("dnd_schedule_enabled", false)

    fun setDndStartHour(h: Int) = prefs.edit().putInt("dnd_start_hour", h).apply()
    fun getDndStartHour(): Int  = prefs.getInt("dnd_start_hour", 22)
    fun setDndEndHour(h: Int)   = prefs.edit().putInt("dnd_end_hour", h).apply()
    fun getDndEndHour(): Int    = prefs.getInt("dnd_end_hour", 8)

    fun isInDndSchedule(): Boolean {
        val now    = Calendar.getInstance()
        val nowH   = now.get(Calendar.HOUR_OF_DAY)
        val startH = getDndStartHour()
        val endH   = getDndEndHour()
        return if (startH > endH) nowH >= startH || nowH < endH
        else nowH in startH until endH
    }

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
