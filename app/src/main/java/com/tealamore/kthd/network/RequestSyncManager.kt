package com.tealamore.kthd.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.ContextPayload
import com.tealamore.kthd.data.PendingRequest
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object RequestSyncManager {
    private const val TAG = "RequestSyncManager"
    private const val ALERT_NOTIFICATION_ID = 1002
    private const val ALERT_CHANNEL_ID = "threshold_alert_channel"

    private val gson = Gson()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun calculateBackoffDelay(
        attempts: Int,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = 60000L
    ): Long {
        if (attempts <= 0) return 0L
        val shift = kotlin.math.min(attempts - 1, 30)
        val factor = 1L shl shift
        val delay = baseDelayMs * factor
        return kotlin.math.min(delay, maxDelayMs)
    }

    suspend fun enqueueConsent(
        context: Context,
        payload: ConsentPayload,
        db: AppDatabase? = null
    ): Long {
        return enqueueRequest(context, RequestType.CONSENT, gson.toJson(payload), db)
    }

    suspend fun enqueueContext(
        context: Context,
        payload: ContextPayload,
        db: AppDatabase? = null
    ): Long {
        return enqueueRequest(context, RequestType.CONTEXT, gson.toJson(payload), db)
    }

    suspend fun enqueueReward(
        context: Context,
        payload: RewardPayload,
        db: AppDatabase? = null
    ): Long {
        return enqueueRequest(context, RequestType.REWARD, gson.toJson(payload), db)
    }

    suspend fun enqueueDeleteParticipant(
        context: Context,
        payload: DeleteParticipantPayload,
        db: AppDatabase? = null
    ): Long {
        return enqueueRequest(context, RequestType.DELETE_PARTICIPANT, gson.toJson(payload), db)
    }

    private suspend fun enqueueRequest(
        context: Context,
        type: RequestType,
        payloadJson: String,
        db: AppDatabase? = null
    ): Long {
        val database = db ?: AppDatabase.getDatabase(context)
        val pendingRequest = PendingRequest(
            requestType = type,
            payloadJson = payloadJson,
            attempts = 0,
            lastAttemptTimestamp = 0L,
            syncStatus = SyncStatus.PENDING
        )
        val id = database.pendingRequestDao().insert(pendingRequest)
        startSync(context, database)
        return id
    }

    fun startSync(context: Context, db: AppDatabase? = null) {
        syncScope.launch {
            processPendingRequests(context.applicationContext, targetDb = db)
        }
    }

    suspend fun processPendingRequests(
        context: Context,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = 60000L,
        apiService: TaskApiService = ApiClient.service,
        targetDb: AppDatabase? = null
    ) {
        val database = targetDb ?: AppDatabase.getDatabase(context)
        val pendingDao = database.pendingRequestDao()
        val contextDao = database.contextPayloadDao()

        val unsyncedRequests = pendingDao.getUnsyncedRequests()
        val now = System.currentTimeMillis()

        for (request in unsyncedRequests) {
            val backoffMs = calculateBackoffDelay(request.attempts, baseDelayMs, maxDelayMs)
            if (request.attempts > 0 && (now - request.lastAttemptTimestamp) < backoffMs) {
                Log.d(TAG, "Skipping request #${request.id} (${request.requestType}) due to backoff window")
                continue
            }

            try {
                when (request.requestType) {
                    RequestType.CONSENT -> {
                        val payload = gson.fromJson(request.payloadJson, ConsentPayload::class.java)
                        apiService.sendConsent(payload)
                    }
                    RequestType.CONTEXT -> {
                        val payload = gson.fromJson(request.payloadJson, ContextPayload::class.java)
                        val response = apiService.sendContext(payload)
                        if (response.notify) {
                            showThresholdNotification(context, payload.appType)
                        }
                        if (payload.id != 0L) {
                            contextDao.update(payload.copy(syncStatus = SyncStatus.SYNCED))
                        }
                    }
                    RequestType.REWARD -> {
                        val payload = gson.fromJson(request.payloadJson, RewardPayload::class.java)
                        apiService.sendReward(payload)
                    }
                    RequestType.DELETE_PARTICIPANT -> {
                        val payload = gson.fromJson(request.payloadJson, DeleteParticipantPayload::class.java)
                        apiService.deleteParticipantData(payload)
                    }
                }

                pendingDao.update(
                    request.copy(
                        syncStatus = SyncStatus.SYNCED
                    )
                )
                Log.d(TAG, "Successfully synced request #${request.id} (${request.requestType})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync request #${request.id} (${request.requestType})", e)
                val newAttempts = request.attempts + 1
                pendingDao.update(
                    request.copy(
                        attempts = newAttempts,
                        lastAttemptTimestamp = System.currentTimeMillis(),
                        syncStatus = SyncStatus.FAILED
                    )
                )
            }
        }
    }

    fun showThresholdNotification(context: Context, appName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createAlertChannel(context)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("Threshold Alert")
            .setContentText("1-minute threshold hit for $appName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows alerts when app usage thresholds are hit"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
