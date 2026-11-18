package com.tealamore.kthd.network

import com.google.gson.annotations.SerializedName

data class ContextResponse(
    @SerializedName("notify") val notify: Boolean
)

data class ConsentPayload(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("consented") val consented: Boolean,
    @SerializedName("consent_time_utc") val consentTimeUtc: String
)

data class ConsentResponse(
    @SerializedName("success") val success: Boolean = true
)

data class RewardPayload(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("context_id") val contextId: Long,
    @SerializedName("task_id") val taskId: Long,
    @SerializedName("screen_locked") val screenLocked: Boolean,
    @SerializedName("app_closed") val appClosed: Boolean,
    @SerializedName("time_to_close") val timeToClose: Int
)

data class RewardResponse(
    @SerializedName("success") val success: Boolean = true
)

data class DeleteParticipantPayload(
    @SerializedName("device_id") val deviceId: String
)

data class DeleteParticipantResponse(
    @SerializedName("success") val success: Boolean = true
)

/**
 * Sent to `POST /error` when another request gets a non-2xx response, so developers can be
 * alerted. See CONTRACT.md for when reports are generated.
 */
data class ErrorReportPayload(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("request_type") val requestType: String,
    @SerializedName("http_status") val httpStatus: Int?,
    @SerializedName("error_type") val errorType: String,
    @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("response_body") val responseBody: String?,
    @SerializedName("request_body") val requestBody: String,
    @SerializedName("attempts") val attempts: Int,
    @SerializedName("will_retry") val willRetry: Boolean,
    @SerializedName("occurred_at_utc") val occurredAtUtc: String
)

data class ErrorReportResponse(
    @SerializedName("success") val success: Boolean = true
)
