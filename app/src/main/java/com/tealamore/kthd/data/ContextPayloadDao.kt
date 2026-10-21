package com.tealamore.kthd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextPayloadDao {
    @Insert
    suspend fun insert(payload: ContextPayload): Long

    @Update
    suspend fun update(payload: ContextPayload)

    @Query("SELECT * FROM context_payloads WHERE sync_status = 'PENDING' OR sync_status = 'FAILED'")
    suspend fun getUnsyncedPayloads(): List<ContextPayload>

    @Query("SELECT * FROM context_payloads ORDER BY timestamp DESC")
    suspend fun getAllPayloads(): List<ContextPayload>

    @Query("SELECT * FROM context_payloads ORDER BY timestamp DESC")
    fun getAllPayloadsFlow(): Flow<List<ContextPayload>>

    @Query("DELETE FROM context_payloads WHERE sync_status = 'SYNCED'")
    suspend fun deleteSyncedPayloads()
}
