package com.tealamore.kthd.network

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.ConsentManager
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.ContextPayload
import com.tealamore.kthd.data.PendingRequestDao
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestSyncManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingRequestDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ConsentManager.setConsented(context, true)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingRequestDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // --- helpers -------------------------------------------------------------------------

    private fun consent(id: String = "test_dev") =
        ConsentPayload(deviceId = id, consented = true, consentTimeUtc = "2026-11-18T12:00:00Z")

    private fun reward(contextId: Long = 1L) = RewardPayload(
        deviceId = "test_dev", contextId = contextId, taskId = 1L,
        screenLocked = false, appClosed = true, timeToClose = 30
    )

    private suspend fun insertContext(): ContextPayload {
        val payload = ContextPayload(taskId = 1L, deviceId = "test_dev", appType = "Social", continuousDuration = 60, switchCount = 2)
        return payload.copy(id = db.contextPayloadDao().insert(payload))
    }

    /** Enqueue without starting a real background sync against the real backend. */
    private suspend fun enqueueConsent(payload: ConsentPayload = consent()) =
        RequestSyncManager.enqueueConsent(context, payload, db = db, triggerSync = false)

    private suspend fun process(api: TaskApiService, baseDelayMs: Long = 1000L) =
        RequestSyncManager.processPendingRequests(context, baseDelayMs = baseDelayMs, apiService = api, targetDb = db)

    private suspend fun row(id: Long) = dao.getById(id)!!

    // --- backoff -------------------------------------------------------------------------

    @Test
    fun testExponentialBackoffCalculation() {
        assertEquals(0L, RequestSyncManager.calculateBackoffDelay(0))
        assertEquals(1000L, RequestSyncManager.calculateBackoffDelay(1))
        assertEquals(2000L, RequestSyncManager.calculateBackoffDelay(2))
        assertEquals(4000L, RequestSyncManager.calculateBackoffDelay(3))
        assertEquals(8000L, RequestSyncManager.calculateBackoffDelay(4))
        assertEquals(16000L, RequestSyncManager.calculateBackoffDelay(5))
        assertEquals(32000L, RequestSyncManager.calculateBackoffDelay(6))
        assertEquals(60000L, RequestSyncManager.calculateBackoffDelay(7)) // max cap
    }

    @Test
    fun testCustomExponentialBackoffParameters() {
        assertEquals(0L, RequestSyncManager.calculateBackoffDelay(0, baseDelayMs = 100L, maxDelayMs = 500L))
        assertEquals(100L, RequestSyncManager.calculateBackoffDelay(1, baseDelayMs = 100L, maxDelayMs = 500L))
        assertEquals(200L, RequestSyncManager.calculateBackoffDelay(2, baseDelayMs = 100L, maxDelayMs = 500L))
        assertEquals(400L, RequestSyncManager.calculateBackoffDelay(3, baseDelayMs = 100L, maxDelayMs = 500L))
        assertEquals(500L, RequestSyncManager.calculateBackoffDelay(4, baseDelayMs = 100L, maxDelayMs = 500L)) // max cap
    }

    @Test
    fun testEnqueueAndProcessConsentSuccess() = runBlocking {
        val id = enqueueConsent()
        assertTrue(id > 0)

        var calls = 0
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                calls++
                return ConsentResponse(success = true)
            }
        }

        assertEquals(SyncResult.Drained, process(api))

        assertEquals(1, calls)
        assertTrue(dao.getUnsyncedRequests().isEmpty())
        val all = dao.getAllRequests()
        assertEquals(1, all.size)
        assertEquals(SyncStatus.SYNCED, all[0].syncStatus)
        assertEquals(RequestType.CONSENT, all[0].requestType)
    }

    @Test
    fun testOfflineFailureIncrementsAttemptsAndAppliesBackoff() = runBlocking {
        enqueueConsent()

        var calls = 0
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                calls++
                throw IOException("Network offline")
            }
        }

        // First attempt fails
        assertTrue(process(api, baseDelayMs = 5000L) is SyncResult.RetryLater)
        assertEquals(1, calls)

        val unsynced = dao.getUnsyncedRequests()
        assertEquals(1, unsynced.size)
        assertEquals(1, unsynced[0].attempts)
        assertEquals(SyncStatus.FAILED, unsynced[0].syncStatus)
        assertTrue(unsynced[0].lastAttemptTimestamp > 0)

        // Immediate retry inside backoff window should skip calling API
        assertTrue(process(api, baseDelayMs = 5000L) is SyncResult.RetryLater)
        assertEquals(1, calls) // No new call made because backoff window is active
    }

    // --- which failures are retried ------------------------------------------------------

    @Test
    fun retryablePolicyCoversNetworkErrorsTimeoutsAnd5xxOnly() {
        assertTrue(RequestSyncManager.isRetryable(IOException("offline")))
        assertTrue(RequestSyncManager.isRetryable(SocketTimeoutException("timeout")))
        assertTrue(RequestSyncManager.isRetryable(httpError(500)))
        assertTrue(RequestSyncManager.isRetryable(httpError(503)))
        assertTrue(RequestSyncManager.isRetryable(httpError(408)))
        assertTrue(RequestSyncManager.isRetryable(httpError(429)))

        assertFalse(RequestSyncManager.isRetryable(httpError(400)))
        assertFalse(RequestSyncManager.isRetryable(httpError(401)))
        assertFalse(RequestSyncManager.isRetryable(httpError(404)))
        assertFalse(RequestSyncManager.isRetryable(httpError(422)))
        assertFalse(RequestSyncManager.isRetryable(com.google.gson.JsonSyntaxException("bad body")))
    }

    @Test
    fun timeoutIsRetriedAndNotReported() = runBlocking {
        val id = enqueueConsent()
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse =
                throw SocketTimeoutException("timeout")
        }

        assertTrue(process(api) is SyncResult.RetryLater)

        assertEquals(SyncStatus.FAILED, row(id).syncStatus)
        assertEquals(1, dao.getAllRequests().size) // no error report queued: there was no response
    }

    @Test
    fun serverErrorIsRetriedAndReportedOnlyOnce() = runBlocking {
        val id = enqueueConsent()
        var calls = 0
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                calls++
                throw httpError(503, """{"error":"maintenance"}""")
            }
        }

        // Fails twice (no backoff so the second pass retries immediately).
        assertTrue(process(api, baseDelayMs = 0L) is SyncResult.RetryLater)
        assertTrue(process(api, baseDelayMs = 0L) is SyncResult.RetryLater)

        assertEquals(2, calls)
        assertEquals(SyncStatus.FAILED, row(id).syncStatus)
        assertEquals(2, row(id).attempts)

        val reports = dao.getAllRequests().filter { it.requestType == RequestType.ERROR_REPORT }
        assertEquals("Only the first 5xx for a request is reported", 1, reports.size)
        val report = com.google.gson.Gson().fromJson(reports[0].payloadJson, ErrorReportPayload::class.java)
        assertEquals(503, report.httpStatus)
        assertEquals("CONSENT", report.requestType)
        assertEquals("""{"error":"maintenance"}""", report.responseBody)
        assertTrue(report.willRetry)
    }

    @Test
    fun clientErrorIsRejectedReportedAndDoesNotBlockTheQueue() = runBlocking {
        val badId = enqueueConsent(consent("bad"))
        val goodId = enqueueConsent(consent("good"))

        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                if (payload.deviceId == "bad") throw httpError(400, """{"error":"missing field"}""")
                return ConsentResponse(success = true)
            }
        }

        assertEquals(SyncResult.Drained, process(api))

        assertEquals(SyncStatus.REJECTED, row(badId).syncStatus)
        assertEquals(SyncStatus.SYNCED, row(goodId).syncStatus)

        // The error report was queued and sent in the same pass.
        assertEquals(1, api.errorReports.size)
        val report = api.errorReports[0]
        assertEquals(400, report.httpStatus)
        assertEquals("CONSENT", report.requestType)
        assertEquals("""{"error":"missing field"}""", report.responseBody)
        assertTrue(report.requestBody.contains("\"device_id\":\"bad\""))
        assertFalse(report.willRetry)

        // Rejected requests are never retried.
        assertEquals(SyncResult.Drained, process(api))
        assertEquals(1, api.errorReports.size)
    }

    @Test
    fun aRejectedErrorReportIsNotItselfReported() = runBlocking {
        enqueueConsent()
        var reportCalls = 0
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse = throw httpError(400)
            override suspend fun reportError(payload: ErrorReportPayload, idempotencyKey: String): ErrorReportResponse {
                reportCalls++
                throw httpError(400)
            }
        }

        assertEquals(SyncResult.Drained, process(api))

        assertEquals(1, reportCalls)
        val all = dao.getAllRequests()
        assertEquals(2, all.size) // the consent and one error report, both rejected
        assertTrue(all.all { it.syncStatus == SyncStatus.REJECTED })
    }

    // --- ordering ------------------------------------------------------------------------

    @Test
    fun aRewardIsNeverSentBeforeItsContext() = runBlocking {
        val ctx = insertContext()
        RequestSyncManager.enqueueContext(context, ctx, db = db, triggerSync = false)
        RequestSyncManager.enqueueReward(context, reward(ctx.id), db = db, triggerSync = false)

        var contextUp = false
        val sent = mutableListOf<String>()
        val api = object : FakeTaskApiService() {
            override suspend fun sendContext(payload: ContextPayload, idempotencyKey: String): ContextResponse {
                if (!contextUp) throw IOException("offline")
                sent += "context"
                return ContextResponse(notify = false)
            }
            override suspend fun sendReward(payload: RewardPayload, idempotencyKey: String): RewardResponse {
                sent += "reward"
                return RewardResponse()
            }
        }

        process(api, baseDelayMs = 0L)
        assertTrue("Reward must wait for its context", sent.isEmpty())

        contextUp = true
        assertEquals(SyncResult.Drained, process(api, baseDelayMs = 0L))
        assertEquals(listOf("context", "reward"), sent)
    }

    // --- context responses ---------------------------------------------------------------

    @Test
    fun contextResponseIsPublishedWhetherOrNotItAsksForANotification() = runBlocking {
        for (notify in listOf(true, false)) {
            val ctx = insertContext()
            RequestSyncManager.enqueueContext(context, ctx, db = db, triggerSync = false)

            val event = async(start = CoroutineStart.UNDISPATCHED) {
                RequestSyncManager.contextResponses.first { it.contextId == ctx.id }
            }
            val api = object : FakeTaskApiService() {
                override suspend fun sendContext(payload: ContextPayload, idempotencyKey: String) = ContextResponse(notify = notify)
            }
            process(api)

            assertEquals(ContextResponseEvent(ctx.id, ctx.taskId, notify), withTimeout(5_000) { event.await() })
            assertEquals(SyncStatus.SYNCED, db.contextPayloadDao().getAllPayloads().first { it.id == ctx.id }.syncStatus)
        }
    }

    @Test
    fun rejectedContextIsMarkedRejectedLocally() = runBlocking {
        val ctx = insertContext()
        RequestSyncManager.enqueueContext(context, ctx, db = db, triggerSync = false)
        val api = object : FakeTaskApiService() {
            override suspend fun sendContext(payload: ContextPayload, idempotencyKey: String): ContextResponse = throw httpError(422)
        }

        process(api)

        assertEquals(SyncStatus.REJECTED, db.contextPayloadDao().getAllPayloads().single().syncStatus)
    }

    // --- consent -------------------------------------------------------------------------

    @Test
    fun studyDataIsNotQueuedWithoutConsent() = runBlocking {
        ConsentManager.setConsented(context, false)
        val ctx = insertContext()

        assertEquals(-1L, RequestSyncManager.enqueueContext(context, ctx, db = db, triggerSync = false))
        assertEquals(-1L, RequestSyncManager.enqueueReward(context, reward(), db = db, triggerSync = false))
        assertTrue(dao.getAllRequests().isEmpty())

        // The consent request itself is always allowed.
        assertTrue(enqueueConsent() > 0)
    }

    // --- deletion while a request is in flight -------------------------------------------

    @Test
    fun aRequestClearedWhileInFlightIsNotResurrectedOrReported() = runBlocking {
        val id = enqueueConsent()
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                dao.deleteAll() // participant deletes their data mid-request
                throw httpError(400)
            }
        }

        assertEquals(SyncResult.Drained, process(api))

        assertNull(dao.getById(id))
        assertTrue(dao.getAllRequests().isEmpty())
        assertTrue(api.errorReports.isEmpty())
    }

    // --- idempotency keys ----------------------------------------------------------------

    @Test
    fun retriesReuseTheIdempotencyKeyAndRequestsDoNotShareOne() = runBlocking {
        enqueueConsent(consent("first"))
        enqueueConsent(consent("second"))

        val keysByDevice = mutableMapOf<String, MutableList<String>>()
        var failFirst = true
        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                keysByDevice.getOrPut(payload.deviceId) { mutableListOf() } += idempotencyKey
                if (payload.deviceId == "first" && failFirst) {
                    failFirst = false
                    throw httpError(503)
                }
                return ConsentResponse()
            }
        }

        process(api, baseDelayMs = 0L) // "first" fails with 503
        process(api, baseDelayMs = 0L) // "first" retried, then "second"

        val firstKeys = keysByDevice.getValue("first")
        val secondKeys = keysByDevice.getValue("second")
        assertEquals(2, firstKeys.size)
        assertEquals("A retry must reuse the same key", firstKeys[0], firstKeys[1])
        assertEquals(1, secondKeys.size)
        assertTrue("Different requests must have different keys", firstKeys[0] != secondKeys[0])
    }

    @Test
    fun errorReportsIdentifyTheFailedRequestAndHaveTheirOwnKey() = runBlocking {
        val ctx = insertContext()
        val requestId = RequestSyncManager.enqueueContext(context, ctx, db = db, triggerSync = false)
        val failedKey = row(requestId).idempotencyKey

        var reportKey: String? = null
        val api = object : FakeTaskApiService() {
            override suspend fun sendContext(payload: ContextPayload, idempotencyKey: String): ContextResponse =
                throw httpError(400, """{"error":"bad app_type"}""")
            override suspend fun reportError(payload: ErrorReportPayload, idempotencyKey: String): ErrorReportResponse {
                reportKey = idempotencyKey
                return super.reportError(payload, idempotencyKey)
            }
        }

        process(api)

        val report = api.errorReports.single()
        assertEquals("POST /context", report.endpoint)
        assertEquals("CONTEXT", report.requestType)
        assertEquals(failedKey, report.idempotencyKey)
        assertEquals("""{"error":"bad app_type"}""", report.responseBody)
        assertTrue(reportKey != null && reportKey != failedKey)
    }
}
