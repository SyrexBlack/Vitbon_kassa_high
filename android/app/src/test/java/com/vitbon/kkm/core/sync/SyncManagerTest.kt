package com.vitbon.kkm.core.sync

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.ProductDto
import com.vitbon.kkm.data.remote.dto.ProductSyncResponseDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class SyncManagerTest {

    @Test
    fun `syncProducts applies server deletedIds to local catalog and reports deleted count`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val prefs = mockk<android.content.SharedPreferences>()
        val editor = mockk<android.content.SharedPreferences.Editor>()

        every { prefs.getLong("lastProductSyncTimestamp", 0L) } returns 10L
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        val responseBody = ProductSyncResponseDto(
            products = listOf(
                ProductDto(
                    id = "p-1",
                    barcode = "4607001234567",
                    name = "Вода",
                    article = "WATER-05",
                    price = 12_900L,
                    vatRate = "NO_VAT",
                    categoryId = null,
                    stock = 5.0,
                    egaisFlag = false,
                    chaseznakFlag = false,
                    updatedAt = 1000L
                )
            ),
            deletedIds = listOf("p-old-1", "p-old-2"),
            serverTimestamp = 2000L
        )

        coEvery { api.getProducts(10L) } returns Response.success(responseBody)
        coEvery { productDao.deleteByIds(listOf("p-old-1", "p-old-2")) } returns 2

        val manager = SyncManager(api, checkDao, checkItemDao, productDao, SyncPrefs(prefs))

        val result = manager.syncProducts()

        assertEquals(1, result.received)
        assertEquals(2, result.deleted)
        coVerify(exactly = 1) { productDao.insertAll(match { it.size == 1 && it[0].id == "p-1" }) }
        coVerify(exactly = 1) { productDao.deleteByIds(listOf("p-old-1", "p-old-2")) }
    }

    @Test
    fun `syncProducts skips deleteByIds when server has no deletedIds`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val prefs = mockk<android.content.SharedPreferences>()
        val editor = mockk<android.content.SharedPreferences.Editor>()

        every { prefs.getLong("lastProductSyncTimestamp", 0L) } returns 0L
        every { prefs.edit() } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.apply() } returns Unit

        val responseBody = ProductSyncResponseDto(
            products = emptyList(),
            deletedIds = emptyList(),
            serverTimestamp = 111L
        )

        coEvery { api.getProducts(0L) } returns Response.success(responseBody)

        val manager = SyncManager(api, checkDao, checkItemDao, productDao, SyncPrefs(prefs))

        val result = manager.syncProducts()

        assertEquals(0, result.received)
        assertEquals(0, result.deleted)
        coVerify(exactly = 0) { productDao.deleteByIds(any()) }
    }
}
