package fr.bonobo.phonezen.data.model

/**
 * États possibles d'un appel individuel.
 */
enum class CallState {
    IDLE,
    DIALING,        // Appel sortant en cours de composition
    RINGING,        // Sonnerie entrante (1er appel)
    ACTIVE,         // Appel actif (audio bidirectionnel)
    HOLD,           // Appel mis en attente (audio coupé côté local)
    INCOMING_SECOND,// 2e appel entrant pendant qu'un appel est ACTIVE ou HOLD
    DISCONNECTED    // Appel terminé
}

/**
 * Représente l'état global du système d'appel (jusqu'à 2 appels simultanés).
 */
data class DualCallState(
    val primaryCall: CallInfo? = null,      // Appel principal (ACTIVE ou HOLD)
    val secondaryCall: CallInfo? = null,    // Second appel (INCOMING_SECOND, ACTIVE ou HOLD)
    val showSecondCallOverlay: Boolean = false
) {
    val hasActiveCall: Boolean get() = primaryCall?.state == CallState.ACTIVE
            || secondaryCall?.state == CallState.ACTIVE
    val hasTwoCalls: Boolean get() = primaryCall != null && secondaryCall != null
}

/**
 * Informations sur un appel individuel.
 */
data class CallInfo(
    val id: String,
    val displayName: String,
    val phoneNumber: String,
    val state: CallState,
    val isOutgoing: Boolean = false,
    val startTimeMs: Long = 0L
)