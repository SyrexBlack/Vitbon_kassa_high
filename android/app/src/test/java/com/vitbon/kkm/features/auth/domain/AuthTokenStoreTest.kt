package com.vitbon.kkm.features.auth.domain

import com.vitbon.kkm.data.remote.ApiClient
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthTokenStoreTest {

    @Test
    fun `api client interceptor returns bearer header from token store`() {
        val tokenStore = mockk<AuthTokenStore>()
        every { tokenStore.read() } returns "opaque-token"

        val header = ApiClient.buildAuthorizationHeader(tokenStore)

        assertEquals("Bearer opaque-token", header)
    }

    @Test
    fun `api client interceptor returns null when token is missing`() {
        val tokenStore = mockk<AuthTokenStore>()
        every { tokenStore.read() } returns null

        val header = ApiClient.buildAuthorizationHeader(tokenStore)

        assertNull(header)
    }

    @Test
    fun `api client normalizes device id and drops blank value`() {
        assertEquals("DEVICE-1", ApiClient.normalizeDeviceId("  DEVICE-1  "))
        assertNull(ApiClient.normalizeDeviceId("   "))
        assertNull(ApiClient.normalizeDeviceId(null))
    }
}
