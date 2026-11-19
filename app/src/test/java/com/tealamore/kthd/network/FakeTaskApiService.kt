package com.tealamore.kthd.network

import com.tealamore.kthd.data.ContextPayload
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

/** Base fake: every endpoint fails unless a test overrides it. */
open class FakeTaskApiService : TaskApiService {
    val errorReports = mutableListOf<ErrorReportPayload>()

    override suspend fun sendContext(payload: ContextPayload, idempotencyKey: String): ContextResponse =
        throw UnsupportedOperationException("sendContext not stubbed")

    override suspend fun sendConsent(payload: ConsentPayload, idempotencyKey: String): ConsentResponse =
        throw UnsupportedOperationException("sendConsent not stubbed")

    override suspend fun sendReward(payload: RewardPayload, idempotencyKey: String): RewardResponse =
        throw UnsupportedOperationException("sendReward not stubbed")

    override suspend fun deleteParticipantData(payload: DeleteParticipantPayload, idempotencyKey: String): DeleteParticipantResponse =
        throw UnsupportedOperationException("deleteParticipantData not stubbed")

    /** Accepts error reports by default and records them. */
    override suspend fun reportError(payload: ErrorReportPayload, idempotencyKey: String): ErrorReportResponse {
        errorReports += payload
        return ErrorReportResponse(success = true)
    }
}

/** The exception Retrofit throws for a non-2xx response. */
fun httpError(code: Int, body: String = "error $code"): HttpException =
    HttpException(Response.error<Any>(code, body.toResponseBody("application/json".toMediaType())))
