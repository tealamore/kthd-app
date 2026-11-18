package com.tealamore.kthd.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.ConsentManager
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import com.tealamore.kthd.network.ApiClient
import com.tealamore.kthd.network.ConsentPayload
import com.tealamore.kthd.network.ConsentResponse
import com.tealamore.kthd.network.FakeTaskApiService
import com.tealamore.kthd.network.RequestSyncManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineConsentIntegrationTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ApiClient.init(context)
        ConsentManager.setConsented(context, false)

        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testOfflineConsentPersistedAndResumedOnReopen() = runBlocking {
        // Step 1: Initial state - user has not consented
        assertFalse(ConsentManager.hasConsented(context))

        // Step 2: User clicks consent while phone is offline
        ConsentManager.setConsented(context, true)
        assertTrue(ConsentManager.hasConsented(context))

        val consentPayload = ConsentPayload(
            deviceId = ApiClient.getDeviceId(context),
            consented = true,
            consentTimeUtc = "2026-11-18T15:00:00Z"
        )

        // Offline network service that throws IOException
        var attemptsMade = 0
        val offlineService = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload): ConsentResponse {
                attemptsMade++
                throw IOException("No internet connection")
            }
        }

        // Enqueue consent request while offline
        RequestSyncManager.enqueueConsent(context, consentPayload, db = db, triggerSync = false)
        RequestSyncManager.processPendingRequests(context, baseDelayMs = 100L, apiService = offlineService, targetDb = db)

        assertEquals(1, attemptsMade)

        // Verify request is saved in Room DB with FAILED/PENDING status
        val pendingDao = db.pendingRequestDao()
        val savedRequests = pendingDao.getAllRequests()
        assertEquals(1, savedRequests.size)
        val offlineRequest = savedRequests[0]
        assertEquals(RequestType.CONSENT, offlineRequest.requestType)
        assertEquals(SyncStatus.FAILED, offlineRequest.syncStatus)
        assertEquals(1, offlineRequest.attempts)

        // Step 3: User kills the app and reopens it. Network connection is restored.
        // Online network service succeeds
        var onlineCallsMade = 0
        val onlineService = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload): ConsentResponse {
                onlineCallsMade++
                return ConsentResponse(success = true)
            }
        }

        // App reopens: processPendingRequests is triggered (e.g. from MainActivity / RequestSyncManager)
        RequestSyncManager.processPendingRequests(context, baseDelayMs = 0L, apiService = onlineService, targetDb = db)

        assertEquals(1, onlineCallsMade)

        // Step 4: Verify consent payload is now marked SYNCED in internal DB
        val updatedRequests = pendingDao.getAllRequests()
        assertEquals(1, updatedRequests.size)
        assertEquals(SyncStatus.SYNCED, updatedRequests[0].syncStatus)
    }
}
