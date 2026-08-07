package fr.bonobo.phonezen.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ── Entité ──
@Entity(tableName = "call_notes")
data class CallNote(
    @PrimaryKey val number   : String,
    val note                 : String,
    val timestamp            : Long = System.currentTimeMillis()
)

// ── DAO ──
@Dao
interface CallNoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: CallNote)

    @Query("SELECT * FROM call_notes WHERE number = :number LIMIT 1")
    suspend fun getNote(number: String): CallNote?

    @Query("SELECT * FROM call_notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<CallNote>>

    @Query("SELECT * FROM call_notes ORDER BY timestamp DESC")
    suspend fun getAllNotesOnce(): List<CallNote>

    @Query("DELETE FROM call_notes WHERE number = :number")
    suspend fun deleteNote(number: String)

    @Query("DELETE FROM call_notes")
    suspend fun deleteAll()
}