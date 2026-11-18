package com.tealamore.kthd

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.data.TaskDao
import com.tealamore.kthd.data.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val taskDao: TaskDao = db.taskDao()
    private val payloadDao = db.contextPayloadDao()
    private val pendingRequestDao = db.pendingRequestDao()

    val activeTask: Flow<Task?> = taskDao.getActiveTaskFlow()
    val completedTasks: Flow<List<Task>> = taskDao.getCompletedTasks()
    
    fun getAllTasks() = taskDao.getAllTasks()
    fun getAllPayloads() = payloadDao.getAllPayloadsFlow()
    fun getAllPendingRequests() = pendingRequestDao.getAllRequestsFlow()

    suspend fun hasActiveTask(): Boolean {
        return taskDao.getActiveTask() != null
    }

    suspend fun startTask(label: String, focusedTask: Boolean = false): Long {
        val newTask = Task(label = label, focusedTask = focusedTask)
        return taskDao.insert(newTask)
    }

    fun completeTask(task: Task) {
        viewModelScope.launch {
            val completedTask = task.copy(
                status = TaskStatus.COMPLETED,
                completedAt = System.currentTimeMillis()
            )
            taskDao.update(completedTask)
        }
    }
}
