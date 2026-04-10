package com.vitbon.kkm.features.licensing.domain

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vitbon.kkm.data.remote.api.VitbonApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LicenseCheckerTest {
    private val context = mockk<Context>(relaxed = true)
    private val api = mockk<VitbonApi>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private lateinit var checker: LicenseChecker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor

        checker = LicenseChecker(context, api, prefs)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `grace period starts with 7 full days`() {
        val dayMs = 24L * 60 * 60 * 1000
        val now = 1_000_000L
        val graceUntil = now + 7L * dayMs

        val result = invokePrivateGraceMethod("handleExpired", now, graceUntil)

        assertTrue(result is LicenseStatus.GracePeriod)
        assertEquals(7, (result as LicenseStatus.GracePeriod).daysLeft)
    }

    @Test
    fun `grace period keeps 6 days after one day elapsed`() {
        val dayMs = 24L * 60 * 60 * 1000
        val graceStart = 1_000_000L
        val now = graceStart + dayMs
        val graceUntil = graceStart + 7L * dayMs

        val result = invokePrivateGraceMethod("handleExpired", now, graceUntil)

        assertTrue(result is LicenseStatus.GracePeriod)
        assertEquals(6, (result as LicenseStatus.GracePeriod).daysLeft)
    }

    @Test
    fun `grace period keeps at least one day while remaining ms is positive`() {
        val dayMs = 24L * 60 * 60 * 1000
        val graceStart = 1_000_000L
        val now = graceStart + 6L * dayMs + 23L * 60 * 60 * 1000
        val graceUntil = graceStart + 7L * dayMs

        val result = invokePrivateGraceMethod("handleExpired", now, graceUntil)

        assertTrue(result is LicenseStatus.GracePeriod)
        assertEquals(1, (result as LicenseStatus.GracePeriod).daysLeft)
    }

    @Test
    fun `LicenseStatus — all states covered`() {
        val active = LicenseStatus.Active
        val grace = LicenseStatus.GracePeriod(3)
        val expired = LicenseStatus.Expired
        val error = LicenseStatus.Error("timeout")

        assertTrue(active is LicenseStatus.Active)
        assertTrue(grace is LicenseStatus.GracePeriod)
        assertEquals(3, (grace as LicenseStatus.GracePeriod).daysLeft)
        assertTrue(expired is LicenseStatus.Expired)
        assertTrue(error is LicenseStatus.Error)
        assertEquals("timeout", (error as LicenseStatus.Error).message)
    }

    @Test
    fun `AppBlockingState — blocked vs unblocked`() {
        val unblocked = AppBlockingState.Unblocked
        val blocked = AppBlockingState.Blocked("Просрочка")

        assertTrue(unblocked is AppBlockingState.Unblocked)
        assertTrue(blocked is AppBlockingState.Blocked)
        assertEquals("Просрочка", (blocked as AppBlockingState.Blocked).reason)
    }

    private fun invokePrivateGraceMethod(methodName: String, now: Long, graceUntil: Long?): LicenseStatus {
        val method = LicenseChecker::class.java.getDeclaredMethod(
            methodName,
            Long::class.javaPrimitiveType,
            java.lang.Long::class.java
        )
        method.isAccessible = true
        return method.invoke(checker, now, graceUntil) as LicenseStatus
    }
}
