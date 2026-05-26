// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.
package fr.bonobo.phonezen.service

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import fr.bonobo.phonezen.data.model.CallStatus

object CallManager {

    private const val TAG = "CallManager"

    // -----------------------------------------------------------------------
    // Map multi-appels  (remplace l'ancien currentCall unique)
    //
    // Clé = identité stable de l'objet Call (callId ou identityHashCode en
    // fallback). La map contient tous les appels non-déconnectés.
    // -----------------------------------------------------------------------
    private val calls = mutableMapOf<String, Call>()

    private var inCallService: InCallService? = null
    private val handler = Handler(Looper.getMainLooper())

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)

    private val listeners      = mutableListOf<(Call?, CallStatus) -> Unit>()
    private val audioListeners = mutableListOf<(Boolean, Boolean, Int) -> Unit>()

    private var isMuted          : Boolean = false
    private var isOnHold         : Boolean = false
    private var hasHoldCall      : Boolean = false
    private var audioRoute       : Int     = CallAudioState.ROUTE_EARPIECE
    private var supportedRoutes  : Int     = CallAudioState.ROUTE_EARPIECE

    private var lastToggleTime = 0L

    // -----------------------------------------------------------------------
    // Résolution de l'appel « courant »
    //
    // Priorité : RINGING > ACTIVE > DIALING/CONNECTING > HOLDING
    // On donne la priorité à l'appel entrant pour qu'il s'affiche par dessus
    // un appel déjà en cours (double appel).
    // -----------------------------------------------------------------------
    private fun resolveCurrentCall(): Call? =
        calls.values.firstOrNull { it.state == Call.STATE_RINGING }
            ?: calls.values.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: calls.values.firstOrNull {
                it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING
            }
            ?: calls.values.firstOrNull { it.state == Call.STATE_HOLDING }

    private fun callKey(call: Call): String =
        System.identityHashCode(call).toString()

    // -----------------------------------------------------------------------
    // API publique — enregistrement / service
    // -----------------------------------------------------------------------

    @Synchronized
    fun setInCallService(service: InCallService?) {
        inCallService = service
        Log.d(TAG, "setInCallService: ${if (service != null) "défini" else "null"}")
    }

    @Synchronized
    fun setCall(call: Call) {
        val key = callKey(call)
        calls[key] = call
        refreshHoldState()
        notify(resolveCurrentCall(), fromState(call.state))
        notifyAudio()
        Log.d(TAG, "setCall [$key] état=${call.state} — map size=${calls.size}")
    }

    @Synchronized
    fun onStateChanged(call: Call, state: Int) {
        val key = callKey(call)

        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            calls.remove(key)
            Log.d(TAG, "onStateChanged [$key] DISCONNECTED — map size=${calls.size}")
        } else {
            calls[key] = call
            Log.d(TAG, "onStateChanged [$key] state=$state — map size=${calls.size}")
        }

        refreshHoldState()

        val current        = resolveCurrentCall()
        val reportedStatus = current?.let { fromState(it.state) } ?: CallStatus.DISCONNECTED
        notify(current, reportedStatus)
        notifyAudio()
    }

    @Synchronized
    fun onAudioStateChanged(state: CallAudioState?) {
        if (state != null) {
            isMuted         = state.isMuted
            audioRoute      = state.route
            supportedRoutes = state.supportedRouteMask
            Log.d(TAG, "onAudioStateChanged: isMuted=$isMuted, route=$audioRoute, supported=$supportedRoutes")
            notifyAudio()
        }
    }

    /**
     * Appelé uniquement quand getCallCount() == 0 (dernier appel raccroché).
     * Remet tout à zéro proprement.
     */
    @Synchronized
    fun clear() {
        if (calls.isNotEmpty()) {
            Log.w(TAG, "clear() appelé alors que la map n'est pas vide (${calls.size} appels) — ignoré")
            return
        }
        inCallService = null
        isMuted       = false
        isOnHold      = false
        audioRoute    = CallAudioState.ROUTE_EARPIECE
        notify(null, CallStatus.DISCONNECTED)
        notifyAudio()
        Log.d(TAG, "clear: tout réinitialisé")
    }

    /**
     * FIX reconnexion après kill de l'app.
     *
     * Quand l'app est killée pendant un appel, calls est vide au redémarrage.
     * Android NE rappelle PAS onCallAdded car InCallService est toujours actif
     * côté système. Cette méthode est appelée depuis InCallActivity.onResume()
     * ou PhoneZenInCallService.onRebind().
     *
     * @return true si au moins un appel a été récupéré
     */
    @Synchronized
    fun reconnectIfNeeded(): Boolean {
        if (calls.isNotEmpty()) {
            Log.d(TAG, "reconnectIfNeeded : map non vide, rien à faire")
            return false
        }

        val service = inCallService ?: run {
            Log.w(TAG, "reconnectIfNeeded : inCallService null — onRebind prendra le relais")
            return false
        }

        val activeCalls = service.calls.filter {
            it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
        }

        if (activeCalls.isEmpty()) {
            Log.d(TAG, "reconnectIfNeeded : aucun appel actif dans le service")
            return false
        }

        activeCalls.forEach { call ->
            val key = callKey(call)
            calls[key] = call
            Log.d(TAG, "reconnectIfNeeded : appel récupéré [$key] état=${call.state}")
        }

        refreshHoldState()
        resolveCurrentCall()?.let { notify(it, fromState(it.state)) }
        notifyAudio()
        return true
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    /** Retourne toujours l'appel résolu dynamiquement. */
    fun getCall(): Call?                 = resolveCurrentCall()
    fun getAudioState(): CallAudioState? = inCallService?.callAudioState

    /** Nombre d'appels simultanés — utilisé par PhoneZenInCallService. */
    fun getCallCount(): Int = calls.size

    // -----------------------------------------------------------------------
    // Listeners
    // -----------------------------------------------------------------------

    fun addListener(l: (Call?, CallStatus) -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }
    fun removeListener(l: (Call?, CallStatus) -> Unit) = listeners.remove(l)

    fun addAudioListener(l: (Boolean, Boolean, Int) -> Unit) {
        if (!audioListeners.contains(l)) audioListeners.add(l)
        l(isMuted, isOnHold, audioRoute)
    }

    /** Version avec info double appel */
    fun addAdvancedAudioListener(l: (Boolean, Boolean, Boolean, Int) -> Unit) {
        l(isMuted, isOnHold, hasHoldCall, audioRoute)
    }
    fun removeAudioListener(l: (Boolean, Boolean, Int) -> Unit) = audioListeners.remove(l)

    // -----------------------------------------------------------------------
    // Actions — toujours sur resolveCurrentCall()
    // -----------------------------------------------------------------------

    fun answer() = resolveCurrentCall()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject() = resolveCurrentCall()?.reject(false, null)

    /**
     * Raccroche l'appel courant résolu dynamiquement.
     * Si ACTIVE → le raccroche.
     * Si HOLDING (1er appel revenu de hold après call-waiting) → le raccroche.
     * Fallback sur inCallService.calls si la map est vide (race condition).
     */
    fun hangUp() {
        val call = resolveCurrentCall()
        if (call != null) {
            Log.d(TAG, "hangUp: disconnect via resolveCurrentCall état=${call.state}")
            try { call.disconnect() } catch (e: Exception) {
                Log.e(TAG, "hangUp error: ${e.message}")
            }
        } else {
            Log.w(TAG, "hangUp: map vide — tentative via inCallService")
            val service = inCallService
            if (service != null) {
                try {
                    service.calls.forEach { c ->
                        if (c.state != Call.STATE_DISCONNECTED &&
                            c.state != Call.STATE_DISCONNECTING) {
                            c.disconnect()
                            Log.d(TAG, "hangUp fallback: disconnect état=${c.state}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "hangUp fallback error: ${e.message}")
                }
            } else {
                Log.e(TAG, "hangUp: impossible — map ET inCallService sont null")
                // Force un clear pour débloquer l'UI dans tous les cas
                calls.clear()
                notify(null, CallStatus.DISCONNECTED)
                notifyAudio()
            }
        }
    }

    fun hold(on: Boolean) {
        if (on) resolveCurrentCall()?.hold() else resolveCurrentCall()?.unhold()
    }

    /** Permute l'appel actif et l'appel en attente */
    fun swapCalls() {
        val active = calls.values.find { it.state == Call.STATE_ACTIVE }
        val held   = calls.values.find { it.state == Call.STATE_HOLDING }

        if (active != null && held != null) {
            Log.d(TAG, "swapCalls: active -> hold, held -> active")
            active.hold()
            held.unhold()
        } else if (held != null) {
            Log.d(TAG, "swapCalls: held -> active (seul appel restant)")
            held.unhold()
        }
    }

    fun toggleMute() {
        val nextMute = !isMuted
        inCallService?.setMuted(nextMute)
        Log.d(TAG, "toggleMute: $nextMute")
    }

    /**
     * Alterne entre les routes audio disponibles.
     * Priorité : Bluetooth > Haut-parleur > Écouteur/Casque
     */
    fun cycleAudioRoute() {
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < 500) return
        lastToggleTime = now

        val service = inCallService ?: return
        val state   = getAudioState() ?: return
        val mask    = state.supportedRouteMask

        val newRoute = when (audioRoute) {
            CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_WIRED_HEADSET -> {
                if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) CallAudioState.ROUTE_BLUETOOTH
                else if (mask and CallAudioState.ROUTE_SPEAKER != 0) CallAudioState.ROUTE_SPEAKER
                else audioRoute
            }
            CallAudioState.ROUTE_BLUETOOTH -> {
                if (mask and CallAudioState.ROUTE_SPEAKER != 0) CallAudioState.ROUTE_SPEAKER
                else CallAudioState.ROUTE_EARPIECE
            }
            CallAudioState.ROUTE_SPEAKER -> {
                if (mask and CallAudioState.ROUTE_EARPIECE != 0) CallAudioState.ROUTE_EARPIECE
                else if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) CallAudioState.ROUTE_WIRED_HEADSET
                else audioRoute
            }
            else -> CallAudioState.ROUTE_EARPIECE
        }

        Log.d(TAG, "cycleAudioRoute: $audioRoute -> $newRoute (mask: $mask)")
        service.setAudioRoute(newRoute)
    }

    fun toggleSpeaker() {
        cycleAudioRoute()
    }

    // -----------------------------------------------------------------------
    // DTMF
    // -----------------------------------------------------------------------

    fun playDtmf(c: Char) {
        val toneType = when (c) {
            '1' -> ToneGenerator.TONE_DTMF_1
            '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3
            '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5
            '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7
            '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9
            '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        toneGenerator.startTone(toneType, 150)
        resolveCurrentCall()?.let { call ->
            call.playDtmfTone(c)
            handler.postDelayed({ call.stopDtmfTone() }, 200)
        }
    }

    fun stopDtmf() = resolveCurrentCall()?.stopDtmfTone()

    // -----------------------------------------------------------------------
    // Privé
    // -----------------------------------------------------------------------

    /**
     * isOnHold = vrai si aucun appel n'est ACTIVE mais au moins un est HOLDING.
     * hasHoldCall = vrai si au moins un appel est en attente (indépendamment de l'appel actif).
     * Recalculé à chaque changement d'état.
     */
    private fun refreshHoldState() {
        val states = calls.values.map { it.state }
        isOnHold = states.none { it == Call.STATE_ACTIVE } &&
                   states.any  { it == Call.STATE_HOLDING }
        hasHoldCall = states.any { it == Call.STATE_HOLDING }
    }

    private fun notify(call: Call?, status: CallStatus) =
        listeners.forEach { it(call, status) }

    private fun notifyAudio() =
        audioListeners.forEach { it(isMuted, isOnHold, audioRoute) }

    fun fromState(state: Int): CallStatus = when (state) {
        Call.STATE_RINGING                        -> CallStatus.RINGING
        Call.STATE_DIALING, Call.STATE_CONNECTING -> CallStatus.DIALING
        Call.STATE_ACTIVE                         -> CallStatus.ACTIVE
        Call.STATE_HOLDING                        -> CallStatus.ON_HOLD
        Call.STATE_DISCONNECTED,
        Call.STATE_DISCONNECTING                  -> CallStatus.DISCONNECTED
        else                                      -> CallStatus.IDLE
    }
}