package com.vitbon.kkm.features.egais.domain

import com.vitbon.kkm.features.egais.domain.EgaisRepository
import com.vitbon.kkm.features.egais.domain.EgaisResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD — vitbon-kassa-1rd.1.3: Tara open-bottle act flow.
 * Act must be sent to UTM before selling from keg/bottle.
 */
class TaraActTest {

    private val egaisRepository = mockk<EgaisRepository>(relaxed = true)
    private val service = TaraActService(egaisRepository)

    @Test
    fun `tara act sent successfully returns success`() = runTest {
        coEvery { egaisRepository.sendTaraAct(any(), any(), any()) } returns
            EgaisResult.Success(egaisId = "tara-001", message = "Акт вскрытия тары отправлен")

        val result = service.sendTaraAct(
            checkId = "check-001",
            productBarcode = "4607000001",
            volume = 1.5
        )

        assertTrue(result is EgaisResult.Success)
        assertEquals("tara-001", (result as EgaisResult.Success).egaisId)
        coVerify { egaisRepository.sendTaraAct("check-001", "4607000001", 1.5) }
    }

    @Test
    fun `tara act fails and returns error when network error`() = runTest {
        coEvery { egaisRepository.sendTaraAct(any(), any(), any()) } returns
            EgaisResult.Error(code = -1, message = "Сеть недоступна")

        val result = service.sendTaraAct(
            checkId = "check-002",
            productBarcode = "4607000002",
            volume = 0.5
        )

        assertTrue(result is EgaisResult.Error)
        assertEquals("Сеть недоступна", (result as EgaisResult.Error).message)
    }
}

class TaraActService(
    private val egaisRepository: EgaisRepository
) {
    suspend fun sendTaraAct(checkId: String, productBarcode: String, volume: Double): EgaisResult {
        return egaisRepository.sendTaraAct(checkId, productBarcode, volume)
    }
}