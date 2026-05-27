package com.vitbon.kkm.data.remote

import com.vitbon.kkm.data.remote.api.VitbonApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiClientTest {

    @Test
    fun `retrofit sends and receives raw string payloads for integration endpoints`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"verified\":true,\"verificationId\":\"verify-1\"}")
        )

        try {
            val api = ApiClient.createRetrofit(server.url("/").toString(), OkHttpClient())
                .create(VitbonApi::class.java)
            val payload = "{\"qrData\":\"MAX-ID-QR\"}"

            val response = api.verifyAge(payload)
            val request = server.takeRequest()

            assertEquals(payload, request.body.readUtf8())
            assertEquals("{\"verified\":true,\"verificationId\":\"verify-1\"}", response.body())
        } finally {
            server.shutdown()
        }
    }
}