package com.vitbon.kkm.data.remote

import com.vitbon.kkm.BuildConfig
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.features.auth.domain.AuthTokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    internal fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    internal fun buildAuthorizationHeader(tokenStore: AuthTokenStore): String? {
        val token = tokenStore.read()
        return if (token.isNullOrBlank()) null else "Bearer $token"
    }

    internal fun normalizeDeviceId(rawDeviceId: String?): String? {
        return rawDeviceId?.trim()?.takeIf { it.isNotBlank() }
    }

    internal fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    fun create(tokenStore: AuthTokenStore, deviceIdProvider: () -> String?): VitbonApi {
        val logging = createLoggingInterceptor()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                buildAuthorizationHeader(tokenStore)?.let {
                    requestBuilder.addHeader("Authorization", it)
                }
                normalizeDeviceId(deviceIdProvider())?.let {
                    requestBuilder.addHeader("X-Device-Id", it)
                }
                chain.proceed(requestBuilder.build())
            }
            .build()

        return createRetrofit(
            baseUrl = BuildConfig.API_BASE_URL,
            client = client
        )
            .create(VitbonApi::class.java)
    }
}
