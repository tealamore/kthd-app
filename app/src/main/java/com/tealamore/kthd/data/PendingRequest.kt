package com.tealamore.kthd.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "pending_requests")
data class PendingRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "request_type") val requestType: RequestType,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "last_attempt_timestamp") val lastAttemptTimestamp: Long = 0L,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING,
    /** Sent as the `Idempotency-Key` header; the same on every retry of this request. */
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String = UUID.randomUUID().toString()
)
