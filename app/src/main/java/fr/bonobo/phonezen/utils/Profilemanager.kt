// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.content.Context
import fr.bonobo.phonezen.data.model.BlockingProfile
import fr.bonobo.phonezen.data.model.VacationConfig
import java.util.Calendar

class ProfileManager(context: Context) {

    private val prefs = context.getSharedPreferences("phonezen_prefs", Context.MODE_PRIVATE)

    fun getActiveProfile(): BlockingProfile =
        BlockingProfile.fromId(prefs.getString("profile_active", BlockingProfile.HOME.id) ?: BlockingProfile.HOME.id)

    fun setActiveProfile(profile: BlockingProfile) =
        prefs.edit().putString("profile_active", profile.id).apply()

    fun isDndEnabled(profile: BlockingProfile): Boolean =
        prefs.getBoolean("dnd_${profile.id}_enabled", false)

    fun setDndEnabled(profile: BlockingProfile, enabled: Boolean) =
        prefs.edit().putBoolean("dnd_${profile.id}_enabled", enabled).apply()

    fun getDndStart(profile: BlockingProfile): Int =
        prefs.getInt("dnd_${profile.id}_start", 22)

    fun setDndStart(profile: BlockingProfile, hour: Int) =
        prefs.edit().putInt("dnd_${profile.id}_start", hour).apply()

    fun getDndEnd(profile: BlockingProfile): Int =
        prefs.getInt("dnd_${profile.id}_end", 8)

    fun setDndEnd(profile: BlockingProfile, hour: Int) =
        prefs.edit().putInt("dnd_${profile.id}_end", hour).apply()

    fun getVacationConfig(): VacationConfig = VacationConfig(
        endTimestamp  = prefs.getLong("vacation_end_ts", -1L),
        autoNightDnd  = isDndEnabled(BlockingProfile.VACATION),
        nightStart    = getDndStart(BlockingProfile.VACATION),
        nightEnd      = getDndEnd(BlockingProfile.VACATION),
        returnProfile = BlockingProfile.fromId(
            prefs.getString("vacation_return_profile", BlockingProfile.HOME.id) ?: BlockingProfile.HOME.id
        )
    )

    fun saveVacationConfig(config: VacationConfig) {
        prefs.edit()
            .putLong("vacation_end_ts", config.endTimestamp)
            .putString("vacation_return_profile", config.returnProfile.id)
            .apply()
        setDndEnabled(BlockingProfile.VACATION, config.autoNightDnd)
        setDndStart(BlockingProfile.VACATION, config.nightStart)
        setDndEnd(BlockingProfile.VACATION, config.nightEnd)
    }

    fun clearVacationEndDate() =
        prefs.edit().putLong("vacation_end_ts", -1L).apply()

    fun isAutoNightDndActive(): Boolean {
        val profile = getActiveProfile()
        if (!isDndEnabled(profile)) return false
        val nowH   = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val startH = getDndStart(profile)
        val endH   = getDndEnd(profile)
        return if (startH > endH) nowH >= startH || nowH < endH
        else nowH in startH until endH
    }

    fun isAllowedByProfile(
        isContact   : Boolean,
        isFavorite  : Boolean,
        isProNumber : Boolean
    ): Boolean? = when (getActiveProfile()) {
        BlockingProfile.WORK -> when {
            isFavorite  -> true
            isContact   -> true
            isProNumber -> true
            else        -> null
        }
        BlockingProfile.HOME -> when {
            isFavorite -> true
            isContact  -> true
            else       -> false
        }
        BlockingProfile.VACATION -> when {
            isFavorite -> true
            else       -> false
        }
    }

    fun isProNumber(normalized: String): Boolean {
        val proPatterns = listOf("^0800", "^0805", "^0809", "^080", "^090")
        return proPatterns.any { normalized.matches(Regex("$it.*")) }
    }
}