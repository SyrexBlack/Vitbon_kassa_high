package com.vitbon.kkm.data.remote

import com.vitbon.kkm.BuildConfig
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.features.auth.domain.AuthTokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    internal fun buildAuthorizationHeader(tokenStore: AuthTokenStore): String? {
        val token = tokenStore.read()
        return if (token.isNullOrBlank()) null else "Bearer $token"
    }

    fun create(tokenStore: AuthTokenStore): VitbonApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

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
                chain.proceed(requestBuilder.build())
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(VitbonApi::class.java)
    }
}
