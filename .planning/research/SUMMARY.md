# VITBON Kassovoye Prilozheniye — Research Synthesis

**Версия:** 1.0  
**Дата:** 2026-06-20  
**Проект:** VITBON Мобильная Касса  
**Статус:** Завершено  
**Потребители:** Engineering leads, product managers, roadmap planners

---

## 1. Executive Summary

VITBON is an offline-first Android cash register application (54-ФЗ) serving 200+ cash register deployments in Russian retail. It must simultaneously satisfy three competing constraints: **fiscal integrity** (чеки persist locally and sync eventually), **regulatory compliance** (ФФД 1.05/1.2, Честный ЗНАК, ЕГАИС), and **hardware abstraction** (MSPOS-K, Нева 01Ф, and future ККТ).

**The project succeeds if and only if five conditions are met:**

| # | Condition | How to Ensure |
|---|---|---|
| 1 | **Fiscal documents are never lost** | Every sale writes to Room before the ККТ call. SyncUpWorker guarantees eventual delivery. Sync queue is bounded and observable. |
| 2 | **FFD version is immutable in production** | `FfdVersionResolver` throws on any attempt to change version after first fiscal document. No UI toggle post-first-document. |
| 3 | **Offline grace period is legally correct** | License grace period is `now > expiresAt + 7 days`, never derived from `lastSuccessfulCheck`. Both `expiresAt` and `lastCheck` are stored and surfaced to the operator. |
| 4 | **Fiscal core is fully isolated** | `FiscalCore` interface has no dependency on Room, Retrofit, or WorkManager. Adapters are swappable without touching use cases or UI. |
| 5 | **Module risk is contained** | ЕГАИС and Честный ЗНАК are compiled-in but runtime-gated. The ЧЗ module blocks sales of marked goods when offline. The ЕГАИС module is synchronous — no fire-and-forget. |

---

## 2. Key Findings

### 2.1 From Stack Research

**Finding 1 — Lock the Kotlin/Compose compiler versions for 2026 H1.**
Kotlin `2.0.x` is released but the Compose compiler plugin rewrite is still maturing. The ecosystem needs 3+ months to harden. Lock to Kotlin `1.9.22`, KSP `1.9.22-1.0.17`, and Compose Compiler `1.5.8` until Compose BOM `2025.01.00+` (Compose 2.0 stable) ships.

**Finding 2 — SQLCipher is non-negotiable for fiscal data at rest.**
Room's SQLite under the hood is not encrypted by default. For a fiscal app, encrypted storage is a regulatory requirement (SEC-01). SQLCipher 4.5.4 + Room `SupportFactory` + key storage in Android Keystore is the validated path.

**Finding 3 — WorkManager is the only reliable background scheduler.**
`Handler`, `AlarmManager`, and `ScheduledExecutorService` all fail under Doze mode. For a fiscal app that must sync checks even overnight, WorkManager with `PeriodicWorkRequest` (30-second minimum) and `OneTimeWorkRequest` on network restoration is the only acceptable choice.

**Finding 4 — The backend is already chosen: Spring Boot 3.2.2 + PostgreSQL 16 + Redis Streams.**
No re-evaluation needed. For 200+ cash registers, Redis Streams provides ordered, persistent queuing for check uploads with at-least-once delivery guarantees. REST is the correct API shape — GraphQL adds complexity without benefit for CRUD + sync.

**Finding 5 — ML Kit outperforms ZXing on DataMatrix (GS1) codes.**
For Честный ЗНАК validation, scanning accuracy of rotated, low-light, and partial DataMatrix codes matters. ML Kit's on-device ML model significantly outperforms ZXing. Use CameraX for camera abstraction across Android 6.0+ (API 23 minimum).

### 2.2 From Feature Research

**Finding 6 — The critical path is FiscalCore → Room → WorkManager → Backend API.**
This chain is sequential and non-negotiable: fiscal operations require Room (offline persistence), Room feeds WorkManager (sync), WorkManager calls Backend API (cloud). Disrupting this chain at any point breaks the offline-first guarantee.

**Finding 7 — ЕГАИС and Честный ЗНАК are high-risk, high-complexity modules.**
Both are 🔴 (high complexity) with external regulatory dependencies. They must be built as feature-flagged modules (compiled in, gated at runtime) with no offline path for mandatory-marked goods sales.

**Finding 8 — Table stakes are all low-to-medium complexity and achievable in Phases 1–3.**
The MVP covers all 54-ФЗ baseline requirements: fiscal operations, KKT integration, offline autonomy, authentication, licensing, and status monitoring.

**Finding 9 — Cloud sync is the primary differentiator.**
Two-way REST sync with incremental sync by timestamp, 30-second periodic pull, and immediate pull on network restoration.

**Finding 10 — Anti-features are clearly bounded and must stay off the roadmap.**
No customer-facing mobile app, no payment terminal integration, no desktop versions, no FFD 1.0, no manual fiscal entry without ККТ.

### 2.3 From Architecture Research

**Finding 11 — The five-layer architecture with strict boundaries is the project backbone.**

| Layer | What it does | What it cannot touch |
|---|---|---|
| UI | Screens, ViewModels, Compose State | DAOs, FiscalCore, Retrofit directly |
| Domain | Use Cases, Models | UI, Room, API client |
| Data | Room DAOs, Retrofit API | FiscalCore, Screens |
| Fiscal Core | ККТ adapters, FFD document builder | Room, UI |
| Sync | WorkManager, SyncManager | FiscalCore |

**Finding 12 — Product sync is pull-based; check sync is push-only.**
Products are pulled incrementally. Checks are pushed to the backend. Reports aggregate from local Room.

**Finding 13 — FFD version is the most sensitive architectural decision.**
`FiscalDocumentBuilder` must be version-aware from day 1. FFD 1.05 and 1.2 have different TLV tag sets. The FFD version is immutable once a fiscal document is recorded.

**Finding 14 — Feature flags are structural, not cosmetic.**
`FeatureManager` is the mechanism that allows ЕГАИС and Честный ЗНАК modules to be compiled in, shipped in the same APK, and activated remotely.

**Finding 15 — Use Cases are the natural transaction boundary.**
`ProcessSaleUseCase` orchestrates the full sale: build `FiscalCheck` → persist to Room → call `FiscalCore` → update sync status.

### 2.4 From Pitfalls Research

**Finding 16 — ФН lock-in is the highest-severity operational risk.**
Switching FFD version after the first fiscal document is recorded permanently bricks the ФН. `FfdVersionResolver` must enforce immutability as a hard invariant.

**Finding 17 — Two sync failure modes are operationally critical.**
(1) **Unbounded PENDING_SYNC queue**: cap at 500 documents, implement retry budgets. (2) **`deletedIds` not applied**: must be applied before inserts.

**Finding 18 — Shift state machine violations are silent but catastrophic.**
`ShiftStateMachine` must enforce transitions atomically and treat ККТ as source of truth on mismatch.

**Finding 19 — ЕГАИС and Честный ЗНАК have a synchronous requirement.**
Both regulatory integrations require synchronous validation before the fiscal receipt prints. Fire-and-forget is a regulatory violation.

**Finding 20 — Fiscal adapter error handling must be granular.**
A `FiscalError` sealed class with `Recoverable` and `NonRecoverable` subtypes is required.

---

## 3. Stack Recommendations

### 3.1 Android (Confirmed — Already in Project)

| Component | Library | Version | Rationale |
|---|---|---|---|
| Language | Kotlin | `1.9.22` | Stable LTS. Kotlin 2.0 pending ecosystem hardening. |
| Build | AGP | `8.2.2` (→ `8.5.0`) | Well-tested. |
| Build | KSP | `1.9.22-1.0.17` | Must match Kotlin version exactly. |
| DI | Hilt | `2.50` | Compile-time validation — non-negotiable for 200+ device fleet. |
| UI | Compose BOM | `2024.01.00` | Stable baseline. |
| UI | Compose Compiler | `1.5.8` | Matches Kotlin 1.9.22. |
| Navigation | Navigation Compose | `2.7.6` | Stable, type-safe routes. |
| Local DB | Room | `2.6.1` | Standard Android ORM. KSP-based. |
| Networking | Retrofit + OkHttp | `2.9.0` + `4.12.0` | De-facto Android REST standard. |
| Async | Coroutines | `1.7.3` | Standard for all I/O and UI state. |
| Background | WorkManager | `2.9.0` | Only reliable scheduler surviving Doze and reboots. |
| Barcode | CameraX + ML Kit | `1.3.4` + `17.3.0` | ML Kit outperforms ZXing on DataMatrix. |
| Security | security-crypto | `1.1.0-alpha06` | For `EncryptedSharedPreferences`. |
| Backend | Spring Boot | `3.2.2` | Already in project. |

### 3.2 Android (Add to Project)

| Component | Library | Priority | Action |
|---|---|---|---|
| DB Encryption | `net.zetetic:android-database-sqlcipher:4.5.4` | **High** | Configure `SupportFactory` in Room builder with Keystore-backed key. |
| Preferences | `androidx.datastore:datastore-preferences:1.0.0` | **Medium** | Migrate grace period counter, sync timestamps. |
| Distribution | Firebase App Distribution | **Medium** | Add `google-services.json`, configure `appDistribution`. |
| Dependabot | Gradle ecosystem config | **Medium** | Add `package-ecosystem: "gradle"`. |

### 3.3 What Not to Use and Why

| Technology | Why Avoid |
|---|---|
| **Realm** | Larger APK (~10 MB), runtime DI model. |
| **Koin** | Runtime-only DI — bugs surface at runtime on 200 devices. |
| **GraphQL** | Overkill for CRUD + sync. REST covers 95% of use cases. |
| **ZXing** | Less accurate on DataMatrix. ML Kit outperforms. |
| **AlarmManager** | Does not survive Doze. WorkManager is the only reliable option. |
| **SharedPreferences** | No coroutines, no type safety, no migration API. |
| **Kotlin 2.0** (now) | Compose compiler 2.0 ecosystem not yet hardened. |

---

## 4. Feature Priorities

### 4.1 Table Stakes — Ship in Phases 1–3 (v1.0)

> Without these, the app does not meet 54-ФЗ baseline and the user goes to a competitor.

| ID | Feature | Complexity |
|---|---|---|
| TS-01 | Продажа товара (scan, manual, discounts, НДС) | 🟢 |
| TS-02 | Возврат товара (by receipt / QR, partial) | 🟢 |
| TS-03 | Чеки коррекции (приход / расход) | 🟢 |
| TS-04 | Открытие / закрытие смены | 🟢 |
| TS-05 | X-отчёт (промежуточный) | 🟢 |
| TS-06 | Z-отчёт (при закрытии смены) | 🟢 |
| TS-07 | Внесение / изъятие наличных | 🟢 |
| TS-08 | Поддержка ФФД 1.05 и 1.2 | 🟡 |
| TS-10 | Интеграция с MSPOS-K (SDK) | 🟡 |
| TS-11 | Интеграция с Нева 01Ф (SDK) | 🟡 |
| TS-12 | Автоопределение версии ФФД при старте | 🟢 |
| TS-13 | Формирование данных чеков по версии ФФД | 🟡 |
| TS-20 | Офлайн-продажи (без интернета) | 🟡 |
| TS-21 | Локальный справочник товаров | 🟢 |
| TS-22 | Буферизация синхронизации при отсутствии связи | 🟢 |
| TS-30 | PIN-код / пароль для входа | 🟢 |
| TS-31 | Ролевая модель (Админ / Старший кассир / Кассир) | 🟢 |
| TS-32 | Журнал действий (audit log) | 🟢 |
| TS-33 | Root detection (warn, don't hard-block) | 🟢 |
| TS-40 | Проверка статуса оплаты (старт + раз в сутки) | 🟢 |
| TS-41 | Блокировка при просрочке | 🟢 |
| TS-42 | Grace period 7 дней | 🟢 |
| TS-50 | Индикатор наличия интернета | 🟢 |
| TS-51 | Индикатор статуса облачного сервера | 🟢 |
| TS-52 | Индикатор статуса ОФД | 🟡 |
| TS-53 | Индикатор статуса тарифа | 🟢 |

### 4.2 Differentiators — Ship in Phases 4+ (v1.1+)

| ID | Feature | Complexity |
|---|---|---|
| D-01 | Двусторонняя синхронизация REST API | 🟡 |
| D-02 | Инкрементальная sync по timestamp | 🟡 |
| D-03 | Batch sync для 200+ касс | 🔴 |
| D-10 | Приёмка товара (документ → облако) | 🟡 |
| D-11 | Списание товара (порча, недостача) | 🟡 |
| D-12 | Инвентаризация (сверка остатков) | 🟡 |
| D-20 | Отчёты по продажам (смена / день / неделя / месяц) | 🟢 |
| D-30 | Модуль маркировки (Честный ЗНАК) | 🔴 |
| D-40 | Интеграция с УТМ (ЕГАИС) | 🔴 |
| D-41 | Проверка возраста через Цифровой ID Max | 🔴 |
| D-50 | Шифрование локального хранилища (SQLCipher) | 🟢 |
| D-51 | Удалённое обновление приложения | 🟡 |

---

## 5. Architecture Decisions

### 5.1 Critical Boundaries

```
UI Layer (Screens + ViewModels)
  → Calls only: Use Cases + FeatureManager
  → Never calls: DAOs, FiscalCore, Retrofit directly

Domain Layer (Use Cases + Models)
  → Defines: Repository interfaces, FiscalCore interface
  → Never calls: DAOs, Retrofit, WorkManager

Data Layer (Room DAOs + Retrofit API + Repositories)
  → Implements: Repository interfaces
  → Never calls: FiscalCore, UI

Fiscal Core Layer (FiscalCore + Adapters + FFD Builder)
  → Completely isolated from data layer
  → Adapters: MSPOSKFiscalCore, Neva01FFiscalCore (delegation)

Sync Layer (SyncManager + WorkManager Workers)
  → Calls: Room + Retrofit + DataStore
  → Never calls: FiscalCore
```

### 5.2 Data Flow: Sale Transaction

```
[1] Barcode scanned / product searched
        │
[2] ScanBarcodeUseCase → ProductDao.findByBarcode()
        │
[3] Cart updated in SalesViewModel state
        │
[4] "Продажа" pressed
        │
[5] ProcessSaleUseCase.execute(cart, cashierId, ...)
        ├──► FiscalCheck built → FiscalDocumentBuilder.buildSale()
        ├──► LocalCheck → CheckDao.insert() [status: PENDING_SYNC]
        └──► FiscalOperationOrchestrator.executeSale()
        │            │
        │            ▼
        │     FiscalCore.printSale() → ККТ
        │
        └──► On success → CheckDao.updateSyncStatus(SYNCED)
            On failure  → CheckDao.updateSyncStatus(FISCAL_ERROR)
        │
[6] SaleResult returned to ViewModel
        │
[7] SyncManager observes PENDING_SYNC checks
        │
[8] SyncUpWorker → API POST /api/v1/checks/sync
```

### 5.3 Check Status State Machine

```
PENDING_SYNC
     ├──► [SyncUpWorker success] ──► SYNCED
     ├──► [SyncUpWorker 4xx/5xx] ──► ERROR (retry: max 3)
     └──► [FiscalCore print failure] ──► FISCAL_ERROR
```

---

## 6. Risk Mitigation

### 6.1 Top 3 Pitfalls to Prevent

#### Pitfall 1 — ФН Lock-In After First Fiscal Document
**What:** Switching FFD version after ФН has recorded any fiscal document permanently bricks the ФН.
**Prevention:** `FfdVersionResolver` throws `IllegalStateException` when `fiscalStatus.currentFdNumber > 0`.

#### Pitfall 2 — Unbounded Sync Queue
**What:** `PENDING_SYNC` queue grows without limit, causing memory pressure and inability to generate accurate reports.
**Prevention:** Cap at 500 documents. Retry budget: max 3 per document. Surface queue depth in status bar.

#### Pitfall 3 — Offline Sale of Mandatory-Marked Goods
**What:** Selling marked goods without online validation transmits invalid data to ОФД.
**Prevention:** When ЧЗ module enabled, block marked-goods sales when validation API unavailable.

### 6.2 Additional Critical Risks

| Risk | Mitigation |
|---|---|
| ФН resource exhaustion | Monitor `documentsRemaining` at every shift open. Warn at ≤5,000 docs. |
| License grace period miscalculation | Store both `expiresAt` and `lastSuccessfulCheck`. Grace = `now > expiresAt + 7 days`. |
| Shift state machine violations | `ShiftState` enum with enforced transitions. ККТ is source of truth. |
| ЕГАИС fire-and-forget | Fully synchronous: validate → wait → confirm → fiscal receipt. |

---

## 7. Implications for Roadmap

### 7.1 Phase Plan

```
Phase A: KKT + FFD (Fiscal Core)
  ├── FiscalCore interface + MSPOSKFiscalCore adapter
  ├── Neva01FFiscalCore adapter
  ├── FiscalDocumentBuilder (FFD 1.05 + 1.2, version-aware)
  ├── FfdVersionResolver (immutable after first FD)
  ├── ShiftStateMachine (atomic transitions)
  ├── FiscalOperationOrchestrator
  └── Shift operations: open, close, X/Z reports, cash in/out
  Gate: Unit tests + integration tests for all FFD scenarios

Phase B: Auth + Licensing + Security
  ├── PIN/password authentication + role-based access
  ├── License checker (grace period: expiresAt + 7 days)
  ├── Audit log
  ├── SQLCipher Room integration
  └── Root detection (soft warning)
  Gate: License expiry flow tested fully offline for 7 days

Phase C: Core Sync + Status Monitoring
  ├── SyncManager (push checks, pull products)
  ├── SyncUpWorker / SyncDownWorker
  ├── Queue depth cap (500 docs) + retry budgets
  ├── FeatureManager (runtime-gated flags)
  └── Status bar (internet, cloud, ОФД, license)
  Gate: 24-hour offline stress test

Phase D: Reports
  ├── Sales reports (shift / day / week / month / period)
  ├── Product and category breakdown
  └── Return reports
  Gate: Report totals match Room aggregation exactly

Phase E: Cloud Sync Completion
  ├── Redis Streams backend
  ├── Two-way REST sync
  ├── Batch sync for 200+ cash registers
  └── Product inventory sync
  Gate: 200 concurrent cash registers sync simultaneously

Phase F: Optional Modules (ЕГАИС + Честный ЗНАК)
  ├── Честный ЗНАК module (feature-flagged)
  └── ЕГАИС module (feature-flagged)
  Gate: Integration tests against sandbox ОФД + ЧЗ test environment
```

### 7.2 Critical Dependencies

```
FiscalCore ── Phase A output ──► Phase B, C, D (all depend on fiscal core)
Room DB ────── Phase 1 ─────────► Phase A, C, D (data persistence)
FeatureManager ─ Phase 1 ────────► Phase F (runtime gate)
WorkManager ─── Phase 1 ────────► Phase C (background sync)
```

---

## 8. Research Gaps Remaining

| # | Gap | Priority |
|---|---|---|
| GAP-01 | ФН replacement flow (FnRegistration lifecycle) | High |
| GAP-02 | Backend ОФД proxy integration details | Medium |
| GAP-03 | Честный ЗНАК ЛМ on-device vs. backend proxy | Medium |
| GAP-04 | Mutual TLS certificate management | Medium |
| GAP-05 | Token rotation/revocation lifecycle | Low |

---

*Research synthesized from: `.planning/research/STACK.md`, `.planning/research/FEATURES.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md`*  
*Generated: 2026-06-20*
