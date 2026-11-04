package com.tealamore.kthd

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.data.TaskDao
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
@Config(sdk = [34])
class CreateTaskFragmentTest {

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
    fun testTaskCreationInDatabase() = runBlocking {
        val newTask = Task(label = "Study History", focusedTask = true)
        val taskId = taskDao.insert(newTask)

        val retrievedTask = taskDao.getTaskById(taskId)
        assertNotNull(retrievedTask)
        assertEquals("Study History", retrievedTask?.label)
        assertEquals(true, retrievedTask?.focusedTask)
    }

    @Test
    fun testCreateTaskFragmentViewCreation() {
        val activityController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = activityController.get()
        val fragment = CreateTaskFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(fragment, "CreateTaskFragment")
            .commitNow()

        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)
    }
}
