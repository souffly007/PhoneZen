// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoicemailViewModel {
package fr.bonobo.phonezen.voicemail

import android.app.Application
import android.provider.VoicemailContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VoicemailViewModel(app: Application) : AndroidViewModel(app) {

    private val _messages = MutableStateFlow<List<VoicemailMessage>>(emptyList())
    val messages: StateFlow<List<VoicemailMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init { loadMessages() }

    fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val ctx = getApplication<Application>()
            val uri = VoicemailContract.Voicemails.buildSourceUri(ctx.packageName)

            val list = mutableListOf<VoicemailMessage>()
            try {
                ctx.contentResolver.query(
                    uri, null, null, null,
                    "${VoicemailContract.Voicemails.DATE} DESC"
                )?.use { cursor ->
                    val idIdx    = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails._ID)
                    val numIdx   = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.NUMBER)
                    val dateIdx  = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.DATE)
                    val durIdx   = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.DURATION)
                    val readIdx  = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.IS_READ)
                    val hasIdx   = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.HAS_CONTENT)
                    val mimeIdx  = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.MIME_TYPE)
                    val tranIdx  = cursor.getColumnIndexOrThrow(VoicemailContract.Voicemails.TRANSCRIPTION)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idIdx)
                        list.add(
                            VoicemailMessage(
                                id           = id,
                                uri          = android.net.Uri.withAppendedPath(uri, id.toString()),
                                number       = cursor.getString(numIdx) ?: "Inconnu",
                                date         = cursor.getLong(dateIdx),
                                duration     = cursor.getLong(durIdx),
                                isRead       = cursor.getInt(readIdx) == 1,
                                hasContent   = cursor.getInt(hasIdx) == 1,
                                mimeType     = cursor.getString(mimeIdx) ?: "audio/amr",
                                transcription = cursor.getString(tranIdx) ?: ""
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("VoicemailVM", "Permission manquante", e)
            }
            _messages.value = list
            _isLoading.value = false
        }
    }

    fun markAsRead(msg: VoicemailMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val values = android.content.ContentValues().apply {
                put(VoicemailContract.Voicemails.IS_READ, 1)
            }
            ctx.contentResolver.update(msg.uri, values, null, null)
            _messages.value = _messages.value.map {
                if (it.id == msg.id) it.copy(isRead = true) else it
            }
        }
    }

    fun deleteMessage(msg: VoicemailMessage) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            ctx.contentResolver.delete(msg.uri, null, null)
            _messages.value = _messages.value.filter { it.id != msg.id }
        }
    }
}