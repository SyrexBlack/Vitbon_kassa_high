package com.vitbon.kkm.features.egais.domain

import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.VatRate
import com.vitbon.kkm.features.egais.domain.AlcoholSaleDecision
import com.vitbon.kkm.features.egais.domain.AlcoholSalePolicyUseCase
import com.vitbon.kkm.features.egais.domain.UtmStatus
import com.vitbon.kkm.features.products.domain.Product
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD — vitbon-kassa-1rd.1.3: Enforce alcohol sale restrictions.
 * EGAIS unavailable → fail closed. Age verification required for all alcohol sales.
 */
class AlcoholSalePolicyUseCaseTest {

    private val egaisRepository = mockk<EgaisRepository>(relaxed = true)
    private val policy = AlcoholSalePolicyUseCase(egaisRepository)

    private val regularProduct = Product(
        id = "p-1", barcode = "4607000001", article = null, name = "Вода",
        price = Money(100_00L), vatRate = VatRate.VAT_22,
        stock = 10.0, egaisFlag = false, chaseznakFlag = false
    )

    private val alcoholProduct = Product(
        id = "p-2", barcode = "4607000002", article = null, name = "Пиво 0.5л",
        price = Money(200_00L), vatRate = VatRate.VAT_22,
        stock = 10.0, egaisFlag = true, chaseznakFlag = false
    )

    private val egaisBeer = Product(
        id = "p-3", barcode = "4607000003", article = null, name = "Вино 0.75л",
        price = Money(500_00L), vatRate = VatRate.VAT_22,
        stock = 5.0, egaisFlag = true, chaseznakFlag = false
    )

    @Test
    fun `non-alcohol products always allowed`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.Ready

        val result = policy.checkCanSellAlcohol(
            products = listOf(regularProduct),
            ageVerificationDone = false
        )

        assertEquals(AlcoholSaleDecision.Allowed, result)
    }

    @Test
    fun `alcohol with UTM ready and age verified allowed`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.Ready

        val result = policy.checkCanSellAlcohol(
            products = listOf(alcoholProduct, egaisBeer),
            ageVerificationDone = true
        )

        assertEquals(AlcoholSaleDecision.Allowed, result)
    }

    @Test
    fun `alcohol without age verification blocked`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.Ready

        val result = policy.checkCanSellAlcohol(
            products = listOf(alcoholProduct),
            ageVerificationDone = false
        )

        assertTrue(result is AlcoholSaleDecision.AgeVerificationRequired)
        val blocked = result as AlcoholSaleDecision.AgeVerificationRequired
        assertTrue(blocked.products.contains("Пиво 0.5л"))
    }

    @Test
    fun `alcohol with UTM unreachable blocked with message`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.Unreachable("Таймаут подключения")

        val result = policy.checkCanSellAlcohol(
            products = listOf(alcoholProduct),
            ageVerificationDone = true
        )

        assertTrue(result is AlcoholSaleDecision.Blocked)
        val blocked = result as AlcoholSaleDecision.Blocked
        assertEquals("EGAIS_UNAVAILABLE", blocked.reason)
        assertTrue(blocked.message.contains("Таймаут"))
    }

    @Test
    fun `alcohol with UTM not configured blocked`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.NotConfigured

        val result = policy.checkCanSellAlcohol(
            products = listOf(alcoholProduct),
            ageVerificationDone = true
        )

        assertTrue(result is AlcoholSaleDecision.Blocked)
        val blocked = result as AlcoholSaleDecision.Blocked
        assertEquals("EGAIS_UNAVAILABLE", blocked.reason)
        assertTrue(blocked.message.contains("не настроен"))
    }

    @Test
    fun `alcohol with UTM auth error blocked`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.AuthError("Ошибка 401")

        val result = policy.checkCanSellAlcohol(
            products = listOf(alcoholProduct),
            ageVerificationDone = true
        )

        assertTrue(result is AlcoholSaleDecision.Blocked)
        val blocked = result as AlcoholSaleDecision.Blocked
        assertEquals("EGAIS_UNAVAILABLE", blocked.reason)
    }

    @Test
    fun `alcohol with mixed UTM errors returns unavailable`() = runTest {
        coEvery { egaisRepository.getUtmStatus() } returns UtmStatus.UnknownError("Неизвестная ошибка")

        val result = policy.checkCanSellAlcohol(
            products = listOf(alcoholProduct),
            ageVerificationDone = true
        )

        assertTrue(result is AlcoholSaleDecision.Blocked)
        val blocked = result as AlcoholSaleDecision.Blocked
        assertEquals("EGAIS_UNAVAILABLE", blocked.reason)
    }
}