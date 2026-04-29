package com.vitbon.kkm.features.rootdetection

import android.content.Context
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRiskGuardMigrationTest {

    @Test
    fun `guard migrates cached legacy root result to secure prefs and applies blocking state`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences()
        plainPrefs.edit()
            .putString("root_risk_cached", "DETECTED")
            .putLong("root_risk_ts", 123L)
            .apply()

        val guard = RootRiskGuard(
            context = TestRootRiskGuardContext(),
            detector = neverRunsDetector(),
            plainPrefs = plainPrefs,
            securePrefs = securePrefs,
            asyncCheckEnabled = false
        )

        val state = guard.getCurrentBlockingState()

        assertTrue(state is AppBlockingState.Blocked)
        assertEquals("DETECTED", securePrefs.getString("root_risk_cached", null))
        assertEquals(123L, securePrefs.getLong("root_risk_ts", 0L))
        assertFalse(plainPrefs.contains("root_risk_cached"))
        assertFalse(plainPrefs.contains("root_risk_ts"))
    }
}

private fun neverRunsDetector() = object : RootDetector {
    override suspend fun check(context: Context): RootCheckResult {
        error("Detector should not run in migration test")
    }
}
