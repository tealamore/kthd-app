package com.tealamore.kthd.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Background retry for the request queue. Scheduled by [RequestSyncManager.scheduleBackgroundSync]
 * with a network constraint; WorkManager re-runs it with exponential backoff until the queue is
 * drained, including after the app process is killed or the device reboots.
 */
class RequestSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        toWorkResult(RequestSyncManager.processPendingRequests(applicationContext))

    companion object {
        fun toWorkResult(result: SyncResult): Result = when (result) {
            SyncResult.Drained -> Result.success()
            is SyncResult.RetryLater -> Result.retry()
        }
    }
}
