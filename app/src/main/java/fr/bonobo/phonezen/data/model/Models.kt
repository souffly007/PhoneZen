package fr.bonobo.phonezen.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Entrée journal ──
data class CallEntry(
    val id       : Long,
    val number   : String,
    val name     : String?,
    val type     : Int,        // CallLog.Calls.TYPE
    val duration : Long,       // secondes
    val timestamp: Long,
    val isBlocked: Boolean = false,
    val simSlot  : Int = -1    // -1 = inconnu, 0 = SIM1, 1 = SIM2
)

// ── Groupe d'appels (même numéro) ──
data class CallGroup(
    val number    : String,
    val name      : String?,
    val photoUri  : String?,
    val calls     : List<CallEntry>,
    val isFavorite: Boolean = false
) {
    val lastCall   : CallEntry get() = calls.first()
    val callCount  : Int get() = calls.size
    val missedCount: Int get() = calls.count {
        it.type == android.provider.CallLog.Calls.MISSED_TYPE && it.duration == 0L
    }
}

// ── Room : numéros bloqués manuellement ──
@Entity(tableName = "blocked_numbers")
data class BlockedNumber(
    @PrimaryKey val number   : String,
    val label    : String = "",
    val reason   : String = "Manuel",
    val timestamp: Long = System.currentTimeMillis()
)

// ── Résultat analyse spam ──
data class SpamResult(
    val isSpam   : Boolean,
    val isPrivate: Boolean = false,
    val reason   : String = "",
    val riskLevel: RiskLevel = RiskLevel.NONE
)

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }