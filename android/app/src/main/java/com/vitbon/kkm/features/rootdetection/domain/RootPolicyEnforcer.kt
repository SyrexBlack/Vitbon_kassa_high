package com.vitbon.kkm.features.rootdetection.domain

import com.vitbon.kkm.features.licensing.domain.AppBlockingState

object RootPolicyEnforcer {

    fun toBlockingState(result: RootCheckResult): AppBlockingState {
        return when (result) {
            is RootCheckResult.Clean -> AppBlockingState.Unblocked
            is RootCheckResult.Detected -> AppBlockingState.Blocked(
                "Устройство скомпрометировано: обнаружен root. Fiscal-операции заблокированы. Код для поддержки: ROOT-${result.indicators.size}"
            )
        }
    }
}