package com.tealamore.kthd

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskForegroundServiceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testServiceCreationAndBinding() {
        val serviceController = Robolectric.buildService(TaskForegroundService::class.java)
        val service = serviceController.get()
        assertNotNull(service)

        val binder = service.onBind(Intent(context, TaskForegroundService::class.java))
        assertNull(binder)
    }

    @Test
    fun testStartCommandWithTaskInfo() {
        val serviceController = Robolectric.buildService(TaskForegroundService::class.java)
        val service = serviceController.get()

        val intent = Intent(context, TaskForegroundService::class.java).apply {
            putExtra("extra_task_label", "Coding Feature")
            putExtra("extra_task_id", 100L)
        }

        val startResult = service.onStartCommand(intent, 0, 1)
        assertEquals(android.app.Service.START_STICKY, startResult)
    }

    @Test
    fun testStopCommand() {
        val serviceController = Robolectric.buildService(TaskForegroundService::class.java)
        val service = serviceController.get()

        val stopIntent = Intent(context, TaskForegroundService::class.java).apply {
            action = "action_stop"
        }

        val startResult = service.onStartCommand(stopIntent, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, startResult)
    }
}
