package fr.bonobo.phonezen.data.model

/**
 * Représente l'état complet d'un appel en cours pour l'UI.
 */
data class CallState(
    val number: String = "",
    val contactName: String? = null,
    val status: CallStatus = CallStatus.IDLE,
    val durationSec: Long = 0,
    val isMuted: Boolean = false,
    val isSpeaker: Boolean = false,
    val isOnHold: Boolean = false,
    val isSpam: Boolean = false,
    val spamReason: String? = null
)