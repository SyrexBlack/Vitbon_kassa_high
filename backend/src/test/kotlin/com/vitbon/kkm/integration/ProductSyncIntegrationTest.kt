package com.vitbon.kkm.integration

import com.fasterxml.jackson.databind.ObjectMapper
import com.vitbon.kkm.api.dto.ProductDto
import com.vitbon.kkm.api.dto.ProductSyncResponseDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp

@SpringBootTest
class ProductSyncIntegrationTest {

    @Autowired
    lateinit var context: WebApplicationContext

    @Autowired
    lateinit var objectMapper: ObjectMapper

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
        val mockMvc: MockMvc = MockMvcBuilders.webAppContextSetup(context).build()
        val base = System.currentTimeMillis()
        val oldTs = base - 3_600_000
        val newTs = base

        withConnection { conn ->
            conn.prepareStatement("DELETE FROM product_deletions").executeUpdate()
            conn.prepareStatement("DELETE FROM products").executeUpdate()
            conn.prepareStatement(
                """
                INSERT INTO product_deletions (product_id, deleted_at)
                VALUES (?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, java.util.UUID.fromString("66666666-6666-6666-6666-666666666666"))
                ps.setTimestamp(2, Timestamp(oldTs))
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO product_deletions (product_id, deleted_at)
                VALUES (?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, java.util.UUID.fromString("99999999-9999-9999-9999-999999999999"))
                ps.setTimestamp(2, Timestamp(newTs))
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO products (
                  id, barcode, name, article, price, vat_rate, category_id, stock,
                  egais_flag, chaseznak_flag, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, java.util.UUID.fromString("77777777-7777-7777-7777-777777777777"))
                ps.setString(2, "4601000000001")
                ps.setString(3, "Old")
                ps.setString(4, "OLD-1")
                ps.setLong(5, 100L)
                ps.setString(6, "VAT_22")
                ps.setObject(7, null)
                ps.setDouble(8, 1.0)
                ps.setBoolean(9, false)
                ps.setBoolean(10, false)
                ps.setTimestamp(11, Timestamp(oldTs))
                ps.executeUpdate()
            }
            conn.prepareStatement(
                """
                INSERT INTO products (
                  id, barcode, name, article, price, vat_rate, category_id, stock,
                  egais_flag, chaseznak_flag, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, java.util.UUID.fromString("88888888-8888-8888-8888-888888888888"))
                ps.setString(2, "4601000000002")
                ps.setString(3, "New")
                ps.setString(4, "NEW-1")
                ps.setLong(5, 200L)
                ps.setString(6, "VAT_22")
                ps.setObject(7, null)
                ps.setDouble(8, 2.0)
                ps.setBoolean(9, false)
                ps.setBoolean(10, false)
                ps.setTimestamp(11, Timestamp(newTs))
                ps.executeUpdate()
            }
        }

        val since = base - 1_800_000

        mockMvc.perform(
            get("/api/v1/products")
                .param("since", since.toString())
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.products.length()").value(1))
            .andExpect(jsonPath("$.products[0].name").value("New"))
            .andExpect(jsonPath("$.products[0].id").value("88888888-8888-8888-8888-888888888888"))
            .andExpect(jsonPath("$.deletedIds.length()").value(1))
            .andExpect(jsonPath("$.deletedIds[0]").value("99999999-9999-9999-9999-999999999999"))
    }

    private fun withConnection(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:h2:mem:vitbon;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "").use(block)
    }
}
