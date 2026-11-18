package com.tealamore.kthd.network

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.google.gson.Gson
import com.tealamore.kthd.ConsentManager
import com.tealamore.kthd.data.AppDatabase
import com.tealamore.kthd.data.ContextPayload
import com.tealamore.kthd.data.PendingRequest
import com.tealamore.kthd.data.RequestType
import com.tealamore.kthd.data.SyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Emitted when the backend has answered a `POST /context` request. */
data class ContextResponseEvent(
    val contextId: Long,
    val taskId: Long,
    val notify: Boolean
)

/** Result of one pass over the request queue. */
sealed class SyncResult {
    /** Every queued request has been sent or permanently rejected. */
    object Drained : SyncResult()

    /** The oldest request failed with a retryable error and should be retried after [delayMs]. */
    data class RetryLater(val delayMs: Long) : SyncResult()
}

/**
 * Persists outgoing backend requests in Room and sends them in order.
 *
 * - Requests are sent strictly first-in, first-out: a request is never sent before an older one
 *   (e.g. a reward before its context, or a new consent before a pending deletion).
 * - Only one pass runs at a time, so a request is never sent twice by overlapping passes.
 * - Network failures, timeouts and 5xx/408/429 responses are retried with exponential backoff.
 *   Other failures (e.g. 4xx) are marked [SyncStatus.REJECTED] and never retried.
 * - Non-2xx responses are reported to `POST /error` (see [CONTRACT.md]).
 * - Retries are driven by WorkManager, so they happen even if nothing new is enqueued and the
 *   app is closed.
 */
object RequestSyncManager {
    private const val TAG = "RequestSyncManager"
    private const val UNIQUE_WORK_NAME = "pending_request_sync"
    private const val MAX_ERROR_BODY_CHARS = 2000
    private val FINISHED_RETENTION_MS = TimeUnit.DAYS.toMillis(7)

    private val gson = Gson()
    private val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val syncMutex = Mutex()

    private val _contextResponses = MutableSharedFlow<ContextResponseEvent>(extraBufferCapacity = 64)

    /** Backend responses to context requests. Reward monitoring starts from these. */
    val contextResponses: SharedFlow<ContextResponseEvent> = _contextResponses.asSharedFlow()

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

    /** Whether a failed request should be retried, or rejected for good. */
    fun isRetryable(error: Throwable): Boolean = when (error) {
        is IOException -> true // offline, DNS failure, connection reset, timeouts
        is HttpException -> error.code() in 500..599 || error.code() == 408 || error.code() == 429
        else -> false
    }

    suspend fun enqueueConsent(
        context: Context,
        payload: ConsentPayload,
        db: AppDatabase? = null,
        triggerSync: Boolean = true
    ): Long = enqueueRequest(context, RequestType.CONSENT, gson.toJson(payload), db, triggerSync)

    /** Returns the queued request id, or -1 if the participant has not consented. */
    suspend fun enqueueContext(
        context: Context,
        payload: ContextPayload,
        db: AppDatabase? = null,
        triggerSync: Boolean = true
    ): Long = enqueueRequest(context, RequestType.CONTEXT, gson.toJson(payload), db, triggerSync)

    /** Returns the queued request id, or -1 if the participant has not consented. */
    suspend fun enqueueReward(
        context: Context,
        payload: RewardPayload,
        db: AppDatabase? = null,
        triggerSync: Boolean = true
    ): Long = enqueueRequest(context, RequestType.REWARD, gson.toJson(payload), db, triggerSync)

    /** Builds the queue entry for a participant data deletion request. */
    fun deleteParticipantRequest(payload: DeleteParticipantPayload): PendingRequest =
        PendingRequest(requestType = RequestType.DELETE_PARTICIPANT, payloadJson = gson.toJson(payload))

    private suspend fun enqueueRequest(
        context: Context,
        type: RequestType,
        payloadJson: String,
        db: AppDatabase?,
        triggerSync: Boolean
    ): Long {
        if (type.requiresConsent() && !ConsentManager.hasConsented(context)) {
            Log.w(TAG, "Dropping $type request: participant has not consented")
            return -1L
        }
        val database = db ?: AppDatabase.getDatabase(context)
        val id = database.pendingRequestDao().insert(
            PendingRequest(requestType = type, payloadJson = payloadJson)
        )
        if (triggerSync) startSync(context, database)
        return id
    }

    private fun RequestType.requiresConsent() =
        this == RequestType.CONTEXT || this == RequestType.REWARD

    /**
     * Attempts to send queued requests now, in-process (so time-sensitive context requests are
     * answered quickly), and schedules a background retry that survives process death.
     */
    fun startSync(context: Context, db: AppDatabase? = null) {
        val appContext = context.applicationContext
        scheduleBackgroundSync(appContext)
        syncScope.launch {
            val result = processPendingRequests(appContext, targetDb = db)
            if (result is SyncResult.RetryLater) scheduleBackgroundSync(appContext)
        }
    }

    /**
     * Enqueues a [RequestSyncWorker] that runs when the device has network and retries with
     * exponential backoff until the queue is drained. A no-op if one is already scheduled.
     */
    fun scheduleBackgroundSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<RequestSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        try {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        } catch (e: IllegalStateException) {
            // Only happens if WorkManager isn't initialized (e.g. in some unit tests).
            Log.e(TAG, "Could not schedule background sync", e)
        }
    }

    /**
     * Sends queued requests oldest-first until the queue is empty or a request fails with a
     * retryable error. Passes are serialized, so overlapping calls never send a request twice.
     */
    suspend fun processPendingRequests(
        context: Context,
        baseDelayMs: Long = 1000L,
        maxDelayMs: Long = 60000L,
        apiService: TaskApiService = ApiClient.service,
        targetDb: AppDatabase? = null,
        clock: () -> Long = System::currentTimeMillis
    ): SyncResult = syncMutex.withLock {
        val database = targetDb ?: AppDatabase.getDatabase(context)
        val pendingDao = database.pendingRequestDao()

        while (true) {
            val request = pendingDao.getNextUnsynced() ?: break

            val backoffMs = calculateBackoffDelay(request.attempts, baseDelayMs, maxDelayMs)
            val waitMs = backoffMs - (clock() - request.lastAttemptTimestamp)
            if (request.attempts > 0 && waitMs > 0) {
                Log.d(TAG, "Request #${request.id} (${request.requestType}) is backing off for ${waitMs}ms")
                return@withLock SyncResult.RetryLater(waitMs)
            }

            val error = try {
                send(request, apiService, database)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }

            // The queue may have been cleared while the request was in flight (participant
            // deleted their data). Don't resurrect it or report on it.
            if (pendingDao.getById(request.id) == null) continue

            if (error == null) {
                pendingDao.update(request.copy(syncStatus = SyncStatus.SYNCED, lastAttemptTimestamp = clock()))
                Log.d(TAG, "Synced request #${request.id} (${request.requestType})")
                continue
            }

            val retryable = isRetryable(error)
            val failed = request.copy(
                attempts = request.attempts + 1,
                lastAttemptTimestamp = clock(),
                syncStatus = if (retryable) SyncStatus.FAILED else SyncStatus.REJECTED
            )
            pendingDao.update(failed)
            Log.e(TAG, "Request #${request.id} (${request.requestType}) failed, retryable=$retryable", error)

            if (!retryable && request.requestType == RequestType.CONTEXT) {
                markContextRejected(request, database)
            }
            if (shouldReport(request, error, retryable)) {
                enqueueErrorReport(context, failed, error, retryable, database)
            }
            if (retryable) {
                return@withLock SyncResult.RetryLater(calculateBackoffDelay(failed.attempts, baseDelayMs, maxDelayMs))
            }
        }

        pendingDao.deleteFinishedBefore(clock() - FINISHED_RETENTION_MS)
        SyncResult.Drained
    }

    private suspend fun send(request: PendingRequest, api: TaskApiService, database: AppDatabase) {
        when (request.requestType) {
            RequestType.CONSENT ->
                api.sendConsent(gson.fromJson(request.payloadJson, ConsentPayload::class.java))
            RequestType.CONTEXT -> {
                val payload = gson.fromJson(request.payloadJson, ContextPayload::class.java)
                val response = api.sendContext(payload)
                if (payload.id != 0L) {
                    database.contextPayloadDao().update(payload.copy(syncStatus = SyncStatus.SYNCED))
                }
                _contextResponses.emit(ContextResponseEvent(payload.id, payload.taskId, response.notify))
            }
            RequestType.REWARD ->
                api.sendReward(gson.fromJson(request.payloadJson, RewardPayload::class.java))
            RequestType.DELETE_PARTICIPANT ->
                api.deleteParticipantData(gson.fromJson(request.payloadJson, DeleteParticipantPayload::class.java))
            RequestType.ERROR_REPORT ->
                api.reportError(gson.fromJson(request.payloadJson, ErrorReportPayload::class.java))
        }
    }

    private suspend fun markContextRejected(request: PendingRequest, database: AppDatabase) {
        val payload = runCatching { gson.fromJson(request.payloadJson, ContextPayload::class.java) }.getOrNull()
        if (payload != null && payload.id != 0L) {
            database.contextPayloadDao().update(payload.copy(syncStatus = SyncStatus.REJECTED))
        }
    }

    /**
     * Every non-2xx response is reported, except: failures of error reports themselves (to avoid
     * loops), and repeated retryable failures of the same request (only the first is reported).
     * Network errors and timeouts have no response and are not reported.
     */
    private fun shouldReport(request: PendingRequest, error: Throwable, retryable: Boolean): Boolean {
        if (request.requestType == RequestType.ERROR_REPORT) return false
        if (!retryable) return true
        return error is HttpException && request.attempts == 0
    }

    private suspend fun enqueueErrorReport(
        context: Context,
        failed: PendingRequest,
        error: Throwable,
        willRetry: Boolean,
        database: AppDatabase
    ) {
        val http = error as? HttpException
        val report = ErrorReportPayload(
            deviceId = ApiClient.getDeviceId(context),
            requestType = failed.requestType.name,
            httpStatus = http?.code(),
            errorType = error.javaClass.simpleName,
            errorMessage = error.message,
            responseBody = http?.let { readErrorBody(it) },
            requestBody = failed.payloadJson,
            attempts = failed.attempts,
            willRetry = willRetry,
            occurredAtUtc = utcNow()
        )
        // Inserted directly (not via enqueueRequest): we're inside a pass, which will pick it up.
        database.pendingRequestDao().insert(
            PendingRequest(requestType = RequestType.ERROR_REPORT, payloadJson = gson.toJson(report))
        )
    }

    private fun readErrorBody(e: HttpException): String? =
        runCatching { e.response()?.errorBody()?.string()?.take(MAX_ERROR_BODY_CHARS) }.getOrNull()

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
}
