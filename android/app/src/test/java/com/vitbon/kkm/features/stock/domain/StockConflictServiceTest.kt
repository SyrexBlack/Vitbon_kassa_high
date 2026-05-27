package com.vitbon.kkm.features.stock.domain

import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.dao.StockMovementDao
import com.vitbon.kkm.data.local.entity.LocalProduct
import com.vitbon.kkm.data.local.entity.StockMovement
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockConflictServiceTest {
    private val productDao = mockk<ProductDao>(relaxed = true)
    private val movementDao = mockk<StockMovementDao>(relaxed = true)
    private val service = StockConflictService(productDao, movementDao)

    @Test
    fun `conflict detected when stock would go negative on sale`() = runTest {
        val product = LocalProduct(
            id = "p-1", barcode = "4607001234567", name = "Вода",
            price = 15000L, categoryId = null, stock = 5.0,
            article = null, vatRate = "none", egaisFlag = false,
            chaseznakFlag = false, updatedAt = System.currentTimeMillis()
        )
        coEvery { productDao.findById("p-1") } returns product

        val conflicts = service.checkSaleConflicts(listOf(
            StockMutation("p-1", 10.0, "SALE")
        ))

        assertEquals(1, conflicts.size)
        assertEquals("p-1", conflicts[0].productId)
        assertTrue(conflicts[0].message.contains("Недостаточно"))
    }

    @Test
    fun `no conflict when stock is sufficient`() = runTest {
        val product = LocalProduct(
            id = "p-1", barcode = "4607001234567", name = "Вода",
            price = 15000L, categoryId = null, stock = 20.0,
            article = null, vatRate = "none", egaisFlag = false,
            chaseznakFlag = false, updatedAt = System.currentTimeMillis()
        )
        coEvery { productDao.findById("p-1") } returns product

        val conflicts = service.checkSaleConflicts(listOf(
            StockMutation("p-1", 10.0, "SALE")
        ))

        assertTrue(conflicts.isEmpty())
    }

    @Test
    fun `conflict detected when product not found`() = runTest {
        coEvery { productDao.findById("p-unknown") } returns null

        val conflicts = service.checkSaleConflicts(listOf(
            StockMutation("p-unknown", 1.0, "SALE")
        ))

        assertEquals(1, conflicts.size)
        assertTrue(conflicts[0].message.contains("не найден"))
    }

    @Test
    fun `ledger balance matches product stock`() = runTest {
        val product = LocalProduct(
            id = "p-1", barcode = "4607001234567", name = "Вода",
            price = 15000L, categoryId = null, stock = 15.0,
            article = null, vatRate = "none", egaisFlag = false,
            chaseznakFlag = false, updatedAt = System.currentTimeMillis()
        )
        coEvery { productDao.findById("p-1") } returns product
        val movements = listOf(
            StockMovement(id = "m-1", productId = "p-1", delta = -5.0, type = "SALE", referenceId = "ch-1", timestamp = System.currentTimeMillis()),
            StockMovement(id = "m-2", productId = "p-1", delta = 20.0, type = "INCOME", referenceId = "inv-1", timestamp = System.currentTimeMillis())
        )
        coEvery { movementDao.findByProductId("p-1") } returns movements

        val result = service.verifyLedgerBalance("p-1")

        assertTrue(result.isBalanced)
        assertFalse(result.hasConflict)
    }

    @Test
    fun `ledger mismatch detected and flagged`() = runTest {
        val product = LocalProduct(
            id = "p-1", barcode = "4607001234567", name = "Вода",
            price = 15000L, categoryId = null, stock = 100.0,
            article = null, vatRate = "none", egaisFlag = false,
            chaseznakFlag = false, updatedAt = System.currentTimeMillis()
        )
        coEvery { productDao.findById("p-1") } returns product
        val movements = listOf(
            StockMovement(id = "m-1", productId = "p-1", delta = -5.0, type = "SALE", referenceId = "ch-1", timestamp = System.currentTimeMillis())
        )
        coEvery { movementDao.findByProductId("p-1") } returns movements

        val result = service.verifyLedgerBalance("p-1")

        assertFalse(result.isBalanced)
        assertTrue(result.hasConflict)
        assertEquals(-5.0, result.ledgerBalance, 0.001)
        assertEquals(100.0, result.productStock, 0.001)
        assertEquals(105.0, result.discrepancy, 0.001)
    }

    @Test
    fun `recordMovement persists stock movement with type and reference`() = runTest {
        val movement = StockMovement(
            id = "m-1", productId = "p-1", delta = -3.0, type = "SALE",
            referenceId = "ch-123", timestamp = System.currentTimeMillis()
        )

        service.recordMovement(movement)

        coVerify { movementDao.insert(movement) }
    }
}