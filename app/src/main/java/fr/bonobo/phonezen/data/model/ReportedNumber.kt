package fr.bonobo.phonezen.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Représente un numéro signalé par la communauté dans Supabase.
 */
@Serializable
data class ReportedNumber(
    @SerialName("number")        val number       : String      = "",
    @SerialName("reports")       val reports      : Long        = 0,
    @SerialName("last_reported") val lastReported : String      = "",   // ISO-8601 ex: "2025-01-15T14:30:00Z"
    @SerialName("expires_at")    val expiresAt    : String      = "",
    @SerialName("tags")          val tags         : List<String> = emptyList()
) {
    /** Seuil minimum de signalements pour considérer un numéro comme suspect */
    fun isSuspect(): Boolean = reports >= SUSPECT_THRESHOLD

    companion object {
        const val TABLE              = "reported_numbers"
        const val FIELD_NUMBER       = "number"
        const val FIELD_REPORTS      = "reports"
        const val FIELD_LAST_REPORTED = "last_reported"
        const val FIELD_EXPIRES_AT   = "expires_at"
        const val FIELD_TAGS         = "tags"

        /** TTL glissant : 40 jours en secondes */
        const val TTL_SECONDS        = 40L * 24 * 60 * 60

        /** Seuil de signalements pour afficher un badge suspect */
        const val SUSPECT_THRESHOLD  = 3L
    }
}
