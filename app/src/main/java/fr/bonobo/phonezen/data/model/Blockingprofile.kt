// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

enum class BlockingProfile(val id: String, val emoji: String, val label: String, val subtitle: String) {
    WORK(
        id       = "work",
        emoji    = "🏢",
        label    = "Travail",
        subtitle = "Contacts + liste blanche + numéros pro (08/09)"
    ),
    HOME(
        id       = "home",
        emoji    = "🏠",
        label    = "Domicile",
        subtitle = "Contacts + liste blanche uniquement"
    ),
    VACATION(
        id       = "vacation",
        emoji    = "🌴",
        label    = "Vacances",
        subtitle = "Favoris + liste blanche · retour auto programmable"
    );

    companion object {
        fun fromId(id: String): BlockingProfile =
            entries.firstOrNull { it.id == id } ?: HOME
    }
}

data class VacationConfig(
    val endTimestamp  : Long           = -1L,
    val autoNightDnd  : Boolean        = false,
    val nightStart    : Int            = 22,
    val nightEnd      : Int            = 9,
    /** Profil activé automatiquement à la date de retour */
    val returnProfile : BlockingProfile = BlockingProfile.HOME
) {
    val hasEndDate : Boolean get() = endTimestamp > 0L
    val isExpired  : Boolean get() = hasEndDate && System.currentTimeMillis() > endTimestamp
}