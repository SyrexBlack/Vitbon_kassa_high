package com.vitbon.kkm.features.rootdetection.domain

import org.junit.Assert.*
import org.junit.Test

class RootDetectorTest {

    @Test
    fun `RootCheckResult Clean is correct type`() {
        val result: RootCheckResult = RootCheckResult.Clean
        assertTrue(result is RootCheckResult.Clean)
    }

    @Test
    fun `RootCheckResult Detected contains indicator list`() {
        val result = RootCheckResult.Detected(
            listOf(
                RootIndicator("su_binary", "/system/xbin/su"),
                RootIndicator("magisk", "com.topjohnwu.magisk")
            )
        )
        val detected = result as RootCheckResult.Detected
        assertEquals(2, detected.indicators.size)
        assertEquals("su_binary", detected.indicators[0].type)
        assertEquals("/system/xbin/su", detected.indicators[0].detail)
    }

    @Test
    fun `RootIndicator has type and detail fields`() {
        val indicator = RootIndicator("test_keys", "ro.build.tags=test-keys")
        assertEquals("test_keys", indicator.type)
        assertEquals("ro.build.tags=test-keys", indicator.detail)
    }

    @Test
    fun `RootDetector interface exists with suspend check method signature`() {
        val mockDetector = object : RootDetector {
            override suspend fun check(context: android.content.Context): RootCheckResult {
                return RootCheckResult.Clean
            }
        }
        assertNotNull(mockDetector)
    }
}