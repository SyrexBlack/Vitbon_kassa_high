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
    fun `getCurrentBlockingState returns Unblocked for clean detector`() {
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
        assertTrue(state is AppBlockingState.Unblocked)
    }
}

internal class TestRootRiskGuardContext : ContextWrapper(android.app.Application())

private fun alwaysCleanDetector() = object : RootDetector {
    override suspend fun check(context: Context): RootCheckResult = RootCheckResult.Clean
}