// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class CallTrustLevel {
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

sealed class CallTrustLevel {
    /** Contact enregistré ou whitelist santé FINESS */
    object Trusted : CallTrustLevel()
    /** 06/07 non enregistré dans les contacts — risque enregistrement vocal */
    object Suspicious : CallTrustLevel()
    /** Spam confirmé (SpamDetector) */
    object Spam : CallTrustLevel()
    /** Inconnu mais pas suspect */
    object Unknown : CallTrustLevel()
}