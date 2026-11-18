package com.tealamore.kthd.network

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.PendingRequestDao
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestSyncManagerTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingRequestDao
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingRequestDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

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
        val payload = ConsentPayload(
            deviceId = "test_dev_1",
            consented = true,
            consentTimeUtc = "2026-11-18T12:00:00Z"
        )

        val id = RequestSyncManager.enqueueConsent(context, payload, db = db)
        assertTrue(id > 0)

        var calls = 0
        val fakeService = object : TaskApiService {
            override suspend fun sendContext(payload: com.tealamore.kthd.data.ContextPayload): ContextResponse = throw UnsupportedOperationException()
            override suspend fun sendConsent(payload: ConsentPayload): ConsentResponse {
                calls++
                return ConsentResponse(success = true)
            }
            override suspend fun sendReward(payload: RewardPayload): RewardResponse = throw UnsupportedOperationException()
            override suspend fun deleteParticipantData(payload: DeleteParticipantPayload): DeleteParticipantResponse = throw UnsupportedOperationException()
        }

        RequestSyncManager.processPendingRequests(context, baseDelayMs = 100L, apiService = fakeService, targetDb = db)

        assertEquals(1, calls)
        val unsynced = dao.getUnsyncedRequests()
        assertTrue(unsynced.isEmpty())

        val all = dao.getAllRequests()
        assertEquals(1, all.size)
        assertEquals(SyncStatus.SYNCED, all[0].syncStatus)
        assertEquals(RequestType.CONSENT, all[0].requestType)
    }

    @Test
    fun testOfflineFailureIncrementsAttemptsAndAppliesBackoff() = runBlocking {
        val payload = ConsentPayload(
            deviceId = "test_dev_offline",
            consented = true,
            consentTimeUtc = "2026-11-18T12:00:00Z"
        )

        RequestSyncManager.enqueueConsent(context, payload, db = db)

        var calls = 0
        val failingService = object : TaskApiService {
            override suspend fun sendContext(payload: com.tealamore.kthd.data.ContextPayload): ContextResponse = throw UnsupportedOperationException()
            override suspend fun sendConsent(payload: ConsentPayload): ConsentResponse {
                calls++
                throw IOException("Network offline")
            }
            override suspend fun sendReward(payload: RewardPayload): RewardResponse = throw UnsupportedOperationException()
            override suspend fun deleteParticipantData(payload: DeleteParticipantPayload): DeleteParticipantResponse = throw UnsupportedOperationException()
        }

        // First attempt fails
        RequestSyncManager.processPendingRequests(context, baseDelayMs = 5000L, apiService = failingService, targetDb = db)
        assertEquals(1, calls)

        val unsynced = dao.getUnsyncedRequests()
        assertEquals(1, unsynced.size)
        assertEquals(1, unsynced[0].attempts)
        assertEquals(SyncStatus.FAILED, unsynced[0].syncStatus)
        assertTrue(unsynced[0].lastAttemptTimestamp > 0)

        // Immediate retry inside backoff window should skip calling API
        RequestSyncManager.processPendingRequests(context, baseDelayMs = 5000L, apiService = failingService, targetDb = db)
        assertEquals(1, calls) // No new call made because backoff window is active
    }
}
