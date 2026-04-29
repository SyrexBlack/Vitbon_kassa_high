package com.vitbon.kkm.features.rootdetection.domain

import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import org.junit.Assert.*
import org.junit.Test

class RootPolicyEnforcerTest {

    @Test
    fun `Clean result maps to Unblocked`() {
        val result = RootCheckResult.Clean
        val state = RootPolicyEnforcer.toBlockingState(result)
        assertTrue(state is AppBlockingState.Unblocked)
    }

    @Test
    fun `Detected result maps to Blocked`() {
        val result = RootCheckResult.Detected(
            listOf(RootIndicator("su_binary", "/system/xbin/su"))
        )
        val state = RootPolicyEnforcer.toBlockingState(result)
        assertTrue(state is AppBlockingState.Blocked)
    }

    @Test
    fun `Detected message contains ROOT-N code`() {
        val result = RootCheckResult.Detected(
            listOf(
                RootIndicator("su_binary", "/system/xbin/su"),
                RootIndicator("magisk", "com.topjohnwu.magisk"),
                RootIndicator("debuggable", "ro.debuggable=1")
            )
        )
        val state = RootPolicyEnforcer.toBlockingState(result) as AppBlockingState.Blocked
        assertTrue(state.reason.contains("ROOT-3"))
    }

    @Test
    fun `Single indicator gets ROOT-1 code`() {
        val result = RootCheckResult.Detected(
            listOf(RootIndicator("su_binary", "/system/bin/su"))
        )
        val state = RootPolicyEnforcer.toBlockingState(result) as AppBlockingState.Blocked
        assertTrue(state.reason.contains("ROOT-1"))
        assertTrue(state.reason.contains("скомпрометировано"))
    }

    @Test
    fun `Detected with empty indicators still blocked`() {
        val result = RootCheckResult.Detected(emptyList())
        val state = RootPolicyEnforcer.toBlockingState(result) as AppBlockingState.Blocked
        assertTrue(state.reason.contains("ROOT-0"))
    }
}