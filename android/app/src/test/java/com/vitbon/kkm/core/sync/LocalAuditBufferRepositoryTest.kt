package com.vitbon.kkm.core.sync

import com.vitbon.kkm.data.local.dao.AuditLogDao
import com.vitbon.kkm.data.local.entity.AuditLogEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuditBufferRepositoryTest {

    @Test
    fun `enqueue adds event to pending queue`() = runBlocking {
        val dao = FakeAuditLogDao()
        val repository = LocalAuditBufferRepository(dao)

        repository.enqueue(
            cashierId = "cashier-1",
            deviceId = "device-1",
            action = "auth.login",
            details = "SUCCESS",
            timestamp = 1_000L
        )

        val pending = repository.pending(limit = 10)

        assertEquals(1, pending.size)
        assertEquals("cashier-1", pending.first().cashierId)
        assertEquals("auth.login", pending.first().action)
        assertEquals(1_000L, pending.first().timestamp)
    }

    @Test
    fun `pending returns oldest events first and respects limit`() = runBlocking {
        val dao = FakeAuditLogDao()
        val repository = LocalAuditBufferRepository(dao)

        repository.enqueue(null, "device-1", "third", null, timestamp = 3_000L)
        repository.enqueue(null, "device-1", "first", null, timestamp = 1_000L)
        repository.enqueue(null, "device-1", "second", null, timestamp = 2_000L)

        val pending = repository.pending(limit = 2)

        assertEquals(2, pending.size)
        assertEquals("first", pending[0].action)
        assertEquals("second", pending[1].action)
    }

    @Test
    fun `acknowledge removes only acknowledged events`() = runBlocking {
        val dao = FakeAuditLogDao()
        val repository = LocalAuditBufferRepository(dao)

        val firstId = repository.enqueue(null, "device-1", "first", null, timestamp = 1_000L)
        repository.enqueue(null, "device-1", "second", null, timestamp = 2_000L)

        repository.acknowledge(listOf(firstId))

        val pending = repository.pending(limit = 10)
        assertEquals(1, pending.size)
        assertEquals("second", pending.first().action)
    }

    @Test
    fun `acknowledge with empty ids is no-op`() = runBlocking {
        val dao = FakeAuditLogDao()
        val repository = LocalAuditBufferRepository(dao)

        repository.enqueue(null, "device-1", "first", null, timestamp = 1_000L)
        repository.acknowledge(emptyList())

        val pending = repository.pending(limit = 10)
        assertTrue(pending.isNotEmpty())
    }
}

private class FakeAuditLogDao : AuditLogDao {
    private val entries = mutableListOf<AuditLogEntry>()

    override suspend fun insert(entry: AuditLogEntry) {
        entries.removeAll { it.id == entry.id }
        entries.add(entry)
    }

    override suspend fun findPending(limit: Int): List<AuditLogEntry> {
        return entries
            .sortedBy { it.timestamp }
            .take(limit)
    }

    override suspend fun deleteByIds(ids: List<String>) {
        entries.removeAll { it.id in ids }
    }
}
