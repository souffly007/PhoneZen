// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
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
import fr.bonobo.phonezen.data.model.AudioRoute
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.utils.CrashHandler

object CallManager {

    private const val TAG = "CallManager"

    private val calls = mutableMapOf<String, Call>()
    private var secondCallKey: String? = null

    private var inCallService: InCallService? = null
    private val handler = Handler(Looper.getMainLooper())

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_DTMF, 80)

    private val listeners      = mutableListOf<(Call?, CallStatus) -> Unit>()
    private val audioListeners = mutableListOf<(Boolean, Boolean, AudioRoute, Boolean) -> Unit>()

    private var isMuted      : Boolean    = false
    private var isOnHold     : Boolean    = false
    private var audioRoute   : AudioRoute = AudioRoute.EARPIECE
    private var isBtAvailable: Boolean    = false

    private var lastToggleTime = 0L

    // ─── Résolution de l'appel courant ────────────────────────────────────────

    private fun resolveCurrentCall(): Call? =
        calls.values.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: calls.values.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: calls.values.firstOrNull {
                it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING
            }
            ?: calls.values.firstOrNull { it.state == Call.STATE_RINGING }

    private fun callKey(call: Call): String =
        call.details?.let { details ->
            val number    = details.handle?.schemeSpecificPart ?: "unknown"
            val timestamp = details.creationTimeMillis
            "$number@$timestamp"
        } ?: System.identityHashCode(call).toString()

    // ─── API publique ─────────────────────────────────────────────────────────

    @Synchronized
    fun setInCallService(service: InCallService?) {
        inCallService = service
        CrashHandler.lastAction = "CallManager.setInCallService: ${if (service != null) "défini" else "null"}"
        Log.d(TAG, "setInCallService: ${if (service != null) "défini" else "null"}")
    }

    @Synchronized
    fun setCall(call: Call) {
        val key    = callKey(call)
        val status = fromState(call.state)
        val number = call.details?.handle?.schemeSpecificPart ?: "?"
        calls[key] = call
        CrashHandler.lastAnalyzedNumber = number
        CrashHandler.lastAction         = "CallManager.setCall: $number state=$status"
        refreshHoldState()
        notify(resolveCurrentCall(), status)
        notifyAudio()
        Log.d(TAG, "setCall [$key] état=${call.state} — map size=${calls.size}")
    }

    @Synchronized
    fun setSecondCall(call: Call) {
        val key    = callKey(call)
        val number = call.details?.handle?.schemeSpecificPart ?: "?"
        calls[key] = call
        secondCallKey = key
        CrashHandler.lastAnalyzedNumber = number
        CrashHandler.lastAction         = "CallManager.setSecondCall: $number"
        refreshHoldState()
        notify(resolveCurrentCall(), fromState(call.state))
        notifyAudio()
        Log.d(TAG, "setSecondCall [$key] état=${call.state} — map size=${calls.size}")
    }

    @Synchronized
    fun isSecondCall(call: Call): Boolean = callKey(call) == secondCallKey

    @Synchronized
    fun onStateChanged(call: Call, state: Int) {
        val key    = callKey(call)
        val status = fromState(state)
        val number = call.details?.handle?.schemeSpecificPart ?: "?"
        CrashHandler.lastAction = "CallManager.onStateChanged: $number → $status"

        if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
            calls.remove(key)
            if (key == secondCallKey) secondCallKey = null
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
        if (state == null) return
        isMuted       = state.isMuted
        isBtAvailable = (state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
        audioRoute    = when {
            (state.route and CallAudioState.ROUTE_BLUETOOTH) != 0 -> AudioRoute.BLUETOOTH
            (state.route and CallAudioState.ROUTE_SPEAKER)   != 0 -> AudioRoute.SPEAKER
            else                                                    -> AudioRoute.EARPIECE
        }
        CrashHandler.lastAction = "CallManager.onAudioStateChanged: route=$audioRoute muted=$isMuted"
        Log.d(TAG, "onAudioStateChanged: isMuted=$isMuted route=$audioRoute btAvailable=$isBtAvailable")
        notifyAudio()
    }

    @Synchronized
    fun clear() {
        if (calls.isNotEmpty()) {
            Log.w(TAG, "clear() appelé alors que la map n'est pas vide — ignoré")
            return
        }
        secondCallKey = null
        inCallService = null
        isMuted       = false
        isOnHold      = false
        audioRoute    = AudioRoute.EARPIECE
        isBtAvailable = false
        CrashHandler.lastAction = "CallManager.clear: réinitialisé"
        notify(null, CallStatus.DISCONNECTED)
        notifyAudio()
        Log.d(TAG, "clear: tout réinitialisé")
    }

    @Synchronized
    fun reconnectIfNeeded(): Boolean {
        if (calls.isNotEmpty()) return false
        val service = inCallService ?: run {
            Log.w(TAG, "reconnectIfNeeded : inCallService null")
            return false
        }
        val activeCalls = service.calls.filter {
            it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
        }
        if (activeCalls.isEmpty()) return false
        activeCalls.forEach { call ->
            val key = callKey(call)
            calls[key] = call
            Log.d(TAG, "reconnectIfNeeded : appel récupéré [$key] état=${call.state}")
        }
        CrashHandler.lastAction = "CallManager.reconnectIfNeeded: ${activeCalls.size} appel(s) récupéré(s)"
        refreshHoldState()
        resolveCurrentCall()?.let { notify(it, fromState(it.state)) }
        notifyAudio()
        return true
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    @Synchronized fun getCall(): Call? = resolveCurrentCall()
    @Synchronized fun getAudioState(): CallAudioState? = inCallService?.callAudioState

    @Synchronized
    fun getCalls(): List<Call> = calls.values
        .filter { it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING }
        .toList()

    @Synchronized
    fun getCallCount(): Int = calls.values.count {
        it.state != Call.STATE_DISCONNECTED && it.state != Call.STATE_DISCONNECTING
    }

    @Synchronized
    fun getActiveCallNumber(): String? =
        calls.values.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?.details?.handle?.schemeSpecificPart
            ?: calls.values.firstOrNull { it.state == Call.STATE_HOLDING }
                ?.details?.handle?.schemeSpecificPart

    // ─── Listeners ────────────────────────────────────────────────────────────

    fun addListener(l: (Call?, CallStatus) -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }
    fun removeListener(l: (Call?, CallStatus) -> Unit) = listeners.remove(l)

    fun addAudioListener(l: (Boolean, Boolean, AudioRoute, Boolean) -> Unit) {
        if (!audioListeners.contains(l)) audioListeners.add(l)
        l(isMuted, isOnHold, audioRoute, isBtAvailable)
    }
    fun removeAudioListener(l: (Boolean, Boolean, AudioRoute, Boolean) -> Unit) =
        audioListeners.remove(l)

    // ─── Actions ──────────────────────────────────────────────────────────────

    fun answer() {
        CrashHandler.lastAction = "CallManager.answer"
        resolveCurrentCall()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun reject() {
        CrashHandler.lastAction = "CallManager.reject"
        resolveCurrentCall()?.reject(false, null)
    }

    fun hangUp() {
        CrashHandler.lastAction = "CallManager.hangUp"
        val call = resolveCurrentCall()
        if (call != null) {
            Log.d(TAG, "hangUp: disconnect état=${call.state}")
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
                            c.state != Call.STATE_DISCONNECTING) c.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "hangUp fallback error: ${e.message}")
                }
            } else {
                calls.clear()
                secondCallKey = null
                notify(null, CallStatus.DISCONNECTED)
                notifyAudio()
            }
        }
    }

    fun hold(on: Boolean) {
        CrashHandler.lastAction = "CallManager.hold: $on"
        if (on) resolveCurrentCall()?.hold() else resolveCurrentCall()?.unhold()
    }

    fun toggleMute() {
        val nextMute = !isMuted
        CrashHandler.lastAction = "CallManager.toggleMute: $nextMute"
        inCallService?.setMuted(nextMute)
        Log.d(TAG, "toggleMute: $nextMute")
    }

    fun setAudioRoute(route: AudioRoute) {
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < 400) { Log.d(TAG, "setAudioRoute ignoré (trop rapide)"); return }
        lastToggleTime = now
        val service = inCallService ?: run { Log.e(TAG, "setAudioRoute: inCallService null"); return }
        val telecomRoute = when (route) {
            AudioRoute.SPEAKER   -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.BLUETOOTH -> if (isBtAvailable) CallAudioState.ROUTE_BLUETOOTH
                                    else CallAudioState.ROUTE_EARPIECE
            AudioRoute.EARPIECE  -> CallAudioState.ROUTE_EARPIECE
        }
        CrashHandler.lastAction = "CallManager.setAudioRoute: $route"
        service.setAudioRoute(telecomRoute)
        Log.d(TAG, "setAudioRoute envoyé: $route → telecom=$telecomRoute")
    }

    fun toggleSpeaker() =
        setAudioRoute(if (audioRoute == AudioRoute.SPEAKER) AudioRoute.EARPIECE else AudioRoute.SPEAKER)

    // ─── DTMF ─────────────────────────────────────────────────────────────────

    fun playDtmf(c: Char) {
        val toneType = when (c) {
            '1' -> ToneGenerator.TONE_DTMF_1; '2' -> ToneGenerator.TONE_DTMF_2
            '3' -> ToneGenerator.TONE_DTMF_3; '4' -> ToneGenerator.TONE_DTMF_4
            '5' -> ToneGenerator.TONE_DTMF_5; '6' -> ToneGenerator.TONE_DTMF_6
            '7' -> ToneGenerator.TONE_DTMF_7; '8' -> ToneGenerator.TONE_DTMF_8
            '9' -> ToneGenerator.TONE_DTMF_9; '0' -> ToneGenerator.TONE_DTMF_0
            '*' -> ToneGenerator.TONE_DTMF_S; '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        toneGenerator.startTone(toneType, 150)
        resolveCurrentCall()?.let { call ->
            call.playDtmfTone(c)
            handler.postDelayed({ call.stopDtmfTone() }, 200)
        }
    }

    fun stopDtmf() = resolveCurrentCall()?.stopDtmfTone()

    // ─── Privé ────────────────────────────────────────────────────────────────

    private fun refreshHoldState() {
        isOnHold = calls.values.none { it.state == Call.STATE_ACTIVE } &&
                   calls.values.any  { it.state == Call.STATE_HOLDING }
    }

    private fun notify(call: Call?, status: CallStatus) =
        listeners.forEach { it(call, status) }

    private fun notifyAudio() =
        audioListeners.forEach { it(isMuted, isOnHold, audioRoute, isBtAvailable) }

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
