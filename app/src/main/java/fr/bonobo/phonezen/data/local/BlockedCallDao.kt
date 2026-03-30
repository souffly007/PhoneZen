package fr.bonobo.phonezen.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {
    @Insert
    suspend fun insert(call: BlockedCall)

    @androidx.room.Delete
    suspend fun delete(call: BlockedCall)

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllBlockedCalls(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    suspend fun getAllBlockedCallsOnce(): List<BlockedCall>

    @Query("DELETE FROM blocked_calls WHERE id = :callId")
    suspend fun deleteById(callId: Int)

    @Query("DELETE FROM blocked_calls")
    suspend fun clearAll()

    @Query("DELETE FROM blocked_calls")
    suspend fun deleteAll()
}