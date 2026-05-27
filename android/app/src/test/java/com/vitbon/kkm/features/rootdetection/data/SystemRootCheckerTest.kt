package com.vitbon.kkm.features.rootdetection.data

import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemRootCheckerTest {

    @Test
    fun `SystemRootChecker implements RootDetector interface`() {
        val checker = SystemRootChecker()
        assertTrue(checker is RootDetector)
        assertNotNull(checker)
    }

    @Test
    fun `dangerous props treats ro secure zero as compromised`() {
        val checker = SystemRootChecker()

        assertTrue(checker.hasDangerousPropertyValues(debuggable = "0", secure = "0"))
    }

    @Test
    fun `dangerous props keeps stock secure device clean`() {
        val checker = SystemRootChecker()

        assertFalse(checker.hasDangerousPropertyValues(debuggable = "0", secure = "1"))
    }

    @Test
    fun `dangerous props treats debuggable one as compromised`() {
        val checker = SystemRootChecker()

        assertTrue(checker.hasDangerousPropertyValues(debuggable = "1", secure = "1"))
    }
}