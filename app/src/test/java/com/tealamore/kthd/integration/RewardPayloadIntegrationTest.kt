package com.tealamore.kthd.integration

import com.tealamore.kthd.network.RewardPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardPayloadIntegrationTest {

    @Test
    fun testRewardPayloadCreation() {
        val payload = RewardPayload(
            deviceId = "test_device_id",
            contextId = 10L,
            taskId = 5L,
            screenLocked = true,
            appClosed = true,
            timeToClose = 45
        )

        assertEquals("test_device_id", payload.deviceId)
        assertEquals(10L, payload.contextId)
        assertEquals(5L, payload.taskId)
        assertTrue(payload.screenLocked)
        assertTrue(payload.appClosed)
        assertEquals(45, payload.timeToClose)
    }

    @Test
    fun testRewardPayloadDefaultsWhenNotClosed() {
        val payload = RewardPayload(
            deviceId = "test_device_id",
            contextId = 11L,
            taskId = 6L,
            screenLocked = false,
            appClosed = false,
            timeToClose = 300
        )

        assertFalse(payload.screenLocked)
        assertFalse(payload.appClosed)
        assertEquals(300, payload.timeToClose)
    }
}
