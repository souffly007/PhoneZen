package fr.bonobo.phonezen.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.data.model.CallState
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.service.CallManager
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InCallViewModel(app: Application) : AndroidViewModel(app) {

    private val detector     = SpamDetector(app)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val TAG = "InCallViewModel"

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state

    private var timerJob       : Job? = null
    private var contactLookupJob: Job? = null

    // ─────────────────────────────────────────────
    // RÉSOLUTION CONTACT — toujours sur IO
    // ─────────────────────────────────────────────
    /**
     * Lance la résolution du nom + photo en arrière-plan.
     * Annule un éventuel lookup précédent (changement d'appel rapide).
     * Met à jour le state dès que le résultat arrive.
     */
    private fun resolveContact(number: String) {
        if (number.isBlank()) return

        contactLookupJob?.cancel()
        contactLookupJob = viewModelScope.launch(Dispatchers.IO) {
            // Retry jusqu'à 3 fois avec délai croissant
            // (MIUI peut refuser le ContentResolver les premières ms)
            repeat(3) { attempt ->
                if (_state.value.contactName != null) return@repeat   // déjà résolu

                if (attempt > 0) delay(700L * attempt)

                val (name, photo) = PhoneUtils.lookupContact(getApplication(), number)

                Log.d(TAG, "resolveContact attempt=$attempt number=$number → name=$name")

                if (name != null) {
                    _state.update { it.copy(contactName = name) }
                    // si ton CallState a un champ photoUri, décommente :
                    // _state.update { it.copy(contactName = name, photoUri = photo) }
                    return@launch   // succès, on arrête les retries
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // LISTENERS CallManager
    // ─────────────────────────────────────────────
    private val listener: (Call?, CallStatus) -> Unit = { call, status ->
        val number = call?.details?.handle?.schemeSpecificPart ?: ""

        // Analyse spam uniquement si c'est un nouveau numéro
        val spam = if (number.isNotEmpty() && _state.value.number != number)
            detector.analyze(number) else null

        // Mise à jour immédiate (sans nom — arrive en async ci-dessous)
        _state.update {
            it.copy(
                number     = number,
                status     = status,
                isSpam     = spam?.isSpam   ?: it.isSpam,
                spamReason = spam?.reason   ?: it.spamReason,
                isOnHold   = status == CallStatus.ON_HOLD
            )
        }

        // Résolution du contact en IO — reset si nouveau numéro
        if (number.isNotEmpty() && number != _state.value.number) {
            _state.update { it.copy(contactName = null) }
        }
        resolveContact(number)

        // Gestion du double appel : cherche s'il y a un autre appel (en attente)
        val otherCall = CallManager.getAudioState()?.let {
            // Ici on triche un peu car l'API Call ne donne pas facilement les autres appels
            // Mais CallManager les a.
            null
        }

        when (status) {
            CallStatus.ACTIVE -> startTimer()
            CallStatus.DISCONNECTED, CallStatus.IDLE -> {
                stopTimer()
                resetAudio()
                // Remet à zéro le nom pour ne pas polluer l'appel suivant
                _state.update { it.copy(contactName = null) }
            }
            else -> { /* RINGING / DIALING : pas de timer */ }
        }
    }

    // AudioListener — source de vérité pour le son
    private val audioListener: (Boolean, Boolean, Int) -> Unit = { muted, onHold, route ->
        val isSpeaker   = route == CallAudioState.ROUTE_SPEAKER
        val isBluetooth = route == CallAudioState.ROUTE_BLUETOOTH
        val isWired     = route == CallAudioState.ROUTE_WIRED_HEADSET

        // Vérifie s'il y a un appel en attente via CallManager
        val hasHold = CallManager.getCallCount() > 1 &&
                     (onHold || CallManager.getCall()?.state == Call.STATE_ACTIVE)

        _state.update {
            it.copy(
                isMuted     = muted,
                isOnHold    = onHold,
                hasHoldCall = hasHold,
                isSpeaker   = isSpeaker,
                isBluetooth = isBluetooth,
                isWired     = isWired
            )
        }
        Log.d(TAG, "audioListener — route=$route isSpeaker=$isSpeaker hasHold=$hasHold isMuted=$muted")
    }

    // ─────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────
    init {
        CallManager.addListener(listener)
        CallManager.addAudioListener(audioListener)

        // Restaure l'état si le ViewModel est recréé en cours d'appel
        CallManager.getCall()?.let { call ->
            val status = CallManager.fromState(call.state)
            val number = call.details?.handle?.schemeSpecificPart ?: ""

            _state.update { it.copy(number = number, status = status) }

            // Résolution contact en arrière-plan dès l'init
            resolveContact(number)

            if (status == CallStatus.ACTIVE) {
                val connectTime = call.details.connectTimeMillis
                if (connectTime > 0) {
                    val elapsed = (System.currentTimeMillis() - connectTime) / 1000
                    _state.update { it.copy(durationSec = elapsed) }
                    startTimer()
                }
            }
        }

        CallManager.getAudioState()?.let { audio ->
            audioListener(audio.isMuted, _state.value.isOnHold, audio.route)
        }
    }

    // ─────────────────────────────────────────────
    // CLEANUP
    // ─────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        CallManager.removeListener(listener)
        CallManager.removeAudioListener(audioListener)
        stopTimer()
        contactLookupJob?.cancel()
    }

    // ─────────────────────────────────────────────
    // ACTIONS
    // ─────────────────────────────────────────────
    fun answer()  = CallManager.answer()
    fun reject()  = CallManager.reject()

    fun hangUp() {
        CallManager.hangUp()
        resetAudio()
    }

    fun toggleMute()    = CallManager.toggleMute()
    fun toggleAudioRoute() {
        Log.d(TAG, "toggleAudioRoute — cycle audio route")
        CallManager.cycleAudioRoute()
    }

    fun toggleHold()    = CallManager.hold(!_state.value.isOnHold)
    fun swapCalls()     = CallManager.swapCalls()
    fun playDtmf(c: Char) = CallManager.playDtmf(c)
    fun stopDtmf()        = CallManager.stopDtmf()

    // ─────────────────────────────────────────────
    // TIMER
    // ─────────────────────────────────────────────
    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(durationSec = it.durationSec + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ─────────────────────────────────────────────
    // RESET AUDIO
    // ─────────────────────────────────────────────
    private fun resetAudio() {
        try {
            // On laisse InCallService gérer le mode audio et le Bluetooth SCO.
            // On se contente de remettre l'UI à un état neutre.
            _state.update {
                it.copy(
                    isSpeaker   = false,
                    isBluetooth = false,
                    isWired     = false,
                    isMuted     = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "resetAudio error: ${e.message}")
        }
    }
}