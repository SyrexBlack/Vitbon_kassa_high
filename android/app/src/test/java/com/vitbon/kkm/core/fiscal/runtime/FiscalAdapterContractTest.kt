package com.vitbon.kkm.core.fiscal.runtime

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class FiscalAdapterContractTest {

    @Test
    fun `mspos adapter contains no synthetic fiscal sign prefixes`() {
        val file = File("src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt")
        val source = file.readText()

        assertFalse(source.contains("MSP_"))
        assertFalse(source.contains("MSPOSKStub"))
    }

    @Test
    fun `neva adapter contains no synthetic fiscal sign prefixes`() {
        val file = File("src/main/java/com/vitbon/kkm/core/fiscal/neva/Neva01FFiscalCore.kt")
        val source = file.readText()

        assertFalse(source.contains("NEVA_"))
        assertFalse(source.contains("Neva01FStub"))
    }
}
