// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
//
// PhoneZen is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License.
package fr.bonobo.phonezen.service

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import fr.bonobo.phonezen.data.model.CallStatus

object CallManager {

    private var currentCall: Call? = null
    private var inCallService: InCallService? = null

    private val listeners = mutableListOf<(Call?, CallStatus) -> Unit>()
    private val audioListeners = mutableListOf<(Boolean, Boolean, Int) -> Unit>()

    private var isMuted: Boolean = false
    private var isOnHold: Boolean = false
    private var audioRoute: Int = CallAudioState.ROUTE_EARPIECE

    @Synchronized
    fun setInCallService(service: InCallService?) {
        this.inCallService = service
    }

    @Synchronized
    fun setCall(call: Call) {
        currentCall = call
        isOnHold = call.state == Call.STATE_HOLDING
        notify(call, fromState(call.state))
        notifyAudio()
    }

    @Synchronized
    fun onStateChanged(call: Call, state: Int) {
        isOnHold = state == Call.STATE_HOLDING
        notify(call, fromState(state))
        notifyAudio()
    }

    @Synchronized
    fun onAudioStateChanged(state: CallAudioState?) {
        if (state != null) {
            isMuted = state.isMuted
            audioRoute = state.route
            notifyAudio()
        }
    }

    @Synchronized
    fun clear() {
        currentCall = null
        inCallService = null
        isMuted = false
        isOnHold = false
        audioRoute = CallAudioState.ROUTE_EARPIECE
        notify(null, CallStatus.DISCONNECTED)
        notifyAudio()
    }

    fun getCall(): Call? = currentCall

    fun getAudioState(): CallAudioState? = inCallService?.callAudioState

    fun addListener(l: (Call?, CallStatus) -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: (Call?, CallStatus) -> Unit) {
        listeners.remove(l)
    }

    fun addAudioListener(l: (Boolean, Boolean, Int) -> Unit) {
        if (!audioListeners.contains(l)) audioListeners.add(l)
        l(isMuted, isOnHold, audioRoute)
    }

    fun removeAudioListener(l: (Boolean, Boolean, Int) -> Unit) {
        audioListeners.remove(l)
    }

    fun answer() = currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject() = currentCall?.reject(false, null)
    fun hangUp() = currentCall?.disconnect()

    fun hold(on: Boolean) {
        if (on) currentCall?.hold() else currentCall?.unhold()
    }

    fun toggleMute() {
        val nextMute = !isMuted
        inCallService?.setMuted(nextMute)
    }

    fun toggleSpeaker() {
        val newRoute = if (audioRoute == CallAudioState.ROUTE_SPEAKER) {
            CallAudioState.ROUTE_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        inCallService?.setAudioRoute(newRoute)
    }

    fun playDtmf(c: Char) = currentCall?.playDtmfTone(c)
    fun stopDtmf() = currentCall?.stopDtmfTone()

    private fun notify(call: Call?, status: CallStatus) {
        listeners.forEach { it(call, status) }
    }

    private fun notifyAudio() {
        audioListeners.forEach { it(isMuted, isOnHold, audioRoute) }
    }

    // RECTIFICATION : On aligne les états avec CallStatus.kt
    fun fromState(state: Int): CallStatus = when (state) {
        Call.STATE_RINGING -> CallStatus.RINGING      // Changé INCOMING -> RINGING
        Call.STATE_DIALING,
        Call.STATE_CONNECTING -> CallStatus.DIALING
        Call.STATE_ACTIVE -> CallStatus.ACTIVE
        Call.STATE_HOLDING -> CallStatus.ON_HOLD     // Changé HOLDING -> ON_HOLD
        Call.STATE_DISCONNECTED -> CallStatus.DISCONNECTED
        else -> CallStatus.IDLE
    }
}
