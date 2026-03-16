package fr.bonobo.phonezen.data.model

enum class CallStatus {
    IDLE,           // Aucun appel
    RINGING,        // Appel entrant (ça sonne)
    DIALING,        // Appel sortant (en cours de numérotation)
    ACTIVE,         // En communication (décroché)
    DISCONNECTED,   // Appel terminé
    CONNECTING,     // Transition
    ON_HOLD         // En attente
}