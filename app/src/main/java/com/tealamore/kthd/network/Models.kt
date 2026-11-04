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
