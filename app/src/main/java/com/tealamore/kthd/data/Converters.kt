package com.tealamore.kthd.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStatus(status: TaskStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): TaskStatus {
        return TaskStatus.valueOf(value)
    }

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus {
        return SyncStatus.valueOf(value)
    }

    @TypeConverter
    fun fromRequestType(type: RequestType): String {
        return type.name
    }

    @TypeConverter
    fun toRequestType(value: String): RequestType {
        return RequestType.valueOf(value)
    }
}
