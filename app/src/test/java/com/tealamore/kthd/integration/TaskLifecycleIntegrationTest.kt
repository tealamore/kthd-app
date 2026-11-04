package com.tealamore.kthd.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.data.TaskDao
import com.tealamore.kthd.data.TaskStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskLifecycleIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun fullTaskLifecycle_createActiveAndComplete() = runBlocking {
        val initialActive = taskDao.getActiveTask()
        assertFalse(initialActive != null)

        val newTask = Task(label = "Study Calculus", focusedTask = true)
        val taskId = taskDao.insert(newTask)
        assertTrue(taskId > 0)

        val activeTask = taskDao.getActiveTaskFlow().first()
        assertNotNull(activeTask)
        assertEquals("Study Calculus", activeTask?.label)
        assertEquals(true, activeTask?.focusedTask)
        assertEquals(TaskStatus.ACTIVE, activeTask?.status)

        val completedTask = activeTask!!.copy(
            status = TaskStatus.COMPLETED,
            completedAt = System.currentTimeMillis()
        )
        taskDao.update(completedTask)

        val completedList = taskDao.getCompletedTasks().first()
        assertTrue(completedList.any { it.id == taskId && it.status == TaskStatus.COMPLETED })
    }
}
