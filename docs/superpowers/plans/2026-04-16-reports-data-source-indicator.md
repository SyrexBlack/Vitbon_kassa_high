# Reports Data Source Indicator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an explicit data-source indicator on Reports so users can see whether values come from backend aggregates or local Room fallback.

**Architecture:** Extend the reports domain model with a small source enum and set it at the decision point inside `ReportsUseCase.getSalesReport`. Keep ViewModel flow unchanged, and render a compact source chip under period filters in `ReportsScreen`. Lock behavior with unit tests for both remote-success and local-fallback branches.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt ViewModel, Room DAOs, Retrofit API, JUnit4 + MockK.

---

## File Map

- Modify: `android/app/src/main/java/com/vitbon/kkm/features/reports/domain/ReportsUseCase.kt`
  - Add `ReportDataSource` enum
  - Add `source` to `SalesReport`
  - Set source in remote and fallback returns
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/ReportsScreen.kt`
  - Add source chip UI under period filter row
- Modify: `android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt`
  - Add assertions for `report.source` in relevant tests

No new files required.

---

### Task 1: Add explicit report source in domain model

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/reports/domain/ReportsUseCase.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt`

- [ ] **Step 1: Write failing test for remote source marker**

In `getSalesReport uses backend report when api succeeds`, add:

```kotlin
assertEquals(ReportDataSource.REMOTE, report.source)
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd android && ./gradlew.bat testDebugUnitTest --tests "*ReportsUseCaseTest.getSalesReport uses backend report when api succeeds*" --no-daemon
```

Expected: compile/test failure because `source` / `ReportDataSource` does not exist yet.

- [ ] **Step 3: Write minimal implementation for source enum and remote assignment**

In `ReportsUseCase.kt`, add enum before `SalesReport`:

```kotlin
enum class ReportDataSource {
    REMOTE,
    LOCAL
}
```

Extend `SalesReport` with `source`:

```kotlin
data class SalesReport(
    val totalSales: Long,
    val totalReturns: Long,
    val cashTotal: Long,
    val cardTotal: Long,
    val sbpTotal: Long,
    val checkCount: Int,
    val returnCount: Int,
    val averageCheck: Long,
    val topProducts: List<ProductSales> = emptyList(),
    val source: ReportDataSource
)
```

Set remote source in remote return block:

```kotlin
return SalesReport(
    totalSales = body.totalRevenue,
    totalReturns = body.totalReturns,
    cashTotal = body.cashRevenue,
    cardTotal = body.cardRevenue,
    sbpTotal = 0L,
    checkCount = body.totalChecks,
    returnCount = body.returnChecks,
    averageCheck = body.averageCheck,
    topProducts = body.topProducts.orEmpty().map {
        ProductSales(
            name = it.name,
            quantity = it.quantity,
            total = it.total
        )
    },
    source = ReportDataSource.REMOTE
)
```

Set local source in fallback return block:

```kotlin
return SalesReport(
    totalSales = checks.sumOf { it.total },
    totalReturns = returns.sumOf { it.total },
    cashTotal = cashTotal,
    cardTotal = cardTotal,
    sbpTotal = sbpTotal,
    checkCount = checks.size,
    returnCount = returns.size,
    averageCheck = if (checks.isNotEmpty()) checks.sumOf { it.total } / checks.size else 0L,
    topProducts = topProducts,
    source = ReportDataSource.LOCAL
)
```

- [ ] **Step 4: Run targeted test to verify it passes**

Run:

```bash
cd android && ./gradlew.bat testDebugUnitTest --tests "*ReportsUseCaseTest.getSalesReport uses backend report when api succeeds*" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit Task 1**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/reports/domain/ReportsUseCase.kt android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt
git commit -m "feat(reports): add explicit sales report source"
```

---

### Task 2: Lock fallback source behavior with tests

**Files:**
- Modify: `android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt`

- [ ] **Step 1: Write failing tests for local fallback source**

Add source assertions to both fallback tests:

```kotlin
assertEquals(ReportDataSource.LOCAL, report.source)
```

In:
- `getSalesReport falls back to local range when backend responds non-success`
- `getSalesReport falls back to local range when backend request throws`

- [ ] **Step 2: Run fallback tests to verify red/green state**

Run:

```bash
cd android && ./gradlew.bat testDebugUnitTest --tests "*ReportsUseCaseTest.getSalesReport falls back to local range when backend responds non-success*" --tests "*ReportsUseCaseTest.getSalesReport falls back to local range when backend request throws*" --no-daemon
```

Expected: If source assignment already complete from Task 1, tests should PASS; if not, they FAIL for missing/incorrect source.

- [ ] **Step 3: Ensure minimal fixes if needed**

If either test fails, adjust only `source` assignment in `ReportsUseCase.getSalesReport` remote/fallback branches (no other behavior changes).

- [ ] **Step 4: Run full ReportsUseCaseTest suite**

Run:

```bash
cd android && ./gradlew.bat testDebugUnitTest --tests "*ReportsUseCaseTest*" --no-daemon
```

Expected: PASS for all report use case tests.

- [ ] **Step 5: Commit Task 2**

```bash
git add android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt android/app/src/main/java/com/vitbon/kkm/features/reports/domain/ReportsUseCase.kt
git commit -m "test(reports): verify source for local fallback branches"
```

---

### Task 3: Add source chip to Reports UI

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/ReportsScreen.kt`

- [ ] **Step 1: Write UI expectation as checklist comment in plan execution notes**

Expected UI behavior to validate after implementation:
- Chip appears under period filters only when `state.report != null`
- `REMOTE` -> `Источник: Облако`
- `LOCAL` -> `Источник: Локально`

- [ ] **Step 2: Implement minimal source chip rendering**

Inside `Column` in `ReportsScreen`, after filter row and before report content block, add:

```kotlin
if (state.report != null) {
    val source = state.report!!.source
    val label = when (source) {
        ReportDataSource.REMOTE -> "Источник: Облако"
        ReportDataSource.LOCAL -> "Источник: Локально"
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    )
}
```

Also add import:

```kotlin
import com.vitbon.kkm.features.reports.domain.ReportDataSource
```

- [ ] **Step 3: Build debug variant to verify compile**

Run:

```bash
cd android && ./gradlew.bat assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit Task 3**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/ReportsScreen.kt
git commit -m "feat(reports): show report data source chip"
```

---

### Task 4: Verification pass (tests + practical smoke)

**Files:**
- Verify: `android/app/src/main/java/com/vitbon/kkm/features/reports/domain/ReportsUseCase.kt`
- Verify: `android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/ReportsScreen.kt`
- Verify: `android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt`

- [ ] **Step 1: Run unit tests for reports module**

```bash
cd android && ./gradlew.bat testDebugUnitTest --tests "*ReportsUseCaseTest*" --no-daemon
```

Expected: PASS.

- [ ] **Step 2: Run practical emulator smoke (online then offline)**

Online check:
1. Launch app and go to Reports with network enabled.
2. Confirm chip text: `Источник: Облако`.

Offline check:
1. Disable network (adb `svc wifi disable`, `svc data disable`).
2. Re-open/reload Reports (toggle period chip if needed).
3. Confirm chip text: `Источник: Локально`.

- [ ] **Step 3: Re-enable emulator network after smoke**

```bash
"/c/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell svc wifi enable
"/c/Users/user/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell svc data enable
```

Expected: normal environment restored.

- [ ] **Step 4: Final commit for verification artifacts (if any tracked files changed)**

If only code/test files changed and already committed in prior tasks, skip extra commit.
If additional tracked verification notes were added, commit with:

```bash
git add <tracked-files>
git commit -m "docs(reports): record data source indicator verification"
```

---

## Spec Coverage Self-Review

- Source enum in domain model: covered in Task 1.
- Correct source assignment in remote vs fallback: covered in Tasks 1-2.
- UI chip below period filters: covered in Task 3.
- Unit tests for both branches: covered in Task 2.
- Preserve existing calculations: enforced by minimal edits and full ReportsUseCase tests in Tasks 2 and 4.

## Placeholder Scan Self-Review

- No `TODO`/`TBD` placeholders.
- All code-edit steps include concrete code blocks.
- All verification steps include concrete commands and expected outcomes.

## Type Consistency Self-Review

- `ReportDataSource` used consistently in `SalesReport` and UI `when` branch.
- `SalesReport` constructor updates defined explicitly for both return paths.

