package com.tealamore.kthd.network

import android.content.Context
import android.provider.Settings
import com.tealamore.kthd.data.ContextPayload
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Every call carries an `Idempotency-Key` header that stays the same across retries of the same
 * request, so the backend can recognise and skip duplicates.
 */
interface TaskApiService {
    @POST("context")
    suspend fun sendContext(
        @Body payload: ContextPayload,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String
    ): ContextResponse

    @POST("consent")
    suspend fun sendConsent(
        @Body payload: ConsentPayload,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String
    ): ConsentResponse

    @POST("reward")
    suspend fun sendReward(
        @Body payload: RewardPayload,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String
    ): RewardResponse

    @POST("participant/delete")
    suspend fun deleteParticipantData(
        @Body payload: DeleteParticipantPayload,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String
    ): DeleteParticipantResponse

    @POST("error")
    suspend fun reportError(
        @Body payload: ErrorReportPayload,
        @Header(IDEMPOTENCY_KEY_HEADER) idempotencyKey: String
    ): ErrorReportResponse

    companion object {
        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
    }
}

object ApiClient {
    private const val BASE_URL = "http://192.168.1.22/" // Replace with real URL

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    private val deviceIdInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        appContext?.let { ctx ->
            builder.header("X-Device-ID", getDeviceId(ctx))
        }
        chain.proceed(builder.build())
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(deviceIdInterceptor)
        .addInterceptor(logging)
        .build()

    val service: TaskApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(TaskApiService::class.java)
    }
}
