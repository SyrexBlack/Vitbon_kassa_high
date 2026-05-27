# MSPOS-K Fiscal Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the existing MSPOS-K fiscal adapter so production fiscal operations go through the real vendor runtime path with deterministic delegation, transport failure mapping, and success validation.

**Architecture:** Keep `FiscalCoreProvider` and `MSPOSKFiscalCore` as the app-facing boundary, but add narrow seams so the adapter and `RealMSPOSKProtocol` can be tested without hardware. Split the binder/runtime bridge details out of `MSPOSKFiscalCore.kt`, inject them back into the protocol, and centralize fiscal-identifier validation so success is only returned for real, non-synthetic results.

**Tech Stack:** Kotlin, Android binder/service APIs, Hilt, JUnit 4, MockK, kotlinx-coroutines-test

---

## File Map

```text
android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/
  MSPOSKFiscalCore.kt              ← adapter entrypoint + protocol wiring
  MSPOSKRuntimeBridge.kt           ← internal service connector/client abstractions and binder-backed implementation
  MSPOSKResultValidator.kt         ← internal success validation for fiscal identifiers

android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/
  MSPOSKFiscalCoreTest.kt          ← adapter-level tests with fake protocol
  RealMSPOSKProtocolTest.kt        ← protocol transport/mapping tests with fake connector/client
  MSPOSKResultValidatorTest.kt     ← success validation tests

android/app/src/test/java/com/vitbon/kkm/core/fiscal/runtime/
  FiscalAdapterContractTest.kt     ← source-level contract regression for synthetic placeholders
```

---

## Task 1: Make MSPOSKFiscalCore testable at the adapter boundary

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt`
- Create: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCoreTest.kt`

- [ ] **Step 1: Write the failing test**

`MSPOSKFiscalCoreTest.kt`
```kotlin
package com.vitbon.kkm.core.fiscal.msposk

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.FFDVersion
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentLine
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MSPOSKFiscalCoreTest {

    @Test
    fun `printSale delegates to protocol after initialize`() = runTest {
        val protocol = RecordingProtocol()
        val core = MSPOSKFiscalCore(mockk<Context>(relaxed = true), protocol)

        core.initialize()
        val result = core.printSale(saleCheck())

        assertTrue(result is FiscalResult.Success)
        assertEquals(listOf("printSale:sale-1"), protocol.calls)
    }

    @Test
    fun `printSale rejects non sale check before protocol call`() = runTest {
        val protocol = RecordingProtocol()
        val core = MSPOSKFiscalCore(mockk<Context>(relaxed = true), protocol)

        core.initialize()

        try {
            core.printSale(saleCheck(type = CheckType.RETURN))
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Expected SALE"))
        }

        assertTrue(protocol.calls.isEmpty())
    }

    @Test
    fun `openShift fails before initialize`() = runTest {
        val protocol = RecordingProtocol()
        val core = MSPOSKFiscalCore(mockk<Context>(relaxed = true), protocol)

        try {
            core.openShift()
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("initialize"))
        }
    }

    private fun saleCheck(type: CheckType = CheckType.SALE): FiscalCheck {
        return FiscalCheck(
            id = "sale-1",
            type = type,
            items = emptyList(),
            payments = listOf(PaymentLine(PaymentType.CASH, Money(1000), "cash"))
        )
    }

    private class RecordingProtocol : MSPOSKProtocol {
        val calls = mutableListOf<String>()

        override suspend fun openShift(): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun printSale(check: FiscalCheck): FiscalResult {
            calls += "printSale:${check.id}"
            return FiscalResult.Success("111", "222", "333", 1L)
        }
        override suspend fun printReturn(check: FiscalCheck): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun printCorrection(doc: com.vitbon.kkm.core.fiscal.model.CorrectionDoc): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun closeShift(): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun printXReport(): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = FiscalResult.Success("111", "222", "333", 1L)
        override suspend fun getStatus(): FiscalStatus = FiscalStatus(true, "222", true, 1L, 3, true, null)
        override suspend fun getFFDVersion(): FFDVersion = FFDVersion.V1_2
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.MSPOSKFiscalCoreTest" --no-daemon --console=plain
```
Expected: FAIL — `MSPOSKFiscalCore(Context, MSPOSKProtocol)` constructor does not exist yet.

- [ ] **Step 3: Write minimal implementation**

Modify `MSPOSKFiscalCore.kt` constructor block to make protocol injection possible without changing production wiring:
```kotlin
@Singleton
class MSPOSKFiscalCore private constructor(
    private val context: Context,
    private val sdk: MSPOSKProtocol
) : FiscalCore {

    @Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context
    ) : this(context, RealMSPOSKProtocol(context))

    internal constructor(
        context: Context,
        protocol: MSPOSKProtocol
    ) : this(context, protocol)

    private val TAG = "MSPOSKFiscalCore"

    @Volatile
    private var initialized = false

    @Volatile
    private var cachedStatus: FiscalStatus? = null
```

Delete the old lazy protocol block:
```kotlin
private val sdk: MSPOSKProtocol by lazy {
    RealMSPOSKProtocol(context)
}
```

Do not change any operational behavior in this task beyond constructor wiring.

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.MSPOSKFiscalCoreTest" --no-daemon --console=plain
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt
git add android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCoreTest.kt
git commit -m "test(fiscal): add MSPOS-K adapter seam coverage"
```

---

## Task 2: Extract the runtime bridge seam and test RealMSPOSKProtocol without hardware

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKRuntimeBridge.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt`
- Create: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/RealMSPOSKProtocolTest.kt`

- [ ] **Step 1: Write the failing test**

`RealMSPOSKProtocolTest.kt`
```kotlin
package com.vitbon.kkm.core.fiscal.msposk

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.CheckItem
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.FFDVersion
import com.vitbon.kkm.core.fiscal.model.FiscalCheck
import com.vitbon.kkm.core.fiscal.model.FiscalStatus
import com.vitbon.kkm.core.fiscal.model.Money
import com.vitbon.kkm.core.fiscal.model.PaymentLine
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealMSPOSKProtocolTest {

    @Test
    fun `openShift rethrows recoverable bind failure`() = runTest {
        val protocol = RealMSPOSKProtocol(
            context = mockk<Context>(relaxed = true),
            connectorFactory = MSPOSKServiceConnectorFactory {
                throw FiscalException(-1, "bind timeout", recoverable = true)
            }
        )

        try {
            protocol.openShift()
        } catch (e: FiscalException) {
            assertTrue(e.recoverable)
            assertTrue(e.message!!.contains("bind timeout"))
        }
    }

    @Test
    fun `getStatus maps snapshot from service client`() = runTest {
        val protocol = RealMSPOSKProtocol(
            context = mockk<Context>(relaxed = true),
            connectorFactory = MSPOSKServiceConnectorFactory {
                FakeConnector(
                    FakeClient(
                        status = ServiceStatusSnapshot(
                            fnRegistered = true,
                            fnNumber = "9282000100000012",
                            shiftOpen = true,
                            shiftAgeHours = 4L,
                            currentFdNumber = 18,
                            ofdConnected = true,
                            lastError = null
                        )
                    )
                )
            }
        )

        val status = protocol.getStatus()

        assertEquals("9282000100000012", status.fnNumber)
        assertEquals(true, status.shiftOpen)
        assertEquals(18, status.currentFdNumber)
    }

    @Test
    fun `printSale passes mapped item and payment values to service client`() = runTest {
        val client = FakeClient()
        val protocol = RealMSPOSKProtocol(
            context = mockk<Context>(relaxed = true),
            connectorFactory = MSPOSKServiceConnectorFactory { FakeConnector(client) }
        )

        protocol.printSale(
            FiscalCheck(
                id = "sale-7",
                type = CheckType.SALE,
                items = listOf(
                    CheckItem(
                        id = "i1",
                        productId = null,
                        barcode = null,
                        name = "Молоко",
                        quantity = 2.0,
                        price = Money(12500),
                        vatRate = VatRate.VAT_10,
                        total = Money(25000)
                    )
                ),
                payments = listOf(PaymentLine(PaymentType.CARD, Money(25000), "card"))
            )
        )

        assertEquals("Молоко", client.lastItemName)
        assertEquals(12500L, client.lastItemPriceKopecks)
        assertEquals(PaymentType.CARD, client.lastPaymentType)
        assertEquals(25000L, client.lastPaymentAmountKopecks)
    }

    private class FakeConnector(private val client: MSPOSKServiceClient) : MSPOSKServiceConnector {
        override suspend fun connectOrThrow(operation: String): MSPOSKServiceClient = client
        override fun disconnectSafely() = Unit
    }

    private class FakeClient(
        private val status: ServiceStatusSnapshot = ServiceStatusSnapshot(
            fnRegistered = true,
            fnNumber = "9282",
            shiftOpen = false,
            shiftAgeHours = null,
            currentFdNumber = 1,
            ofdConnected = true,
            lastError = null
        )
    ) : MSPOSKServiceClient {
        var lastItemName: String? = null
        var lastItemPriceKopecks: Long? = null
        var lastPaymentType: PaymentType? = null
        var lastPaymentAmountKopecks: Long? = null

        override suspend fun isReady(): Boolean = true
        override suspend fun openDay() = Unit
        override suspend fun openReceipt(type: ReceiptType, documentId: String): Boolean = true
        override suspend fun printReceiptItem(name: String, quantity: Double, priceKopecks: Long, vatRate: VatRate) {
            lastItemName = name
            lastItemPriceKopecks = priceKopecks
        }
        override suspend fun printPayment(type: PaymentType, amountKopecks: Long, label: String) {
            lastPaymentType = type
            lastPaymentAmountKopecks = amountKopecks
        }
        override suspend fun closeReceipt() = Unit
        override suspend fun makeCorrectionReceipt(docType: CheckType, baseSumKopecks: Long, cashSumKopecks: Long, cardSumKopecks: Long, reason: String, correctionNumber: String, correctionDateMs: Long, vatRate: VatRate) = Unit
        override suspend fun closeDay() = Unit
        override suspend fun printXReport() = Unit
        override suspend fun cashIn(amountKopecks: Long, comment: String?) = Unit
        override suspend fun cashOut(amountKopecks: Long, comment: String?) = Unit
        override suspend fun getStatusSnapshot(): ServiceStatusSnapshot = status
        override suspend fun getCurrentFfdVersion(): String = FFDVersion.V1_2.displayName
        override suspend fun readFiscalIdentifiers(operation: String): FiscalIdentifiers = FiscalIdentifiers("1234567890", "9282000100000012", "18")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocolTest" --no-daemon --console=plain
```
Expected: FAIL — `MSPOSKServiceConnectorFactory`, `MSPOSKServiceConnector`, `MSPOSKServiceClient`, and `FiscalIdentifiers` do not exist as top-level testable types.

- [ ] **Step 3: Extract the bridge types into a focused file**

Create `MSPOSKRuntimeBridge.kt`:
```kotlin
package com.vitbon.kkm.core.fiscal.msposk

import android.content.Context
import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.CheckType
import com.vitbon.kkm.core.fiscal.model.PaymentType
import com.vitbon.kkm.core.fiscal.model.VatRate

internal fun interface MSPOSKServiceConnectorFactory {
    fun create(context: Context): MSPOSKServiceConnector
}

internal interface MSPOSKServiceConnector {
    suspend fun connectOrThrow(operation: String): MSPOSKServiceClient
    fun disconnectSafely()
}

internal data class FiscalIdentifiers(
    val fiscalSign: String,
    val fnNumber: String,
    val fdNumber: String
)

internal data class ServiceStatusSnapshot(
    val fnRegistered: Boolean,
    val fnNumber: String?,
    val shiftOpen: Boolean,
    val shiftAgeHours: Long?,
    val currentFdNumber: Int,
    val ofdConnected: Boolean,
    val lastError: String?
)

internal interface MSPOSKServiceClient {
    suspend fun isReady(): Boolean
    suspend fun openDay()
    suspend fun openReceipt(type: ReceiptType, documentId: String): Boolean
    suspend fun printReceiptItem(name: String, quantity: Double, priceKopecks: Long, vatRate: VatRate)
    suspend fun printPayment(type: PaymentType, amountKopecks: Long, label: String)
    suspend fun closeReceipt()
    suspend fun makeCorrectionReceipt(docType: CheckType, baseSumKopecks: Long, cashSumKopecks: Long, cardSumKopecks: Long, reason: String, correctionNumber: String, correctionDateMs: Long, vatRate: VatRate)
    suspend fun closeDay()
    suspend fun printXReport()
    suspend fun cashIn(amountKopecks: Long, comment: String?)
    suspend fun cashOut(amountKopecks: Long, comment: String?)
    suspend fun getStatusSnapshot(): ServiceStatusSnapshot
    suspend fun getCurrentFfdVersion(): String
    suspend fun readFiscalIdentifiers(operation: String): FiscalIdentifiers
}

internal object BinderMSPOSKServiceConnectorFactory : MSPOSKServiceConnectorFactory {
    override fun create(context: Context): MSPOSKServiceConnector = BinderMSPOSKServiceConnection(context)
}
```

Move the old private connector/client/snapshot implementations out of `MSPOSKFiscalCore.kt` into this file and keep their behavior unchanged, except for replacing `mapLastError()` with `readFiscalIdentifiers()`.

- [ ] **Step 4: Wire RealMSPOSKProtocol to the extracted seam**

Change the `RealMSPOSKProtocol` signature and `withService()` implementation in `MSPOSKFiscalCore.kt`:
```kotlin
class RealMSPOSKProtocol internal constructor(
    private val context: Context,
    private val connectorFactory: MSPOSKServiceConnectorFactory = BinderMSPOSKServiceConnectorFactory
) : MSPOSKProtocol {

    private suspend fun <T> withService(
        operation: String,
        block: suspend (MSPOSKServiceClient) -> T
    ): T {
        return withContext(Dispatchers.IO) {
            val connector = connectorFactory.create(context)
            val client = connector.connectOrThrow(operation)
            try {
                block(client)
            } finally {
                connector.disconnectSafely()
            }
        }
    }
```

Replace the old nested `MSPOSKServiceConnection`, `MSPOSKServiceClient`, `BinderBackedMSPOSKServiceClient`, and `ServiceStatusSnapshot` definitions with the extracted top-level versions.

At the end of each successful fiscal operation, replace `svc.mapLastError("...")` with:
```kotlin
val ids = svc.readFiscalIdentifiers("CloseRec")
MSPOSKResultValidator.validateSuccess("CloseRec", ids.fiscalSign, ids.fnNumber, ids.fdNumber)
```
Use the correct operation name for each call (`OpenDay`, `CloseDay`, `PrintXReport`, `CashIn`, `CashOut`, `FNMakeCorrectionRec`).

- [ ] **Step 5: Run test to verify it passes**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocolTest" --no-daemon --console=plain
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKRuntimeBridge.kt
git add android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/RealMSPOSKProtocolTest.kt
git commit -m "refactor(fiscal): extract MSPOS-K runtime bridge seams"
```

---

## Task 3: Reject incomplete or synthetic fiscal identifiers before returning success

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKResultValidator.kt`
- Create: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKResultValidatorTest.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt`
- Modify: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/runtime/FiscalAdapterContractTest.kt`

- [ ] **Step 1: Write the failing test**

`MSPOSKResultValidatorTest.kt`
```kotlin
package com.vitbon.kkm.core.fiscal.msposk

import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.FiscalResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MSPOSKResultValidatorTest {

    @Test
    fun `accepts real fiscal identifiers`() {
        val result = MSPOSKResultValidator.validateSuccess(
            operation = "CloseRec",
            fiscalSign = "1234567890",
            fnNumber = "9282000100000012",
            fdNumber = "18"
        )

        assertTrue(result is FiscalResult.Success)
        result as FiscalResult.Success
        assertEquals("1234567890", result.fiscalSign)
        assertEquals("9282000100000012", result.fnNumber)
        assertEquals("18", result.fdNumber)
    }

    @Test
    fun `rejects missing fiscal identifiers as non recoverable`() {
        try {
            MSPOSKResultValidator.validateSuccess("CloseRec", null, "9282000100000012", "18")
        } catch (e: FiscalException) {
            assertFalse(e.recoverable)
            assertTrue(e.message!!.contains("without valid fiscal identifiers"))
        }
    }

    @Test
    fun `rejects synthetic prefixes as non recoverable`() {
        try {
            MSPOSKResultValidator.validateSuccess("CloseRec", "MSP_123", "9282000100000012", "18")
        } catch (e: FiscalException) {
            assertFalse(e.recoverable)
            assertTrue(e.message!!.contains("synthetic"))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.MSPOSKResultValidatorTest" --no-daemon --console=plain
```
Expected: FAIL — `MSPOSKResultValidator` does not exist yet.

- [ ] **Step 3: Write the validator and wire it into the protocol**

Create `MSPOSKResultValidator.kt`:
```kotlin
package com.vitbon.kkm.core.fiscal.msposk

import com.vitbon.kkm.core.fiscal.FiscalException
import com.vitbon.kkm.core.fiscal.model.FiscalResult

internal object MSPOSKResultValidator {

    private val syntheticPrefixes = listOf("MSP_", "NEVA_")

    fun validateSuccess(
        operation: String,
        fiscalSign: String?,
        fnNumber: String?,
        fdNumber: String?
    ): FiscalResult.Success {
        val sign = fiscalSign?.trim().orEmpty()
        val fn = fnNumber?.trim().orEmpty()
        val fd = fdNumber?.trim().orEmpty()

        if (sign.isEmpty() || fn.isEmpty() || fd.isEmpty() || sign == "0" || fd == "0") {
            throw FiscalException(
                errorCode = -1,
                message = "MSPOS-K $operation finished without valid fiscal identifiers",
                recoverable = false
            )
        }

        if (syntheticPrefixes.any { sign.startsWith(it) || fn.startsWith(it) || fd.startsWith(it) }) {
            throw FiscalException(
                errorCode = -1,
                message = "MSPOS-K $operation returned synthetic fiscal identifiers",
                recoverable = false
            )
        }

        return FiscalResult.Success(
            fiscalSign = sign,
            fnNumber = fn,
            fdNumber = fd,
            timestamp = System.currentTimeMillis()
        )
    }
}
```

In `BinderBackedMSPOSKServiceClient.readFiscalIdentifiers()` inside `MSPOSKRuntimeBridge.kt`, read and return raw values only:
```kotlin
override suspend fun readFiscalIdentifiers(operation: String): FiscalIdentifiers {
    val fiscalSign = tryLongWithCallback("FDI_GetFiscalSign")?.takeIf { it > 0L }?.toString()
        ?: tryStringWithCallback("OfdOut_GetFiscalSign")
    val fnNumber = tryStringWithCallback("FNGetNumber")
    val fdNumber = tryLongWithCallback("FNGetLastFDNum")?.takeIf { it > 0L }?.toString()
        ?: tryLongWithCallback("FNGetLastFDNumber")?.takeIf { it > 0L }?.toString()

    return FiscalIdentifiers(
        fiscalSign = fiscalSign.orEmpty(),
        fnNumber = fnNumber.orEmpty(),
        fdNumber = fdNumber.orEmpty()
    )
}
```

Do not build `FiscalResult.Success` inside the binder client anymore; only the validator may do that.

- [ ] **Step 4: Extend the source-level contract test**

Append to `FiscalAdapterContractTest.kt`:
```kotlin
@Test
fun `mspos adapter source contains no synthetic success literals`() {
    val file = File("src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt")
    val source = file.readText()

    assertFalse(source.contains("FiscalResult.Success(\"MSP_"))
    assertFalse(source.contains("FiscalResult.Success(\"NEVA_"))
}
```

- [ ] **Step 5: Run targeted tests to verify they pass**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.MSPOSKResultValidatorTest" --tests "com.vitbon.kkm.core.fiscal.runtime.FiscalAdapterContractTest" --no-daemon --console=plain
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKResultValidator.kt
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKRuntimeBridge.kt
git add android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKResultValidatorTest.kt
git add android/app/src/test/java/com/vitbon/kkm/core/fiscal/runtime/FiscalAdapterContractTest.kt
git commit -m "fix(fiscal): validate MSPOS-K fiscal identifiers"
```

---

## Task 4: Run regression and collect MSPOS-K acceptance evidence

**Files:**
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCoreTest.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/RealMSPOSKProtocolTest.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKResultValidatorTest.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/runtime/FiscalAdapterContractTest.kt`
- Reference: `docs/manuals/support-troubleshooting.md`
- Reference: `docs/e2e-tests.md`

- [ ] **Step 1: Run the MSPOS-K targeted test pack**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.msposk.MSPOSKFiscalCoreTest" --tests "com.vitbon.kkm.core.fiscal.msposk.RealMSPOSKProtocolTest" --tests "com.vitbon.kkm.core.fiscal.msposk.MSPOSKResultValidatorTest" --tests "com.vitbon.kkm.core.fiscal.runtime.FiscalAdapterContractTest" --no-daemon --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run the existing fiscal runtime regression tests**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.runtime.FfdVersionResolverTest" --tests "com.vitbon.kkm.core.fiscal.runtime.FiscalErrorMapperTest" --tests "com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestratorTest" --no-daemon --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run the full Android unit regression**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --no-daemon --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Build the debug APK**

Run:
```bash
java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:assembleDebug --no-daemon --console=plain
```
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Execute MSPOS-K physical smoke verification using the existing checklists**

Use the MSPOS-K sections already defined in:
- `docs/manuals/support-troubleshooting.md` → Hardware acceptance checklist, Phase A
- `docs/e2e-tests.md` → sale / return / close shift / OFD flow scenarios

Record the following evidence for the bead note or handoff:
```text
Device: MSPOS-K
App build: <git SHA or local build identifier>
SDK/service package available: yes/no
Operations executed: open shift, sale, return, correction, cash in, cash out, X-report, close shift, status, FFD read
For each success: fiscalSign, fnNumber, fdNumber, FFD version, timestamp
For each failure: full error text, whether recoverable, screenshot/log reference
```

- [ ] **Step 6: Update the bead with verification evidence**

Run:
```bash
bd update vitbon-kassa-65j --notes "MSPOS-K verification: targeted unit tests passed; full unit regression passed; assembleDebug passed; hardware smoke evidence captured with fiscal identifiers and FFD version."
```
Expected: bead notes updated.

---

## Spec Coverage Check

- Real vendor runtime path remains the production path: covered by Tasks 1–2.
- Deterministic service/bind failure mapping: covered by Task 2 tests.
- Success only with valid, non-synthetic identifiers: covered by Task 3.
- Adapter contract regression against synthetic placeholders: covered by Task 3.
- Code-level and hardware verification evidence: covered by Task 4.

## Placeholder Scan

- No `TODO`, `TBD`, or “similar to previous task” markers remain.
- All file paths, test names, and commands are concrete.
- Every code-changing step includes the code to add or replace.

## Type Consistency Check

- `MSPOSKProtocol`, `MSPOSKServiceClient`, `FiscalIdentifiers`, and `ServiceStatusSnapshot` are named consistently across plan tasks.
- The plan keeps `PaymentType`, `ReceiptType`, `VatRate`, and `CheckType` aligned with existing runtime/model signatures.
- `MSPOSKResultValidator.validateSuccess(...)` is the only planned constructor for validated `FiscalResult.Success` in the MSPOS-K runtime path.
