package com.tealamore.kthd.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Checks the key really goes out on the wire as an `Idempotency-Key` header. */
class IdempotencyKeyHeaderTest {

    @Test
    fun everyEndpointSendsTheIdempotencyKeyHeader() = runBlocking {
        val captured = mutableListOf<Request>()
        val fakeServer = Interceptor { chain ->
            captured += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"success":true,"notify":false}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val api = Retrofit.Builder()
            .baseUrl("http://backend.test/")
            .client(OkHttpClient.Builder().addInterceptor(fakeServer).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TaskApiService::class.java)

        api.sendConsent(ConsentPayload("dev", true, "2026-11-18T12:00:00Z"), "key-consent")
        api.sendContext(
            com.tealamore.kthd.data.ContextPayload(taskId = 1, deviceId = "dev", appType = "Social", continuousDuration = 60, switchCount = 1),
            "key-context"
        )
        api.sendReward(RewardPayload("dev", 1, 1, false, true, 10), "key-reward")
        api.deleteParticipantData(DeleteParticipantPayload("dev"), "key-delete")
        api.reportError(
            ErrorReportPayload("dev", "REWARD", "POST /reward", "key-reward", 400, "HttpException", null, null, "{}", 1, false, "2026-11-18T12:00:00Z"),
            "key-error"
        )

        assertEquals(
            listOf("/consent", "/context", "/reward", "/participant/delete", "/error"),
            captured.map { it.url.encodedPath }
        )
        assertEquals(
            listOf("key-consent", "key-context", "key-reward", "key-delete", "key-error"),
            captured.map { it.header("Idempotency-Key") }
        )
    }
}
