// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

data class CallUiState(
    val status        : CallStatus     = CallStatus.IDLE,
    val number        : String         = "",
    val contactName   : String?        = null,
    val isOnHold      : Boolean        = false,
    val isMuted       : Boolean        = false,
    val isBtAvailable : Boolean        = false,
    val audioRoute    : AudioRoute     = AudioRoute.EARPIECE,
    val isSpam        : Boolean        = false,
    val spamReason    : String?        = null,
    val durationSec   : Long           = 0,
    val trustLevel    : CallTrustLevel = CallTrustLevel.Unknown,
    /** Informations ARCEP sur le numéro (null = lookup pas encore résolu) */
    val arcepInfo     : ArcepInfo?     = null,
)

/**
 * Sous-ensemble des données ARCEP destiné à l'affichage dans l'UI d'appel.
 */
data class ArcepInfo(
    val operateur        : String,
    val categorie        : String,
    val territoire       : String,
    val isSuspiciousType : Boolean = false,
) {
    /** Ex : "SFR · Mobile" */
    val displayLine: String
        get() = buildString {
            append(operateur)
            if (categorie.isNotBlank()) append(" · $categorie")
        }

    /** Territoire DOM/TOM uniquement — on masque "Métropole" et "National" */
    val displayTerritory: String?
        get() = territoire.takeIf {
            it.isNotBlank() && it != "Métropole" && it != "National"
        }
}