package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalException

object FiscalErrorMapper {
    fun map(t: Throwable): FiscalRuntimeResult.Error {
        if (t is FiscalException) {
            val code = when {
                t.message?.contains("format", ignoreCase = true) == true -> "FORMAT_INVALID"
                t.message?.contains("shift", ignoreCase = true) == true -> "SHIFT_ERROR"
                else -> "FISCAL_ERROR"
            }
            return FiscalRuntimeResult.Error(
                code = code,
                message = t.message ?: "Fiscal error",
                recoverable = t.recoverable
            )
        }
        return FiscalRuntimeResult.Error(
            code = "FISCAL_UNKNOWN",
            message = t.message ?: "Unknown error",
            recoverable = false
        )
    }
}
