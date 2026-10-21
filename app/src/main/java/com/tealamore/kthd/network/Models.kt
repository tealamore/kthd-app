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
