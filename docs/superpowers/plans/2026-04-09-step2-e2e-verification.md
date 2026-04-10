# Step 2 E2E Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove with reproducible evidence that MVP Step 2 flow (Auth → Sales → Fiscal → Sync → Reports) works end-to-end and document any remaining gaps.

**Architecture:** Execute verification as two independent lanes (backend lane and Android lane), then merge evidence into a single summary. The backend lane validates deterministic login behavior. The Android lane validates seed, hybrid auth behavior, sync state transitions, and report date-range logic through tests and command output.

**Tech Stack:** Kotlin, Spring Boot tests, Gradle, Android Room/WorkManager/Hilt, JUnit, Beads (`bd`)

---

## File Structure and Responsibilities

- Modify: `backend/src/test/kotlin/com/vitbon/kkm/integration/AuthIntegrationTest.kt`
  - Responsibility: add deterministic backend auth behavior tests aligned to `AuthService`.
- Create: `android/app/src/test/java/com/vitbon/kkm/features/bootstrap/domain/SeedDataUseCaseTest.kt`
  - Responsibility: verify first-run seeding behavior for demo cashier and product.
- Create: `android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt`
  - Responsibility: verify reports aggregate over date-range results from `CheckDao.findByDateRange`.
- Modify: `android/app/src/test/java/com/vitbon/kkm/features/auth/domain/AuthUseCaseTest.kt`
  - Responsibility: verify local auth and best-effort backend warning behavior with deterministic doubles.
- Modify: `docs/superpowers/specs/2026-04-09-step2-e2e-verification-design.md`
  - Responsibility: append final evidence summary section if needed.

No production code changes are planned in this verification pass unless a failing test reveals a defect.

---

### Task 1: Backend deterministic auth verification

**Files:**
- Modify: `backend/src/test/kotlin/com/vitbon/kkm/integration/AuthIntegrationTest.kt`

- [ ] **Step 1: Write failing test for demo PIN success path**

```kotlin
@Test
fun `AuthService login returns demo cashier for pin 1111`() {
    val service = AuthService()

    val response = service.login("1111")

    assertEquals("demo-token-1111", response.token)
    assertEquals("cashier-demo-1", response.cashier.id)
    assertEquals("Демо Кассир", response.cashier.name)
    assertEquals("CASHIER", response.cashier.role)
}
```

- [ ] **Step 2: Run backend test to verify RED/GREEN status**

Run: `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.AuthIntegrationTest" --no-daemon`
Expected: If test absent yet, compilation/test selection fails (RED). After adding code, PASS (GREEN).

- [ ] **Step 3: Write failing test for invalid PIN -> 401 mapping**

```kotlin
@Test
fun `AuthService login throws unauthorized for invalid pin`() {
    val service = AuthService()

    val ex = assertThrows(ResponseStatusException::class.java) {
        service.login("9999")
    }

    assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
}
```

- [ ] **Step 4: Run backend auth test again and verify pass**

Run: `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.AuthIntegrationTest" --no-daemon`
Expected: `BUILD SUCCESSFUL` and tests passed.

- [ ] **Step 5: Commit backend verification tests**

```bash
git add backend/src/test/kotlin/com/vitbon/kkm/integration/AuthIntegrationTest.kt
git commit -m "test(backend): verify deterministic auth pin behavior"
```

---

### Task 2: Android seed verification tests

**Files:**
- Create: `android/app/src/test/java/com/vitbon/kkm/features/bootstrap/domain/SeedDataUseCaseTest.kt`

- [ ] **Step 1: Write failing seed test for empty DB scenario**

```kotlin
@Test
fun `seedIfNeeded inserts demo cashier and product when storage is empty`() = runTest {
    val cashierDao = mock<CashierDao>()
    val productDao = mock<ProductDao>()
    whenever(cashierDao.count()).thenReturn(0)
    whenever(productDao.count()).thenReturn(0)

    val useCase = SeedDataUseCase(cashierDao, productDao)
    useCase.seedIfNeeded()

    verify(cashierDao).insert(argThat { id == "cashier-demo-1" && role == "CASHIER" })
    verify(productDao).insert(argThat { barcode == "4607001234567" && price == 12900L })
}
```

- [ ] **Step 2: Run test to verify RED/GREEN state**

Run: `cd android && ./gradlew.bat testDebugUnitTest --tests "*SeedDataUseCaseTest" --no-daemon`
Expected: RED if test class not yet wired; GREEN once class/imports are complete.

- [ ] **Step 3: Write failing seed test for non-empty DB scenario**

```kotlin
@Test
fun `seedIfNeeded does not insert data when cashiers and products already exist`() = runTest {
    val cashierDao = mock<CashierDao>()
    val productDao = mock<ProductDao>()
    whenever(cashierDao.count()).thenReturn(1)
    whenever(productDao.count()).thenReturn(1)

    val useCase = SeedDataUseCase(cashierDao, productDao)
    useCase.seedIfNeeded()

    verify(cashierDao, never()).insert(any())
    verify(productDao, never()).insert(any())
}
```

- [ ] **Step 4: Re-run seed tests and verify pass**

Run: `cd android && ./gradlew.bat testDebugUnitTest --tests "*SeedDataUseCaseTest" --no-daemon`
Expected: `BUILD SUCCESSFUL` and both tests passed.

- [ ] **Step 5: Commit seed tests**

```bash
git add android/app/src/test/java/com/vitbon/kkm/features/bootstrap/domain/SeedDataUseCaseTest.kt
git commit -m "test(android): cover bootstrap seed behavior"
```

---

### Task 3: Android auth and reports verification tests

**Files:**
- Modify: `android/app/src/test/java/com/vitbon/kkm/features/auth/domain/AuthUseCaseTest.kt`
- Create: `android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt`

- [ ] **Step 1: Write failing reports test for date-range aggregation source**

```kotlin
@Test
fun `getSalesReport aggregates checks returned by date range query`() = runTest {
    val dao = mock<CheckDao>()
    whenever(dao.findByDateRange(100L, 200L)).thenReturn(
        listOf(
            LocalCheck(id = "1", localUuid = "1", shiftId = null, cashierId = null, deviceId = "d", type = "sale", fiscalSign = null, ofdResponse = null, ffdVersion = null, status = "SYNCED", subtotal = 1000, discount = 0, total = 1000, taxAmount = 0, paymentType = "cash", createdAt = 120L, syncedAt = 130L),
            LocalCheck(id = "2", localUuid = "2", shiftId = null, cashierId = null, deviceId = "d", type = "return", fiscalSign = null, ofdResponse = null, ffdVersion = null, status = "SYNCED", subtotal = 500, discount = 0, total = 500, taxAmount = 0, paymentType = "cash", createdAt = 140L, syncedAt = 150L)
        )
    )

    val report = ReportsUseCase(dao).getSalesReport(100L, 200L)

    assertEquals(1000L, report.totalSales)
    assertEquals(500L, report.totalReturns)
    verify(dao).findByDateRange(100L, 200L)
}
```

- [ ] **Step 2: Run reports test and verify pass/fail state**

Run: `cd android && ./gradlew.bat testDebugUnitTest --tests "*ReportsUseCaseTest" --no-daemon`
Expected: RED before class creation, GREEN after implementation.

- [ ] **Step 3: Replace fake auth test with real AuthUseCase-focused unit test doubles**

```kotlin
@Test
fun `authenticate returns success for cashier with matching pin hash`() = runTest {
    val cashierDao = mock<CashierDao>()
    val api = mock<VitbonApi>()
    val prefs = inMemoryPrefs()
    val context = mockOnlineContext(false)

    whenever(cashierDao.findByPinHash("03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"))
        .thenReturn(LocalCashier("cashier-demo-1", "Демо Кассир", "03ac...46f4", "CASHIER", 0L))

    val result = AuthUseCase(cashierDao, api, prefs, context).authenticate("1234")

    assertTrue(result is AuthResult.Success)
}
```

- [ ] **Step 4: Add best-effort backend warning test for non-success response**

```kotlin
@Test
fun `validateWithBackendBestEffort returns warning on backend invalid response`() = runTest {
    val cashierDao = mock<CashierDao>()
    val api = mock<VitbonApi>()
    val prefs = inMemoryPrefs()
    val context = mockOnlineContext(true)

    whenever(api.login(LoginRequestDto("1111"))).thenReturn(Response.error(401, "".toResponseBody(null)))

    val warning = AuthUseCase(cashierDao, api, prefs, context).validateWithBackendBestEffort("1111")

    assertEquals("Локальный вход выполнен, но сервер не подтвердил авторизацию", warning)
}
```

- [ ] **Step 5: Run Android auth/reports tests and verify pass**

Run: `cd android && ./gradlew.bat testDebugUnitTest --tests "*AuthUseCaseTest" --tests "*ReportsUseCaseTest" --no-daemon`
Expected: `BUILD SUCCESSFUL` with all targeted tests passed.

- [ ] **Step 6: Commit Android verification tests**

```bash
git add android/app/src/test/java/com/vitbon/kkm/features/auth/domain/AuthUseCaseTest.kt
git add android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt
git commit -m "test(android): verify auth warnings and report date-range aggregation"
```

---

### Task 4: End-to-end verification run and evidence capture

**Files:**
- Modify: `docs/superpowers/specs/2026-04-09-step2-e2e-verification-design.md` (append evidence section if needed)

- [ ] **Step 1: Run full Android unit test suite for confidence baseline**

Run: `cd android && ./gradlew.bat testDebugUnitTest --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run backend tests for confidence baseline**

Run: `cd backend && ./gradlew.bat test --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run Android assemble verification command**

Run: `cd android && ./gradlew.bat assembleDebug --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run backend compile verification command**

Run: `cd backend && ./gradlew.bat clean compileKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Capture manual E2E smoke evidence checklist**

Checklist to execute and record:

```text
1) Fresh app start -> seed created (demo cashier + demo product)
2) Offline login with PIN 1111 succeeds
3) Perform sale with barcode 4607001234567
4) Verify checks and check_items rows exist for created check
5) Enable network and trigger sync
6) Verify check transitions to SYNCED and syncedAt is set
7) Verify reports include the synced check in selected date range
8) Simulate backend invalid auth while local auth succeeds -> warning shown, session retained
```

- [ ] **Step 6: Append evidence summary to spec or implementation notes**

Include:
- command outputs (success/failure)
- test class results
- pass/fail per checklist item
- unresolved gaps with exact reproduction steps

- [ ] **Step 7: Commit verification artifacts**

```bash
git add docs/superpowers/specs/2026-04-09-step2-e2e-verification-design.md
git commit -m "docs(verification): add step 2 E2E evidence summary"
```

---

## Self-Review

### Spec coverage

- Hybrid auth validated: Task 3 + Task 4 checklist
- Sale persistence and sync transitions: Task 4 checklist
- Reports date-range logic: Task 3 reports test + Task 4 checklist
- Backend deterministic auth: Task 1 tests
- Evidence-first completion: Task 4 outputs + summary

No uncovered requirement remains from the verification spec.

### Placeholder scan

- No TBD/TODO placeholders.
- Each task has explicit file paths and exact commands.

### Type consistency

- Types and methods align with existing code:
  - `AuthUseCase.validateWithBackendBestEffort(pin: String): String?`
  - `ReportsUseCase.getSalesReport(fromTs: Long, toTs: Long)`
  - `CheckDao.findByDateRange(fromTs: Long, toTs: Long)`

Plan complete and internally consistent.
