package com.tealamore.kthd

import com.tealamore.kthd.network.ContextResponseEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextResponseHandlerTest {

    private val notified = mutableListOf<Long>()
    private val monitored = mutableListOf<Long>()
    private val handler = ContextResponseHandler(
        showNotification = { notified += it.contextId },
        startRewardMonitoring = { monitored += it.contextId }
    )

    private fun pending(id: Long) = ContextResponseHandler.PendingContext(
        contextId = id, taskId = 7L, targetPackage = "com.example.social", appName = "Social", deviceId = "dev"
    )

    @Test
    fun rewardMonitoringDoesNotStartBeforeTheBackendResponds() {
        handler.register(pending(1))
        assertTrue(monitored.isEmpty())
        assertTrue(notified.isEmpty())
    }

    @Test
    fun rewardMonitoringStartsAfterAResponseWithNotification() {
        handler.register(pending(1))
        handler.onResponse(ContextResponseEvent(contextId = 1, taskId = 7, notify = true))
        assertEquals(listOf(1L), notified)
        assertEquals(listOf(1L), monitored)
    }

    @Test
    fun rewardMonitoringStartsAfterAResponseWithoutNotification() {
        handler.register(pending(1))
        handler.onResponse(ContextResponseEvent(contextId = 1, taskId = 7, notify = false))
        assertTrue(notified.isEmpty())
        assertEquals(listOf(1L), monitored)
    }

    @Test
    fun eachContextStartsMonitoringAtMostOnce() {
        handler.register(pending(1))
        handler.onResponse(ContextResponseEvent(1, 7, notify = false))
        handler.onResponse(ContextResponseEvent(1, 7, notify = false))
        assertEquals(listOf(1L), monitored)
    }

    @Test
    fun responsesForUnknownOrClearedContextsAreIgnored() {
        handler.onResponse(ContextResponseEvent(99, 7, notify = true)) // e.g. synced after the service restarted

        handler.register(pending(2))
        handler.clear() // service destroyed / task ended
        handler.onResponse(ContextResponseEvent(2, 7, notify = true))

        handler.register(pending(3))
        handler.unregister(3) // enqueue was refused
        handler.onResponse(ContextResponseEvent(3, 7, notify = true))

        assertTrue(notified.isEmpty())
        assertTrue(monitored.isEmpty())
    }
}
