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
        return dao.getAllOnce().any {
            fr.bonobo.phonezen.utils.InternationalNumbers.matchesStored(rawNumber, it.number, PhoneUtils.regionProvider())
        }
    }

    suspend fun insert(rawNumber: String, label: String = "") {
        val normalized = fr.bonobo.phonezen.utils.InternationalNumbers.e164(rawNumber, PhoneUtils.regionProvider()) ?: return
        dao.insert(BlockedNumber(number = normalized, label = label))
    }

    suspend fun delete(entry: BlockedNumber) = dao.delete(entry)

    suspend fun deleteByNumber(rawNumber: String) {
        dao.getAllOnce().filter {
            it.number == rawNumber || fr.bonobo.phonezen.utils.InternationalNumbers.matchesStored(rawNumber, it.number, PhoneUtils.regionProvider())
        }.forEach { dao.delete(it) }
    }
}
