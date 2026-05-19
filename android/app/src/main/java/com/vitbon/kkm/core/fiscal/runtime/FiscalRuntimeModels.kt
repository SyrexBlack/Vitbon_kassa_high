package com.vitbon.kkm.core.fiscal.runtime

sealed class FiscalRuntimeResult {
    data class Success(
        val fiscalSign: String,
        val fnNumber: String,
        val fdNumber: String,
        val ffdVersion: String,
        val warnings: List<String> = emptyList()
    ) : FiscalRuntimeResult()

    data class Error(
        val code: String,
        val message: String,
        val recoverable: Boolean
    ) : FiscalRuntimeResult()
}
