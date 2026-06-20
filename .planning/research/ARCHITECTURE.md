# Architecture Research — Android Kassovoye Prilozheniye (POS)

**Версия:** 1.0  
**Дата:** 2026-06-20  
**Проект:** VITBON Мобильная Касса  
**Статус:** Завершено

---

## 1. Executive Summary

Android Kassovoye Prilozheniye (КА) systems follow a layered architecture optimized for **offline-first fiscal operations** with **cloud synchronization**. The architecture must balance three competing concerns:

1. **Fiscal integrity** — чеки must persist locally and sync eventually
2. **Regulatory compliance** — 54-ФЗ, ФФД 1.05/1.2, Честный ЗНАК, ЕГАИС
3. **Hardware abstraction** — MSPOS-K, Нева 01Ф, and future ККТ variants

The VITBON system implements a **Clean Architecture** variant with five distinct layers and two orthogonal module systems (features + fiscal core adapters).

---

## 2. Layer Architecture

### 2.1 Layer Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 1: UI (Presentation)                                                │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌───────────────────────┐ │
│  │ SalesScreen │ │ ShiftScreen │ │ReportsScreen│ │ StatusBar / LicenseBar│ │
│  └──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └───────────┬───────────┘ │
│         │                │                │                     │             │
│         ▼                ▼                ▼                     ▼             │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │  ViewModels (Compose State Management)                                   │ │
│  │  SalesViewModel, ShiftViewModel, ReportsViewModel, StatusViewModel        │ │
│  └──────────────────────────────────┬────────────────────────────────────────┘ │
└────────────────────────────────────┼──────────────────────────────────────────┘
                                     │ Use Case Calls
┌────────────────────────────────────┼──────────────────────────────────────────┐
│  LAYER 2: Business Logic (Domain)  │                                          │
│  ┌─────────────────────────────────┴─────────────────────────────────────────┐ │
│  │  Use Cases: ProcessSaleUseCase, ScanBarcodeUseCase, ShiftUseCase,         │ │
│  │             ReportsUseCase, LicenseChecker, RolePolicy                    │ │
│  │  Models: Cart, FiscalCheck, CheckItem, PaymentLine, SaleResult           │ │
│  └──────────────────────────────────┬────────────────────────────────────────┘ │
│                                     │ Repository Interfaces                   │
└────────────────────────────────────┼──────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼──────────────────────────────────────────┐
│  LAYER 3: Data Access              │                                          │
│  ┌──────────────┐                  │  ┌──────────────┐                        │
│  │ Local (Room) │◄─────────────────┼─►│ Remote (API) │                        │
│  │ VitbonDatabase│                 │  │ VitbonApi    │                        │
│  │  - CheckDao  │                 │  │ (Retrofit)   │                        │
│  │  - ProductDao│                 │  │              │                        │
│  │  - ShiftDao  │                 │  │              │                        │
│  └──────────────┘                  │  └──────────────┘                        │
└────────────────────────────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼──────────────────────────────────────────┐
│  LAYER 4: Fiscal Core (Adapter)    │                                          │
│  ┌─────────────────────────────────┴─────────────────────────────────────────┐ │
│  │  FiscalCore Interface (abstracts ККТ vendor SDK)                          │ │
│  │                    │                                                      │ │
│  │     ┌──────────────┴──────────────┐                                      │ │
│  │     │                              │                                      │ │
│  │     ▼                              ▼                                      │ │
│  │  MSPOSKFiscalCore           Neva01FFiscalCore                            │ │
│  │  (MSPOS-K SDK wrapper)       (Нева 01Ф SDK wrapper)                       │ │
│  └──────────────────────────────────┬────────────────────────────────────────┘ │
│                                     │                                         │
│  ┌──────────────────────────────────┼────────────────────────────────────────┐ │
│  │  FiscalDocumentBuilder (FFD-aware TLV tag generator)                      │ │
│  │  FFDVersionResolver → FiscalOperationOrchestrator                         │ │
│  └──────────────────────────────────┴────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼──────────────────────────────────────────┐
│  LAYER 5: Sync & Infrastructure    │                                          │
│  ┌─────────────────────────────────┴─────────────────────────────────────────┐ │
│  │  SyncManager ──► WorkManager (SyncUpWorker / SyncDownWorker)              │ │
│  │  LocalAuditBufferRepository ──► API sync                                  │ │
│  │  SyncPrefs ──► timestamps, retry state                                    │ │
│  │  SyncMonitor ──► network state, triggers on connectivity change          │ │
│  └───────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Component Boundaries

| Layer | Contains | Talks To | Doesn't Talk To |
|-------|----------|----------|----------------|
| **UI** | Screens, ViewModels | Use Cases | DAOs, FiscalCore, API |
| **Domain** | Use Cases, Models | Repository Interfaces, FiscalCore | UI, DAOs, API Client |
| **Data** | Room DAOs, Retrofit API | Repository Interfaces (implements) | Screens, FiscalCore directly |
| **Fiscal Core** | FiscalCore, Adapters, FFD Builder | Business Logic (FiscalCheck) | UI, Room |
| **Sync** | SyncManager, Workers | API, Room, Network state | FiscalCore |

**Key boundary rule:** The UI layer never calls Room DAOs or FiscalCore directly. All data access goes through Use Cases → Repository interfaces → Repository implementations.

### 2.3 Data Flow

#### Primary Path: Sale Transaction

```
[1] Cashier scans barcode / searches product
    │
    ▼
[2] SalesScreen → SalesViewModel.onBarcodeScanned()
    │
    ▼
[3] ScanBarcodeUseCase.execute(barcode)
    │   └── ProductDao.findByBarcode()
    │   └── returns: Product?
    │
    ▼
[4] Cart updated in SalesViewModel state
    │
    ▼
[5] Cashier presses "Продажа"
    │
    ▼
[6] ProcessSaleUseCase.execute(cart, cashierId, ...)
    │
    ├─► [A] FiscalCheck built from Cart
    │       └── FiscalDocumentBuilder.buildSale()
    │
    ├─► [B] LocalCheck + LocalCheckItems → CheckDao.insert()
    │       └── Status: PENDING_SYNC (optimistic)
    │
    ├─► [C] FiscalOperationOrchestrator.executeSale(fiscalCheck)
    │       └── FiscalCore.printSale() → ККТ hardware
    │
    ├─► [D] On success: CheckDao.updateSyncStatus(SYNCED)
    │       On failure: CheckDao.updateSyncStatus(FISCAL_ERROR)
    │
    ▼
[7] SaleResult returned to ViewModel
    │
    ▼
[8] SyncManager observes PENDING_SYNC checks
    │
    ▼
[9] SyncUpWorker triggered (network available)
    │
    ▼
[10] API POST /api/v1/checks/sync
     │   └── CheckDto with items array
     │
     ▼
[11] On 200: CheckDao.markSynced(syncedAt)
     On 4xx/5xx: CheckDao.updateSyncStatus(ERROR)
```

#### Sync Flow (Pull)

```
[1] SyncDownWorker triggers (periodic or on network restore)
    │
    ▼
[2] API GET /api/v1/products?since={lastSyncTimestamp}
    │
    ▼
[3] ProductDao.insertAll() — upsert new/updated
    │
    ▼
[4] ProductDao.deleteByIds() — remove deleted
    │
    ▼
[5] SyncPrefs.lastProductSyncTimestamp updated
```

### 2.4 Offline Queue Behavior

```
┌─────────────────────────────────────────────────────────┐
│  OFFLINE MODE                                          │
│                                                         │
│  Sale executed → PENDING_SYNC → stored in Room          │
│       │                                                 │
│       ├── Network detected ──► SyncUpWorker runs        │
│       │                              │                  │
│       │                              ▼                  │
│       │                         API POST succeeds       │
│       │                              │                  │
│       │                              ▼                  │
│       │                    CheckDao.markSynced()        │
│       │                    Status → SYNCED              │
│       │                                                 │
│       └── Network absent ──► check stays PENDING_SYNC   │
│                                SyncUpWorker RETRYs      │
│                                (exponential backoff)    │
└─────────────────────────────────────────────────────────┘
```

**Conflict resolution:** Server wins by `timestamp`. If server rejects a check (`4xx`), status → `ERROR` and check remains in local DB for manual review.

---

## 3. Fiscal Core Architecture

### 3.1 FiscalCore Interface

```kotlin
interface FiscalCore {
    suspend fun openShift(): FiscalResult
    suspend fun printSale(check: FiscalCheck, cashierName: String, cashierInn: String?): FiscalResult
    suspend fun printReturn(check: FiscalCheck, cashierName: String, cashierInn: String?): FiscalResult
    suspend fun printCorrection(doc: CorrectionDoc, cashierName: String, cashierInn: String?): FiscalResult
    suspend fun closeShift(): FiscalResult
    suspend fun printXReport(): FiscalResult
    suspend fun cashIn(amount: Money, comment: String?): FiscalResult
    suspend fun cashOut(amount: Money, comment: String?): FiscalResult
    suspend fun getStatus(): FiscalStatus
    suspend fun getFFDVersion(): FFDVersion
    suspend fun initialize(): Boolean
    suspend fun shutdown()
}
```

### 3.2 Adapter Pattern for ККТ Vendors

```
FiscalCoreProvider
    │
    ├── Device detection (Build.MODEL / DeviceInfo)
    │
    ▼
┌───────────────────────────────────────────┐
│  FiscalCore (interface)                  │
└─────────────────┬─────────────────────────┘
                  │
     ┌────────────┴────────────┐
     │                         │
     ▼                         ▼
MSPOSKFiscalCore      Neva01FFiscalCore
(SKK SDK wrapper)     (Нева SDK wrapper)
```

**Benefit:** New ККТ models only require new adapter implementations — no changes to use cases, UI, or sync logic.

### 3.3 FFD Version Handling

```
FfdVersionResolver
    │
    ├── On app start ──► FiscalCore.getFFDVersion() ──► "1.05" or "1.2"
    │
    ├── FfdPolicyStore ──► cached version per session
    │
    └── FiscalDocumentBuilder(version)
            │
            ├── FFD 1.05: tags 1000-1080, 1174, 1214-1216
            │
            └── FFD 1.2:  + 1125, 1187, 1008, 1234-1238, 1162-1163
```

---

## 4. Feature Module Architecture

### 4.1 Module Structure

```
android/app/src/main/java/com/vitbon/kkm/
├── features/
│   ├── sales/           # Sale flow (UI, ViewModel, Use Cases)
│   ├── returns/         # Return by receipt / QR
│   ├── correction/      # Correction checks
│   ├── shift/           # Open/close shift, X/Z reports
│   ├── cashdrawer/      # Cash in/out
│   ├── products/        # Product catalog
│   ├── acceptance/      # Goods acceptance
│   ├── writeoff/        # Stock writeoff
│   ├── inventory/       # Inventory reconciliation
│   ├── reports/         # Sales, movement, returns reports
│   ├── statuses/        # System status monitoring
│   ├── licensing/       # License checking, grace period
│   ├── auth/            # PIN/password authentication
│   ├── egais/           # ЕГАИС module (feature-flagged)
│   ├── chaseznak/       # Честный ЗНАК module (feature-flagged)
│   └── rootdetection/   # Root detection
├── core/
│   ├── fiscal/          # FiscalCore + adapters + FFD
│   ├── sync/            # SyncManager + Workers + Audit buffer
│   └── features/        # Feature flag system
├── data/
│   ├── local/          # Room database + DAOs + entities
│   ├── remote/         # Retrofit API client + DTOs
│   └── security/       # Keystore, SQLCipher, SecurePrefs
└── di/                  # Hilt dependency injection modules
```

### 4.2 Feature Flag System

```
FeatureManager
    │
    ├── FeatureFlag enum: EGAAIS, CHASEZNAK, REPORTS, ...
    │
    ├── isEnabled(flag) ──► Check local cache + remote config
    │
    └── FeatureGuard(flag) ──► Annotation for conditional UI/routes
```

**Module activation flow:**
1. Backend sends `featureFlags` in config/sync response
2. `FeatureManager` caches flags in `SyncPrefs`
3. UI routes check `FeatureGuard` before showing ЕГАИС/ЧЗ menus
4. Feature-flagged code paths are compiled in but gated at runtime

---

## 5. Database Schema (Room)

### 5.1 Core Tables

| Table | Purpose | Sync Strategy |
|-------|---------|---------------|
| `checks` | Fiscal check records | Push to backend, mark `SYNCED` |
| `check_items` | Line items per check | Embedded in check push |
| `products` | Product catalog | Pull from backend (incremental) |
| `shifts` | Open/closed shift state | Local only (reference) |
| `cashiers` | Cashier credentials | Pull from backend |
| `categories` | Product categories | Pull from backend |
| `audit_log` | Action audit trail | Push to backend (batched) |
| `inventory_documents` | Acceptance/writeoff/inventory | Push to backend |
| `stock_movements` | Movement ledger | Derived/calculated |

### 5.2 Check Status State Machine

```
PENDING_SYNC
    │
    ├──► [SyncUpWorker success] ──► SYNCED
    │
    ├──► [SyncUpWorker 4xx] ──────► ERROR
    │
    └──► [Fiscal print fail] ──────► FISCAL_ERROR
```

---

## 6. Build Order & Dependencies

### 6.1 Dependency Graph

```
Phase 1 (Architecture)
│
├── Domain Layer ──────────────────────────────┐
│   ├── Entities (FiscalCheck, CheckItem, etc) │
│   ├── Repository Interfaces                  │
│   └── Use Case Interfaces                    │
│                                              │
├── Data Layer ◄───────────────────────────────┤
│   ├── Room Database + DAOs                  │
│   ├── Retrofit API client                   │
│   └── Repository Implementations            │
│                                              │
├── Fiscal Core Layer ◄────────────────────────┤
│   ├── FiscalCore interface                  │
│   ├── MSPOSKFiscalCore adapter              │
│   ├── Neva01FFiscalCore adapter             │
│   └── FiscalDocumentBuilder (FFD-aware)      │
│                                              │
├── Sync Layer ◄───────────────────────────────┤
│   ├── SyncManager                           │
│   ├── SyncUpWorker / SyncDownWorker         │
│   └── SyncPrefs                             │
│                                              │
└── Feature Flags Layer ◄─────────────────────┤
    ├── FeatureManager                        │
    └── FeatureGuard                          │


Phase 2 (Core Fiscal Operations)
│
├── Shift Management ◄─────────────────────────┤
│   └── ShiftUseCase + ShiftDao               │
│                                              │
└── Sales Flow ◄──────────────────────────────┤
    ├── ProcessSaleUseCase                    │
    ├── FiscalOperationOrchestrator           │
    └── SalesViewModel                        │


Phase 3 (UI & Remaining Features)
│
├── Auth Feature ◄────────────────────────────┤
├── Reports Feature ◄─────────────────────────┤
├── Status Monitoring ◄───────────────────────┤
├── ЕГАИС Module ◄────────────────────────────┤
└── Честный ЗНАК Module ◄─────────────────────┤
```

### 6.2 Module Dependency Rules

1. **Feature modules** depend on `core/domain` and `core/fiscal` (never the reverse)
2. **Sync layer** depends on `data/local` and `data/remote` (never on fiscal core)
3. **Fiscal core** is isolated — it doesn't know about Room or Retrofit
4. **UI** depends only on `features/*/presentation` and `core/features`

### 6.3 Suggested Build Order

| # | Build Artifact | Dependencies | Rationale |
|---|---------------|--------------|----------|
| 1 | `domain/` models | None | Pure Kotlin, no Android deps |
| 2 | `core/fiscal/` | domain | Fiscal models are domain-owned |
| 3 | `data/local/` (Room) | domain | DAOs implement repository interfaces |
| 4 | `data/remote/` (Retrofit) | domain | API DTOs map to domain |
| 5 | `core/sync/` | data/local, data/remote | Workers use Room + API |
| 6 | `core/features/` | none | Feature flags are pure logic |
| 7 | `features/auth/` | domain, data/local | Auth needs cashier entities |
| 8 | `features/sales/` | domain, fiscal core, data/local | Sales → fiscal → persistence |
| 9 | `features/shift/` | domain, fiscal core | Shift management |
| 10 | `features/reports/` | domain, data/local | Reports aggregate from Room |
| 11 | `features/statuses/` | core/sync, data/remote | Status reads sync state + API |
| 12 | `features/egais/` | domain, feature flags | ЕГАИС gated by feature flag |
| 13 | `features/chaseznak/` | domain, feature flags | ЧЗ gated by feature flag |

---

## 7. Key Architectural Patterns

### 7.1 Repository Pattern

```kotlin
// Domain layer defines interface
interface CheckRepository {
    suspend fun save(localCheck: LocalCheck)
    suspend fun findById(id: String): LocalCheck?
    suspend fun findPendingSync(): List<LocalCheck>
    suspend fun markSynced(id: String, syncedAt: Long)
}

// Data layer implements
class CheckRepositoryImpl @Inject constructor(
    private val checkDao: CheckDao
) : CheckRepository { ... }
```

### 7.2 Use Case Pattern

```kotlin
// Single responsibility, one public method
class ProcessSaleUseCase @Inject constructor(
    private val fiscalOrchestrator: FiscalOperationOrchestrator,
    private val checkDao: CheckDao,
    private val checkItemDao: CheckItemDao
) {
    suspend fun execute(cart: Cart, ...): SaleResult { ... }
}
```

### 7.3 State Management (ViewModel + Compose)

```
@Composable
fun SalesScreen(viewModel: SalesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    // ...
}
```

### 7.4 WorkManager for Background Sync

```kotlin
// Deterministic, battery-efficient background work
class SyncUpWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val result = syncManager.syncChecks()
        return if (result.failed > 0) Result.retry() else Result.success()
    }
}
```

---

## 8. Security Architecture

### 8.1 Storage Security

| Asset | Protection | Mechanism |
|-------|-----------|-----------|
| Cashier credentials | Encrypted at rest | SQLCipher (Room) |
| Auth tokens | Hardware-backed | Android Keystore / KeyMaster |
| Sync timestamps | Unencrypted | SyncPrefs (shared) |
| Product catalog | Unencrypted | Room (non-sensitive) |
| Fiscal data | Encrypted in transit | TLS 1.3 + mTLS |

### 8.2 Root Detection

```
RootDetector
    │
    ├── SystemRootChecker ──► su binary, test-keys, root management apps
    │
    ├── RootPolicyEnforcer ──► blocks launch OR shows warning
    │
    └── RootRiskGuard ──────► FeatureGuard for sensitive operations
```

---

## 9. External Integrations

### 9.1 Integration Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│  VITBON Android App                                                      │
│                                                                         │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────────┐   │
│  │ FiscalCore   │   │ SyncManager  │   │ FeatureManager           │   │
│  │ (KKT SDK)    │   │ (WorkManager)│   │ (Feature Flags)           │   │
│  └──────┬───────┘   └──────┬───────┘   └───────────┬──────────────┘   │
│         │                   │                       │                    │
└─────────┼───────────────────┼───────────────────────┼────────────────────┘
          │                   │                       │
          ▼                   ▼                       ▼
    ┌──────────┐      ┌──────────────┐        ┌──────────┐
    │ MSPOS-K  │      │ REST API     │        │ Backend  │
    │ Нева 01Ф │      │ (VitbonApi)  │        │ Config   │
    └──────────┘      └──────┬───────┘        └──────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
   ┌─────────┐         ┌───────────┐         ┌──────────┐
   │ ОФД     │         │ Cloud     │         │ License   │
   │ Server  │         │ Backend   │         │ Server   │
   └─────────┘         │ ( товар ) │         └──────────┘
                        └───────────┘
                              │
                        ┌─────┴─────┐
                        ▼           ▼
                  ┌──────────┐ ┌──────────┐
                  │ ЕГАИС    │ │ Честный  │
                  │ (УТМ)    │ │ ЗНАК (ЛМ)│
                  └──────────┘ └──────────┘
```

### 9.2 External API Contracts

| System | Protocol | Purpose |
|--------|----------|---------|
| Cloud Backend | REST/HTTPS | Check sync, product catalog, reports |
| ОФД | ККТ SDK | Fiscal document transmission |
| License Server | REST/HTTPS | Tariff status check |
| ЕГАИС (УТМ) | Local HTTP | Alcohol inventory/age verification |
| Честный ЗНАК (ЛМ) | Local HTTP | Marked goods validation |

---

## 10. Quality Gate Compliance

- [x] **Components clearly defined with boundaries** — Five distinct layers with explicit talk-to rules documented in Section 2.2
- [x] **Data flow direction explicit** — Sale flow documented step-by-step in Section 2.3 with offline queue behavior
- [x] **Build order implications noted** — Dependency graph in Section 6 with numbered build order table

---

## 11. Key Findings Summary

| Topic | Finding | Implication for Roadmap |
|-------|---------|------------------------|
| **Fiscal core isolation** | FiscalCore is completely isolated from data layer | Phase 2 (fiscal core) can be built and tested independently |
| **Sync is pull-based for products** | Products pulled incrementally with timestamp | Phase 1 must include SyncPrefs for timestamp tracking |
| **Checks are push-only** | No pull for checks; status is local | Reports must aggregate from local Room, not API |
| **FFD version affects document structure** | Different TLV tag sets per version | FiscalDocumentBuilder must be version-aware from day 1 |
| **Feature flags are runtime-gated** | ЕГАИС/ЧЗ compiled in but disabled | Phase 1 must include FeatureManager skeleton |
| **Offline-first is structural** | Every sale writes to Room before fiscal core | Sync layer depends on data layer, not the reverse |
| **Use cases orchestrate transactions** | ProcessSaleUseCase handles fiscal + persistence + sync status | Use cases are the natural integration boundary |

---

## 12. References

- **Project.md**: `.planning/PROJECT.md` — Full requirements and constraints
- **Design doc**: `docs/superpowers/specs/2026-04-08-vitbon-kkm-design.md` — System architecture design
- **Master plan**: `docs/superpowers/plans/vitbon-kkm-master-plan.md` — Phase breakdown
- **FiscalCore interface**: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/FiscalCore.kt`
- **SyncManager**: `android/app/src/main/java/com/vitbon/kkm/core/sync/SyncManager.kt`
- **ProcessSaleUseCase**: `android/app/src/main/java/com/vitbon/kkm/features/sales/domain/ProcessSaleUseCase.kt`
- **FiscalDocumentBuilder**: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt`
