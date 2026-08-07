// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

import fr.bonobo.phonezen.data.model.CallUiState

/**
 * Statut d'appel tel que vu par l'UI.
 * Mappé depuis les états Telecom dans CallManager.fromState().
 */
enum class CallStatus {
    IDLE,
    RINGING,
    DIALING,
    ACTIVE,
    ON_HOLD,
    DISCONNECTED
}