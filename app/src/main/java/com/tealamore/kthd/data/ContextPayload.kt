package com.tealamore.kthd.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "context_payloads")
data class ContextPayload(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    @ColumnInfo(name = "device_id") @SerializedName("device_id") val deviceId: String = "",
    @ColumnInfo(name = "app_type") val appType: String,
    @ColumnInfo(name = "continuous_duration") val continuousDuration: Long,
    @ColumnInfo(name = "switch_count") val switchCount: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_status") val syncStatus: SyncStatus = SyncStatus.PENDING
)
