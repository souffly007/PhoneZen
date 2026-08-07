// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

/**
 * Mode d'affichage de l'écran d'appel entrant/sortant.
 *
 * FULLSCREEN : InCallActivity plein écran (comportement actuel)
 * COMPACT    : carte en bas de l'écran (heads-up), WindowManager overlay
 * MINI       : barre flottante ~72dp tout en haut, WindowManager overlay
 */
enum class CallPopupMode(val label: String, val emoji: String) {
    FULLSCREEN("Plein écran", "📱"),
    COMPACT   ("Compact",     "🪟"),
    MINI      ("Mini",        "➖"),
}