package fr.bonobo.phonezen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Contact (depuis ContentResolver, pas Room) ──
data class Contact(
    val contactId: Long,
    val name: String,
    val phoneNumber: String,
    val phoneType: Int,
    val photoUri: String? = null,
    val isFavorite: Boolean = false
)

// ── Entrée journal ──
data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,        // CallLog.Calls.TYPE
    val duration: Long,   // secondes
    val timestamp: Long,
    val isBlocked: Boolean = false
)

// ── Groupe d'appels (même numéro) ──
data class CallGroup(
    val number: String,
    val name: String?,
    val photoUri: String?,
    val calls: List<CallEntry>,
    val isFavorite: Boolean = false
) {
    val lastCall: CallEntry get() = calls.first()
    val callCount: Int get() = calls.size
    val missedCount: Int get() = calls.count { it.type == android.provider.CallLog.Calls.MISSED_TYPE }
}

// ── Room : numéros bloqués manuellement ──
@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    @PrimaryKey val number: String,
    val label: String = "",
    val reason: String = "Manuel",
    val timestamp: Long = System.currentTimeMillis()
)

// RECTIFICATION : CallState et CallStatus ont été SUPPRIMÉS d'ici
// car ils possèdent maintenant leurs propres fichiers .kt
// Cela règle les erreurs de "Redeclaration".

// ── Résultat analyse spam ──
data class SpamResult(
    val isSpam: Boolean,
    val isPrivate: Boolean = false,
    val reason: String = "",
    val riskLevel: RiskLevel = RiskLevel.NONE
)

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }