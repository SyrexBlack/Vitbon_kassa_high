package com.vitbon.kkm.features.inventory.domain

import com.vitbon.kkm.data.local.dao.InventoryDocumentDao
import com.vitbon.kkm.data.local.dao.InventoryDocumentItemDao
import com.vitbon.kkm.data.local.entity.LocalInventoryDocument
import com.vitbon.kkm.data.local.entity.LocalInventoryDocumentItem
import com.vitbon.kkm.data.remote.api.VitbonApi
import com.vitbon.kkm.data.remote.dto.DocumentDto
import com.vitbon.kkm.data.remote.dto.DocumentItemDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED — vitbon-kassa-1rd.5.2: offline-first inventory documents.
 * Documents saved locally first, API send attempted, retry possible.
 * Sync status tracks pending/synced/error states.
 */
class InventoryDocumentRepositoryTest {

    private val dao = mockk<InventoryDocumentDao>(relaxed = true)
    private val itemDao = mockk<InventoryDocumentItemDao>(relaxed = true)
    private val api = mockk<VitbonApi>()
    private val repository = InventoryDocumentRepository(dao, itemDao, api)

    @Test
    fun `saveDocument stores locally and returns pending status`() = runTest {
        val doc = LocalInventoryDocument(
            id = "doc-1",
            type = "INVENTORY",
            status = "PENDING_SYNC",
            createdAt = System.currentTimeMillis()
        )
        val items = listOf(
            LocalInventoryDocumentItem(
                id = "item-1",
                documentId = "doc-1",
                barcode = "4607001234567",
                name = "Вода",
                expected = 10.0F,
                actual = 8.0F
            )
        )

        val result = repository.saveDocument(doc, items)

        assertEquals("PENDING_SYNC", result.status)
        coVerify { dao.insert(doc) }
    }

    @Test
    fun `submitDocument saves locally first then sends to API`() = runTest {
        val doc = LocalInventoryDocument(
            id = "doc-1",
            type = "INVENTORY",
            status = "PENDING_SYNC",
            createdAt = System.currentTimeMillis()
        )
        val items = listOf(
            LocalInventoryDocumentItem(
                id = "item-1",
                documentId = "doc-1",
                barcode = "4607001234567",
                name = "Вода",
                expected = 10.0F,
                actual = 8.0F
            )
        )
        coEvery { api.sendInventory(any()) } returns mockk(relaxed = true) {
            every { isSuccessful } returns true
        }

        val result = repository.submitDocument(doc, items)

        assertEquals("PENDING_SYNC", result.status)
        coVerify { dao.insert(match<LocalInventoryDocument> { it.status == "PENDING_SYNC" }) }
        coVerify { api.sendInventory(any()) }
    }

    @Test
    fun `submitDocument saves locally even if API fails`() = runTest {
        val doc = LocalInventoryDocument(
            id = "doc-2",
            type = "INVENTORY",
            status = "PENDING_SYNC",
            createdAt = System.currentTimeMillis()
        )
        val items = listOf(
            LocalInventoryDocumentItem(
                id = "item-2",
                documentId = "doc-2",
                barcode = "4607001234567",
                name = "Вода",
                expected = 10.0F,
                actual = 8.0F
            )
        )
        coEvery { api.sendInventory(any()) } returns mockk(relaxed = true) {
            every { isSuccessful } returns true
            every { code() } returns 503
        }

        val result = repository.submitDocument(doc, items)

        assertEquals("PENDING_SYNC", result.status)
        coVerify { dao.insert(match<LocalInventoryDocument> { it.id == "doc-2" && it.status == "PENDING_SYNC" }) }
    }

    @Test
    fun `markSynced updates document status to PENDING_SYNC`() = runTest {
        coEvery { dao.findById("doc-1") } returns LocalInventoryDocument(
            id = "doc-1",
            type = "INVENTORY",
            status = "PENDING_SYNC",
            createdAt = System.currentTimeMillis()
        )
        coEvery { dao.updateStatus("doc-1", "PENDING_SYNC", "fs-sign-1", null, any()) } returns Unit

        repository.markSynced("doc-1", "fs-sign-1")

        coVerify { dao.updateStatus("doc-1", "PENDING_SYNC", "fs-sign-1", null, any()) }
    }

    @Test
    fun `markError updates document status to SYNC_ERROR`() = runTest {
        coEvery { dao.findById("doc-1") } returns LocalInventoryDocument(
            id = "doc-1",
            type = "INVENTORY",
            status = "PENDING_SYNC",
            createdAt = System.currentTimeMillis()
        )

        repository.markError("doc-1", "Connection refused")

        coVerify { dao.updateStatus("doc-1", "SYNC_ERROR", null, errorMessage = "Connection refused") }
    }

    @Test
    fun `retryPending finds PENDING_SYNC documents and re-submits`() = runTest {
        val pending = listOf(
            LocalInventoryDocument(
                id = "doc-pending",
                type = "INVENTORY",
                status = "PENDING_SYNC",
                createdAt = System.currentTimeMillis()
            )
        )
        coEvery { dao.findPendingSync() } returns pending
        coEvery { api.sendInventory(any()) } returns mockk(relaxed = true) {
            every { isSuccessful } returns true
        }

        val result = repository.retryPending()

        assertEquals(1, result)
        coVerify { api.sendInventory(any()) }
        coVerify { dao.updateStatus("doc-pending", "PENDING_SYNC", null) }
    }

    @Test
    fun `getPendingCount returns count of unsynced documents`() = runTest {
        coEvery { dao.countPending() } returns 3

        val count = repository.getPendingCount()

        assertEquals(3, count)
    }
}
