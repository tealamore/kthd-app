package com.tealamore.kthd.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class TaskDatabaseTest {

    private lateinit var taskDao: TaskDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        taskDao = db.taskDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun writeTaskAndReadInList() = runBlocking {
        val task = Task(label = "Test Task", description = "Test Description")
        taskDao.insert(task)
        val allTasks = taskDao.getAllTasks().first()
        assertEquals(allTasks[0].label, task.label)
    }

    @Test
    fun updateTaskStatus() = runBlocking {
        val task = Task(label = "Test Task")
        val id = taskDao.insert(task)
        val savedTask = taskDao.getTaskById(id)
        assertNotNull(savedTask)
        
        val updatedTask = savedTask!!.copy(status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis())
        taskDao.update(updatedTask)
        
        val newSavedTask = taskDao.getTaskById(id)
        assertEquals(TaskStatus.COMPLETED, newSavedTask?.status)
        assertNotNull(newSavedTask?.completedAt)
    }
}
