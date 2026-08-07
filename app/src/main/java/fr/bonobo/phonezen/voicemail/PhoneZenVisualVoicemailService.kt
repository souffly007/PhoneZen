// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
package fr.bonobo.phonezen.voicemail

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.VoicemailContract
import android.telecom.PhoneAccountHandle
import android.telephony.VisualVoicemailService
import android.telephony.VisualVoicemailSms
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

class PhoneZenVisualVoicemailService : VisualVoicemailService() {

    companion object {
        private const val TAG = "PhoneZenVVM"
    }

    override fun onCellServiceConnected(
        task              : VisualVoicemailTask,
        phoneAccountHandle: PhoneAccountHandle
    ) {
        Log.i(TAG, "onCellServiceConnected account=${phoneAccountHandle.id}")
        task.finish()
    }

    override fun onSmsReceived(
        task: VisualVoicemailTask,
        sms : VisualVoicemailSms
    ) {
        try {
            Log.i(TAG, "========== VVM SMS REÇU ==========")
            Log.i(TAG, "prefix=${sms.prefix}")
            Log.i(TAG, "body=${sms.messageBody}")
            Log.i(TAG, "phoneAccount=${sms.phoneAccountHandle?.id}")
            Log.i(TAG, "fields=${bundleToString(sms.fields)}")

            val parsed = parseVisualVoicemailSms(sms)
            Log.i(TAG, "Parsed VVM: $parsed")

            if (parsed.messageId.isBlank()) {
                Log.w(TAG, "VVM ignoré: messageId vide")
                return
            }

            val insertedUri = insertVoicemail(parsed)

            if (insertedUri != null) {
                Log.i(TAG, "Voicemail inséré: $insertedUri")
                enqueueAudioDownload(insertedUri, parsed)
            } else {
                Log.e(TAG, "Insertion VoicemailContract échouée")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur onSmsReceived VVM", e)
        } finally {
            task.finish()
        }
    }

    override fun onSimRemoved(
        task              : VisualVoicemailTask,
        phoneAccountHandle: PhoneAccountHandle
    ) {
        Log.i(TAG, "onSimRemoved account=${phoneAccountHandle.id}")
        task.finish()
    }

    override fun onStopped(task: VisualVoicemailTask) {
        Log.w(TAG, "onStopped")
        task.finish()
    }

    // ─── Parsing ──────────────────────────────────────────────────────────────

    private fun parseVisualVoicemailSms(sms: VisualVoicemailSms): ParsedVvmMessage {
        val fields = sms.fields
        val body   = sms.messageBody.orEmpty()
        val prefix = sms.prefix.orEmpty().uppercase(Locale.ROOT)

        val map = mutableMapOf<String, String>()
        fields?.keySet()?.forEach { key -> map[key] = fields.get(key)?.toString().orEmpty() }
        map.putAll(parseBodyFields(body))

        val carrier = detectCarrierFormat(prefix, body, map)

        val number = firstNonBlank(
            map["From"], map["from"], map["CLI"], map["cli"],
            map["Sender"], map["sender"], map["Num"], map["number"]
        ) ?: "Inconnu"

        val duration = firstNonBlank(
            map["Dur"], map["dur"], map["Duration"], map["duration"],
            map["T"], map["Length"], map["length"]
        )?.toLongOrNull() ?: 0L

        val messageId = firstNonBlank(
            map["Id"], map["id"], map["MsgId"], map["msgId"],
            map["Message-Id"], map["M"], map["UID"], map["uid"]
        ) ?: ""

        val dateRaw = firstNonBlank(
            map["Date"], map["date"], map["Time"], map["time"],
            map["Timestamp"], map["timestamp"]
        ).orEmpty()

        val audioUri = firstNonBlank(
            map["Uri"], map["URI"], map["url"], map["URL"],
            map["Content-Location"], map["audio"]
        )

        return ParsedVvmMessage(
            carrierFormat  = carrier,
            prefix         = prefix.ifBlank { map["prefix"].orEmpty() },
            number         = number,
            timestamp      = parseTimestamp(dateRaw) ?: System.currentTimeMillis(),
            duration       = duration,
            messageId      = messageId,
            remoteAudioUri = audioUri,
            rawBody        = body
        )
    }

    private fun parseBodyFields(body: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (body.isBlank()) return result

        val colonIndex = body.indexOf(':')
        if (colonIndex >= 0) {
            result["prefix"] = body.substring(0, colonIndex).trim()
            body.substring(colonIndex + 1)
                .split(";", "\n", "\r")
                .forEach { part ->
                    val eq = part.indexOf('=')
                    if (eq > 0) {
                        result[part.substring(0, eq).trim()] =
                            part.substring(eq + 1).trim()
                    }
                }
        } else {
            result["prefix"] = body.takeWhile { !it.isWhitespace() }.trim()
        }
        return result
    }

    private fun detectCarrierFormat(
        prefix: String,
        body  : String,
        fields: Map<String, String>
    ): String {
        val raw = "$prefix $body ${fields.keys.joinToString()}".uppercase(Locale.ROOT)
        return when {
            raw.contains("FREE")                          -> "FREE"
            raw.contains("ORANGE") || raw.contains("888") -> "ORANGE"
            raw.contains("BOUYGUES") || raw.contains("660") -> "BOUYGUES"
            raw.contains("SFR") || raw.contains("123")    -> "SFR"
            raw.contains("SYNC") || raw.contains("STATUS") -> "OMTP"
            else                                           -> "UNKNOWN"
        }
    }

    // ─── VoicemailContract ────────────────────────────────────────────────────

    private fun insertVoicemail(message: ParsedVvmMessage): Uri? {
        val values = ContentValues().apply {
            put(VoicemailContract.Voicemails.NUMBER,         message.number)
            put(VoicemailContract.Voicemails.DATE,           message.timestamp)
            put(VoicemailContract.Voicemails.DURATION,       message.duration)
            put(VoicemailContract.Voicemails.IS_READ,        0)
            put(VoicemailContract.Voicemails.HAS_CONTENT,    0)
            put(VoicemailContract.Voicemails.SOURCE_DATA,    message.messageId)
            put(VoicemailContract.Voicemails.SOURCE_PACKAGE, packageName)
            put(VoicemailContract.Voicemails.TRANSCRIPTION,  "")
        }
        return try {
            val sourceUri = VoicemailContract.Voicemails.buildSourceUri(packageName)
            contentResolver.insert(sourceUri, values)
        } catch (e: SecurityException) {
            Log.e(TAG, "WRITE_VOICEMAIL refusé: PhoneZen doit être dialer par défaut", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erreur insertion VoicemailContract", e)
            null
        }
    }

    // ─── Download Worker ──────────────────────────────────────────────────────

    private fun enqueueAudioDownload(
        voicemailUri: Uri,
        message     : ParsedVvmMessage
    ) {
        try {
            val credentials = VvmCredentialsManager(applicationContext).load()

            when {
                credentials == null -> {
                    Log.w(TAG, "Aucune config VVM sauvegardée — audio ignoré")
                    notifyMwiFallback(message)
                }
                credentials.user.isBlank() -> {
                    Log.w(TAG, "Identifiant IMAP manquant — audio ignoré (serveur: ${credentials.host})")
                    notifyMwiFallback(message)
                }
                credentials.password.isBlank() -> {
                    Log.w(TAG, "Mot de passe IMAP manquant — audio ignoré (serveur: ${credentials.host})")
                    notifyMwiFallback(message)
                }
                else -> {
                    VoicemailDownloadWorker.enqueue(
                        context      = applicationContext,
                        voicemailUri = voicemailUri,
                        msgId        = message.messageId,
                        imapHost     = credentials.host,
                        imapPort     = credentials.port,
                        imapUser     = credentials.user,
                        imapPassword = credentials.password
                    )
                    Log.i(TAG, "VoicemailDownloadWorker lancé msgId=${message.messageId}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur enqueue VoicemailDownloadWorker", e)
            notifyMwiFallback(message)
        }
    }

    // ─── Fallback MWI ─────────────────────────────────────────────────────────

    private fun notifyMwiFallback(message: ParsedVvmMessage) {
        Log.w(
            TAG,
            "Fallback MWI: message détecté mais VVM incomplet. " +
                    "Proposer l'appel messagerie classique. " +
                    "from=${message.number}, id=${message.messageId}"
        )
        /*
         * VoicemailNotificationHelper.showFallbackNotification(
         *     context = applicationContext,
         *     title   = "Message vocal disponible",
         *     text    = "Touchez pour appeler le répondeur"
         * )
         */
    }

    // ─── Utilitaires ──────────────────────────────────────────────────────────

    private fun parseTimestamp(value: String): Long? {
        if (value.isBlank()) return null
        val cleaned = value.trim()

        cleaned.toLongOrNull()?.let { raw ->
            return if (raw > 10_000_000_000L) raw else raw * 1000L
        }

        val patterns = listOf(
            "yyMMddHHmmssZ",
            "yyyyMMddHHmmssZ",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(cleaned)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun bundleToString(bundle: Bundle?): String {
        if (bundle == null) return "null"
        return bundle.keySet().joinToString(prefix = "{", postfix = "}") { key ->
            "$key=${bundle.get(key)}"
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private data class ParsedVvmMessage(
        val carrierFormat  : String,
        val prefix         : String,
        val number         : String,
        val timestamp      : Long,
        val duration       : Long,
        val messageId      : String,
        val remoteAudioUri : String?,
        val rawBody        : String
    )
}