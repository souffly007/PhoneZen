// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.utils

import android.content.Context
import android.util.LruCache

object ContactResolver {

    private const val CACHE_SIZE = 300

    // LruCache n'accepte pas les valeurs null.
    // On utilise une sentinel string pour représenter "numéro inconnu".
    private const val NOT_FOUND = "\u0000"

    private val cache = object : LruCache<String, String>(CACHE_SIZE) {}

    fun resolveName(context: Context, rawNumber: String?): String? {
        val number = rawNumber?.trim().orEmpty()
        if (number.isBlank()) return null

        val normalized = PhoneUtils.normalizeNumber(number)

        // Vérifier le cache — NOT_FOUND signifie "déjà cherché, pas trouvé"
        cache.get(normalized)?.let { cached ->
            return if (cached == NOT_FOUND) null else cached
        }

        val name = try {
            PhoneUtils.lookupContactName(context, normalized)
        } catch (e: Exception) {
            null
        }

        // Stocker NOT_FOUND au lieu de null pour éviter le NullPointerException
        cache.put(normalized, name ?: NOT_FOUND)
        return name
    }

    fun displayName(context: Context, rawNumber: String?): String {
        val number = rawNumber?.trim().orEmpty()
        return resolveName(context, number) ?: number.ifBlank { "Numéro inconnu" }
    }

    fun clear() {
        cache.evictAll()
    }
}