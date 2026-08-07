// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VoicemailDownloadWorker {
package fr.bonobo.phonezen.voicemail

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.VoicemailContract
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Properties
import java.util.concurrent.TimeUnit
import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.MimeMultipart

class VoicemailDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "VoicemailDownload"

        // Clés WorkData
        const val KEY_VOICEMAIL_URI = "voicemail_uri"
        const val KEY_MSG_ID        = "msg_id"
        const val KEY_IMAP_HOST     = "imap_host"
        const val KEY_IMAP_PORT     = "imap_port"
        const val KEY_IMAP_USER     = "imap_user"
        const val KEY_IMAP_PASSWORD = "imap_password"

        // MIME types audio courants pour la VVM
        private val AUDIO_MIME_TYPES = setOf(
            "audio/amr", "audio/amr-wb",
            "audio/ogg", "audio/mp4",
            "audio/mpeg", "audio/3gpp"
        )

        /**
         * Enqueue le worker depuis le service VVM.
         * Nécessite un accès réseau ; retente jusqu'à 3 fois avec backoff exponentiel.
         */
        fun enqueue(
            context     : Context,
            voicemailUri: Uri,
            msgId       : String,
            imapHost    : String,
            imapPort    : Int,
            imapUser    : String,
            imapPassword: String
        ) {
            val data = Data.Builder()
                .putString(KEY_VOICEMAIL_URI, voicemailUri.toString())
                .putString(KEY_MSG_ID,        msgId)
                .putString(KEY_IMAP_HOST,     imapHost)
                .putInt   (KEY_IMAP_PORT,     imapPort)
                .putString(KEY_IMAP_USER,     imapUser)
                .putString(KEY_IMAP_PASSWORD, imapPassword)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<VoicemailDownloadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS   // 30s → 1min → 2min …
                )
                .addTag("vvm_download_$msgId")
                .build()

            WorkManager.getInstance(context).enqueue(request)
            Log.d(TAG, "Worker enqueué pour msgId=$msgId")
        }
    }

    // ─── doWork ──────────────────────────────────────────────────────────────

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val voicemailUriStr = inputData.getString(KEY_VOICEMAIL_URI)
            ?: return@withContext Result.failure(buildError("URI manquante"))

        val msgId        = inputData.getString(KEY_MSG_ID)        ?: ""
        val imapHost     = inputData.getString(KEY_IMAP_HOST)     ?: ""
        val imapPort     = inputData.getInt   (KEY_IMAP_PORT, 993)
        val imapUser     = inputData.getString(KEY_IMAP_USER)     ?: ""
        val imapPassword = inputData.getString(KEY_IMAP_PASSWORD) ?: ""

        val voicemailUri = Uri.parse(voicemailUriStr)

        Log.i(TAG, "Début téléchargement — msgId=$msgId host=$imapHost:$imapPort")

        return@withContext try {
            downloadViaImap(
                voicemailUri = voicemailUri,
                msgId        = msgId,
                host         = imapHost,
                port         = imapPort,
                user         = imapUser,
                password     = imapPassword
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur téléchargement VVM", e)
            // Retry automatique si runAttemptCount < 3, sinon failure
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(buildError(e.message ?: "Erreur inconnue"))
        }
    }

    // ─── IMAP ─────────────────────────────────────────────────────────────────

    private fun downloadViaImap(
        voicemailUri: Uri,
        msgId       : String,
        host        : String,
        port        : Int,
        user        : String,
        password    : String
    ): Result {
        val props = Properties().apply {
            put("mail.store.protocol",       "imaps")
            put("mail.imaps.host",           host)
            put("mail.imaps.port",           port.toString())
            put("mail.imaps.ssl.enable",     "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout",           "20000")
        }

        val session: Session = Session.getInstance(props)
        val store: Store     = session.getStore("imaps")

        store.connect(host, user, password)
        Log.d(TAG, "Connecté au serveur IMAP $host")

        val folder: Folder = store.getFolder("INBOX").also { it.open(Folder.READ_ONLY) }

        return try {
            val message = folder.messages.firstOrNull { msg ->
                // Cherche par Message-ID ou header opérateur spécifique
                msg.getHeader("Message-ID")?.any { it.contains(msgId) } == true ||
                        msg.getHeader("X-VVM-Message-Id")?.any { it.contains(msgId) } == true
            }

            if (message == null) {
                Log.w(TAG, "Message IMAP introuvable pour msgId=$msgId — retry")
                return Result.retry()
            }

            // Extrait la pièce jointe audio du message MIME
            extractAndSaveAudio(voicemailUri, message.content)

        } finally {
            folder.close(false)
            store.close()
        }
    }

    // ─── Extraction MIME ──────────────────────────────────────────────────────

    private fun extractAndSaveAudio(voicemailUri: Uri, content: Any?): Result {
        return when (content) {
            is MimeMultipart -> {
                // Parcourt les parties MIME pour trouver l'audio
                (0 until content.count).forEach { i ->
                    val part     = content.getBodyPart(i)
                    val mimeType = part.contentType.lowercase().substringBefore(";").trim()

                    if (mimeType in AUDIO_MIME_TYPES) {
                        Log.i(TAG, "Partie audio trouvée: $mimeType")
                        val stream = part.inputStream
                        return saveAudioToProvider(voicemailUri, stream, mimeType)
                    }
                }
                Log.w(TAG, "Aucune partie audio dans le message MIME")
                Result.failure(buildError("Pas d'audio dans le message"))
            }
            is InputStream -> {
                // Message simple (rare) — on suppose AMR par défaut
                saveAudioToProvider(voicemailUri, content, "audio/amr")
            }
            else -> {
                Log.w(TAG, "Type de contenu IMAP inattendu: ${content?.javaClass}")
                Result.failure(buildError("Type de contenu non supporté"))
            }
        }
    }

    // ─── Écriture dans VoicemailContract ─────────────────────────────────────

    private fun saveAudioToProvider(
        voicemailUri: Uri,
        audioStream : InputStream,
        mimeType    : String
    ): Result {
        return try {
            // 1. Écrit le flux audio
            applicationContext.contentResolver
                .openOutputStream(voicemailUri)
                ?.use { out -> audioStream.copyTo(out) }
                ?: return Result.failure(buildError("Impossible d'ouvrir l'OutputStream"))

            // 2. Met à jour les métadonnées : HAS_CONTENT + MIME_TYPE
            val update = ContentValues().apply {
                put(VoicemailContract.Voicemails.HAS_CONTENT, 1)
                put(VoicemailContract.Voicemails.MIME_TYPE,   mimeType)
            }
            val updated = applicationContext.contentResolver
                .update(voicemailUri, update, null, null)

            if (updated > 0) {
                Log.i(TAG, "Audio VVM sauvegardé avec succès → $voicemailUri ($mimeType)")
                Result.success()
            } else {
                Log.e(TAG, "Mise à jour ContentProvider échouée pour $voicemailUri")
                Result.failure(buildError("Update ContentProvider échoué"))
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission WRITE_VOICEMAIL révoquée !", e)
            Result.failure(buildError("Permission révoquée"))
        } catch (e: Exception) {
            Log.e(TAG, "Erreur écriture audio", e)
            Result.retry()
        }
    }

    // ─── Utilitaire ───────────────────────────────────────────────────────────

    private fun buildError(message: String): Data =
        Data.Builder().putString("error", message).build()
}