package com.tealamore.kthd

import com.tealamore.kthd.network.ContextResponseEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks the context requests sent while a task is running, and starts reward monitoring only
 * once the backend has responded to one, whether or not it asked for a notification.
 *
 * Responses for contexts this handler didn't register (e.g. ones synced after the task or
 * service ended) are ignored, so they never produce a reward.
 */
class ContextResponseHandler(
    private val showNotification: (PendingContext) -> Unit,
    private val startRewardMonitoring: (PendingContext) -> Unit
) {
    data class PendingContext(
        val contextId: Long,
        val taskId: Long,
        val targetPackage: String,
        val appName: String,
        val deviceId: String
    )

    private val awaitingResponse = ConcurrentHashMap<Long, PendingContext>()

    /** Call before the context request is enqueued, so its response can't be missed. */
    fun register(pending: PendingContext) {
        awaitingResponse[pending.contextId] = pending
    }

    fun unregister(contextId: Long) {
        awaitingResponse.remove(contextId)
    }

    fun onResponse(event: ContextResponseEvent) {
        val pending = awaitingResponse.remove(event.contextId) ?: return
        if (event.notify) showNotification(pending)
        startRewardMonitoring(pending)
    }

    fun clear() = awaitingResponse.clear()
}
