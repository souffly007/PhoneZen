package fr.bonobo.phonezen.data.dao

import androidx.room.*
import fr.bonobo.phonezen.data.model.BlockedNumber
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BlockedNumber>>

    @Query("SELECT COUNT(*) FROM blocked_numbers WHERE number = :number")
    suspend fun isBlocked(number: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlockedNumber)

    @Delete
    suspend fun delete(entry: BlockedNumber)

    @Query("DELETE FROM blocked_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)
}
