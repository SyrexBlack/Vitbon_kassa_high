package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalCore
import com.vitbon.kkm.core.fiscal.model.FFDVersion
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FfdVersionResolverTest {

    @Test
    fun `resolve uses core when no lock`() = runTest {
        val core = mockk<FiscalCore>()
        val store = mockk<FfdPolicyStore>()
        coEvery { core.getFFDVersion() } returns FFDVersion.V1_2
        every { store.read() } returns FfdPolicyState(null, "unknown", false, 0L)
        every { store.saveResolved(any(), any(), any(), any()) } returns Unit

        val resolver = FfdVersionResolver(core, store)
        val resolved = resolver.resolve(forceRefresh = false)

        assertEquals("1.2", resolved)
    }

    @Test
    fun `resolve keeps locked manual value`() = runTest {
        val core = mockk<FiscalCore>()
        val store = mockk<FfdPolicyStore>()
        every { store.read() } returns FfdPolicyState("1.05", "manual", true, 1L)

        val resolver = FfdVersionResolver(core, store)
        val resolved = resolver.resolve(forceRefresh = false)

        assertEquals("1.05", resolved)
    }
}
