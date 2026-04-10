package com.vitbon.kkm.features.bootstrap.domain

import com.vitbon.kkm.data.local.dao.CashierDao
import com.vitbon.kkm.data.local.dao.ProductDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataUseCaseTest {

    private val cashierDao = mockk<CashierDao>(relaxed = true)
    private val productDao = mockk<ProductDao>(relaxed = true)
    private val useCase = SeedDataUseCase(cashierDao, productDao)

    @Test
    fun `seedIfNeeded inserts demo cashier and product when counts are zero`() = runBlocking {
        coEvery { cashierDao.count() } returns 0
        coEvery { productDao.count() } returns 0

        useCase.seedIfNeeded()

        coVerify(exactly = 1) { cashierDao.insert(withArg { cashier ->
            assertEquals("cashier-demo-1", cashier.id)
            assertEquals("Демо Кассир", cashier.name)
            assertEquals("CASHIER", cashier.role)
            assertEquals(64, cashier.pinHash.length)
            assertTrue(cashier.pinHash.all { it in '0'..'9' || it in 'a'..'f' })
            assertTrue(cashier.createdAt > 0)
        }) }

        coVerify(exactly = 1) { productDao.insert(withArg { product ->
            assertEquals("product-demo-water-05", product.id)
            assertEquals("4607001234567", product.barcode)
            assertEquals("Вода 0.5л", product.name)
            assertEquals("DEMO-WATER-05", product.article)
            assertEquals(12900L, product.price)
            assertEquals("NO_VAT", product.vatRate)
            assertEquals(100.0, product.stock, 0.0)
            assertFalse(product.egaisFlag)
            assertFalse(product.chaseznakFlag)
            assertTrue(product.updatedAt > 0)
        }) }
    }

    @Test
    fun `seedIfNeeded does not insert when counts are non-zero`() = runBlocking {
        coEvery { cashierDao.count() } returns 1
        coEvery { productDao.count() } returns 10

        useCase.seedIfNeeded()

        coVerify(exactly = 0) { cashierDao.insert(any()) }
        coVerify(exactly = 0) { productDao.insert(any()) }
    }
}
