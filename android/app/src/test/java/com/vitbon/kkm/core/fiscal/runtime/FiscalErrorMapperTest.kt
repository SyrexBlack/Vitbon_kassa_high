package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalException
import org.junit.Assert.assertEquals
import org.junit.Test

class FiscalErrorMapperTest {

    @Test
    fun `maps format errors to FORMAT_INVALID`() {
        val err = FiscalErrorMapper.map(FiscalException(1001, "invalid format", recoverable = true))
        assertEquals("FORMAT_INVALID", err.code)
        assertEquals(true, err.recoverable)
    }

    @Test
    fun `maps unknown errors to FISCAL_UNKNOWN`() {
        val err = FiscalErrorMapper.map(IllegalStateException("boom"))
        assertEquals("FISCAL_UNKNOWN", err.code)
    }
}
