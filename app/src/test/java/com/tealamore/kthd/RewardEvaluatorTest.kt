package com.tealamore.kthd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardEvaluatorTest {

    @Test
    fun testAppClosedAfter45Seconds() {
        val startTime = 1000000L
        val now = startTime + 45000L

        val state = RewardEvaluator.evaluate(
            targetPackage = "com.instagram.android",
            currentForegroundPackage = "com.tealamore.kthd",
            isScreenOff = false,
            isLocked = false,
            startTimeMs = startTime,
            nowMs = now,
            previousScreenLockedEver = false,
            previousAppClosedEver = false,
            previousTimeToCloseSec = 300
        )

        assertFalse(state.screenLocked)
        assertTrue(state.appClosed)
        assertEquals(45, state.timeToCloseSec)
    }

    @Test
    fun testAppNeverClosedDuring5Minutes() {
        val startTime = 1000000L
        val now = startTime + 150000L

        val state = RewardEvaluator.evaluate(
            targetPackage = "com.instagram.android",
            currentForegroundPackage = "com.instagram.android",
            isScreenOff = false,
            isLocked = false,
            startTimeMs = startTime,
            nowMs = now,
            previousScreenLockedEver = false,
            previousAppClosedEver = false,
            previousTimeToCloseSec = 300
        )

        assertFalse(state.screenLocked)
        assertFalse(state.appClosed)
        assertEquals(300, state.timeToCloseSec)
    }

    @Test
    fun testScreenLockedDuringObservation() {
        val startTime = 1000000L
        val now = startTime + 30000L

        val state = RewardEvaluator.evaluate(
            targetPackage = "com.instagram.android",
            currentForegroundPackage = "com.instagram.android",
            isScreenOff = false,
            isLocked = true,
            startTimeMs = startTime,
            nowMs = now,
            previousScreenLockedEver = false,
            previousAppClosedEver = false,
            previousTimeToCloseSec = 300
        )

        assertTrue(state.screenLocked)
        assertFalse(state.appClosed)
    }

    @Test
    fun testScreenLockedPersistsWhenPreviouslyLocked() {
        val startTime = 1000000L
        val now = startTime + 60000L

        val state = RewardEvaluator.evaluate(
            targetPackage = "com.instagram.android",
            currentForegroundPackage = "com.instagram.android",
            isScreenOff = false,
            isLocked = false,
            startTimeMs = startTime,
            nowMs = now,
            previousScreenLockedEver = true,
            previousAppClosedEver = false,
            previousTimeToCloseSec = 300
        )

        assertTrue(state.screenLocked)
    }
}
