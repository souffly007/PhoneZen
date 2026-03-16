package fr.bonobo.phonezen.data.model

import com.google.firebase.Timestamp

/**
 * Représente un numéro signalé par la communauté dans Firestore.
 */
data class ReportedNumber(
    val number       : String    = "",
    val reports      : Long      = 0,
    val lastReported : Timestamp = Timestamp.now(),
    val expiresAt    : Timestamp = Timestamp.now(),
    val tags         : List<String> = emptyList()
) {
    /** Seuil minimum de signalements pour considérer un numéro comme suspect */
    fun isSuspect(): Boolean = reports >= 3

    /** Conversion vers Map pour l'envoi à Firestore */
    fun toMap(): Map<String, Any> = mapOf(
        "number"        to number,
        "reports"       to reports,
        "last_reported" to lastReported,
        "expires_at"    to expiresAt,
        "tags"          to tags
    )

    companion object {
        /** Noms des champs Firestore */
        const val FIELD_NUMBER        = "number"
        const val FIELD_REPORTS       = "reports"
        const val FIELD_LAST_REPORTED = "last_reported"
        const val FIELD_EXPIRES_AT    = "expires_at"
        const val FIELD_TAGS          = "tags"

        const val COLLECTION = "reported_numbers"

        /** TTL glissant : 40 jours en secondes */
        const val TTL_SECONDS = 40L * 24 * 60 * 60

        /** Seuil de signalements pour afficher un badge suspect */
        const val SUSPECT_THRESHOLD = 3L
    }
}