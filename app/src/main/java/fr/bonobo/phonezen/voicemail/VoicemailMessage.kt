// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoicemailMessage {
package fr.bonobo.phonezen.voicemail

data class VoicemailMessage(
    val id          : Long,
    val uri         : android.net.Uri,
    val number      : String,
    val date        : Long,
    val duration    : Long,       // secondes
    val isRead      : Boolean,
    val hasContent  : Boolean,
    val mimeType    : String,
    val transcription: String
)