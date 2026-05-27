package com.vitbon.kkm.core.sync

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.entity.AuditLogEntry
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.AuditSyncEntryDto
import com.vitbon.kkm.data.remote.dto.AuditSyncRequestDto
import com.vitbon.kkm.data.remote.dto.AuditSyncResponseDto
import com.vitbon.kkm.data.remote.dto.FailedAuditSyncDto
import com.vitbon.kkm.data.remote.dto.ProductDto
import com.vitbon.kkm.data.remote.dto.ProductSyncResponseDto
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences().apply {
            edit().putLong("lastProductSyncTimestamp", 10L).apply()
        }

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

        val manager = SyncManager(
            api,
            checkDao,
            checkItemDao,
            productDao,
            auditBufferRepository,
            SyncPrefs(prefs, InMemorySharedPreferences())
        )

        val result = manager.syncProducts()

        assertEquals(1, result.received)
        assertEquals(2, result.deleted)
        // Verify deleteByIds is called BEFORE insertAll
        coVerifyOrder {
            productDao.deleteByIds(listOf("p-old-1", "p-old-2"))
            productDao.insertAll(match { it.size == 1 && it[0].id == "p-1" })
        }
    }

    @Test
    fun `syncProducts skips deleteByIds when server has no deletedIds`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()

        val responseBody = ProductSyncResponseDto(
            products = emptyList(),
            deletedIds = emptyList(),
            serverTimestamp = 111L
        )

        coEvery { api.getProducts(0L) } returns Response.success(responseBody)

        val manager = SyncManager(
            api,
            checkDao,
            checkItemDao,
            productDao,
            auditBufferRepository,
            SyncPrefs(prefs, InMemorySharedPreferences())
        )

        val result = manager.syncProducts()

        assertEquals(0, result.received)
        assertEquals(0, result.deleted)
        coVerify(exactly = 0) { productDao.deleteByIds(any()) }
    }

    @Test
    fun `syncAuditLogs acknowledges successfully ingested buffered events`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()
        val pending = listOf(
            AuditLogEntry(
                id = "11111111-1111-1111-1111-111111111111",
                cashierId = "cashier-1",
                deviceId = "device-1",
                action = "auth.emergency.enter",
                details = "SUCCESS",
                timestamp = 1_000L
            ),
            AuditLogEntry(
                id = "22222222-2222-2222-2222-222222222222",
                cashierId = null,
                deviceId = "device-1",
                action = "auth.emergency.exit",
                details = "SUCCESS",
                timestamp = 2_000L
            )
        )

        coEvery { auditBufferRepository.pending(100) } returns pending
        coEvery {
            api.syncAudit(
                AuditSyncRequestDto(
                    events = listOf(
                        AuditSyncEntryDto(
                            id = "11111111-1111-1111-1111-111111111111",
                            cashierId = "cashier-1",
                            deviceId = "device-1",
                            action = "auth.emergency.enter",
                            details = "SUCCESS",
                            timestamp = 1_000L
                        ),
                        AuditSyncEntryDto(
                            id = "22222222-2222-2222-2222-222222222222",
                            cashierId = null,
                            deviceId = "device-1",
                            action = "auth.emergency.exit",
                            details = "SUCCESS",
                            timestamp = 2_000L
                        )
                    )
                )
            )
        } returns Response.success(
            AuditSyncResponseDto(processed = 2, failed = emptyList())
        )

        val manager = SyncManager(
            api,
            checkDao,
            checkItemDao,
            productDao,
            auditBufferRepository,
            SyncPrefs(prefs, InMemorySharedPreferences())
        )

        val result = manager.syncAuditLogs()

        assertEquals(2, result.synced)
        assertEquals(0, result.failed)
        coVerify { auditBufferRepository.acknowledge(listOf("11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222")) }
    }

    @Test
    fun `syncAuditLogs keeps failed buffered events pending`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()
        val pending = listOf(
            AuditLogEntry(
                id = "11111111-1111-1111-1111-111111111111",
                cashierId = "cashier-1",
                deviceId = "device-1",
                action = "auth.emergency.enter",
                details = "SUCCESS",
                timestamp = 1_000L
            ),
            AuditLogEntry(
                id = "22222222-2222-2222-2222-222222222222",
                cashierId = null,
                deviceId = "other-device",
                action = "auth.emergency.exit",
                details = "DENY:DEVICE_MISMATCH",
                timestamp = 2_000L
            )
        )

        coEvery { auditBufferRepository.pending(100) } returns pending
        coEvery { auditBufferRepository.acknowledge(listOf("11111111-1111-1111-1111-111111111111")) } returns Unit
        coEvery { 
            api.syncAudit(any())
        } returns Response.success(
            AuditSyncResponseDto(
                processed = 1,
                failed = listOf(
                    FailedAuditSyncDto(
                        id = "22222222-2222-2222-2222-222222222222",
                        error = "DEVICE_MISMATCH"
                    )
                )
            )
        )

        val manager = SyncManager(
            api,
            checkDao,
            checkItemDao,
            productDao,
            auditBufferRepository,
            SyncPrefs(prefs, InMemorySharedPreferences())
        )

        val result = manager.syncAuditLogs()

        assertEquals(1, result.synced)
        assertEquals(1, result.failed)
        coVerify { auditBufferRepository.acknowledge(listOf("11111111-1111-1111-1111-111111111111")) }
    }
}
