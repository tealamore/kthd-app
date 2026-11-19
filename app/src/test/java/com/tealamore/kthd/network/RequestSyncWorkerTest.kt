package com.tealamore.kthd.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestSyncWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
    }

    @Test
    fun workerRetriesUntilTheQueueIsDrained() {
        assertEquals(ListenableWorker.Result.success(), RequestSyncWorker.toWorkResult(SyncResult.Drained))
        assertEquals(ListenableWorker.Result.retry(), RequestSyncWorker.toWorkResult(SyncResult.RetryLater(1000L)))
    }

    @Test
    fun backgroundSyncIsScheduledOnceAndWaitsForNetwork() {
        RequestSyncManager.scheduleBackgroundSync(context)
        RequestSyncManager.scheduleBackgroundSync(context) // KEEP: no duplicate

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("pending_request_sync").get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        assertEquals(NetworkType.CONNECTED, infos[0].constraints.requiredNetworkType)
    }
}
