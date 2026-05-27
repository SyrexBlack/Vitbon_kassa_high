package com.vitbon.kkm.features.licensing.domain

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.vitbon.kkm.data.remote.dto.LicenseCheckResponseDto
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.security.PrefsMigration
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class LicenseCheckerTest {
    private val context = mockk<Context>(relaxed = true)
    private val api = mockk<VitbonApi>(relaxed = true)
    private val plainPrefs = mockk<SharedPreferences>(relaxed = true)
    private val securePrefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    private lateinit var checker: LicenseChecker

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        mockkStatic(Settings.Secure::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "device-1"

        every { plainPrefs.contains(any()) } returns false
        every { securePrefs.contains(any()) } returns false
        every { plainPrefs.edit() } returns editor
        every { securePrefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor

        checker = LicenseChecker(context, api, plainPrefs, securePrefs)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkStatic(Settings.Secure::class)
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

    @Test
    fun `constructor migrates legacy license values into secure prefs`() {
        val inMemoryPlainPrefs = InMemorySharedPreferences()
        val inMemorySecurePrefs = InMemorySharedPreferences()
        inMemoryPlainPrefs.edit()
            .putString(PrefsMigration.KEY_LICENSE_STATUS, "GRACE_PERIOD")
            .putLong(PrefsMigration.KEY_LAST_CHECK, 100L)
            .putLong(PrefsMigration.KEY_GRACE_UNTIL, 200L)
            .apply()

        LicenseChecker(context, api, inMemoryPlainPrefs, inMemorySecurePrefs)

        assertEquals("GRACE_PERIOD", inMemorySecurePrefs.getString(PrefsMigration.KEY_LICENSE_STATUS, null))
        assertEquals(100L, inMemorySecurePrefs.getLong(PrefsMigration.KEY_LAST_CHECK, 0L))
        assertEquals(200L, inMemorySecurePrefs.getLong(PrefsMigration.KEY_GRACE_UNTIL, 0L))
        assertFalse(inMemoryPlainPrefs.contains(PrefsMigration.KEY_LICENSE_STATUS))
        assertFalse(inMemoryPlainPrefs.contains(PrefsMigration.KEY_LAST_CHECK))
        assertFalse(inMemoryPlainPrefs.contains(PrefsMigration.KEY_GRACE_UNTIL))
    }

    @Test
    fun `check blocks app when backend returns UNLICENSED`() {
        coEvery { api.checkLicense(any()) } returns Response.success(
            LicenseCheckResponseDto(
                status = "UNLICENSED",
                expiryDate = null,
                graceUntil = null
            )
        )

        val result = kotlinx.coroutines.runBlocking { checker.check() }

        assertTrue(result is LicenseStatus.Expired)
        assertTrue(checker.blockingState.value is AppBlockingState.Blocked)
        assertEquals(
            "Устройство не лицензировано. Обратитесь в поддержку.",
            (checker.blockingState.value as AppBlockingState.Blocked).reason
        )
    }

    @Test
    fun `check blocks app on first failed verification without prior active license`() {
        val offlineApi = mockk<VitbonApi>()
        val inMemoryPlainPrefs = InMemorySharedPreferences()
        val inMemorySecurePrefs = InMemorySharedPreferences()
        val localChecker = LicenseChecker(context, offlineApi, inMemoryPlainPrefs, inMemorySecurePrefs)
        coEvery { offlineApi.checkLicense(any()) } throws RuntimeException("timeout")

        val result = kotlinx.coroutines.runBlocking { localChecker.check() }

        assertTrue(result is LicenseStatus.Error)
        assertTrue(localChecker.blockingState.value is AppBlockingState.Blocked)
        assertEquals(0L, inMemorySecurePrefs.getLong(PrefsMigration.KEY_GRACE_UNTIL, 0L))
    }

    @Test
    fun `check starts grace after failed verification when device was previously active`() {
        val offlineApi = mockk<VitbonApi>()
        val inMemoryPlainPrefs = InMemorySharedPreferences()
        val inMemorySecurePrefs = InMemorySharedPreferences().apply {
            edit()
                .putString(PrefsMigration.KEY_LICENSE_STATUS, "ACTIVE")
                .putLong(PrefsMigration.KEY_LAST_CHECK, 1L)
                .apply()
        }
        val localChecker = LicenseChecker(context, offlineApi, inMemoryPlainPrefs, inMemorySecurePrefs)
        coEvery { offlineApi.checkLicense(any()) } returns Response.error(
            503,
            "unavailable".toResponseBody("text/plain".toMediaType())
        )

        val result = kotlinx.coroutines.runBlocking { localChecker.check() }

        assertTrue(result is LicenseStatus.GracePeriod)
        assertEquals(7, (result as LicenseStatus.GracePeriod).daysLeft)
        assertTrue(localChecker.blockingState.value is AppBlockingState.Unblocked)
        assertTrue(inMemorySecurePrefs.getLong(PrefsMigration.KEY_GRACE_UNTIL, 0L) > 0L)
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
