// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.class VvmCredentialsManager {
package fr.bonobo.phonezen.voicemail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stocke et récupère les credentials IMAP VVM via Android Keystore (AES-256-GCM).
 * Le mot de passe n'est jamais en clair en dehors de cette classe.
 */
class VvmCredentialsManager(private val context: Context) {

    companion object {
        private const val TAG              = "VvmCredentials"
        private const val KEYSTORE_ALIAS   = "phonezen_vvm_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION   = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH   = 128
        private const val PREFS_NAME       = "vvm_secure_prefs"

        // Clés SharedPreferences (valeurs chiffrées)
        private const val KEY_IMAP_HOST     = "imap_host"
        private const val KEY_IMAP_PORT     = "imap_port"
        private const val KEY_IMAP_USER     = "imap_user"
        private const val KEY_IMAP_PASSWORD = "imap_password_enc"
        private const val KEY_IMAP_IV       = "imap_password_iv"
    }

    // ─── Données credentials ──────────────────────────────────────────────────

    data class ImapCredentials(
        val host    : String,
        val port    : Int,
        val user    : String,
        val password: String
    )

    // ─── API publique ─────────────────────────────────────────────────────────

    /** Sauvegarde les credentials. Le mot de passe est chiffré via Keystore. */
    fun save(credentials: ImapCredentials) {
        val (encPassword, iv) = encrypt(credentials.password)

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IMAP_HOST,     credentials.host)
            .putInt   (KEY_IMAP_PORT,     credentials.port)
            .putString(KEY_IMAP_USER,     credentials.user)
            .putString(KEY_IMAP_PASSWORD, encPassword)
            .putString(KEY_IMAP_IV,       iv)
            .apply()

        Log.d(TAG, "Credentials IMAP sauvegardés pour ${credentials.user}@${credentials.host}")
    }

    /** Retourne les credentials ou null si absents / erreur déchiffrement. */
    fun load(): ImapCredentials? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val host        = prefs.getString(KEY_IMAP_HOST,     null) ?: return null
        val port        = prefs.getInt   (KEY_IMAP_PORT,     993)
        val user        = prefs.getString(KEY_IMAP_USER,     null) ?: return null
        val encPassword = prefs.getString(KEY_IMAP_PASSWORD, null) ?: return null
        val iv          = prefs.getString(KEY_IMAP_IV,       null) ?: return null

        val password = decrypt(encPassword, iv) ?: return null

        return ImapCredentials(host, port, user, password)
    }

    /** Supprime tous les credentials (ex : déconnexion utilisateur). */
    fun clear() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
        Log.d(TAG, "Credentials IMAP effacés")
    }

    fun hasCredentials(): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_IMAP_HOST)

    // ─── Chiffrement AES-256-GCM via Android Keystore ────────────────────────

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }

        // Retourne la clé existante si déjà générée
        keyStore.getKey(KEYSTORE_ALIAS, null)?.let { return it as SecretKey }

        // Génère une nouvelle clé AES-256 dans le Keystore matériel si disponible
        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // pas de biométrie obligatoire
                .build()
        )
        return keyGen.generateKey().also {
            Log.d(TAG, "Clé AES-256 générée dans le Keystore")
        }
    }

    /** Retourne Pair(base64CipherText, base64IV) */
    private fun encrypt(plainText: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val iv          = cipher.iv

        return Pair(
            Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            Base64.encodeToString(iv,          Base64.NO_WRAP)
        )
    }

    /** Retourne le texte déchiffré ou null en cas d'erreur. */
    private fun decrypt(encBase64: String, ivBase64: String): String? {
        return try {
            val cipher    = Cipher.getInstance(TRANSFORMATION)
            val iv        = Base64.decode(ivBase64,  Base64.NO_WRAP)
            val cipherBytes = Base64.decode(encBase64, Base64.NO_WRAP)

            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur déchiffrement credentials", e)
            null
        }
    }
}