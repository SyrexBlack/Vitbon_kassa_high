package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.core.fiscal.FiscalCore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FfdVersionResolver @Inject constructor(
    private val fiscalCore: FiscalCore,
    private val policyStore: FfdPolicyStore
) {
    suspend fun resolve(forceRefresh: Boolean): String {
        val current = policyStore.read()
        if (current.locked && !current.version.isNullOrBlank() && !forceRefresh) {
            return current.version
        }

        val resolved = fiscalCore.getFFDVersion().displayName
        policyStore.saveResolved(
            version = resolved,
            source = "auto",
            locked = current.locked,
            timestampMs = System.currentTimeMillis()
        )
        return resolved
    }

    fun saveManual(version: String) {
        policyStore.saveResolved(
            version = version,
            source = "manual",
            locked = true,
            timestampMs = System.currentTimeMillis()
        )
    }
}
