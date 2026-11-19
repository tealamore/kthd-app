package com.tealamore.kthd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingRequestDao {
    @Insert
    suspend fun insert(request: PendingRequest): Long

    @Update
    suspend fun update(request: PendingRequest)

    @Query("SELECT * FROM pending_requests WHERE id = :id")
    suspend fun getById(id: Long): PendingRequest?

    /** Oldest request that still needs sending. Requests are sent strictly in insertion order. */
    @Query("SELECT * FROM pending_requests WHERE sync_status = 'PENDING' OR sync_status = 'FAILED' ORDER BY id ASC LIMIT 1")
    suspend fun getNextUnsynced(): PendingRequest?

    @Query("SELECT * FROM pending_requests WHERE sync_status = 'PENDING' OR sync_status = 'FAILED' ORDER BY id ASC")
    suspend fun getUnsyncedRequests(): List<PendingRequest>

    @Query("SELECT * FROM pending_requests ORDER BY created_at DESC")
    suspend fun getAllRequests(): List<PendingRequest>

    /** Removes finished (synced or rejected) requests created before [cutoffMs]. */
    @Query("DELETE FROM pending_requests WHERE (sync_status = 'SYNCED' OR sync_status = 'REJECTED') AND created_at < :cutoffMs")
    suspend fun deleteFinishedBefore(cutoffMs: Long)

    @Query("DELETE FROM pending_requests")
    suspend fun deleteAll()
}
