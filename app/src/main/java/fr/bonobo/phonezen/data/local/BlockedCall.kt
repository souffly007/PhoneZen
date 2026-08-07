package fr.bonobo.phonezen.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_calls",
    indices = [
        Index(value = ["number"]),
        Index(value = ["timestamp"])
    ]
)
data class BlockedCall(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val number: String,
    val reason: String,
    val riskLevel: String,
    val timestamp: Long = System.currentTimeMillis()
)