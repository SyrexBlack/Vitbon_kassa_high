package com.vitbon.kkm.features.rootdetection

import android.content.Context
import android.content.ContextWrapper
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import org.junit.Assert.assertTrue
import org.junit.Test

class RootRiskGuardTest {

    @Test
    fun `getCurrentBlockingState stays blocked until a fresh root check completes`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences()
        val guard = RootRiskGuard(
            context = TestRootRiskGuardContext(),
            detector = alwaysCleanDetector(),
            plainPrefs = plainPrefs,
            securePrefs = securePrefs,
            asyncCheckEnabled = false
        )
        val state = guard.getCurrentBlockingState()
        assertTrue(state is AppBlockingState.Blocked)
    }

    @Test
    fun `cached clean result does not unblock without a fresh root check`() {
        val plainPrefs = InMemorySharedPreferences()
        val securePrefs = InMemorySharedPreferences().apply {
            edit()
                .putString("root_risk_cached", "CLEAN")
                .putLong("root_risk_ts", 123L)
                .apply()
        }
        val guard = RootRiskGuard(
            context = TestRootRiskGuardContext(),
            detector = alwaysCleanDetector(),
            plainPrefs = plainPrefs,
            securePrefs = securePrefs,
            asyncCheckEnabled = false
        )

        val state = guard.getCurrentBlockingState()

        assertTrue(state is AppBlockingState.Blocked)
    }
}

internal class TestRootRiskGuardContext : ContextWrapper(android.app.Application())

private fun alwaysCleanDetector() = object : RootDetector {
    override suspend fun check(context: Context): RootCheckResult = RootCheckResult.Clean
}