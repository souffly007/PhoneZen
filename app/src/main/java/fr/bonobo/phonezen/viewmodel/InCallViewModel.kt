package fr.bonobo.phonezen.viewmodel

import android.app.Application
import android.telecom.CallAudioState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.model.CallState
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.service.CallManager
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class InCallViewModel(app: Application) : AndroidViewModel(app) {

    private val detector = SpamDetector(app)

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state

    private var timerJob: Job? = null

    // Listener mis à jour pour correspondre à notre Enum CallStatus
    private val listener: (android.telecom.Call?, CallStatus) -> Unit = { call, status ->
        val number = call?.details?.handle?.schemeSpecificPart ?: ""
        val name = if (number.isNotEmpty()) PhoneUtils.lookupContactName(app, number) else null

        val spam = if (number.isNotEmpty() && _state.value.number != number) detector.analyze(number) else null

        _state.value = _state.value.copy(
            number = number,
            contactName = name,
            status = status,
            isSpam = spam?.isSpam ?: _state.value.isSpam,
            spamReason = spam?.reason ?: _state.value.spamReason,
            // Correction : On utilise ON_HOLD défini dans notre Enum
            isOnHold = status == CallStatus.ON_HOLD
        )

        when (status) {
            CallStatus.ACTIVE -> startTimer()
            CallStatus.DISCONNECTED, CallStatus.IDLE -> stopTimer()
            else -> { /* Pas de timer pour RINGING ou DIALING */ }
        }
    }

    private val audioListener: (Boolean, Boolean, Int) -> Unit = { muted, onHold, route ->
        _state.value = _state.value.copy(
            isMuted = muted,
            isOnHold = onHold,
            isSpeaker = route == CallAudioState.ROUTE_SPEAKER
        )
    }

    init {
        CallManager.addListener(listener)
        CallManager.addAudioListener(audioListener)

        CallManager.getCall()?.let { call ->
            val status = CallManager.fromState(call.state)
            listener(call, status)

            if (status == CallStatus.ACTIVE) {
                val connectTime = call.details.connectTimeMillis
                if (connectTime > 0) {
                    val elapsed = (System.currentTimeMillis() - connectTime) / 1000
                    _state.value = _state.value.copy(durationSec = elapsed)
                    startTimer()
                }
            }
        }

        CallManager.getAudioState()?.let { audio ->
            audioListener(audio.isMuted, _state.value.isOnHold, audio.route)
        }
    }

    override fun onCleared() {
        super.onCleared()
        CallManager.removeListener(listener)
        CallManager.removeAudioListener(audioListener)
        stopTimer()
    }

    // --- Actions appelées par InCallScreen ---
    fun answer() = CallManager.answer()
    fun reject() = CallManager.reject()
    fun hangUp() = CallManager.hangUp()

    fun toggleMute() = CallManager.toggleMute()
    fun toggleSpeaker() = CallManager.toggleSpeaker()

    fun toggleHold() {
        val currentlyOnHold = _state.value.isOnHold
        CallManager.hold(!currentlyOnHold)
    }

    fun playDtmf(c: Char) = CallManager.playDtmf(c)
    fun stopDtmf() = CallManager.stopDtmf()

    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.value = _state.value.copy(durationSec = _state.value.durationSec + 1)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}