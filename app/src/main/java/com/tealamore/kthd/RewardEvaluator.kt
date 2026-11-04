package com.tealamore.kthd

object RewardEvaluator {

    data class RewardState(
        val screenLocked: Boolean,
        val appClosed: Boolean,
        val timeToCloseSec: Int
    )

    fun evaluate(
        targetPackage: String,
        currentForegroundPackage: String?,
        isScreenOff: Boolean,
        isLocked: Boolean,
        startTimeMs: Long,
        nowMs: Long,
        previousScreenLockedEver: Boolean,
        previousAppClosedEver: Boolean,
        previousTimeToCloseSec: Int
    ): RewardState {
        val screenLockedEver = previousScreenLockedEver || isLocked || isScreenOff

        var appClosedEver = previousAppClosedEver
        var timeToCloseSec = previousTimeToCloseSec

        if (!appClosedEver && currentForegroundPackage != targetPackage) {
            appClosedEver = true
            val elapsedSec = ((nowMs - startTimeMs) / 1000).toInt()
            timeToCloseSec = elapsedSec.coerceIn(0, 300)
        }

        return RewardState(
            screenLocked = screenLockedEver,
            appClosed = appClosedEver,
            timeToCloseSec = timeToCloseSec
        )
    }
}
