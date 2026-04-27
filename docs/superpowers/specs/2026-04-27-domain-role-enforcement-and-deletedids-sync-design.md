# Spec: Domain Role Enforcement + deletedIds Sync — Phase B Residual Gaps

**Date:** 2026-04-27
**Parent epic:** vitbon-kassa-0xt — Phase B (P0): security, auth, RBAC, audit hardening
**Task:** vitbon-kassa-bot — Close residual TZ gaps after Phase B

## Status

- [ ] Design approved
- [ ] Implementation plan written
- [ ] Tests written
- [ ] Backend + Android suites pass

---

## Overview

Two independent gaps remain after Phase B branch (`feat/phase-b-security-auth-rbac-audit-pr`):

1. **Domain Role Enforcement** — Android use cases lack role checks at the domain layer. Backend route RBAC is enforced, but domain logic must also gate operations by cashier role.
2. **deletedIds Sync** — Backend correctly returns `deletedIds` in all four delta-sync responses (products, checks, shifts, cashiers). Android reads the field but never deletes matching rows from Room DB.

---

## Gap 1: Domain Role Enforcement

### Role Matrix (per TZ)

| Use case | Method | Allowed roles | Throws |
|----------|--------|--------------|--------|
| `ProcessSaleUseCase` | `execute()` | CASHIER, SENIOR_CASHIER, ADMIN | — |
| `ReturnUseCase` | `processReturn()` | SENIOR_CASHIER, ADMIN | `DomainAccessDeniedException` |
| `CorrectionUseCase` | `processCorrection()` | ADMIN only | `DomainAccessDeniedException` |
| `CashDrawerUseCase` | `execute()` | SENIOR_CASHIER, ADMIN | `DomainAccessDeniedException` |
| `ShiftUseCase` | `closeShift()` | ADMIN only | `DomainAccessDeniedException` |

### Existing Infrastructure

- `CashierRole` enum: `ADMIN`, `SENIOR_CASHIER`, `CASHIER` — defined in `AuthModels.kt`
- `AuthUseCase.getCurrentCashierRole(): CashierRole?` — exists, not wired into use cases
- Backend route-level RBAC is already implemented (Phase B)

### New File: `RoleGuard.kt`

**Path:** `android/.../domain/RoleGuard.kt`

```kotlin
package com.vitbon.kassa.domain

class DomainAccessDeniedException(message: String) : RuntimeException(message)

fun requireRole(actual: CashierRole?, vararg allowed: CashierRole) {
    if (actual == null) {
        throw DomainAccessDeniedException("No role — not authenticated")
    }
    if (actual !in allowed) {
        throw DomainAccessDeniedException(
            "Role ${actual.name} not allowed. Required: ${allowed.toList()}"
        )
    }
}
```

### Changes Per Use Case

Each use case follows this pattern:

```kotlin
class ProcessSaleUseCase @Inject constructor(
    private val authUseCase: AuthUseCase,   // ADD
    private val fiscalOrchestrator: FiscalOperationOrchestrator,
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao
) {
    suspend fun execute(...): FiscalResult {
        requireRole(authUseCase.getCurrentCashierRole(), CashierRole.CASHIER, CashierRole.SENIOR_CASHIER, CashierRole.ADMIN)
        // ... existing business logic
    }
}
```

**Files to modify:**

| File | Role guard added |
|------|-----------------|
| `ProcessSaleUseCase.kt` | `CASHIER, SENIOR_CASHIER, ADMIN` (no-op — all allowed) |
| `ReturnUseCase.kt` | `SENIOR_CASHIER, ADMIN` |
| `CorrectionUseCase.kt` | `ADMIN` |
| `CashDrawerUseCase.kt` | `SENIOR_CASHIER, ADMIN` |
| `ShiftUseCase.kt` | `ADMIN` |

> Note: `ProcessSaleUseCase` guard is a no-op structurally (all roles allowed) but provides an explicit enforcement point and consistency with other use cases.

### ViewModel Error Handling

ViewModels catch `DomainAccessDeniedException` and surface it as UI state:

```kotlin
try {
    useCase.execute(...)
} catch (e: DomainAccessDeniedException) {
    _uiState.update { it.copy(accessDenied = true) }
}
```

**Files to modify:**

| ViewModel | Error handling |
|-----------|---------------|
| `SalesViewModel` | catch → `accessDenied = true`, snackbar message |
| `ReturnViewModel` | catch → `accessDenied = true`, snackbar message |
| `CorrectionViewModel` | catch → `accessDenied = true`, snackbar message |
| `CashDrawerViewModel` | catch → `accessDenied = true`, snackbar message |
| `ShiftViewModel` | catch → `accessDenied = true`, snackbar message |

### Test Strategy

Unit tests per use case — 2 scenarios each:

| Test | Setup | Expected |
|------|-------|----------|
| Happy path | Mock `AuthUseCase` returns allowed role | No exception thrown |
| Denied path | Mock `AuthUseCase` returns disallowed role | `DomainAccessDeniedException` thrown |
| Null role | Mock `AuthUseCase` returns null | `DomainAccessDeniedException` thrown |

File: `android/.../domain/RoleGuardTest.kt` — parameterized tests across all five use cases.

---

## Gap 2: deletedIds Synchronous Application

### Scope

All four sync responses from backend return `deletedIds`:

| Entity | Sync method | DTO field |
|--------|-----------|-----------|
| Products | `api.getProductsSync()` | `ProductSyncResponseDto.deletedIds` |
| Checks | `api.getChecksSync()` | `CheckSyncResponseDto.deletedIds` |
| Shifts | `api.getShiftsSync()` | `ShiftSyncResponseDto.deletedIds` |
| Cashiers | `api.getCashiersSync()` | `CashierSyncResponseDto.deletedIds` |

Backend implementation is complete and tested (`ProductSyncIntegrationTest`).

### New DAO Methods

Add to each DAO:

```kotlin
@Dao
interface ProductDao {
    // ... existing methods ...

    @Query("DELETE FROM products WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface CheckDao {
    @Query("DELETE FROM checks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface ShiftDao {
    @Query("DELETE FROM shifts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface CashierDao {
    @Query("DELETE FROM cashiers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}
```

> Edge case: `ids` empty list → Room JSQLite no-op, no exception. Confirmed safe.

### SyncManager Changes

In `SyncManager.kt`, add a private helper and update each sync method:

```kotlin
private suspend inline fun <reified T> applyDeletedIds(
    dao: GenericDeleteDao,
    deletedIds: List<String>
) {
    if (deletedIds.isNotEmpty()) {
        dao.deleteByIds(deletedIds)  // type-safe via reified + extension
    }
}

suspend fun syncProducts(): ProductSyncResult {
    val response = api.getProductsSync(syncPrefs.lastProductSyncTimestamp)
    val body = response.body() ?: return ProductSyncResult(0, 0, isSuccess = false)

    if (body.deletedIds.isNotEmpty()) {
        productDao.deleteByIds(body.deletedIds)
    }

    val entities = body.products.map { it.toEntity() }
    productDao.insertAll(entities)
    syncPrefs.lastProductSyncTimestamp = body.serverTimestamp
    return ProductSyncResult(entities.size, body.deletedIds.size, isSuccess = true)
}
```

Same pattern applied to: `syncChecks()`, `syncShifts()`, `syncCashiers()`.

**No change to retry/exponential backoff behavior.** If `deleteByIds` throws, the exception propagates up and the existing `SyncDownWorker` retry logic handles it. If `deletedIds` references an already-absent row, `DELETE WHERE id IN (...)` affects 0 rows — no exception, no problem.

### Test Strategy

Unit tests in `SyncManagerTest.kt`:

| Test | Setup | Verify |
|------|-------|--------|
| deletedIds applied before insert | API returns `deletedIds: ["p-1"]` | `productDao.deleteByIds(["p-1"])` called first, `insertAll` called after |
| empty deletedIds — no delete call | API returns `deletedIds: []` | `deleteByIds` not called |
| delete fails — no insert | `deleteByIds` throws | `insertAll` not called, exception propagated |

### SyncDownWorker: No Changes Required

Existing retry/backoff wiring in `SyncDownWorker` is unchanged. The sync method returns `Result.failure()` on any exception, which triggers backoff retry. `deletedIds` processing is inside the sync method, so it participates in that retry transparently.

---

## Implementation Order

1. `RoleGuard.kt` + `DomainAccessDeniedException` (new file)
2. Five use cases — inject `AuthUseCase`, add `requireRole()` call
3. Five ViewModels — catch `DomainAccessDeniedException`, surface `accessDenied` state
4. Unit tests for RoleGuard across all five use cases
5. Four DAO files — add `deleteByIds()` method
6. `SyncManager.kt` — add `applyDeletedIds` helper, update all four sync methods
7. Unit tests for `deletedIds` sync
8. Full backend suite: `testDebugUnitTest` + backend tests

---

## Acceptance Criteria

- [ ] `ProcessSaleUseCase` accepts CASHIER, SENIOR_CASHIER, ADMIN
- [ ] `ReturnUseCase` rejects CASHIER with `DomainAccessDeniedException`
- [ ] `CorrectionUseCase` rejects non-ADMIN with `DomainAccessDeniedException`
- [ ] `CashDrawerUseCase` rejects CASHIER with `DomainAccessDeniedException`
- [ ] `ShiftUseCase` rejects non-ADMIN with `DomainAccessDeniedException`
- [ ] ViewModels show snackbar on `DomainAccessDeniedException`
- [ ] `ProductDao.deleteByIds()` exists and is called during product sync
- [ ] `CheckDao.deleteByIds()`, `ShiftDao.deleteByIds()`, `CashierDao.deleteByIds()` exist
- [ ] Backend test suite: all tests pass
- [ ] Android unit test suite: all tests pass
