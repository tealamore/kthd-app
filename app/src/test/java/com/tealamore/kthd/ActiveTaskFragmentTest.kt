package com.tealamore.kthd

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.data.TaskDao
import com.tealamore.kthd.data.TaskStatus
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
class ActiveTaskFragmentTest {

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
    fun testActiveTaskDatabaseQuery() = runBlocking {
        val activeTask = Task(label = "Study Math", description = "Calculus Chapter 3", focusedTask = true)
        taskDao.insert(activeTask)

        val fetchedTask = taskDao.getActiveTask()
        assertNotNull(fetchedTask)
        assertEquals("Study Math", fetchedTask?.label)
        assertEquals("Calculus Chapter 3", fetchedTask?.description)
        assertEquals(true, fetchedTask?.focusedTask)
        assertEquals(TaskStatus.ACTIVE, fetchedTask?.status)
    }

    @Test
    fun testActiveTaskFragmentViewCreation() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = activityController.get()
        val fragment = ActiveTaskFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(fragment, "ActiveTaskFragment")
            .commitNow()

        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)
    }
}
