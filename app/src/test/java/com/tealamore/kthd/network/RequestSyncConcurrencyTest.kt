package com.tealamore.kthd.network

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.PendingRequest
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Two sync passes can be started at the same time in production: MainActivity.onCreate calls
 * startSync, and every enqueue* call starts another pass. These tests run two passes
 * concurrently against the same queue to show what happens when they overlap.
 *
 * The fake API holds the first in-flight call open until the second pass has also called the
 * API (or a short timeout elapses). If passes are serialized, the second call never arrives
 * while the first is in flight, the wait times out, and the first pass simply finishes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestSyncConcurrencyTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val gson = Gson()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertPendingConsent(): Long {
        val payload = ConsentPayload(
            deviceId = "race_device",
            consented = true,
            consentTimeUtc = "2026-11-18T12:00:00Z"
        )
        // Inserted directly so no background sync is kicked off behind the test's back.
        return db.pendingRequestDao().insert(
            PendingRequest(requestType = RequestType.CONSENT, payloadJson = gson.toJson(payload))
        )
    }

    private fun runTwoPassesConcurrently(api: TaskApiService) = runBlocking {
        (1..2).map {
            async(Dispatchers.Default) {
                RequestSyncManager.processPendingRequests(context, apiService = api, targetDb = db)
            }
        }.awaitAll()
    }

    @Test
    fun overlappingPassesSendTheSameRequestOnlyOnce() {
        runBlocking { insertPendingConsent() }

        val consentCalls = AtomicInteger(0)
        val secondCallArrived = CompletableDeferred<Unit>()

        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                if (consentCalls.incrementAndGet() == 1) {
                    // Keep the first request "on the wire" until the other pass also sends it.
                    withTimeoutOrNull(OVERLAP_WINDOW_MS) { secondCallArrived.await() }
                } else {
                    secondCallArrived.complete(Unit)
                }
                return ConsentResponse(success = true)
            }
        }

        runTwoPassesConcurrently(api)

        assertEquals(
            "The same queued consent request was POSTed more than once",
            1,
            consentCalls.get()
        )
    }

    @Test
    fun aFailingPassDoesNotOverwriteARequestAnotherPassSynced() {
        val requestId = runBlocking { insertPendingConsent() }

        val consentCalls = AtomicInteger(0)
        val successfulCalls = AtomicInteger(0)
        val secondCallFinished = CompletableDeferred<Unit>()

        val api = object : FakeTaskApiService() {
            override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse {
                if (consentCalls.incrementAndGet() == 1) {
                    // Slow request: still in flight while the other pass sends the same row
                    // successfully, then it times out.
                    withTimeoutOrNull(OVERLAP_WINDOW_MS) { secondCallFinished.await() }
                    throw IOException("timeout")
                }
                successfulCalls.incrementAndGet()
                secondCallFinished.complete(Unit)
                return ConsentResponse(success = true)
            }
        }

        runTwoPassesConcurrently(api)

        val row = runBlocking { db.pendingRequestDao().getAllRequests().single { it.id == requestId } }
        if (successfulCalls.get() > 0) {
            assertEquals(
                "The backend accepted this request, but a stale pass overwrote the row so it " +
                    "will be sent again",
                SyncStatus.SYNCED,
                row.syncStatus
            )
        }
        assertEquals("The same queued consent request was POSTed more than once", 1, consentCalls.get())
    }

    companion object {
        private const val OVERLAP_WINDOW_MS = 500L
    }
}
