package com.tealamore.kthd.integration

import android.content.Context
import com.tealamore.kthd.ConsentManager
import com.tealamore.kthd.ParticipantDataDeletion
import com.tealamore.kthd.TaskForegroundService
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.ContextPayload
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import com.tealamore.kthd.data.Task
import com.tealamore.kthd.network.ApiClient
import com.tealamore.kthd.network.ConsentPayload
import com.tealamore.kthd.network.ConsentResponse
import com.tealamore.kthd.network.DeleteParticipantPayload
import com.tealamore.kthd.network.DeleteParticipantResponse
import com.tealamore.kthd.network.FakeTaskApiService
import com.tealamore.kthd.network.RequestSyncManager
import com.tealamore.kthd.network.RewardPayload
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParticipantDataDeletionIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ApiClient.init(context)
        ConsentManager.setConsented(context, true)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedStudyData() = runBlocking {
        val taskId = db.taskDao().insert(Task(label = "Write essay"))
        val ctx = ContextPayload(taskId = taskId, deviceId = "dev", appType = "Social", continuousDuration = 60, switchCount = 1)
        val ctxId = db.contextPayloadDao().insert(ctx)
        RequestSyncManager.enqueueContext(context, ctx.copy(id = ctxId), db = db, triggerSync = false)
        RequestSyncManager.enqueueReward(
            context,
            RewardPayload("dev", ctxId, taskId, screenLocked = false, appClosed = true, timeToClose = 12),
            db = db,
            triggerSync = false
        )
    }

    @Test
    fun deletionWithdrawsConsentWipesLocalDataAndQueuesOnlyTheDeleteRequest() = runBlocking {
        seedStudyData()

        ParticipantDataDeletion.deleteAllData(context, db, triggerSync = false)

        assertFalse(ConsentManager.hasConsented(context))
        assertTrue(db.contextPayloadDao().getAllPayloads().isEmpty())
        assertEquals(null, db.taskDao().getActiveTask())

        val queued = db.pendingRequestDao().getAllRequests()
        assertEquals("Awaiting context/reward requests must be cancelled", 1, queued.size)
        assertEquals(RequestType.DELETE_PARTICIPANT, queued[0].requestType)
        val payload = Gson().fromJson(queued[0].payloadJson, DeleteParticipantPayload::class.java)
        assertEquals(ApiClient.getDeviceId(context), payload.deviceId)
    }

    @Test
    fun deletionStopsDataCollection() = runBlocking {
        ParticipantDataDeletion.deleteAllData(context, db, triggerSync = false)

        val stopped = shadowOf(context as android.app.Application).nextStoppedService
        assertEquals(TaskForegroundService::class.java.name, stopped.component?.className)
    }

    @Test
    fun noStudyDataCanBeQueuedAfterDeletion() = runBlocking {
        ParticipantDataDeletion.deleteAllData(context, db, triggerSync = false)

        val ctx = ContextPayload(taskId = 1, deviceId = "dev", appType = "Social", continuousDuration = 60, switchCount = 1)
        assertEquals(-1L, RequestSyncManager.enqueueContext(context, ctx, db = db, triggerSync = false))
        assertEquals(1, db.pendingRequestDao().getAllRequests().size)
    }

    @Test
    fun reconsentIsSentOnlyAfterTheDeletionReachesTheBackend() = runBlocking {
        ParticipantDataDeletion.deleteAllData(context, db, triggerSync = false)

        // Participant re-consents while the backend is unreachable.
        ConsentManager.setConsented(context, true)
        RequestSyncManager.enqueueConsent(
            context, ConsentPayload("dev", true, "2026-11-18T12:00:00Z"), db = db, triggerSync = false
        )

        var backendUp = false
        val calls = mutableListOf<String>()
        val api = object : FakeTaskApiService() {
            override suspend fun deleteParticipantData(payload: DeleteParticipantPayload, idempotencyKey: String): DeleteParticipantResponse {
                if (!backendUp) throw IOException("offline")
                calls += "delete"
                return DeleteParticipantResponse()
            }
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                calls += "consent"
                return ConsentResponse()
            }
        }

        RequestSyncManager.processPendingRequests(context, baseDelayMs = 0L, apiService = api, targetDb = db)
        assertTrue("New consent must not overtake the pending deletion", calls.isEmpty())

        backendUp = true
        RequestSyncManager.processPendingRequests(context, baseDelayMs = 0L, apiService = api, targetDb = db)
        assertEquals(listOf("delete", "consent"), calls)
        assertTrue(db.pendingRequestDao().getAllRequests().all { it.syncStatus == SyncStatus.SYNCED })
    }

    @Test
    fun deletionIsNotHeldUpByARequestStuckRetrying() = runBlocking {
        // A consent request is failing with 503 and backing off for a long time.
        RequestSyncManager.enqueueConsent(
            context, ConsentPayload("dev", true, "2026-11-18T12:00:00Z"), db = db, triggerSync = false
        )
        val calls = mutableListOf<String>()
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                calls += "consent"
                throw com.tealamore.kthd.network.httpError(503)
            }
            override suspend fun deleteParticipantData(payload: DeleteParticipantPayload, idempotencyKey: String): DeleteParticipantResponse {
                calls += "delete"
                return DeleteParticipantResponse()
            }
        }
        val longBackoff = 60_000L
        RequestSyncManager.processPendingRequests(context, baseDelayMs = longBackoff, apiService = api, targetDb = db)

        ParticipantDataDeletion.deleteAllData(context, db, triggerSync = false)

        // The stuck request (and the error report it produced) were wiped, so the deletion is
        // sent right away instead of waiting out the backoff.
        RequestSyncManager.processPendingRequests(context, baseDelayMs = longBackoff, apiService = api, targetDb = db)
        assertEquals(listOf("consent", "delete"), calls)
        assertTrue(db.pendingRequestDao().getUnsyncedRequests().isEmpty())
    }
}
