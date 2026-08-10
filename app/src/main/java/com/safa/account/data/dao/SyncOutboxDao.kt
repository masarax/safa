package com.safa.account.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.safa.account.data.model.SyncOutbox
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncOutboxDao {
    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingOutbox(): List<SyncOutbox>

    @Query("SELECT * FROM sync_outbox ORDER BY id DESC")
    fun getAllOutboxFlow(): Flow<List<SyncOutbox>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(outbox: SyncOutbox): Long

    @Update
    suspend fun update(outbox: SyncOutbox)

    @Query("UPDATE sync_outbox SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_outbox SET status = :status, lastError = :error, retryCount = retryCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markFailed(id: Int, status: String, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM sync_outbox WHERE status = 'SYNCED'")
    suspend fun purgeSynced()
}
