package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalException
import org.junit.Assert.*
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

    @Test
    fun `maps shift errors to SHIFT_ERROR`() {
        val err = FiscalErrorMapper.map(FiscalException(2, "Shift not open", recoverable = false))
        assertEquals("SHIFT_ERROR", err.code)
        assertEquals(false, err.recoverable)
    }

    @Test
    fun `maps recoverable timeout errors as recoverable`() {
        val err = FiscalErrorMapper.map(FiscalException(3, "timeout", recoverable = true))
        assertEquals("FISCAL_ERROR", err.code)
        assertEquals(true, err.recoverable)
        assertTrue(err.message.contains("timeout"))
    }

    @Test
    fun `maps non-recoverable errors with message preserved`() {
        val err = FiscalErrorMapper.map(FiscalException(4, "FN memory full", recoverable = false))
        assertEquals("FISCAL_ERROR", err.code)
        assertEquals(false, err.recoverable)
        assertTrue(err.message.contains("FN memory"))
    }

    @Test
    fun `maps fiscal exception with null message`() {
        val err = FiscalErrorMapper.map(FiscalException(5, "", recoverable = true))
        assertEquals("FISCAL_ERROR", err.code)
        assertEquals(true, err.recoverable)
    }

    @Test
    fun `maps generic throwable to FISCAL_UNKNOWN`() {
        val err = FiscalErrorMapper.map(RuntimeException("unexpected"))
        assertEquals("FISCAL_UNKNOWN", err.code)
        assertEquals(false, err.recoverable)
        assertTrue(err.message.contains("unexpected"))
    }

    @Test
    fun `maps empty fiscal exception to FISCAL_UNKNOWN`() {
        val err = FiscalErrorMapper.map(FiscalException(0, "", recoverable = false))
        assertEquals("FISCAL_ERROR", err.code)
    }
}
