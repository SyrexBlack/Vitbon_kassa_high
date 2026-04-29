package com.vitbon.kkm.features.rootdetection.data

import android.content.pm.PackageManager
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
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
}