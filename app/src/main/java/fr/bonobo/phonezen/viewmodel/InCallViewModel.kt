// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025-2026 Franck R-F (souffly007)
// This file is part of PhoneZen.
package fr.bonobo.phonezen.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.telecom.Call
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bonobo.phonezen.arcep.ArcepLookupHelper
import fr.bonobo.phonezen.ameli.AmeliLookupHelper
import fr.bonobo.phonezen.dila.DilaLookupHelper
import fr.bonobo.phonezen.police.PoliceLookupHelper
import fr.bonobo.phonezen.data.model.ArcepInfo
import fr.bonobo.phonezen.data.model.AudioRoute
import fr.bonobo.phonezen.data.model.CallTrustLevel
import fr.bonobo.phonezen.data.model.CallUiState
import fr.bonobo.phonezen.data.model.CallStatus
import fr.bonobo.phonezen.service.CallManager
import fr.bonobo.phonezen.utils.ContactResolver
import fr.bonobo.phonezen.utils.CrashHandler
import fr.bonobo.phonezen.utils.PhoneUtils
import fr.bonobo.phonezen.utils.SpamDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class InCallViewModel(app: Application) : AndroidViewModel(app) {

    private val detector     = SpamDetector(app)
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val arcep        = ArcepLookupHelper.getInstance(app)
    private val dila         = DilaLookupHelper.getInstance(app)
    private val ameli        = AmeliLookupHelper.getInstance(app)
    private val policeNat    = PoliceLookupHelper.getInstance(app)
    private val TAG          = "InCallViewModel"

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private var timerJob        : Job? = null
    private var contactLookupJob: Job? = null
    private var trustJob        : Job? = null
    private var arcepJob        : Job? = null

    // ─── Listeners CallManager ────────────────────────────────────────────────

    private val listener: (Call?, CallStatus) -> Unit = listener@{ call, status ->
        val number = call?.details?.handle?.schemeSpecificPart ?: ""
        Log.d(TAG, "listener: status=$status number=$number")

        CrashHandler.lastAction = "CallManager.listener: status=$status"

        if (status == _state.value.status && number == _state.value.number) {
            Log.d(TAG, "Doublon ignoré: $status / $number")
            return@listener
        }

        val isOutgoing = status == CallStatus.DIALING
        val spam = if (!isOutgoing && number.isNotEmpty() && number != _state.value.number)
            detector.analyze(number) else null

        _state.update {
            it.copy(
                number     = number,
                status     = status,
                isSpam     = if (isOutgoing) false else (spam?.isSpam ?: it.isSpam),
                spamReason = if (isOutgoing) null  else (spam?.reason ?: it.spamReason),
                isOnHold   = when (status) {
                    CallStatus.ACTIVE       -> false
                    CallStatus.ON_HOLD      -> true
                    CallStatus.DISCONNECTED,
                    CallStatus.IDLE         -> false
                    else                    -> it.isOnHold
                }
            )
        }

        if (number.isNotEmpty() && number != _state.value.number) {
            _state.update {
                it.copy(
                    contactName = null,
                    trustLevel  = CallTrustLevel.Unknown,
                    arcepInfo   = null,
                )
            }
            resolveContact(number)
            resolveCallTrust(number)
            resolveArcep(number, isOutgoing)
        }

        when (status) {
            CallStatus.ACTIVE -> {
                CrashHandler.lastAction = "startTimer: appel actif"
                startTimer()
            }
            CallStatus.DISCONNECTED, CallStatus.IDLE -> {
                CrashHandler.lastAction = "stopTimer: appel terminé"
                stopTimer()
                resetAudio()
            }
            else -> {}
        }
    }

    private val audioListener: (Boolean, Boolean, AudioRoute, Boolean) -> Unit =
        { muted, onHold, route, btAvailable ->
            Log.d(TAG, "audioListener: muted=$muted onHold=$onHold route=$route bt=$btAvailable")
            _state.update {
                it.copy(
                    isMuted       = muted,
                    isOnHold      = onHold,
                    audioRoute    = route,
                    isBtAvailable = btAvailable
                )
            }
        }

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        Log.d(TAG, "ViewModel Initialized: ${this.hashCode()}")
        CrashHandler.lastAction = "InCallViewModel.init"
        CallManager.addListener(listener)
        CallManager.addAudioListener(audioListener)
        initializeFromManager()
    }

    private fun initializeFromManager() {
        CrashHandler.lastAction = "InCallViewModel.initializeFromManager"
        CallManager.getCall()?.let { call ->
            val status     = CallManager.fromState(call.state)
            val number     = call.details?.handle?.schemeSpecificPart ?: ""
            val isOutgoing = status == CallStatus.DIALING
            _state.update {
                it.copy(
                    number   = number,
                    status   = status,
                    isOnHold = status == CallStatus.ON_HOLD,
                    isSpam   = false,
                )
            }
            resolveContact(number)
            resolveCallTrust(number)
            resolveArcep(number, isOutgoing)
            if (status == CallStatus.ACTIVE) startTimer()
        }
    }

    private fun resolveContact(number: String) {
        if (number.isBlank()) return
        CrashHandler.lastAnalyzedNumber = number
        CrashHandler.lastAction         = "resolveContact: $number"
        contactLookupJob?.cancel()
        contactLookupJob = viewModelScope.launch(Dispatchers.IO) {
            val (name, _) = PhoneUtils.lookupContact(getApplication(), number)
            if (name != null) {
                _state.update { it.copy(contactName = name) }
            }
        }
    }

    private fun resolveArcep(number: String, isOutgoing: Boolean) {
        if (number.isBlank()) return
        CrashHandler.lastAnalyzedNumber = number
        CrashHandler.lastAction         = "resolveArcep: $number"
        arcepJob?.cancel()
        arcepJob = viewModelScope.launch(Dispatchers.IO) {
            val info = arcep.lookup(number) ?: return@launch
            if (info.isUnknown) return@launch

            val arcepInfo = ArcepInfo(
                operateur        = info.operateur,
                categorie        = info.categorie,
                territoire       = info.territoire,
                isSuspiciousType = info.isSuspiciousType,
            )
            _state.update { it.copy(arcepInfo = arcepInfo) }

            if (!isOutgoing && info.isSuspiciousType) {
                val current = _state.value.trustLevel
                if (current == CallTrustLevel.Unknown) {
                    _state.update { it.copy(trustLevel = CallTrustLevel.Suspicious) }
                    Log.d(TAG, "ARCEP: numéro surtaxé → trustLevel = Suspicious")
                }
            }
        }
    }

    /**
     * Détermine le niveau de confiance de l'appelant.
     *
     * Appel SORTANT (DIALING) :
     *   → Contact connu  = Trusted
     *   → Inconnu        = Unknown
     *
     * Appel ENTRANT :
     *   1. SpamDetector positif          → Spam
     *   2. Whitelist santé FINESS        → Trusted
     *   3. Whitelist service public DILA → Trusted
     *   4. Whitelist Ameli               → Trusted
     *   5. Police Nationale              → Trusted
     *   6. Contact Android enregistré    → Trusted
     *   7. Mobile 06/07 non enregistré   → Suspicious
     *   8. Sinon                         → Unknown
     */
    private fun resolveCallTrust(number: String) {
        if (number.isBlank()) return
        CrashHandler.lastAnalyzedNumber = number
        CrashHandler.lastAction         = "resolveCallTrust: début"
        trustJob?.cancel()
        trustJob = viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()

            // ── Appel sortant ─────────────────────────────────────────────
            val isOutgoing = _state.value.status == CallStatus.DIALING
            if (isOutgoing) {
                CrashHandler.lastAction = "resolveCallTrust: appel sortant ContactResolver"
                val contactName = ContactResolver.resolveName(app, number)
                _state.update {
                    it.copy(
                        trustLevel = if (contactName != null) CallTrustLevel.Trusted
                        else CallTrustLevel.Unknown
                    )
                }
                return@launch
            }

            // ── Appel entrant ─────────────────────────────────────────────

            // 1. SpamDetector
            CrashHandler.lastAction = "resolveCallTrust: SpamDetector"
            val spamResult = detector.analyze(number)
            if (spamResult.isSpam) {
                _state.update { it.copy(trustLevel = CallTrustLevel.Spam) }
                return@launch
            }

            // 2. Whitelist santé FINESS
            CrashHandler.lastAction = "resolveCallTrust: FINESS"
            val isHealthcare = spamResult.reason?.contains("santé", ignoreCase = true) == true
                    || spamResult.reason?.contains("Établissement", ignoreCase = true) == true
            if (isHealthcare) {
                _state.update { it.copy(trustLevel = CallTrustLevel.Trusted) }
                return@launch
            }

            // 3. Whitelist service public DILA
            CrashHandler.lastAction = "resolveCallTrust: DILA"
            val dilaInfo = dila.lookup(number)
            if (dilaInfo != null) {
                Log.d(TAG, "DILA: ${dilaInfo.nom} (${dilaInfo.categorie}) → Trusted")
                _state.update { it.copy(trustLevel = CallTrustLevel.Trusted) }
                return@launch
            }

            // 4. Whitelist Ameli
            CrashHandler.lastAction = "resolveCallTrust: Ameli"
            val ameliInfo = ameli.lookup(number)
            if (ameliInfo != null) {
                Log.d(TAG, "Ameli: ${ameliInfo.specialite} → Trusted")
                _state.update { it.copy(trustLevel = CallTrustLevel.Trusted) }
                return@launch
            }

            // 5. Police Nationale
            CrashHandler.lastAction = "resolveCallTrust: Police Nationale"
            val policeInfo = policeNat.lookup(number)
            if (policeInfo != null) {
                Log.d(TAG, "Police: ${policeInfo.nom} → Trusted")
                _state.update { it.copy(trustLevel = CallTrustLevel.Trusted) }
                return@launch
            }

            // 6. Contact Android enregistré
            CrashHandler.lastAction = "resolveCallTrust: ContactResolver"
            val contactName = ContactResolver.resolveName(app, number)
            if (contactName != null) {
                _state.update { it.copy(trustLevel = CallTrustLevel.Trusted) }
                return@launch
            }

            // 7. Mobile 06/07 non enregistré → Suspicious
            CrashHandler.lastAction = "resolveCallTrust: détection mobile inconnu"
            val normalized = PhoneUtils.normalizeNumber(number)
            val isMobile   = normalized.startsWith("06") || normalized.startsWith("07")
                    || normalized.startsWith("+336") || normalized.startsWith("+337")
            if (isMobile) {
                _state.update { it.copy(trustLevel = CallTrustLevel.Suspicious) }
                return@launch
            }

            // 8. Inconnu mais pas suspect
            CrashHandler.lastAction = "resolveCallTrust: terminé → Unknown"
            _state.update { it.copy(trustLevel = CallTrustLevel.Unknown) }
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    fun answer() {
        CrashHandler.lastAction = "InCallViewModel.answer"
        CallManager.answer()
    }

    fun reject() {
        CrashHandler.lastAction = "InCallViewModel.reject"
        CallManager.reject()
    }

    fun hangUp() {
        CrashHandler.lastAction = "InCallViewModel.hangUp"
        CallManager.hangUp()
        resetAudio()
    }

    fun toggleMute()                     = CallManager.toggleMute()
    fun setAudioRoute(route: AudioRoute) = CallManager.setAudioRoute(route)

    fun toggleHold() {
        val currentIsOnHold = _state.value.isOnHold
        val shouldHold      = !currentIsOnHold
        CrashHandler.lastAction = "InCallViewModel.toggleHold → $shouldHold"
        Log.d(TAG, "Tentative de toggleHold. Actuel: $currentIsOnHold")
        CallManager.hold(shouldHold)
        _state.update { it.copy(isOnHold = shouldHold) }
    }

    fun playDtmf(c: Char) = CallManager.playDtmf(c)
    fun stopDtmf()        = CallManager.stopDtmf()

    // ─── Timer ────────────────────────────────────────────────────────────────

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

    private fun resetAudio() {
        try {
            audioManager.mode             = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            _state.update {
                it.copy(
                    isMuted    = false,
                    audioRoute = AudioRoute.EARPIECE,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "resetAudio error: ${e.message}")
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCleared() {
        Log.d(TAG, "ViewModel Cleared: ${this.hashCode()}")
        CrashHandler.lastAction = "InCallViewModel.onCleared"
        CallManager.removeListener(listener)
        CallManager.removeAudioListener(audioListener)
        stopTimer()
        contactLookupJob?.cancel()
        trustJob?.cancel()
        arcepJob?.cancel()
        super.onCleared()
    }
}
