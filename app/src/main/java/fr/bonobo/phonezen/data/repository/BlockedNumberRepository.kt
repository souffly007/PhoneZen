// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class BlockedNumberRepository {
package fr.bonobo.phonezen.data.repository

import fr.bonobo.phonezen.data.dao.BlockedNumberDao
import fr.bonobo.phonezen.utils.PhoneUtils
import kotlinx.coroutines.flow.Flow
import fr.bonobo.phonezen.data.model.BlockedNumber


class BlockedNumberRepository(private val dao: BlockedNumberDao) {

    fun getAll(): Flow<List<BlockedNumber>> = dao.getAll()

    suspend fun isBlocked(rawNumber: String): Boolean {
        val normalized = PhoneUtils.normalizeNumber(rawNumber)
        // Vérifie les deux formats : local (0X) et international (+33X)
        val local = if (normalized.startsWith("+33"))
            "0" + normalized.substring(3) else normalized
        val intl  = if (normalized.startsWith("0"))
            "+33" + normalized.substring(1) else normalized
        return dao.isBlocked(local) > 0 || dao.isBlocked(intl) > 0
    }

    suspend fun insert(rawNumber: String, label: String = "") {
        val normalized = PhoneUtils.normalizeNumber(rawNumber)
        dao.insert(BlockedNumber(number = normalized, label = label))
    }

    suspend fun delete(entry: BlockedNumber) = dao.delete(entry)

    suspend fun deleteByNumber(rawNumber: String) {
        val normalized = PhoneUtils.normalizeNumber(rawNumber)
        // Supprime les deux formats si présents
        dao.deleteByNumber(normalized)
        val alt = if (normalized.startsWith("+33"))
            "0" + normalized.substring(3)
        else if (normalized.startsWith("0"))
            "+33" + normalized.substring(1)
        else null
        alt?.let { dao.deleteByNumber(it) }
    }
}