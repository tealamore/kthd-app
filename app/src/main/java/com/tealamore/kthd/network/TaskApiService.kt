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
import retrofit2.http.POST

interface TaskApiService {
    @POST("context")
    suspend fun sendContext(@Body payload: ContextPayload): ContextResponse

    @POST("consent")
    suspend fun sendConsent(@Body payload: ConsentPayload): ConsentResponse

    @POST("reward")
    suspend fun sendReward(@Body payload: RewardPayload): RewardResponse
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
