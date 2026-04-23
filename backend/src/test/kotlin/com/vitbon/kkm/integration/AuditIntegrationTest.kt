package com.vitbon.kkm.integration

import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AuditIntegrationTest {

    @Autowired
    lateinit var entityManager: EntityManager

    @Test
    fun `schema includes auth_sessions and audit_events tables`() {
        assertDoesNotThrow {
            entityManager.createNativeQuery("SELECT COUNT(*) FROM auth_sessions").singleResult
        }
        assertDoesNotThrow {
            entityManager.createNativeQuery("SELECT COUNT(*) FROM audit_events").singleResult
        }
    }
}
