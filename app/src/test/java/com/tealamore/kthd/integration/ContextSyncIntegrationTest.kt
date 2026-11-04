package com.tealamore.kthd.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.ContextPayload
import com.tealamore.kthd.data.ContextPayloadDao
import com.tealamore.kthd.data.SyncStatus
import com.tealamore.kthd.network.ApiClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContextSyncIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ContextPayloadDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.contextPayloadDao()
        ApiClient.init(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun contextPayload_insertAndMarkSynced() = runBlocking {
        val payload = ContextPayload(
            taskId = 1,
            deviceId = "test_device_123",
            appType = "Social",
            continuousDuration = 60,
            switchCount = 2,
            syncStatus = SyncStatus.PENDING
        )
        val id = dao.insert(payload)
        assertTrue(id > 0)

        val unsynced = dao.getUnsyncedPayloads()
        assertEquals(1, unsynced.size)
        assertEquals(SyncStatus.PENDING, unsynced[0].syncStatus)

        dao.update(payload.copy(id = id, syncStatus = SyncStatus.SYNCED))

        val remainingUnsynced = dao.getUnsyncedPayloads()
        assertTrue(remainingUnsynced.isEmpty())
    }
}
