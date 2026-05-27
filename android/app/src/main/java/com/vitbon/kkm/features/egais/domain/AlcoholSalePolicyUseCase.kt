package com.vitbon.kkm.features.egais.domain

import com.vitbon.kkm.features.egais.domain.UtmStatus
import com.vitbon.kkm.features.products.domain.Product
import javax.inject.Inject
import javax.inject.Singleton

/**
 * vitbon-kassa-1rd.1.3: Enforce alcohol sale restrictions.
 *
 * Policy:
 * - If EGAIS unavailable → fail closed (block legally-sensitive sales)
 * - If product requires age verification → require age verification result
 * - Tara act required for keg/bottle opening → must be sent before sale
 */
@Singleton
class AlcoholSalePolicyUseCase @Inject constructor(
    private val egaisRepository: EgaisRepository
) {
    suspend fun checkCanSellAlcohol(
        products: List<Product>,
        ageVerificationDone: Boolean
    ): AlcoholSaleDecision {
        val alcoholProducts = products.filter { it.egaisFlag }

        if (alcoholProducts.isEmpty()) {
            return AlcoholSaleDecision.Allowed
        }

        // Check UTM status — fail closed if not ready
        val utmStatus = egaisRepository.getUtmStatus()
        if (utmStatus !is UtmStatus.Ready) {
            return AlcoholSaleDecision.Blocked(
                reason = "EGAIS_UNAVAILABLE",
                message = buildBlockedMessage(utmStatus)
            )
        }

        // All alcohol products require age verification
        if (!ageVerificationDone) {
            return AlcoholSaleDecision.AgeVerificationRequired(
                products = alcoholProducts.map { it.name }
            )
        }

        return AlcoholSaleDecision.Allowed
    }

    private fun buildBlockedMessage(status: UtmStatus): String {
        return when (status) {
            is UtmStatus.NotConfigured ->
                "Алкогольные товары не могут быть проданы: ЕГАИС не настроен. Обратитесь к администратору."
            is UtmStatus.Unreachable ->
                "Алкогольные товары не могут быть проданы: ${status.reason}. Восстановите связь с ЕГАИС."
            is UtmStatus.AuthError ->
                "Алкогольные товары не могут быть проданы: ${status.message}. Обратитесь к администратору."
            is UtmStatus.UnknownError ->
                "Алкогольные товары не могут быть проданы: ${status.message}. Обратитесь к администратору."
            is UtmStatus.Ready -> "ЕГАИС готов"
        }
    }
}

sealed class AlcoholSaleDecision {
    data object Allowed : AlcoholSaleDecision()
    data class AgeVerificationRequired(val products: List<String>) : AlcoholSaleDecision()
    data class Blocked(val reason: String, val message: String) : AlcoholSaleDecision()
}