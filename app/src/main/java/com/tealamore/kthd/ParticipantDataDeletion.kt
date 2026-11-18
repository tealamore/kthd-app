package com.tealamore.kthd

import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.network.ApiClient
import com.tealamore.kthd.network.DeleteParticipantPayload
import com.tealamore.kthd.network.RequestSyncManager

/**
 * Handles a participant's "Delete My Data" request. Afterwards the app holds no study data,
 * collects nothing, and can't be used until the participant consents again.
 */
object ParticipantDataDeletion {

    suspend fun deleteAllData(
        context: Context,
        db: AppDatabase = AppDatabase.getDatabase(context),
        triggerSync: Boolean = true
    ) {
        // 1. Withdraw consent first: from here on, RequestSyncManager refuses new context and
        //    reward requests, even from collection coroutines that haven't stopped yet.
        ConsentManager.setConsented(context, false)

        // 2. Stop data collection.
        context.stopService(Intent(context, TaskForegroundService::class.java))

        // 3. Cancel every queued request and wipe local data, leaving only the deletion request.
        //    A request already in flight may still complete; the sync pass will not resurrect
        //    it, and the deletion request is sent after it.
        val deviceId = ApiClient.getDeviceId(context)
        db.withTransaction {
            db.pendingRequestDao().deleteAll()
            db.contextPayloadDao().deleteAll()
            db.taskDao().deleteAll()
            db.pendingRequestDao().insert(
                RequestSyncManager.deleteParticipantRequest(DeleteParticipantPayload(deviceId = deviceId))
            )
        }

        // 4. Send the deletion request (retried in the background until the backend accepts it).
        if (triggerSync) RequestSyncManager.startSync(context, db)
    }
}
