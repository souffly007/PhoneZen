// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoiceDialHelper {
package fr.bonobo.phonezen.util

object VoiceDialHelper {

    // Préfixes vocaux reconnus (ordre important : les plus longs en premier)
    private val PREFIXES = listOf(
        "appelle-moi ",
        "appelez ",
        "appeler ",
        "appelle ",
        "appel à ",
        "appel ",
        "compose le ",
        "composez le ",
        "appelle le ",
        "appeler le ",
    )

    /**
     * Extrait le nom (ou numéro) depuis une commande vocale.
     * "Appelle Jacques Dupont" → "Jacques Dupont"
     * "appeler le 0612345678" → "0612345678"
     * Retourne null si aucun préfixe reconnu et le texte ne ressemble pas à un nom.
     */
    fun parseCommand(rawText: String): String? {
        val text = rawText.trim()
        val lower = text.lowercase()

        // Cherche un préfixe connu
        for (prefix in PREFIXES) {
            if (lower.startsWith(prefix)) {
                val result = text.substring(prefix.length).trim()
                return result.ifEmpty { null }
            }
        }

        // Pas de préfixe → on retourne quand même le texte brut
        // (l'utilisateur a peut-être juste dit le nom directement)
        return text.ifEmpty { null }
    }

    /**
     * Détermine si la commande ressemble à un numéro de téléphone.
     */
    fun isPhoneNumber(text: String): Boolean =
        text.filter { it.isDigit() || it == '+' }.length >= 6 &&
                text.all { it.isDigit() || it == '+' || it == ' ' }
}