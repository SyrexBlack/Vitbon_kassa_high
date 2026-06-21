package com.vitbon.kkm.core.sync

import com.vitbon.kkm.data.local.dao.CheckDao
import com.vitbon.kkm.data.local.dao.CheckItemDao
import com.vitbon.kkm.data.local.dao.ProductDao
import com.vitbon.kkm.data.local.entity.AuditLogEntry
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.AuditSyncEntryDto
import com.vitbon.kkm.data.remote.dto.AuditSyncRequestDto
import com.vitbon.kkm.data.remote.dto.AuditSyncResponseDto
import com.vitbon.kkm.data.remote.dto.CheckSyncResponseDto
import com.vitbon.kkm.data.remote.dto.FailedAuditSyncDto
import com.vitbon.kkm.data.remote.dto.ProductDto
import com.vitbon.kkm.data.remote.dto.ProductSyncResponseDto
import com.vitbon.kkm.testutil.InMemorySharedPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // ─── vitbon-kassa-1rd.6.1: 500-doc queue cap ────────────────────────────

    @Test
    fun `syncChecks caps fetched batch at CHECK_BATCH_LIMIT (500) to avoid OOM on long-offline devices`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()

        val limitSlot = slot<Int>()
        coEvery { checkDao.findPendingSync(capture(limitSlot)) } returns emptyList()

        val manager = SyncManager(
            api,
            checkDao,
            checkItemDao,
            productDao,
            auditBufferRepository,
            SyncPrefs(prefs, InMemorySharedPreferences())
        )

        manager.syncChecks()

        assertEquals(500, limitSlot.captured)
    }

    @Test
    fun `syncChecks passes only the capped batch to api_syncChecks (rest stays PENDING_SYNC for next cycle)`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()

        // Simulate 500 pending checks returned by the capped query
        val capped = (0 until 500).map { i ->
            com.vitbon.kkm.data.local.entity.LocalCheck(
                id = "check-$i",
                localUuid = "uuid-$i",
                shiftId = "shift-1",
                cashierId = "c1",
                deviceId = "d1",
                type = "SALE",
                fiscalSign = "fs-$i",
                ofdResponse = null,
                ffdVersion = "1.2",
                status = "PENDING_SYNC",
                subtotal = 1000L,
                discount = 0L,
                total = 1000L,
                taxAmount = 200L,
                paymentType = "CASH",
                createdAt = i.toLong(),
                syncedAt = null
            )
        }
        coEvery { checkDao.findPendingSync(500) } returns capped
        coEvery { checkItemDao.findByCheckId(any()) } returns emptyList()
        coEvery { api.syncChecks(any()) } returns Response.success(
            CheckSyncResponseDto(
                processed = 500,
                failed = emptyList()
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

        val result = manager.syncChecks()

        assertEquals(500, result.synced)
        assertEquals(0, result.failed)
        // The cap is enforced at the SQL level: only one findPendingSync(500) call
        coVerify(exactly = 1) { checkDao.findPendingSync(500) }
    }

    @Test
    fun `syncChecks returns zero result when no pending checks exist (no api call)`() = runTest {
        val api = mockk<VitbonApi>()
        val checkDao = mockk<CheckDao>(relaxed = true)
        val checkItemDao = mockk<CheckItemDao>(relaxed = true)
        val productDao = mockk<ProductDao>(relaxed = true)
        val auditBufferRepository = mockk<LocalAuditBufferRepository>(relaxed = true)
        val prefs = InMemorySharedPreferences()

        coEvery { checkDao.findPendingSync(500) } returns emptyList()

        val manager = SyncManager(
            api,
            checkDao,
            checkItemDao,
            productDao,
            auditBufferRepository,
            SyncPrefs(prefs, InMemorySharedPreferences())
        )

        val result = manager.syncChecks()

        assertEquals(0, result.synced)
        assertEquals(0, result.failed)
        coVerify(exactly = 0) { api.syncChecks(any()) }
    }
}
