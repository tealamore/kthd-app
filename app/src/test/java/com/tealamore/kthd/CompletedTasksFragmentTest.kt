package com.tealamore.kthd

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.data.TaskDao
import com.tealamore.kthd.data.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CompletedTasksFragmentTest {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        taskDao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testCompletedTasksFlow() = runBlocking {
        val task1 = Task(label = "Task 1", status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis())
        val task2 = Task(label = "Task 2", status = TaskStatus.COMPLETED, completedAt = System.currentTimeMillis() + 1000)

        taskDao.insert(task1)
        taskDao.insert(task2)

        val completedTasks = taskDao.getCompletedTasks().first()
        assertEquals(2, completedTasks.size)
    }

    @Test
    fun testCompletedTasksFragmentViewCreation() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = activityController.get()
        val fragment = CompletedTasksFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(fragment, "CompletedTasksFragment")
            .commitNow()

        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)
    }
}
