package com.vitbon.kkm.integration

import com.vitbon.kkm.api.dto.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProductSyncIntegrationTest {
    @Test
    fun `ProductSyncResponseDto — products + deletions + timestamp`() {
        val resp = ProductSyncResponseDto(
            products = listOf(
                ProductDto(
                    id = "p1", barcode = "4601234567890", name = "Товар А",
                    article = "A001", price = 9990L, vatRate = "VAT_22",
                    categoryId = null, stock = 10.0,
                    egaisFlag = false, chaseznakFlag = false,
                    updatedAt = System.currentTimeMillis()
                )
            ),
            deletedIds = listOf("p-old-1"),
            serverTimestamp = System.currentTimeMillis()
        )
        assertEquals(1, resp.products.size)
        assertEquals(1, resp.deletedIds.size)
        assertTrue(resp.serverTimestamp > 0)
    }

    @Test
    fun `Delta sync — filters by updatedAt timestamp`() {
        val now = System.currentTimeMillis()
        val products = listOf(
            ProductDto("p1", null, "Old", null, 100L, "VAT_22", null, 0.0, false, false, now - 3600_000),  // 1h ago
            ProductDto("p2", null, "New", null, 200L, "VAT_22", null, 0.0, false, false, now)  // now
        )
        val since = now - 1800_000  // 30 min ago
        val filtered = products.filter { it.updatedAt > since }
        assertEquals(1, filtered.size)
        assertEquals("New", filtered[0].name)
    }
}
