// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.content.Context
import fr.bonobo.phonezen.data.model.BlockingProfile
import fr.bonobo.phonezen.data.model.VacationConfig

class ProfileManager(context: Context) {

    private val prefs = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)

    // ─────────────────────────────────────────────
    // PROFIL ACTIF
    // ─────────────────────────────────────────────

    fun getActiveProfile(): BlockingProfile {
        val profile = BlockingProfile.fromId(
            prefs.getString("active_profile", BlockingProfile.HOME.id) ?: BlockingProfile.HOME.id
        )
        // Expiration vacances → basculer sur le profil de retour configuré
        if (profile == BlockingProfile.VACATION && getVacationConfig().isExpired) {
            val returnProfile = getVacationConfig().returnProfile
            setActiveProfile(returnProfile)
            return returnProfile
        }
        return profile
    }

    fun setActiveProfile(profile: BlockingProfile) {
        prefs.edit().putString("active_profile", profile.id).apply()
    }

    // ─────────────────────────────────────────────
    // CONFIGURATION VACANCES
    // ─────────────────────────────────────────────

    fun getVacationConfig(): VacationConfig = VacationConfig(
        endTimestamp  = prefs.getLong("vacation_end_ts", -1L),
        autoNightDnd  = prefs.getBoolean("vacation_night_dnd", false),
        nightStart    = prefs.getInt("vacation_night_start", 22),
        nightEnd      = prefs.getInt("vacation_night_end", 9),
        returnProfile = BlockingProfile.fromId(
            prefs.getString("vacation_return_profile", BlockingProfile.HOME.id) ?: BlockingProfile.HOME.id
        )
    )

    fun saveVacationConfig(config: VacationConfig) {
        prefs.edit()
            .putLong("vacation_end_ts",             config.endTimestamp)
            .putBoolean("vacation_night_dnd",        config.autoNightDnd)
            .putInt("vacation_night_start",          config.nightStart)
            .putInt("vacation_night_end",            config.nightEnd)
            .putString("vacation_return_profile",    config.returnProfile.id)
            .apply()
    }

    fun clearVacationEndDate() {
        prefs.edit().putLong("vacation_end_ts", -1L).apply()
    }

    // ─────────────────────────────────────────────
    // LOGIQUE DE DÉCISION PAR PROFIL
    // ─────────────────────────────────────────────

    fun isAllowedByProfile(
        isContact  : Boolean,
        isFavorite : Boolean,
        isProNumber: Boolean
    ): Boolean? {
        return when (getActiveProfile()) {
            BlockingProfile.WORK ->
                if (isContact || isFavorite || isProNumber) true else null

            BlockingProfile.HOME ->
                if (isContact || isFavorite) true else null

            BlockingProfile.VACATION ->
                if (isFavorite) true
                else if (isContact) false
                else null
        }
    }

    fun isAutoNightDndActive(): Boolean {
        if (getActiveProfile() != BlockingProfile.VACATION) return false
        val config = getVacationConfig()
        if (!config.autoNightDnd) return false

        val now    = java.util.Calendar.getInstance()
        val nowM   = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val startM = config.nightStart * 60
        val endM   = config.nightEnd   * 60

        return if (startM > endM) nowM >= startM || nowM < endM
        else nowM in startM until endM
    }

    fun isProNumber(normalizedNumber: String): Boolean =
        normalizedNumber.startsWith("08") || normalizedNumber.startsWith("09")
}