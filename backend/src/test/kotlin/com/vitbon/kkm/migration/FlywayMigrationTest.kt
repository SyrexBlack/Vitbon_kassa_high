package com.vitbon.kkm.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

class FlywayMigrationTest {

    @Test
    fun `flyway V7 migration creates document ownership columns`() {
        val dbName = "vitbon_migration_${UUID.randomUUID()}"
        val url = "jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"

        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE documents (
                        id UUID PRIMARY KEY,
                        type VARCHAR(20) NOT NULL,
                        timestamp TIMESTAMP NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        Flyway.configure()
            .dataSource(url, "sa", "")
            .baselineOnMigrate(true)
            .baselineVersion("6")
            .load()
            .migrate()

        DriverManager.getConnection(url, "sa", "").use { connection ->
            connection.prepareStatement(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = 'documents'
                """.trimIndent()
            ).use { statement ->
                val resultSet = statement.executeQuery()
                val columns = mutableSetOf<String>()
                while (resultSet.next()) {
                    columns += resultSet.getString("column_name")
                }

                assertTrue(columns.contains("cashier_id"))
                assertTrue(columns.contains("device_id"))
            }
        }
    }
}