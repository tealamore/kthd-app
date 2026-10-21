package com.tealamore.kthd.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "status") val status: TaskStatus = TaskStatus.ACTIVE,
    @ColumnInfo(name = "focused_task") val focusedTask: Boolean = false,
    @ColumnInfo(name = "started_at") val startedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null
)

