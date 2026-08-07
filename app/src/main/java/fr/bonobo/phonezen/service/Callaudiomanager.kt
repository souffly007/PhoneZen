// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.
package fr.bonobo.phonezen.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import fr.bonobo.phonezen.data.model.AudioRoute
import android.os.Build
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gère le focus audio et les paramètres de routage pendant les appels,
 * en particulier lors du double appel (call waiting) pour éviter toute coupure.
 *
 * Stratégie :
 * - Lors d'un double appel, on NE relâche PAS le focus audio.
 * - Le hold/resume du 2e appel est géré exclusivement via TelecomManager/Call API.
 * - Aucun changement de mode AudioManager pendant la bascule entre appels.
 */
@Singleton
class CallAudioManager @Inject constructor(
    private val context: Context
) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var isHoldingFocus = false

    companion object {
        private const val TAG = "CallAudioManager"
    }

    // ─── Focus audio ──────────────────────────────────────────────────────────

    /**
     * Acquiert le focus audio exclusif pour la téléphonie.
     * À appeler au début du premier appel actif.
     */
    fun acquireAudioFocus() {
        if (isHoldingFocus) {
            Log.d(TAG, "Focus already held, skipping acquire")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    onAudioFocusChanged(focusChange)
                }
                .build()

            val result = audioManager.requestAudioFocus(request)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = request
                isHoldingFocus = true
                Log.d(TAG, "Audio focus granted")
            } else {
                Log.w(TAG, "Audio focus request failed: $result")
            }
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                { focusChange -> onAudioFocusChanged(focusChange) },
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
            isHoldingFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            Log.d(TAG, "Audio focus (legacy) granted: $isHoldingFocus")
        }

        setCallAudioMode()
    }

    /**
     * Relâche le focus audio.
     * À appeler uniquement quand TOUS les appels sont terminés.
     */
    fun releaseAudioFocus() {
        if (!isHoldingFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }

        isHoldingFocus = false
        restoreNormalAudioMode()
        Log.d(TAG, "Audio focus released")
    }

    // ─── Bascule entre appels (SWAP) ──────────────────────────────────────────

    /**
     * Appelé avant un swap d'appels.
     * On NE touche PAS au focus : c'est TelecomManager qui gère le routage.
     * On se contente de s'assurer que le mode audio reste en MODE_IN_CALL.
     */
    fun onCallSwap() {
        Log.d(TAG, "onCallSwap: maintaining audio mode IN_CALL, no focus change")
        ensureCallAudioMode()
    }

    /**
     * Appelé quand le 2e appel arrive (call waiting).
     * On maintient le focus et le mode audio pour ne pas couper l'appel actif.
     */
    fun onSecondCallArrived() {
        Log.d(TAG, "onSecondCallArrived: maintaining audio focus and mode")
        ensureCallAudioMode()
    }

    /**
     * Appelé quand un des appels se termine (mais pas tous).
     * Aucune action sur le focus, l'autre appel continue.
     */
    fun onOneCallEnded() {
        Log.d(TAG, "onOneCallEnded: keeping audio focus for remaining call")
        ensureCallAudioMode()
    }

    // ─── Route audio (écouteur / haut-parleur / Bluetooth) ───────────────────

    /**
     * Retourne la route audio actuelle.
     * Correspond au type AudioRouteType de CallManager.
     */
    fun getCurrentRoute(): AudioRoute {
        return when {
            audioManager.isBluetoothScoOn -> AudioRoute.BLUETOOTH
            audioManager.isSpeakerphoneOn -> AudioRoute.SPEAKER
            else -> AudioRoute.EARPIECE
        }
    }

    /**
     * Bascule vers la route audio demandée.
     * Compatible avec l'InCallService Telecom qui peut aussi gérer les routes.
     */
    fun setAudioRoute(route: AudioRoute) {
        when (route) {
            AudioRoute.EARPIECE -> {
                audioManager.isSpeakerphoneOn = false
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
            }
            AudioRoute.SPEAKER -> {
                if (audioManager.isBluetoothScoOn) {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                }
                audioManager.isSpeakerphoneOn = true
            }
            AudioRoute.BLUETOOTH -> {
                audioManager.isSpeakerphoneOn = false
                audioManager.startBluetoothSco()
                audioManager.isBluetoothScoOn = true
            }
        }
        Log.d(TAG, "Audio route set to: $route")
    }

    // ─── Privé ────────────────────────────────────────────────────────────────

    private fun setCallAudioMode() {
        audioManager.mode = AudioManager.MODE_IN_CALL
        Log.d(TAG, "Audio mode set to MODE_IN_CALL")
    }

    private fun ensureCallAudioMode() {
        if (audioManager.mode != AudioManager.MODE_IN_CALL) {
            Log.w(TAG, "Audio mode was ${audioManager.mode}, resetting to MODE_IN_CALL")
            audioManager.mode = AudioManager.MODE_IN_CALL
        }
    }

    private fun restoreNormalAudioMode() {
        audioManager.isSpeakerphoneOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        Log.d(TAG, "Audio mode restored to NORMAL")
    }

    private fun onAudioFocusChanged(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.w(TAG, "Audio focus LOST — another app took over")
                // Ne pas relâcher ici : laisser l'InCallService décider
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost transiently")
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus regained")
                ensureCallAudioMode()
            }
        }
    }
}