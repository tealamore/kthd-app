package com.tealamore.kthd.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("SELECT * FROM tasks ORDER BY started_at DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveTask(): Task?

    @Query("SELECT * FROM tasks WHERE status = 'ACTIVE' LIMIT 1")
    fun getActiveTaskFlow(): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completed_at DESC")
    fun getCompletedTasks(): Flow<List<Task>>
}
