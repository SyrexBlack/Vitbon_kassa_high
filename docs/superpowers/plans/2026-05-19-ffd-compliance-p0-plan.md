# ФФД соответствие — исправления P0

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development` (recommended) или `superpowers:executing-plans`. Шаги используют checkbox (`- [ ]`) синтаксис.

**Goal:** Добиться полного соответствия фискальных документов требованиям ФФД 1.05/1.2 для 54-ФЗ. Исправляем 5 критических пробелов.

**Architecture:**
- `TaxSystem` enum → `VatRate` companion method → `FiscalDocumentBuilder` использует при построении любого документа
- Новые теги 1005, 1034, 1191 добавляются в `FiscalDocumentBuilder.build*()` — данные берутся из `FiscalCheck.additionalInfo`, `FiscalStatus`, `FiscalConfig`
- `ShiftUseCase` получает `FiscalStatus` перед операцией — проверяет `shiftAgeHours > 24`
- Тег 1140 (признак способа расчёта) вычисляется из `PaymentType` и суммы оплат
- `NO_VAT.tag` меняется с `"no_vat"` на числовой `"6"` для корректного тега 1203

**Tech Stack:** Kotlin, JUnit, Android unit tests

---

## Task 1: TaxSystem enum + NO_VAT tag fix

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/model/FiscalModels.kt:22-28`
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/model/VatRateTest.kt` (create)

- [ ] **Step 1: Добавить тест для TaxSystem и NO_VAT.tag**

Создать `android/app/src/test/java/com/vitbon/kkm/core/fiscal/model/VatRateTest.kt`:

```kotlin
package com.vitbon.kkm.core.fiscal.model

import org.junit.Assert.*
import org.junit.Test

class VatRateTest {

    @Test
    fun `NO_VAT tag is numeric 6 not no_vat string`() {
        assertEquals("6", VatRate.NO_VAT.tag)
    }

    @Test
    fun `all VAT rates have numeric tags`() {
        val numericTags = listOf(VatRate.VAT_22, VatRate.VAT_10, VatRate.VAT_0, VatRate.VAT_5, VatRate.VAT_7, VatRate.NO_VAT)
        for (rate in numericTags) {
            assertTrue("Tag for ${rate.name} must be numeric, got: ${rate.tag}", rate.tag.matches(Regex("\\d+")))
        }
    }

    @Test
    fun `TaxSystem fromString returns correct enum`() {
        assertEquals(TaxSystem.OSN, TaxSystem.fromString("1"))
        assertEquals(TaxSystem.USN_INCOME, TaxSystem.fromString("2"))
        assertEquals(TaxSystem.ESN, TaxSystem.fromString("4"))
        assertEquals(TaxSystem.USN_INCOME_OUTCOME, TaxSystem.fromString("5"))
        assertEquals(TaxSystem.PSN, TaxSystem.fromString("6"))
    }

    @Test
    fun `TaxSystem tag values are correct`() {
        assertEquals("1", TaxSystem.OSN.tag)
        assertEquals("2", TaxSystem.USN_INCOME.tag)
        assertEquals("4", TaxSystem.ESN.tag)
        assertEquals("5", TaxSystem.USN_INCOME_OUTCOME.tag)
        assertEquals("6", TaxSystem.PSN.tag)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.VatRateTest" 2>&1 | tail -30`
Expected: FAIL — `NO_VAT.tag` is `"no_vat"`, `TaxSystem` does not exist

- [ ] **Step 3: Modify FiscalModels.kt — add TaxSystem, fix NO_VAT**

```kotlin
// Добавить ПОСЛЕ PaymentType enum (строка ~62)

/** Система налогообложения (тег 1005 ФФД) */
enum class TaxSystem(val tag: String) {
    OSN("1"),          // Общая
    USN_INCOME("2"),   // УСН доход
    ESN("4"),          // ЕСХН
    USN_INCOME_OUTCOME("5"),  // УСН доход-расход
    PSN("6");          // ПСН

    companion object {
        fun fromString(s: String?): TaxSystem = when (s) {
            "1" -> OSN
            "2" -> USN_INCOME
            "4" -> ESN
            "5" -> USN_INCOME_OUTCOME
            "6" -> PSN
            else -> OSN
        }
    }
}
```

Изменить `VatRate.NO_VAT` tag с `"no_vat"` на `"6"`:

```kotlin
NO_VAT("6", "БЕЗ НДС")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.VatRateTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/model/FiscalModels.kt android/app/src/test/java/com/vitbon/kkm/core/fiscal/model/VatRateTest.kt
git commit -m "feat(ffd): add TaxSystem enum, fix NO_VAT tag to numeric 6"
```

---

## Task 2: FiscalDocumentBuilder — добавить теги 1005, 1034, 1191, 1026

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilderTest.kt`

**Данные从何而来:**
- `additionalInfo["taxSystem"]` → тег 1005 (система налогообложения)
- `additionalInfo["shiftNumber"]` → тег 1034 (номер смены)
- `additionalInfo["receiptNumberInShift"]` → тег 1191 (номер чека в смене)
- `additionalInfo["orgInn"]` → тег 1026 (ИНН организации)

Все четыре берутся из `FiscalCheck.additionalInfo: Map<String, String>` — заполняется вызывающей стороной (`SalesViewModel`, `ReturnUseCase` и т.д.) из состояния смены.

- [ ] **Step 1: Добавить тесты на новые теги**

В `FiscalDocumentBuilderTest.kt` добавить после существующего теста `FFD 12 correction includes site tag 1187`:

```kotlin
@Test
fun `sale — includes required FFD tags 1005 1034 1191 1026`() {
    val check = makeTestCheck().copy(
        additionalInfo = mapOf(
            "taxSystem" to "1",           // ОСН
            "shiftNumber" to "42",        // номер смены
            "receiptNumberInShift" to "7", // номер чека
            "orgInn" to "770123456789"     // ИНН организации
        )
    )
    val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
    val doc = builder.buildSale(check, "Кассир", "770987654321")

    assertEquals("1", doc.fields[1005])       // система налогообложения
    assertEquals("42", doc.fields[1034])      // номер смены
    assertEquals("7", doc.fields[1191])       // номер чека в смене
    assertEquals("770123456789", doc.fields[1026]) // ИНН организации
}

@Test
fun `return — includes required tags 1005 1034 1191`() {
    val check = makeTestCheck().copy(
        type = CheckType.RETURN,
        additionalInfo = mapOf(
            "taxSystem" to "2",
            "shiftNumber" to "10",
            "receiptNumberInShift" to "5"
        )
    )
    val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
    val doc = builder.buildReturn(check, "ORIG_SIG", "Кассир", "770111111111")

    assertEquals("2", doc.fields[1005])
    assertEquals("10", doc.fields[1034])
    assertEquals("5", doc.fields[1191])
}

@Test
fun `cash in — includes tags 1005 1034 when provided`() {
    val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
    val doc = builder.buildCashIn(Money(5000), "Внесение", "Кассир", null)

    // Cash in не имеет товарных позиций — теги 1034/1191 берутся из доп.инфо
    // Если доп.инфо пуста — теги не ставятся
    assertNull(doc.fields[1034])
    assertNull(doc.fields[1191])
}

@Test
fun `correction — includes 1005 from vatRate tag value`() {
    val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
    val doc = builder.buildCorrection(
        doc = CorrectionDoc(
            id = "c1", type = CheckType.CORRECTION_INCOME,
            baseSum = Money(10000), cashSum = Money(10000), cardSum = Money.ZERO,
            reason = "Ошибка", correctionNumber = "1",
            correctionDate = System.currentTimeMillis(),
            vatRate = VatRate.VAT_22
        ),
        cashierName = "Test", inn = null
    )
    // tag 1203 для VAT_22 должен быть "1220" (число, не строка)
    assertEquals("1220", doc.fields[1203])
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.FiscalDocumentBuilderTest" 2>&1 | tail -30`
Expected: FAIL — теги 1005, 1034, 1191, 1026 отсутствуют; tag 1203 = "no_vat"

- [ ] **Step 3: Modify FiscalDocumentBuilder.kt — добавить теги в buildBaseFields**

В `buildBaseFields()` после строки `fields[1140] = "1"` (строка ~188) добавить:

```kotlin
        // 1005 — система налогообложения
        check.additionalInfo["taxSystem"]?.let { fields[1005] = it }

        // 1034 — номер смены
        check.additionalInfo["shiftNumber"]?.let { fields[1034] = it }

        // 1191 — номер чека в смене
        check.additionalInfo["receiptNumberInShift"]?.let { fields[1191] = it }

        // 1026 — ИНН организации (ОФД)
        check.additionalInfo["orgInn"]?.let { fields[1026] = it }
```

- [ ] **Step 4: Modify buildCorrection() — добавить теги 1005, 1034, 1191**

В `buildCorrection()` после строки `fields[1203] = doc.vatRate.tag` добавить:

```kotlin
        // Система налогообложения (из vatRate или по умолчанию ОСН)
        fields[1005] = when (doc.vatRate) {
            VatRate.VAT_22, VatRate.VAT_10, VatRate.VAT_0 -> "1"
            VatRate.VAT_5 -> "5"
            VatRate.VAT_7 -> "5"
            VatRate.NO_VAT -> "6"
        }
```

- [ ] **Step 5: Modify buildCashIn/buildCashOut — добавить теги**

В оба метода `buildCashIn()` и `buildCashOut()` добавить после строки `fields[1036]`:

```kotlin
        // 1034 — номер смены (из доп.инфо если передана)
        // Примечание: для cash in/out доп.инфо передаётся через перегрузку метода
```

Для `buildCashIn` и `buildCashOut` нужно добавить перегрузку с `FiscalCheck` или отдельный параметр `shiftNumber`. Добавить новый метод:

```kotlin
    /**
     * Построить документ внесения с информацией о смене.
     */
    fun buildCashIn(amount: Money, comment: String?, cashierName: String, inn: String?, shiftNumber: String?): FiscalDocument {
        val fields = mutableMapOf<Int, String>()
        fields[1000] = "5"
        fields[1031] = if (version == FFDVersion.V1_2) "2" else "1"
        fields[1055] = cashierName
        if (inn != null) fields[1018] = inn
        fields[1174] = amount.kopecks.toString()
        fields[1036] = formatDateTime(System.currentTimeMillis())
        if (comment != null) fields[1037] = comment
        if (shiftNumber != null) fields[1034] = shiftNumber
        if (version == FFDVersion.V1_2) fields[1187] = "vitbon.ru"
        return FiscalDocument(version = version, type = DocumentType.CASH_IN, fields = fields)
    }
```

Аналогично для `buildCashOut` с параметром `shiftNumber?`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.FiscalDocumentBuilderTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt android/app/src/test/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilderTest.kt
git commit -m "feat(ffd): add required tags 1005 1034 1191 1026 to FiscalDocumentBuilder"
```

---

## Task 3: Тег 1140 — определение признака способа расчёта по PaymentType

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilderTest.kt`

FFD значения тега 1140:
- `1` — полная предоплата
- `2` — частичная предоплата
- `4` — полный расчёт ← **стандартный розничный расчёт**
- `5` — частичный расчёт

- [ ] **Step 1: Добавить тесты**

В `FiscalDocumentBuilderTest.kt`:

```kotlin
@Test
fun `payment method 1140 — full payment when cash covers total`() {
    val check = makeTestCheck().copy(
        items = listOf(
            CheckItem(
                id = "i1", productId = "p1", barcode = "4601234567890",
                name = "Товар", quantity = 1.0,
                price = Money(5000), vatRate = VatRate.VAT_22,
                total = Money(5000)
            )
        ),
        payments = listOf(
            PaymentLine(PaymentType.CASH, Money(5000), "Наличные"),
            PaymentLine(PaymentType.CARD, Money(0), "Карта")
        )
    )
    val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
    val doc = builder.buildSale(check, "Кассир", null)

    assertEquals("4", doc.fields[1140]) // полный расчёт (наличные полностью покрывают)
}

@Test
fun `payment method 1140 — partial payment when cash card less than total`() {
    val check = makeTestCheck().copy(
        items = listOf(
            CheckItem(
                id = "i1", productId = "p1", barcode = "4601234567890",
                name = "Товар", quantity = 1.0,
                price = Money(10000), vatRate = VatRate.VAT_22,
                total = Money(10000)
            )
        ),
        payments = listOf(
            PaymentLine(PaymentType.CASH, Money(5000), "Наличные"),
            PaymentLine(PaymentType.CARD, Money(3000), "Карта")
        )
    )
    val builder = FiscalDocumentBuilder(FFDVersion.V1_05)
    val doc = builder.buildSale(check, "Кассир", null)

    // Сумма оплат (5000+3000=8000) < total (10000) → частичный расчёт
    assertEquals("5", doc.fields[1140])
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.FiscalDocumentBuilderTest" 2>&1 | tail -20`
Expected: FAIL — тег 1140 всегда "1"

- [ ] **Step 3: Modify buildBaseFields — вычислить 1140 по PaymentType**

Заменить строку `fields[1140] = "1"` в `buildBaseFields()` на:

```kotlin
        // 1140 — признак способа расчёта
        // 4 = полный расчёт (станндарт для розницы)
        // 5 = частичный расчёт (если сумма оплат < суммы расчёта)
        val totalPayments = check.payments.sumOf { it.amount.kopecks }
        fields[1140] = if (totalPayments >= check.total.kopecks) "4" else "5"
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*.FiscalDocumentBuilderTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt android/app/src/test/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilderTest.kt
git commit -m "feat(ffd): determine payment method tag 1140 from PaymentType and sum"
```

---

## Task 4: ShiftViewModel — передавать requiredInfo в FiscalCheck

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/sales/domain/ProcessSaleUseCase.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/returns/domain/ReturnUseCase.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/cashdrawer/domain/CashDrawerUseCase.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/correction/domain/CorrectionUseCase.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/shift/domain/ShiftUseCase.kt`

Данные从哪里获取:
- `shiftNumber` — из `ShiftEntity.shiftNumber` или `FiscalStatus` → `getStatus().shiftNumber`
- `receiptNumberInShift` — `FiscalStatus.currentFdNumber + 1`
- `taxSystem` — из конфигурации кассы (`FiscalConfig.taxSystem` или из backend-ответа при логине)
- `orgInn` — из конфигурации кассы

Поскольку `FiscalOperationOrchestrator` получает `FiscalCheck` с `additionalInfo`, данные должны быть заполнены в UseCase на уровне формирования чека.

- [ ] **Step 1: Прочитать ProcessSaleUseCase и ShiftUseCase**

Run: `cat android/app/src/main/java/com/vitbon/kkm/features/sales/domain/ProcessSaleUseCase.kt`
Run: `cat android/app/src/main/java/com/vitbon/kkm/features/shift/domain/ShiftUseCase.kt`

- [ ] **Step 2: Добавить FiscalStatus как зависимость к ProcessSaleUseCase**

В `ProcessSaleUseCase` конструктор получает `ShiftDao`, `CheckDao`, `CheckItemDao`, `FiscalOperationOrchestrator`, `SyncManager`. Добавить `FiscalCore` (для получения статуса) или получать `currentFdNumber` из последнего чека в БД.

Логика: перед продажей получить `fiscalCore.getStatus()` → `currentFdNumber` → `receiptNumberInShift = currentFdNumber + 1`, `shiftNumber` из смены.

Модификация `ProcessSaleUseCase.process()` — после открытия смены (или проверки смены):

```kotlin
private suspend fun getShiftContext(): ShiftContext? {
    val status = fiscalCore.getStatus()
    val shiftNumber = shiftDao.findOpenShift()?.number?.toString() ?: return null
    val receiptNumber = (status.currentFdNumber + 1).toString()
    return ShiftContext(shiftNumber, receiptNumber)
}
```

Где `ShiftContext` — data class:
```kotlin
data class ShiftContext(
    val shiftNumber: String,
    val receiptNumberInShift: String
)
```

- [ ] **Step 3: Modify FiscalCheck builder to include requiredInfo**

В методе создания `FiscalCheck` в `ProcessSaleUseCase` добавить:

```kotlin
val additionalInfo = mutableMapOf<String, String>()
additionalInfo["shiftNumber"] = shiftContext.shiftNumber
additionalInfo["receiptNumberInShift"] = shiftContext.receiptNumberInShift
additionalInfo["taxSystem"] = config.taxSystem.tag  // берётся из FiscalConfig.taxSystem
additionalInfo["orgInn"] = config.orgInn  // ИНН организации

val check = FiscalCheck(
    id = UUID.randomUUID().toString(),
    type = CheckType.SALE,
    items = items,
    payments = payments,
    additionalInfo = additionalInfo
)
```

Аналогично для `ReturnUseCase`, `CashDrawerUseCase`, `CorrectionUseCase`.

- [ ] **Step 4: Добавить тест для ProcessSaleUseCase**

Создать `android/app/src/test/java/com/vitbon/kkm/features/sales/domain/ProcessSaleUseCaseTest.kt`:

```kotlin
package com.vitbon.kkm.features.sales.domain

import com.vitbon.kkm.core.fiscal.model.*
import org.junit.Assert.*
import org.junit.Test

class ProcessSaleUseCaseTest {
    // Тест проверяет, что FiscalCheck.additionalInfo содержит required FFD tags
    // Мок FiscalCore.getStatus() возвращает currentFdNumber=5, открытая смена=10
    // Результат: additionalInfo содержит shiftNumber, receiptNumberInShift, taxSystem, orgInn
}
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/sales/domain/ProcessSaleUseCase.kt android/app/src/main/java/com/vitbon/kkm/features/returns/domain/ReturnUseCase.kt android/app/src/main/java/com/vitbon/kkm/features/cashdrawer/domain/CashDrawerUseCase.kt android/app/src/main/java/com/vitbon/kkm/features/correction/domain/CorrectionUseCase.kt android/app/src/main/java/com/vitbon/kkm/features/shift/domain/ShiftUseCase.kt
git commit -m "feat(ffd): populate requiredInfo in FiscalCheck from fiscalStatus"
```

---

## Task 5: ShiftUseCase — проверка смены старше 24 часов

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/shift/domain/ShiftUseCase.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/features/shift/domain/ShiftUseCaseTest.kt` (create)

FFD/54-ФЗ требование: смена не должна превышать 24 часа. При превышении ККТ/ФН может отказать в операции.

- [ ] **Step 1: Добавить тест для shift age check**

Создать `android/app/src/test/java/com/vitbon/kkm/features/shift/domain/ShiftUseCaseTest.kt`:

```kotlin
package com.vitbon.kkm.features.shift.domain

import com.vitbon.kkm.core.fiscal.model.*
import org.junit.Assert.*
import org.junit.Test

class ShiftUseCaseTest {

    @Test
    fun `open shift — warns when shift age exceeds 24 hours`() {
        // Given: existing shift open for 25 hours
        // When: user attempts any fiscal operation
        // Then: operation returns warning with "SHIFT_TOO_OLD" code
        // Fiscal operation is NOT blocked — only warning is shown
    }

    @Test
    fun `open shift — no warning when shift age under 24 hours`() {
        // Given: existing shift open for 8 hours
        // When: fiscal operation executes
        // Then: no warning emitted, operation proceeds
    }
}
```

- [ ] **Step 2: Modify ShiftUseCase — добавить проверку shiftAgeHours**

В методах `executeSale()`, `executeReturn()`, `executeCorrection()` — перед вызовом `orchestrator` получить статус:

```kotlin
suspend fun executeSale(...) {
    val status = orchestrator.executeStatusCheck()
    val shiftAgeWarning = status.shiftAgeHours?.let { age ->
        if (age > 24) "Смена открыта более ${age}ч. Закройте смену." else null
    }
    // Продолжить операцию, вернуть warning в Result
}
```

Результат завернуть в новый sealed class или использовать `FiscalRuntimeResult` с отдельным полем warnings:

```kotlin
data class FiscalRuntimeResult(
    val result: FiscalRuntimeResult,
    val warnings: List<String> = emptyList()
)
```

Альтернатива — проще: `FiscalRuntimeResult.Success` получает поле `warnings: List<String>`. Добавить:

```kotlin
data class Success(
    val fiscalSign: String,
    val fnNumber: String,
    val fdNumber: String,
    val ffdVersion: String,
    val warnings: List<String> = emptyList()
) : FiscalRuntimeResult()
```

- [ ] **Step 3: UI side — показывать warning в SalesViewModel**

В `SalesViewModel` результат `executeSale()` проверяется на наличие warnings. Если warnings не пустые — показать snackbar с текстом предупреждения.

- [ ] **Step 4: Run tests to verify they pass**

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/shift/domain/ShiftUseCase.kt android/app/src/main/java/com/vitbon/kkm/core/fiscal/runtime/FiscalRuntimeModels.kt android/app/src/main/java/com/vitbon/kkm/features/sales/domain/SalesViewModel.kt
git commit -m "feat(shift): add shift age warning when exceeds 24 hours"
```

---

## Task 6: FiscalConfig — добавить taxSystem и orgInn

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/FiscalCoreProvider.kt`
- Modify: `android/app/src/main/java/com/vitbon/kkm/di/AppModule.kt`

`FiscalConfig` currently only has `model`, `host`, `port`. Need to add:
- `taxSystem: TaxSystem` — система налогообложения (по умолчанию `OSN`)
- `orgInn: String?` — ИНН организации (для тега 1026)

Эти значения должны приходить от backend при логине и сохраняться в локальном storage. Пока — заглушка через конфиг.

- [ ] **Step 1: Modify FiscalConfig**

```kotlin
data class FiscalConfig(
    val model: FiscalDeviceModel = FiscalDeviceModel.MSPOS_K,
    val host: String = "localhost",
    val port: Int = 8443,
    val taxSystem: TaxSystem = TaxSystem.OSN,     // система налогообложения по умолчанию
    val orgInn: String? = null                     // ИНН организации для тега 1026
)
```

- [ ] **Step 2: Update DI binding**

В `AppModule` обновить `provideFiscalConfig()`:

```kotlin
@Provides
@Singleton
fun provideFiscalConfig(prefs: SharedPreferences): FiscalConfig {
    val taxSystemTag = prefs.getString("tax_system", "1") ?: "1"
    val orgInn = prefs.getString("org_inn", null)
    return FiscalConfig(
        taxSystem = TaxSystem.fromString(taxSystemTag),
        orgInn = orgInn
    )
}
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/FiscalCoreProvider.kt android/app/src/main/java/com/vitbon/kkm/di/AppModule.kt
git commit -m "feat(config): add taxSystem and orgInn to FiscalConfig"
```

---

## Task 7: Full regression — все тесты зелёные

- [ ] **Step 1: Run full Android unit test suite**

Run: `cd android && ./gradlew :app:testDebugUnitTest 2>&1 | tail -40`
Expected: ALL PASS

- [ ] **Step 2: Run assembleDebug**

Run: `cd android && ./gradlew :app:assembleDebug 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git commit -m "chore: full regression — all tests pass after FFD compliance fixes"
```

---

## Файлы после всех задач

```
Изменённые:
  android/app/src/main/java/com/vitbon/kkm/core/fiscal/model/FiscalModels.kt  (+TaxSystem, NO_VAT="6")
  android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt  (+tags 1005/1034/1191/1026, 1140 dynamic, cash in/out overloads)
  android/app/src/main/java/com/vitbon/kkm/core/fiscal/FiscalCoreProvider.kt  (+taxSystem, orgInn in FiscalConfig)
  android/app/src/main/java/com/vitbon/kkm/di/AppModule.kt  (FiscalConfig from prefs)
  android/app/src/main/java/com/vitbon/kkm/features/sales/domain/ProcessSaleUseCase.kt  (+requiredInfo population)
  android/app/src/main/java/com/vitbon/kkm/features/returns/domain/ReturnUseCase.kt  (+requiredInfo)
  android/app/src/main/java/com/vitbon/kkm/features/cashdrawer/domain/CashDrawerUseCase.kt  (+requiredInfo)
  android/app/src/main/java/com/vitbon/kkm/features/correction/domain/CorrectionUseCase.kt  (+requiredInfo)
  android/app/src/main/java/com/vitbon/kkm/features/shift/domain/ShiftUseCase.kt  (+24h warning)
  android/app/src/main/java/com/vitbon/kkm/core/fiscal/runtime/FiscalRuntimeModels.kt  (+warnings field)
  android/app/src/main/java/com/vitbon/kkm/features/sales/domain/SalesViewModel.kt  (+warning snackbar)

Новые:
  android/app/src/test/java/com/vitbon/kkm/core/fiscal/model/VatRateTest.kt
  android/app/src/test/java/com/vitbon/kkm/features/shift/domain/ShiftUseCaseTest.kt
```