// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entité Room représentant un établissement de santé whitelisté.
 *
 * Alimentée depuis Supabase (via [HealthcareRepository]) avec fallback
 * sur assets/hospitals_whitelist.json si aucune donnée en base.
 *
 * Champs :
 *  - [id]       : UUID Supabase (clé primaire)
 *  - [number]   : numéro exact normalisé (ex: "0140275757"), peut être null si entry = préfixe only
 *  - [prefix]   : préfixe 6 chiffres normalisé (ex: "014427"), peut être null si entry = numéro exact only
 *  - [name]     : nom lisible de l'établissement (ex: "AP-HP")
 *  - [type]     : catégorie (hospital | samu | lab | cancer_center | military_hospital | emergency | appointment)
 *  - [region]   : région administrative (ex: "Île-de-France")
 *  - [verified] : true = validé manuellement, false = signalement communautaire en attente
 *  - [source]   : origine de la donnée ("FINESS" | "official" | "community")
 *  - [syncedAt] : timestamp Unix (ms) de la dernière synchronisation Supabase
 */
@Entity(
    tableName = "healthcare_whitelist",
    indices = [
        Index(value = ["number"],  unique = false),
        Index(value = ["prefix"],  unique = false),
        Index(value = ["verified"])
    ]
)
data class HealthcareEntry(

    @PrimaryKey
    val id: String,                          // UUID Supabase

    val number  : String?   = null,          // numéro exact normalisé, nullable
    val prefix  : String?   = null,          // préfixe normalisé, nullable
    val name    : String,
    val type    : String    = "hospital",
    val region  : String    = "France",
    val verified: Boolean   = true,
    val source  : String    = "official",
    val syncedAt: Long      = 0L             // System.currentTimeMillis() au moment du fetch
)