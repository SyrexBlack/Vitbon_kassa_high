# Architecture Research — v1.1 Production Readiness

**Версия:** 1.1
**Дата:** 2026-06-21
**Проект:** VITBON Мобильная Касса
**Статус:** Завершено
**Потребители:** Engineering leads, SRE, QA, roadmap planners

---

## 1. Executive Summary

VITBON v1.0 MVP shipped 2026-06-21 with 52/52 requirements validated across 7 phases. v1.1 **Production Readiness** is a subsequent milestone focused on **non-blocking hardening, sandbox integration, and validation** — not new customer features. v1.1 closes the 5 deferred GAP items from v1.0 plus 3 external sandbox validation paths, and adds three new production-readiness capabilities (load testing, offline stress, runtime environment separation).

The v1.0 5-layer architecture (UI → Domain → Data → Fiscal Core → Sync) **remains the backbone**. v1.1 does **not** restructure layers, introduce new abstraction layers, or change primary data flows. Instead, v1.1 adds **three orthogonal subsystems** that slot in alongside the existing architecture:

| Subsystem | Purpose | Where It Lives | v1.0 Impact |
|-----------|---------|----------------|-------------|
| **Environment & Configuration** | Sandbox/production/dev URL separation, BuildConfig, license-gated endpoints | `data/remote/config/` + `core/features/` | None — additive |
| **Security Lifecycle** | SQLCipher key rotation, mTLS cert hot-reload, token revocation propagation | `data/security/` extensions | Minor — SecurityManager interface added |
| **Operational Testing & Tooling** | 24h offline stress harness, 200-cass load test, ФН replacement simulator | `:testing` (new), `:tools` (new), CI integration | None — out-of-process |

**v1.1's cardinal rule:** no v1.0 path may regress. Every new component is either additive (new package) or surgical (extends existing component via interface). All hardening features must be **feature-flagged off in production builds** by default and only enabled for testing/sandbox via `BuildConfig.ENVIRONMENT`.

### 1.1 The Six v1.1 Features Mapped to Architecture

| # | v1.1 Feature | Architecture Touchpoints | New Components | Modified Components |
|---|--------------|--------------------------|----------------|---------------------|
| 1 | Sandbox integration endpoints | Data layer (`data/remote/config/`), BuildConfig | `EnvironmentConfig`, `SandboxEndpointProvider` | `VitbonApi` (URL builder), `NetworkModule` (DI) |
| 2 | Load test harness (200+ касс) | CI pipeline + `backend/` test module | `:loadtest` (Kotlin/JVM), `LoadTestOrchestrator` | `SyncUpWorker` (instrumented profile), Redis Streams test consumer |
| 3 | 24-hour offline stress test | `:testing` Android module, Room stress fixtures | `OfflineStressHarness`, `SyntheticCatalogSeeder`, `OfflineMetricsCollector` | `SyncPrefs` (test-only hook) |
| 4 | SQLCipher key rotation | Data security (`data/security/`) | `KeyRotationManager`, `RotationState`, `Reencryptor` | `EncryptedDatabaseFactory`, `KeyStoreManager` (interface split) |
| 5 | ФН replacement flow | Fiscal Core + Sync | `FnRegistrationRepository`, `FnReplacementStateMachine`, `InFlightOperationGuard` | `ShiftStateMachine`, `FiscalOperationOrchestrator`, `ProcessSaleUseCase` |
| 6 | Mutual TLS cert management | Data security + Auth | `CertVault`, `CertHotReloader`, `CertLifecycleManager` | `OkHttpClient` builder, `AuthTokenStore`, `NetworkModule` |
| 7 | Token rotation/revocation | Auth + Sync | `TokenRevocationRegistry`, `RevocationPushReceiver` | `AuthTokenStore`, `AuthRepository`, `SyncDownWorker` |

**Total:** ~14 new components, 9 modified existing components. No layer boundary changes.

### 1.2 Layer-Boundary Invariants (v1.0 → v1.1)

The v1.0 boundary rules in `ARCHITECTURE.md §2.2` are **preserved verbatim**. v1.1 does not grant new cross-layer access rights:

- **UI** still does not call DAOs, FiscalCore, or Retrofit directly.
- **Domain** still does not call DAOs, Retrofit, or WorkManager.
- **Data** still does not call FiscalCore or UI.
- **Fiscal Core** still does not call Room or UI.
- **Sync** still does not call FiscalCore.

**New constraint (v1.1):** `core/hardening/` (the new shared hardening module) may be depended on by all five layers because it provides **cross-cutting infrastructure** (environment config, security lifecycle). It never contains business logic.

---

## 2. v1.0 Baseline Recap

The v1.0 architecture is documented in `.planning/research/ARCHITECTURE.md`. Key facts v1.1 builds on:

- **5 layers** with strict talk-to rules.
- **5 repositories** in domain → implemented in data layer (CheckRepository, ProductRepository, ShiftRepository, DocumentRepository, AuditRepository).
- **FiscalCore** is the abstraction; two adapters: `MSPOSKFiscalCore`, `Neva01FFiscalCore`.
- **SyncManager** orchestrates push (SyncUpWorker) and pull (SyncDownWorker) via WorkManager.
- **FeatureManager** runtime-gates ЕГАИС and ЧЗ modules.
- **LicenseChecker** uses 7-day grace period from `expiresAt`.
- **SQLCipher 4.5.4** with key in Android Keystore.
- **Backend**: Spring Boot 3.2.2 with 9 controllers and Redis Streams.

The 5 deferred v1.0 GAP items (from `STATE.md` "Open Items") are **promoted to v1.1 requirements**:

- GAP-01 → ФН replacement flow (Feature #5)
- GAP-02 → Backend ОФД proxy integration (already in backend/, not a v1.1 architecture concern)
- GAP-03 → ЧЗ ЛМ approach (resolved: backend proxy — see §7)
- GAP-04 → Mutual TLS cert management (Feature #6)
- GAP-05 → Token rotation/revocation (Feature #7)

---

## 3. v1.1 Layer Architecture (Delta View)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  LAYER 1: UI (Presentation) — UNCHANGED                                    │
│  SalesScreen, ShiftScreen, ReportsScreen, StatusBar / LicenseBar           │
│  + FnReplacementScreen (new, Feature #5)                                   │
│  + CertificateStatusBanner (new, Feature #6)                                │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │ Use Case Calls
┌────────────────────────────────────┼────────────────────────────────────────┐
│  LAYER 2: Business Logic (Domain)  │                                        │
│  Existing use cases + new:        │                                        │
│    - FnRegistrationUseCase (new)  │                                        │
│    - RotateDbKeyUseCase (new)     │                                        │
│    - ReloadMtlsCertUseCase (new)  │                                        │
│    - ProcessRevocationUseCase (new)                                        │
│  + Repository Interfaces: FnRepository, CertificateRepository,            │
│                           RevocationRepository (new)                        │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼────────────────────────────────────────┐
│  LAYER 3: Data Access              │                                        │
│  Existing Room + Retrofit. New:    │                                        │
│    - EnvironmentConfig (BuildConfig-backed)                                │
│    - FnRegistrationDao (new)                                                │
│    - CertificateVaultDao (new)                                              │
│    - RevocationRegistryDao (new)                                            │
│  + Security: CertVault, KeyRotationManager (extends)                        │
│  + Repositories: FnRegistrationRepositoryImpl, CertificateRepositoryImpl,  │
│                  RevocationRepositoryImpl (new)                             │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼────────────────────────────────────────┐
│  LAYER 4: Fiscal Core (Adapter)    │                                        │
│  FiscalCore + adapters UNCHANGED.  │                                        │
│  + FnReplacementStateMachine (new)  — sits in domain, called from use case │
│  + InFlightOperationGuard (new)    — locks shift during ФН swap            │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼────────────────────────────────────────┐
│  LAYER 5: Sync & Infrastructure    │                                        │
│  SyncManager, Workers UNCHANGED.   │                                        │
│  + RevocationPushReceiver (new)    — subscribes to /sync/revocations        │
│  + CertHotReloader (new)           — polls /sync/cert-bundle                │
│  + EnvironmentRouter (new)         — chooses base URL per env               │
└────────────────────────────────────┴────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼────────────────────────────────────────┐
│  CROSS-CUTTING: core/hardening/    │  ← NEW MODULE (v1.1)                    │
│  - EnvironmentConfig (sandbox/prod)                                          │
│  - HardeningFlags (per-feature build-time + runtime gates)                  │
│  - BuildFlavorRegistry (sandbox vs prod flavor detection)                   │
│  - TestHooks (only available in debug/sandbox builds)                        │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
┌────────────────────────────────────┼────────────────────────────────────────┐
│  OUT-OF-PROCESS: testing/ + loadtest/  ← NEW (v1.1)                        │
│  :testing (Android lib)    — offline stress, ФН simulator                  │
│  :loadtest (JVM)           — 200-cass load harness                         │
│  :tools (CLI)              — cert generator, key rotation CLI              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Component Boundary Updates (v1.1 Delta)

| Layer | New in Layer | Talks To (new) | Doesn't Talk To (still) |
|-------|--------------|----------------|--------------------------|
| **UI** | `FnReplacementScreen`, `CertificateStatusBanner` | New use cases | FiscalCore, DAOs, Retrofit, CertVault directly |
| **Domain** | `FnRegistrationUseCase`, `RotateDbKeyUseCase`, `ReloadMtlsCertUseCase`, `ProcessRevocationUseCase` | New repository interfaces | DAOs, Retrofit, WorkManager, CertVault impl |
| **Data** | New DAOs, new repository impls, `EnvironmentConfig`, `CertVault`, `KeyRotationManager` | New repository interfaces | FiscalCore, UI, InFlightOperationGuard |
| **Fiscal Core** | (none — adapters unchanged) | n/a | Room, UI, network |
| **Sync** | `RevocationPushReceiver`, `CertHotReloader`, `EnvironmentRouter` | API (extended), Room (extended) | FiscalCore, InFlightOperationGuard |
| **Hardening (new)** | `EnvironmentConfig`, `HardeningFlags`, `BuildFlavorRegistry`, `TestHooks` | BuildConfig, SharedPrefs, LicenseChecker | Domain business logic |

**Boundary rule (new):** `core/hardening/` may **read** BuildConfig and SharedPrefs but must **never contain business rules**. It is pure infrastructure.

---

## 4. Feature #1 — Sandbox Integration Endpoints

### 4.1 Problem Statement

v1.0 uses a single base URL defined in `BuildConfig.API_BASE_URL`. To integrate with the ОФД sandbox, Честный ЗНАК test environment, УТМ test transport module, and Цифровой ID Max sandbox, the app must support **per-environment endpoint routing** with the following requirements:

- Production builds → production endpoints only.
- Sandbox builds (internal QA, dev) → can route to any combination of prod/sandbox per external service.
- The choice must be **runtime-overridable** for QA scenarios (a tester forces all endpoints to sandbox).
- Per-license gating: a "Sandbox Mode" feature flag must be licensed — a normal customer cannot accidentally enable sandbox.

### 4.2 Architecture Integration

```
┌───────────────────────────────────────────────────────────────┐
│  Build Time (Gradle)                                          │
│  buildFlavor = sandbox | production                           │
│  buildConfigField("String", "ENVIRONMENT", "\"SANDBOX\"")     │
│  buildConfigField("String", "OFD_BASE_URL", "\"https://sandbox-ofd...\"") │
│  buildConfigField("String", "CHASEZNAK_BASE_URL", ...)        │
│  buildConfigField("String", "EGAIS_UTM_URL", ...)             │
│  buildConfigField("String", "CID_MAX_URL", ...)               │
└──────────────────────┬────────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────────┐
│  core/hardening/EnvironmentConfig.kt (singleton)              │
│  - reads BuildConfig at startup                               │
│  - exposes EndpointResolver interface                          │
│  - has override channel: LicenseChecker.isSandboxAllowed()    │
└──────────────────────┬────────────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┬────────────────┐
        ▼              ▼              ▼                ▼
    ┌────────┐   ┌──────────┐   ┌──────────┐    ┌──────────┐
    │ Vitbon │   │OFD Proxy │   │Chaseznak │    │Egais UTM │
    │ API    │   │client    │   │client    │    │client    │
    └────────┘   └──────────┘   └──────────┘    └──────────┘
```

### 4.3 New Components

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `EnvironmentConfig` | `core/hardening/` | Reads `BuildConfig` at init, exposes typed `Environment` enum (PROD, SANDBOX, DEV) |
| `EndpointResolver` | `core/hardening/` | Interface: `resolveUrl(service: ExternalService): String` |
| `DefaultEndpointResolver` | `data/remote/config/` | Implementation: combines BuildConfig + per-service sandbox override map from `SyncPrefs` |
| `SandboxOverrideRepository` | `data/remote/` | Stores QA-applied sandbox overrides in encrypted DataStore |
| `BuildFlavorRegistry` | `core/hardening/` | Centralized accessor: `currentFlavor()`, `isSandboxBuild()`, `versionName` |

### 4.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `NetworkModule` (Hilt) | Now provides `OkHttpClient` with `EndpointResolver`-aware interceptor that rewrites base URL per request |
| `VitbonApi` (Retrofit) | URL construction goes through `EndpointResolver`; the `baseUrl` becomes dynamic |
| `OkHttpClient.Builder` | New `EnvironmentInterceptor` adds `X-VITBON-Env: SANDBOX` header on all requests |
| `LicenseChecker` | New method `isSandboxModeAllowed(): Boolean` — true only if license has `SANDBOX_OPT_IN` feature and `BuildConfig.ENVIRONMENT != PROD` |
| `FeatureManager` | New flag: `SANDBOX_MODE` (gated by `LicenseChecker.isSandboxModeAllowed()`) |

### 4.5 Data Flow

```
[1] App start
    │
    ▼
[2] BuildFlavorRegistry.init() → reads BuildConfig.ENVIRONMENT
    │
    ▼
[3] EnvironmentConfig resolves EndpointResolver
    │
    ├── [Production build] → DefaultEndpointResolver
    │       ├── All URLs from BuildConfig
    │       └── No overrides accepted
    │
    └── [Sandbox build] → SandboxEndpointResolver (decorator)
            ├── Default URLs from BuildConfig
            ├── Overrides from SandboxOverrideRepository (if set)
            └── Re-checked: LicenseChecker.isSandboxModeAllowed()
    │
    ▼
[4] NetworkModule provides OkHttpClient with EnvironmentInterceptor
    │
    ▼
[5] VitbonApi, OFDClient, ChaseznakClient, EgaisClient, CidMaxClient
        all inject EndpointResolver
    │
    ▼
[6] On request: interceptor rewrites URL, adds X-VITBON-Env header
```

### 4.6 Security Boundaries

- **Production builds cannot reach sandbox URLs:** `DefaultEndpointResolver` throws `SecurityException` if a sandbox URL is requested from a prod build.
- **Sandbox mode is license-gated:** Without `SANDBOX_OPT_IN` license feature, the `SandboxOverrideRepository` rejects all writes.
- **All sandbox traffic still uses mTLS:** The same `CertVault` and `OkHttpClient` is used — no security downgrade.
- **Sandbox build flag is baked at compile time:** A production APK literally cannot be set to sandbox at runtime.

### 4.7 Sandbox Endpoint Inventory

| External Service | Production URL Pattern | Sandbox URL Pattern | License Required |
|------------------|------------------------|---------------------|------------------|
| VITBON Cloud Backend | `https://api.vitbon.ru/v1/` | `https://sandbox-api.vitbon.ru/v1/` | SANDBOX_OPT_IN |
| ОФД (Fiscal Data) | per-customer OFD endpoint | `https://sandbox.ofd.ru/api/v1/` | SANDBOX_OPT_IN |
| Честный ЗНАК ЛМ | `https://markirovka.crpt.ru/api/v3/` | `https://markirovka.sandbox.crpt.ru/api/v3/` | SANDBOX_OPT_IN |
| УТМ (ЕГАИС) | customer on-prem | `https://sandbox-utm.vitbon.local:8080/` | SANDBOX_OPT_IN |
| Цифровой ID Max | `https://idmax.esphere.ru/api/v1/` | `https://sandbox-idmax.esphere.ru/api/v1/` | SANDBOX_OPT_IN |

---

## 5. Feature #2 — Load Test Harness (200+ Касс)

### 5.1 Problem Statement

v1.0 backend uses Redis Streams for ordered, persistent queuing of check uploads. Per `SUMMARY.md Finding 3`, "WorkManager is the only reliable background scheduler" and per `STACK.md Finding 4`, "For 200+ cash registers, Redis Streams provides ordered, persistent queuing with at-least-once delivery."

The v1.0 backend passed **functional** tests but not **load** tests. v1.1 must prove the system handles 200+ concurrent cash registers pushing checks simultaneously. The test must:

- Run in CI as a nightly job.
- Not be deployed to production.
- Use the **real backend** (not a mock) with **synthetic cash register clients**.
- Measure end-to-end latency, Redis Streams depth, p99 sync delay, and database write throughput.

### 5.2 Architecture Integration

The load test harness lives **outside the Android app** as a JVM-based test module. It uses the **same Retrofit DTOs** as the Android app (via a shared `:core-network-models` module) but runs as a synthetic client fleet.

```
┌──────────────────────────────────────────────────────────────────┐
│  CI: Nightly Load Test                                           │
│                                                                  │
│  [1] docker-compose up backend (PostgreSQL + Redis + Spring Boot)│
│  [2] Boot 200 synthetic cash registers (Kotlin/JVM)              │
│      ├── Each runs LoadTestAgent coroutine                       │
│      ├── Each uses a unique deviceId (UUID)                      │
│      ├── Each mints a real auth token via /auth/login            │
│      └── Each pushes 1 check/sec for 30 minutes                  │
│  [3] Collect metrics                                             │
│      ├── Redis Streams depth (per consumer group)                │
│      ├── Backend API latency (p50, p95, p99)                     │
│      ├── DB write throughput (checks/sec)                         │
│      └── Memory/CPU on backend                                   │
│  [4] Assert SLA: p99 sync < 5s, no lost checks, no OOM           │
└──────────────────────────────────────────────────────────────────┘
```

### 5.3 New Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `:loadtest` (Gradle module) | `backend/loadtest/` | JVM-only Kotlin module — depends on `:core-network-models` |
| `LoadTestOrchestrator` | `:loadtest` | Spawns N agents, waits for completion, reports metrics |
| `LoadTestAgent` | `:loadtest` | Simulates one cash register: login, push checks, retry on failure |
| `CheckGenerator` | `:loadtest` | Creates synthetic `CheckDto` with random SKUs from a fixed catalog |
| `MetricsCollector` | `:loadtest` | Subscribes to Micrometer/Prometheus on the backend, captures p50/p95/p99 |
| `LoadTestConfig` | `:loadtest` | YAML config: agent count, duration, RPS, target SLA |
| `RedisDepthProbe` | `:loadtest` | Periodically reads `XLEN` of the check stream + consumer group lag |

### 5.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `SyncUpWorker` (Android) | Add `workerId` and `deviceId` to logs to make production traces correlatable with load test traces |
| Backend `ChecksController` | Add Micrometer instrumentation (counters, timers) — already in Spring Boot Actuator footprint, just needs exposure |
| `docker-compose.yml` | New `docker-compose.loadtest.yml` profile that brings up backend + load generator together |
| `.github/workflows/` | New `nightly-loadtest.yml` workflow |

### 5.5 Data Flow

```
[1] CI triggers nightly at 02:00 MSK
    │
    ▼
[2] Spins up backend stack (docker-compose)
    │   ├── PostgreSQL with 100k pre-seeded products
    │   ├── Redis Streams
    │   └── Spring Boot with --loadtest profile (enables verbose metrics)
    │
    ▼
[3] LoadTestOrchestrator starts 200 LoadTestAgents
    │   ├── Each agent gets a unique deviceId (UUID)
    │   ├── Each calls POST /auth/login (real auth flow)
    │   └── Each enters loop: generate check → POST /api/v1/checks/sync
    │
    ▼
[4] After 30 min, MetricsCollector reads:
    │   ├── Prometheus /actuator/prometheus
    │   ├── Redis XLEN <stream>
    │   └── Backend logs (grep for ERROR)
    │
    ▼
[5] Generate report (HTML + JSON)
    │
    ▼
[6] Assert SLA; post to Slack/email on failure
```

### 5.6 Production Safety

- **The load test never runs against production.** It requires an explicit `LOADTEST=true` env var on the backend, which disables auth rate limits, enables verbose logging, and clears the database on startup.
- **Synthetic data only:** All test checks have a `deviceId` prefix `loadtest-` so production dashboards can filter them out.
- **Resource isolation:** The test runs on a dedicated CI runner with reserved CPU/RAM.
- **No test data ever syncs to real ФН:** Tests are HTTP-level only — no ККТ calls.

### 5.7 Build Order Position

The load test harness **depends on** the backend being functional and the Retrofit DTOs being stable. Build it **after**:
- `VitbonApi` DTOs are stable (already shipped in v1.0).
- Backend endpoints are stable (already shipped in v1.0).
- `docker-compose.yml` exists (already in repo).

This makes it a **low-risk, late-build** item.

---

## 6. Feature #3 — 24-Hour Offline Stress Test

### 6.1 Problem Statement

v1.0 ships with offline sale support, 500-doc queue cap, and WorkManager-driven sync. The "24-hour offline" requirement (from `SUMMARY.md` Phase C gate) was deferred: **no test proves the system survives 24 hours offline and successfully syncs afterward**.

The test must:
- Run on a real Android device (or emulator with realistic storage/CPU constraints).
- Use a production-equivalent dataset: 5,000 products, 50 cashiers, 30 days of historical checks.
- Run for 24 hours with network disabled.
- Generate a realistic sale pattern: ~300 sales/hour × 24h = 7,200 sales.
- Re-enable network, verify all checks sync within SLA.
- Verify no data loss, no duplicate, no out-of-order sync.

### 6.2 Architecture Integration

The test harness lives in a new `:testing` Android library module. It is **only** included in `sandbox` and `internalDebug` build variants — **never** in production APKs.

```
┌───────────────────────────────────────────────────────────────┐
│  :testing (Android library)                                    │
│  - depends on :app (compileOnly)                               │
│  - included in: internalDebug, sandbox variants                │
│  - excluded from: release, production variants                │
│                                                                │
│  OfflineStressHarness                                          │
│    ├── SyntheticCatalogSeeder (5,000 products)                 │
│    ├── SyntheticCashierSeeder (50 cashiers)                    │
│    ├── SaleSimulator (~300 sales/hour)                         │
│    ├── NetworkGate (toggles airplane mode via adb)             │
│    └── OfflineMetricsCollector (memory, queue depth, errors)   │
└───────────────────────────────────────────────────────────────┘
```

### 6.3 New Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `:testing` (Gradle module) | `android/testing/` | New Android library — sandbox/internalDebug only |
| `OfflineStressHarness` | `:testing` | Orchestrates the 24h test: seed → simulate → collect |
| `SyntheticCatalogSeeder` | `:testing` | Inserts 5,000 products via `ProductRepository` (bypasses network) |
| `SyntheticCashierSeeder` | `:testing` | Inserts 50 cashier records |
| `SaleSimulator` | `:testing` | Drives `ProcessSaleUseCase` at a configurable rate |
| `NetworkGate` | `:testing` | Wraps `ConnectivityManager` to allow test-controlled offline state |
| `OfflineMetricsCollector` | `:testing` | Periodically snapshots: queue depth, PENDING_SYNC count, DB size, memory |
| `StressTestReportGenerator` | `:testing` | After test, writes HTML/JSON report |

### 6.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `app/build.gradle.kts` | Adds `internalDebug` and `sandbox` build variants; `:testing` is only a dependency of those |
| `ProductRepository` | New (test-only) `seedSyntheticCatalog(products: List<Product>)` method, gated by `BuildConfig.ENVIRONMENT != PROD` |
| `SyncPrefs` | New (test-only) hook `simulateNetworkRestore()` that fires the `SyncMonitor` callback |
| `MainActivity` | New (test-only) intent extra `STRESS_TEST_MODE` that boots into `OfflineStressHarness` instead of normal UI |

### 6.5 Data Flow

```
[1] Build: ./gradlew :app:assembleInternalDebug
    │
    ▼
[2] Install on test device (Pixel 6 or similar)
    │
    ▼
[3] adb shell am start -n com.vitbon.kkm/.MainActivity --es STRESS_TEST_MODE 1
    │
    ▼
[4] OfflineStressHarness.run():
    │   ├── [4a] SyntheticCatalogSeeder.seed(5000) — ~30s
    │   ├── [4b] SyntheticCashierSeeder.seed(50) — ~5s
    │   ├── [4c] NetworkGate.disable() — airplane mode ON
    │   ├── [4d] SaleSimulator.start(rate=300/h) — runs 24h
    │   │       ├── Every sale: ProcessSaleUseCase → Room PENDING_SYNC
    │   │       ├── Periodic snapshot: queue depth, memory, errors
    │   │       └── Expected: ~7,200 PENDING_SYNC checks at end
    │   ├── [4e] NetworkGate.enable() — airplane mode OFF
    │   ├── [4f] Wait for SyncUpWorker to drain queue
    │   │       └── Expected: all 7,200 → SYNCED within 10 min
    │   └── [4g] Assert: no data loss, no duplicates, no OOM
    │
    ▼
[5] StressTestReportGenerator writes report to /sdcard/Download/stress-report.html
    │
    ▼
[6] adb pull /sdcard/Download/stress-report.html
```

### 6.6 Production Safety

- **`:testing` module is never bundled in release builds.** The `internalDebug` and `sandbox` variants are internal-only.
- **No test data ever syncs to real ФН:** `SaleSimulator` uses a `FakeFiscalCore` (already exists per `STATE.md` Phase 7).
- **No test data ever reaches production backend:** All test devices have a `deviceId` prefix `stress-` filtered out by backend dashboards.
- **The test harness is opt-in:** It requires an explicit `STRESS_TEST_MODE` intent extra.

### 6.7 Production-Equivalent Data

| Data Type | Volume | Source |
|-----------|--------|--------|
| Products | 5,000 | Synthetic: 100 categories × 50 products each, random prices/SKUs/barcodes |
| Cashiers | 50 | Synthetic: names from a fixed list, role distribution: 2 admin, 10 senior, 38 regular |
| Historical checks | 30 days × ~500/day = 15,000 | Backfilled via direct Room insert (bypasses fiscal) |
| Active shift | 1 open shift per device | Synthetic cashier logs in, opens shift |

This matches the worst-case real-world deployment: a busy supermarket with 50 cashiers and 5,000 SKUs.

---

## 7. Feature #4 — SQLCipher Key Rotation

### 7.1 Problem Statement

v1.0 uses SQLCipher 4.5.4 with a key stored in Android Keystore. Per `SUMMARY.md Finding 2`, "SQLCipher is non-negotiable for fiscal data at rest." However, v1.0 has **no key rotation mechanism** — a compromised key is a permanent compromise, and there is no way to rotate keys on a 200+ device fleet.

v1.1 must add:
- **Online key rotation:** When the device is online, the backend can request a key rotation. The app generates a new key, re-encrypts the database with the new key, and confirms.
- **Offline key rotation:** If a key is suspected compromised, an admin can trigger rotation via the Admin PIN. The app must handle the case where the device may be offline at the time.
- **In-memory data handling:** Any decrypted data held in memory at the moment of rotation must be safely re-encrypted or discarded.

### 7.2 Architecture Integration

Key rotation is a **data-layer** concern. It does not cross into Domain or Fiscal Core. The flow is:

```
┌────────────────────────────────────────────────────────────────┐
│  KeyRotationManager (data/security/)                           │
│                                                                │
│  1. Generate new key                                           │
│  2. Open DB with new key in "shadow" mode                      │
│  3. ATTACH old DB as 'old'                                     │
│  4. For each table: INSERT INTO new SELECT * FROM old          │
│  5. Verify row counts match                                    │
│  6. DETACH old, swap DB files                                  │
│  7. Update Keystore: new key alias active, old alias retired   │
│  8. Reopen with new key                                        │
└────────────────────────────────────────────────────────────────┘
```

### 7.3 New Components

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `KeyRotationManager` | `data/security/` | Orchestrates the rotation: new key, re-encrypt, swap, verify |
| `KeyStoreManager` (refactored) | `data/security/` | Interface split: `getActiveKey()`, `rotateKey()`, `retireKey()`. Implementation: `AndroidKeyStoreManager` |
| `RotationState` | `data/security/` | Sealed class: `Idle`, `InProgress(percent)`, `Completed(newKeyAlias)`, `Failed(reason)` |
| `Reencryptor` | `data/security/` | Performs the SQLite-level re-encryption using `sqlcipher_export()` |
| `KeyRotationDao` | `data/security/` | Tracks rotation history (timestamp, old key alias, new key alias) for audit |
| `RotateDbKeyUseCase` | `domain/security/` | Use case that wraps `KeyRotationManager` for UI/admin invocation |

### 7.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `EncryptedDatabaseFactory` | Now uses `KeyStoreManager.getActiveKey()` (was: direct `getKey()`) |
| `AppDatabase` (Room) | On startup, checks `KeyRotationStateDao` — if rotation was in-progress, resumes |
| `AuthTokenStore` | Decryption uses the **current** active key — must handle post-rotation transparently |
| `MainActivity` | On cold start, if rotation is in progress, show progress screen instead of normal UI |

### 7.5 Online vs Offline Rotation

| Mode | Trigger | Constraints |
|------|---------|-------------|
| **Online (server-initiated)** | Backend `POST /api/v1/admin/rotate-key` (admin role) | Device must be online. Confirms via `POST /api/v1/admin/rotate-key/ack` |
| **Offline (admin-initiated)** | Admin enters Admin PIN, selects "Rotate DB Key" | Must run while shift is closed (rotation locks the DB for ~30-60s) |
| **Emergency (compromised)** | Admin PIN → "Emergency Rotate" | Logs to audit, requires 2FA from cloud (deferred to v1.2 if not feasible) |

### 7.6 In-Memory Data Handling

This is the **most subtle** part of the design. At the moment of rotation:

- **Room cursors:** Closed before rotation. `AppDatabase.close()` is called.
- **ViewModels holding state:** Have already serialized state to SavedStateHandle (no live cursor).
- **Repository caches:** Cleared via `@Singleton` scope teardown or explicit `cache.invalidate()`.
- **AuthTokenStore:** Holds tokens in EncryptedSharedPreferences (separate from SQLCipher key) — unaffected.

The `KeyRotationManager` enforces a strict **order of operations**:
```
1. AppDatabase.close()              ← flushes Room, closes all cursors
2. Reencryptor.run()                ← 30-60s, no DB access
3. AppDatabase.open(newKey)         ← opens with new key
4. KeyStoreManager.rotateKey()      ← atomic alias swap
5. Audit log: "Key rotated at <ts>"  ← written via new DB
```

If rotation fails at step 2 or 3, the app **must** fail-safe: the old DB is intact (rotation is non-destructive until step 6's file swap), and the old key remains active.

### 7.7 Security Boundaries

- **Key aliases in Keystore:** `vitbon_db_key_v1`, `vitbon_db_key_v2`, ... Retired aliases are kept (not deleted) for forensic recovery, marked with `KeyProperties.PURPOSE_DECRYPT` only.
- **Rotation requires Admin PIN:** The `RotateDbKeyUseCase` checks `AdminPinHasher.verify()`.
- **Rotation is audit-logged:** A new `KeyRotationDao` entry is written post-rotation.
- **Old DB file is not deleted immediately:** It is moved to `databases/vitbon-<timestamp>.db.archived` for 30 days, then purged by a background task.

---

## 8. Feature #5 — ФН (Fiscal Storage) Replacement Flow

### 8.1 Problem Statement

Per `SUMMARY.md Finding 16`, "Switching FFD version after the first fiscal document permanently bricks the ФН." This means ФН replacement is an **inevitable** operational event (every ФН has a finite document capacity, typically ~250,000 docs). v1.0 has no flow for ФН replacement. v1.1 must add:

- **Detection:** When `documentsRemaining < 5,000` (per `SUMMARY.md` risk), surface warning to admin.
- **Replacement flow:** Admin replaces ФН hardware. The app must:
  - Block the current shift (close it first).
  - Mark all in-flight operations as failed/retryable.
  - Re-initialize the new ФН (registration with ОФД).
  - Verify FFD version matches the old ФН (cannot change FFD after first document).
  - Resume sync.
- **In-flight operations:** What happens to a sale that was being printed when the ФН was unplugged?

### 8.2 Architecture Integration

ФН replacement is a **Fiscal Core** concern that touches the **Domain** (use case) and **Data** (FnRegistration entity) layers.

```
┌────────────────────────────────────────────────────────────────┐
│  FnRegistration (data/local/fn_registration)                   │
│  - fnSerial: String                                             │
│  - ffdVersion: String                                           │
│  - registeredAt: Long                                           │
│  - documentsIssued: Int                                         │
│  - replacedAt: Long?                                            │
│  - registrationDocNumber: String?  (ОФД registration receipt)  │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│  FnRegistrationRepository (domain)                              │
│  - getCurrent(): FnRegistration?                               │
│  - registerNew(serial, ffd): FnRegistration                    │
│  - markReplaced(serial, replacedAt)                             │
│  - getHistory(): List<FnRegistration>                           │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│  FnReplacementStateMachine (domain/fiscal)                     │
│  States: IDLE → DETECTING → CLOSING_SHIFT → WAITING_HW →       │
│          REGISTERING → VERIFYING → ACTIVE | FAILED             │
│  Transitions are atomic; recoverable failure retries,          │
│  non-recoverable failure requires manual admin intervention.    │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│  InFlightOperationGuard (core/sync)                            │
│  - Tracks operations in PENDING state (print, cash-in, etc.)  │
│  - On ФН swap: marks them FISCAL_RETRY, surfaces to UI         │
│  - After new ФН active: replays them via FiscalOperationOrchestrator │
└────────────────────────────────────────────────────────────────┘
```

### 8.3 New Components

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `FnRegistration` (entity) | `data/local/` | Room entity for ФН registration records (history) |
| `FnRegistrationDao` | `data/local/` | DAO for FnRegistration |
| `FnRegistrationRepository` + Impl | `domain/` + `data/` | Domain interface, Room-backed impl |
| `FnReplacementStateMachine` | `domain/fiscal/` | State machine for ФН replacement flow |
| `FnRegistrationUseCase` | `domain/fiscal/` | Orchestrates: detect low capacity → notify admin → drive replacement flow |
| `InFlightOperationGuard` | `core/sync/` | Tracks in-progress fiscal operations; replays them after ФН swap |
| `FnReplacementScreen` | `features/settings/fn/` | UI for admin to drive the replacement flow |
| `FnCapacityWarning` | `features/statuses/` | Status indicator: 🟢/🟡/🔴 based on `documentsRemaining` |
| `FiscalCore.replaceFn()` (interface extension) | `core/fiscal/` | New method: physically initializes a new ФН; throws on hardware not present |

### 8.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `FiscalCore` (interface) | New method: `replaceFn(newSerial: String): FiscalResult` |
| `MSPOSKFiscalCore` | Implements `replaceFn` via SDK call |
| `Neva01FFiscalCore` | Implements `replaceFn` via SDK call (delegation) |
| `ShiftStateMachine` | On `replaceFn` triggered, requires shift CLOSED before proceeding |
| `FiscalOperationOrchestrator` | Holds an `InFlightOperationGuard` reference; checks guard before each operation |
| `ProcessSaleUseCase` | New pre-check: if `FnRegistration.documentsRemaining < 100`, throw `FnExhaustedException` |
| `FfdVersionResolver` | Reads `FnRegistration.ffdVersion` as the source of truth (was: cached in memory) |
| `StatusBar` | New indicator: ФН capacity gauge |

### 8.5 In-Flight Operations Handling

The critical question: **what happens to a sale mid-print when the ФН is unplugged?**

| Operation State | On ФН Unplug | On ФН Replace Complete |
|-----------------|--------------|------------------------|
| `IDLE` (not started) | No effect | No effect |
| `PRINTING` (SDK call in progress) | SDK throws `IO_ERROR` → mark `FISCAL_RETRY` | Replay via `FiscalOperationOrchestrator` |
| `PRINTED` (SDK returned OK, Room not yet updated) | Local DB may be inconsistent | Replay: check Room state first, only retry if `PENDING_FISCAL` |
| `SYNCED` (everything done) | No effect | No effect |

The `InFlightOperationGuard` exposes a `getOperationsInFlight(): List<OperationGuard>` and `replay(operation)` API. It uses a small Room table `in_flight_operations` (idempotent on `(operation_id, attempt)`).

### 8.6 Data Flow: ФН Replacement

```
[1] FnCapacityWarning fires: documentsRemaining = 4,200 < 5,000
    │
    ▼
[2] Status bar shows 🟡 warning, Status screen shows detail
    │
    ▼
[3] Admin opens FnReplacementScreen, sees: "Замените ФН в ближайшее время"
    │
    ▼
[4] Admin physically swaps ФН hardware (MSPOS-K rear panel)
    │
    ▼
[5] Admin taps "Начать замену" in FnReplacementScreen
    │
    ▼
[6] FnReplacementStateMachine transitions: IDLE → CLOSING_SHIFT
    │   └── If shift is open: ShiftStateMachine.closeShift() first
    │
    ▼
[7] WAITING_HW: App polls FiscalCore for new ФН detection (every 2s, max 5min)
    │   └── On detected: read new serial
    │
    ▼
[8] REGISTERING: FiscalCore.replaceFn(newSerial)
    │   └── Sends ФН registration document to ОФД
    │   └── On success: FnRegistration.registeredAt = now
    │
    ▼
[9] VERIFYING: Compare new FFD version with old FnRegistration.ffdVersion
    │   └── If mismatch: ABORT (operator must use same FFD)
    │   └── If match: continue
    │
    ▼
[10] ACTIVE: New FnRegistration is current
    │   └── InFlightOperationGuard.replayAll() — replays any PENDING operations
    │
    ▼
[11] UI: "Замена ФН завершена. Смену можно открыть."
```

### 8.7 Security Boundaries

- **ФН replacement requires Admin PIN:** `FnRegistrationUseCase` checks `AdminPinHasher.verify()`.
- **ФН replacement is audit-logged:** Old FnRegistration gets `replacedAt`, new one gets `registeredAt`. Both go to backend on next sync.
- **FFD version cannot change:** The `VERIFYING` step is non-bypassable. If mismatch, the only way forward is to put the old ФН back.
- **Operations cannot bypass the guard:** `FiscalOperationOrchestrator` checks `InFlightOperationGuard` before every operation during a swap.

---

## 9. Feature #6 — Mutual TLS Cert Management

### 9.1 Problem Statement

v1.0 uses TLS 1.3 with mTLS for backend communication (per `ARCHITECTURE.md §8.1`). v1.0 has no mechanism for:
- **Cert provisioning:** How does a 200-device fleet get its initial mTLS cert?
- **Cert rotation:** How often do certs rotate? (Industry standard: 90 days for device certs.)
- **Hot-reload:** Can a cert be rotated without an app restart?
- **Cert lifecycle in AuthTokenStore:** The cert and the auth token are separate concerns but both flow through the same OkHttp client.

### 9.2 Architecture Integration

Cert management is a **data security** concern with sync-layer integration for hot-reload.

```
┌──────────────────────────────────────────────────────────────┐
│  CertVault (data/security/cert)                              │
│  - Stores cert chain + private key in Android Keystore       │
│  - Exposes: getActiveCert(), getCertMetadata()               │
│  - On rotation: atomically swap, notify subscribers          │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  CertHotReloader (core/sync)                                 │
│  - Periodic poll: GET /api/v1/admin/cert-bundle              │
│  - On new cert: CertVault.rotate()                           │
│  - OkHttpClient.KeyManager is updated (live)                 │
└──────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────────────────────┐
│  OkHttpClient.Builder                                        │
│  - Uses X509KeyManager wrapper that delegates to CertVault   │
│  - On cert rotation, the wrapper's key alias changes;        │
│    next request picks up new cert automatically              │
└──────────────────────────────────────────────────────────────┘
```

### 9.3 New Components

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `CertVault` | `data/security/cert/` | Stores cert chain + key in Keystore; supports alias-based rotation |
| `CertLifecycleManager` | `data/security/cert/` | Tracks cert metadata: alias, notBefore, notAfter, serial, issuer |
| `CertHotReloader` | `core/sync/cert/` | WorkManager-driven job: poll backend for cert bundle, rotate if newer |
| `DynamicKeyManager` | `data/security/cert/` | OkHttp `X509KeyManager` wrapper that delegates to `CertVault` |
| `CertRepository` + Impl | `domain/security/` | Domain interface for cert operations |
| `ReloadMtlsCertUseCase` | `domain/security/` | Use case for admin-triggered manual reload |

### 9.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `OkHttpClient.Builder` (NetworkModule) | Uses `DynamicKeyManager` instead of static `KeyManager` |
| `AuthTokenStore` | Cert rotation is transparent — tokens are stored separately in EncryptedSharedPreferences |
| `WorkManager` schedules | New `CertHotReloadWorker` registered (periodic, every 6 hours) |
| `MainActivity` | On cold start, verifies cert is still valid; if expired and no network, refuses to start |

### 9.5 Hot-Reload Mechanism

The key design insight: **OkHttp reads the `KeyManager` on every TLS handshake**, not at client construction time. So if we make our `X509KeyManager` delegate to `CertVault`, the next request after a rotation will use the new cert **without rebuilding the client**.

```kotlin
class DynamicKeyManager(
    private val vault: CertVault
) : X509KeyManager {
    override fun chooseClientAlias(
        keyType: Array<String>?,
        issuers: Array<Principal>?,
        socket: Socket?
    ): String? {
        return vault.getActiveCertAlias()  // reads current state
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? {
        return vault.getActiveCertChain()  // reads current state
    }

    override fun getPrivateKey(alias: String?): PrivateKey? {
        return vault.getActiveCertPrivateKey()  // reads current state
    }
}
```

When `CertVault.rotate(newAlias)` is called, all three methods start returning the new cert on the next call. No client rebuild needed.

### 9.6 Cert Lifecycle Stages

| Stage | Trigger | Action |
|-------|---------|--------|
| **Provision** | First app launch, no cert in vault | App calls `POST /api/v1/admin/cert-enroll` with CSR; receives cert |
| **Active** | Cert is in vault and not expired | Normal operation |
| **Aging** | `notAfter - now < 14 days` | CertHotReloader proactively fetches new cert |
| **Rotation** | New cert downloaded | `CertVault.rotate()` atomic swap |
| **Expired** | `notAfter - now < 0` AND no new cert available | App refuses to sync; cashier can still sell offline |
| **Revoked** | Backend says cert was revoked | Emergency rotation; sync resumes with new cert |

### 9.7 Data Flow: Cert Rotation

```
[1] CertHotReloader runs (every 6h via WorkManager)
    │
    ▼
[2] GET /api/v1/admin/cert-bundle?deviceId={id}&currentSerial={serial}
    │
    ▼
[3] Backend responds with new cert (or 304 Not Modified)
    │
    ▼
[4] If new cert:
    │   ├── CertLifecycleManager.validate(cert) — check chain, expiry
    │   ├── CertVault.rotate(newAlias) — atomic swap
    │   ├── DynamicKeyManager picks up new cert on next request
    │   └── Audit log: "Cert rotated at <ts>"
    │
    ▼
[5] Next API call uses new cert transparently
```

### 9.8 Security Boundaries

- **Private key never leaves Keystore:** CertVault holds an alias; the key is generated and stored in AndroidKeyStore, never as bytes in app memory.
- **Cert rotation requires device authentication:** If the device has biometric/PIN set, the Keystore requires user auth for key use (`setUserAuthenticationRequired(true)` on cert key).
- **Cert pin (SPKI hash) is not used:** We rely on system CA trust store + cert revocation lists. SPKI pinning would prevent legitimate cert rotation.
- **TLS 1.3 only:** OkHttp config rejects TLS 1.2 and below.

---

## 10. Feature #7 — Token Rotation/Revocation

### 10.1 Problem Statement

v1.0 ships `AuthTokenStore` for session tokens but has no mechanism for:
- **Server-initiated revocation:** Backend says "this token is revoked" (e.g., device reported stolen, license expired hard, admin action).
- **Propagation to 200+ clients:** How does a revocation reach all clients fast?
- **Token refresh:** Long-lived refresh tokens vs short-lived access tokens.

v1.1 must add:
- **Push channel:** Backend can push revocation events to subscribed clients.
- **Polling fallback:** For clients that miss the push (offline, network issues), periodic poll.
- **Graceful degradation:** On revocation, the app does not crash — it forces re-auth.

### 10.2 Architecture Integration

Token revocation is an **auth + sync** concern. It crosses the data and sync layers.

```
┌────────────────────────────────────────────────────────────────┐
│  Backend: /api/v1/auth/revocations (SSE or WebSocket)         │
│  Pushes: { deviceId, tokenId, revokedAt, reason }             │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼ (SSE stream or WebSocket)
┌────────────────────────────────────────────────────────────────┐
│  RevocationPushReceiver (core/sync/auth)                      │
│  - Subscribes to backend push channel                         │
│  - On event: RevocationRegistry.markRevoked(...)              │
│  - Triggers AuthRepository.logout() if affected                │
└────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────────┐
│  RevocationRegistry (data/security/auth)                      │
│  - Stores revoked tokenIds locally                            │
│  - On every API call: check registry, fail fast if revoked    │
│  - Periodic sync via SyncDownWorker to catch missed pushes    │
└────────────────────────────────────────────────────────────────┘
```

### 10.3 New Components

| Component | Layer | Responsibility |
|-----------|-------|----------------|
| `RevocationRegistry` | `data/security/auth/` | Room table + in-memory cache of revoked tokenIds |
| `RevocationPushReceiver` | `core/sync/auth/` | SSE/WS subscriber; dispatches to AuthRepository |
| `RevocationRepository` + Impl | `domain/security/` | Domain interface |
| `ProcessRevocationUseCase` | `domain/security/` | Use case: on revocation, force logout + audit log |
| `AuthInterceptor` (modified) | `data/remote/` | OkHttp interceptor: checks `RevocationRegistry` before every request |

### 10.4 Modified Components

| Component | Modification |
|-----------|--------------|
| `AuthTokenStore` | New method: `isRevoked(tokenId): Boolean`; also stores `tokenId` (was: just `accessToken` string) |
| `AuthRepository` | New method: `handleRevocation(reason)` — clears tokens, posts to UI |
| `SyncDownWorker` | On every run, also pulls `GET /api/v1/auth/revocations?since=...` |
| `OkHttpClient` (NetworkModule) | AuthInterceptor checks `RevocationRegistry` per request |
| `AuthScreen` | New flow: if forced logout due to revocation, show "Ваша сессия завершена: {reason}" |

### 10.5 Push Channel Choice

| Option | Pros | Cons | Decision |
|--------|------|------|----------|
| **Server-Sent Events (SSE)** | Simple, HTTP-based, auto-reconnect | One-way only (we don't need return) | ✓ **Selected** |
| **WebSocket** | Bidirectional, lower overhead | More complex, needs heartbeat | ✗ Overkill |
| **Long polling** | Universal support | High latency, battery cost | ✗ Legacy |
| **FCM Push** | Works when app is killed | Requires Google Play Services; extra SDK | ✗ Out of stack (no FCM in v1.0) |

SSE is the right choice: it's HTTP, works through corporate proxies, and auto-reconnects.

### 10.6 Data Flow: Revocation

```
[1] Admin revokes device token in backend admin panel
    │
    ▼
[2] Backend publishes revocation event to SSE channel
    │   event: { deviceId: "vitbon-abc123", tokenId: "tk_xyz", reason: "DEVICE_STOLEN" }
    │
    ▼
[3] RevocationPushReceiver receives event
    │
    ▼
[4] RevocationRegistry.markRevoked("tk_xyz", reason)
    │   └── Persisted to Room immediately (survives restart)
    │
    ▼
[5] ProcessRevocationUseCase:
    │   ├── AuthRepository.handleRevocation(reason)
    │   │       ├── AuthTokenStore.clear()
    │   │       ├── AuthScreenState = FORCE_LOGOUT
    │   │       └── Audit log: "Token revoked: <reason>"
    │   └── If reason = "DEVICE_STOLEN": also wipe local DB (after admin re-auth)
    │
    ▼
[6] AuthInterceptor on next request: returns 401 with X-Revoked-Reason header
    │
    ▼
[7] Fallback poll: SyncDownWorker pulls GET /api/v1/auth/revocations?since=<ts>
        to catch clients that missed the push
```

### 10.7 Security Boundaries

- **Revocation registry is encrypted:** The `RevocationRegistry` Room table uses SQLCipher (same DB as fiscal data).
- **Force logout cannot be bypassed:** `AuthTokenStore.clear()` removes tokens from EncryptedSharedPreferences immediately; subsequent `getToken()` returns null.
- **"DEVICE_STOLEN" triggers data wipe:** A `RemoteWipeUseCase` (already a v1.0 deferred item, now in scope) clears Room, resets Keystore aliases (forcing re-enrollment).
- **Revocation push channel uses mTLS:** Same cert as the API channel (Feature #6).
- **Revocation is audit-logged:** The receiving client logs the event before processing.

---

## 11. Build Order & Dependencies (v1.1)

### 11.1 Dependency Graph (v1.1)

```
Phase v1.1-1: Environment & Sandbox (Feature #1)
│
├── EnvironmentConfig (core/hardening/)
├── EndpointResolver (core/hardening/)
├── SandboxOverrideRepository (data/remote/)
├── Modified: NetworkModule, VitbonApi, LicenseChecker
│
│   ▼ (provides build flavor separation for everything below)
│
Phase v1.1-2: Security Lifecycle Foundation (Features #4, #6, #7)
│
├── KeyRotationManager (Feature #4)
│   ├── Modified: EncryptedDatabaseFactory, AuthTokenStore
│   └── RotateDbKeyUseCase
│
├── CertVault + CertHotReloader (Feature #6)
│   ├── Modified: NetworkModule, WorkManager
│   └── ReloadMtlsCertUseCase
│
└── RevocationRegistry + PushReceiver (Feature #7)
    ├── Modified: AuthTokenStore, AuthRepository, SyncDownWorker
    └── ProcessRevocationUseCase
    │
    │   ▼ (security foundation in place)
    │
Phase v1.1-3: Fiscal Reliability (Feature #5)
│
├── FnRegistration entity + DAO + Repository
├── FnReplacementStateMachine
├── InFlightOperationGuard
├── Modified: FiscalCore, ShiftStateMachine, ProcessSaleUseCase, FfdVersionResolver
├── FnRegistrationUseCase
└── FnReplacementScreen
    │
    │   ▼ (fiscal flow hardened)
    │
Phase v1.1-4: Testing & Tooling (Features #2, #3)
│
├── :testing module (Feature #3)
│   ├── OfflineStressHarness
│   ├── SyntheticCatalogSeeder
│   ├── SaleSimulator
│   └── NetworkGate
│
└── :loadtest module (Feature #2)
    ├── LoadTestOrchestrator
    ├── LoadTestAgent
    ├── CheckGenerator
    └── MetricsCollector
    │
    │   ▼ (testing infrastructure ready)
    │
Phase v1.1-5: CI Integration
│
├── .github/workflows/nightly-loadtest.yml
├── .github/workflows/sandbox-validation.yml
└── docker-compose.loadtest.yml
```

### 11.2 Suggested Build Order (v1.1)

| # | Build Artifact | Dependencies | Rationale |
|---|---------------|--------------|-----------|
| 1 | `core/hardening/` module | None | Pure infrastructure, no business logic |
| 2 | `EnvironmentConfig` + `EndpointResolver` | `core/hardening/` | Establishes sandbox/prod separation upfront — every later module uses it |
| 3 | `NetworkModule` modification | #2 | OkHttp interceptor pattern must be in place before cert/token work |
| 4 | `CertVault` + `DynamicKeyManager` | #3 | Cert hot-reload needs the new OkHttp plumbing |
| 5 | `CertHotReloader` + `CertRepository` | #4 | Worker-driven cert refresh |
| 6 | `RevocationRegistry` + `RevocationRepository` | #4 | Token revocation piggybacks on cert-protected channel |
| 7 | `RevocationPushReceiver` | #6 | SSE subscriber |
| 8 | `AuthInterceptor` modification | #6, #7 | Fails fast on revoked tokens |
| 9 | `KeyRotationManager` + `Reencryptor` | #3 | Needs the network + storage plumbing |
| 10 | `RotateDbKeyUseCase` | #9 | UI entry point |
| 11 | `FnRegistration` entity + DAO | None | Pure Room work |
| 12 | `FnRegistrationRepository` | #11 | Domain interface |
| 13 | `FiscalCore.replaceFn()` extension | #12 | Adds new method to interface |
| 14 | `FnReplacementStateMachine` | #13 | State machine |
| 15 | `InFlightOperationGuard` | #14 | Tracks operations during swap |
| 16 | `FnRegistrationUseCase` | #14, #15 | Orchestrates the full flow |
| 17 | `FnReplacementScreen` UI | #16 | Admin UI |
| 18 | `:testing` module skeleton | #1, #2, #3 | Android lib module setup |
| 19 | `SyntheticCatalogSeeder`, `SyntheticCashierSeeder` | #18 | Test data |
| 20 | `SaleSimulator` + `NetworkGate` | #19 | Test driver |
| 21 | `OfflineStressHarness` | #20 | Orchestrator |
| 22 | `StressTestReportGenerator` | #21 | Report output |
| 23 | `:loadtest` module skeleton | None (JVM) | Independent of Android |
| 24 | `LoadTestAgent` + `CheckGenerator` | #23 | Synthetic clients |
| 25 | `LoadTestOrchestrator` | #24 | Multi-agent driver |
| 26 | `MetricsCollector` + `RedisDepthProbe` | #25 | SLA validation |
| 27 | CI workflows + docker-compose.loadtest | #25, #26 | CI integration |

### 11.3 Module Dependency Rules (v1.1 additions)

1. `core/hardening/` depends on **nothing** in the business layers. It may depend on `BuildConfig`, AndroidX core, and `androidx.security.crypto`.
2. `:testing` depends on `:app` (compileOnly) and is consumed only by `internalDebug`/`sandbox` variants.
3. `:loadtest` depends on `:core-network-models` (new shared module) and is **JVM-only** (no Android deps).
4. `InFlightOperationGuard` lives in `core/sync/` (despite the name suggesting fiscal) because it must coordinate with WorkManager retry logic.
5. `FnReplacementStateMachine` lives in `domain/fiscal/` because it enforces fiscal invariants (FFD immutability, shift closure).

### 11.4 Shared Network Models Module (New)

To support `:loadtest` (JVM) without dragging in Android dependencies, v1.1 extracts the Retrofit DTOs into a new `:core-network-models` Gradle module:

```
:core-network-models  (pure Kotlin)
  ├── CheckDto.kt
  ├── ProductDto.kt
  ├── ShiftDto.kt
  ├── DocumentDto.kt
  └── ... (all DTOs, no Retrofit annotations — annotations added in :app)
```

Both `:app` (Android) and `:loadtest` (JVM) depend on this module.

---

## 12. New Module Catalog (v1.1)

### 12.1 Production Modules (in :app)

```
android/app/src/main/java/com/vitbon/kkm/
├── core/
│   ├── hardening/           ← NEW
│   │   ├── EnvironmentConfig.kt
│   │   ├── EndpointResolver.kt
│   │   ├── HardeningFlags.kt
│   │   ├── BuildFlavorRegistry.kt
│   │   └── TestHooks.kt
│   ├── security/            ← EXTENDED
│   │   ├── cert/
│   │   │   ├── CertVault.kt
│   │   │   ├── CertLifecycleManager.kt
│   │   │   └── DynamicKeyManager.kt
│   │   ├── auth/
│   │   │   ├── RevocationRegistry.kt
│   │   │   └── AuthInterceptor.kt
│   │   └── rotation/
│   │       ├── KeyRotationManager.kt
│   │       ├── RotationState.kt
│   │       └── Reencryptor.kt
│   ├── sync/                ← EXTENDED
│   │   ├── cert/
│   │   │   └── CertHotReloader.kt
│   │   ├── auth/
│   │   │   └── RevocationPushReceiver.kt
│   │   └── routing/
│   │       └── EnvironmentRouter.kt
│   └── fiscal/              ← EXTENDED (interface)
│       └── FiscalCore.kt    (adds replaceFn)
├── domain/                  ← EXTENDED
│   ├── fn/
│   │   ├── FnRegistrationRepository.kt
│   │   └── FnRegistrationUseCase.kt
│   ├── security/
│   │   ├── CertificateRepository.kt
│   │   ├── RevocationRepository.kt
│   │   ├── RotateDbKeyUseCase.kt
│   │   ├── ReloadMtlsCertUseCase.kt
│   │   └── ProcessRevocationUseCase.kt
│   └── fiscal/
│       ├── FnReplacementStateMachine.kt
│       └── InFlightOperationGuard.kt
├── data/                    ← EXTENDED
│   ├── local/
│   │   ├── fn/
│   │   │   ├── FnRegistrationDao.kt
│   │   │   └── FnRegistrationEntity.kt
│   │   ├── cert/
│   │   │   └── CertMetadataDao.kt
│   │   └── revocation/
│   │       └── RevocationDao.kt
│   └── remote/
│       ├── config/
│       │   ├── DefaultEndpointResolver.kt
│       │   └── SandboxEndpointResolver.kt
│       └── SandboxOverrideRepository.kt
├── features/                ← EXTENDED
│   ├── settings/fn/
│   │   ├── FnReplacementScreen.kt
│   │   └── FnReplacementViewModel.kt
│   ├── statuses/
│   │   └── FnCapacityIndicator.kt
│   └── auth/
│       └── ForcedLogoutDialog.kt
└── di/                      ← EXTENDED
    ├── HardeningModule.kt
    ├── SecurityModule.kt
    └── CertModule.kt
```

### 12.2 Test Modules

```
android/testing/                          ← NEW (Android library)
├── src/main/java/com/vitbon/testing/
│   ├── OfflineStressHarness.kt
│   ├── SyntheticCatalogSeeder.kt
│   ├── SyntheticCashierSeeder.kt
│   ├── SaleSimulator.kt
│   ├── NetworkGate.kt
│   ├── OfflineMetricsCollector.kt
│   └── StressTestReportGenerator.kt
└── build.gradle.kts                       (compileOnly :app, sandbox/internalDebug only)

backend/loadtest/                         ← NEW (JVM library)
├── src/main/kotlin/com/vitbon/loadtest/
│   ├── LoadTestOrchestrator.kt
│   ├── LoadTestAgent.kt
│   ├── CheckGenerator.kt
│   ├── MetricsCollector.kt
│   ├── RedisDepthProbe.kt
│   └── LoadTestConfig.kt
└── build.gradle.kts                       (JVM, depends on :core-network-models)

core-network-models/                      ← NEW (pure Kotlin)
├── src/main/kotlin/com/vitbon/network/
│   ├── CheckDto.kt
│   ├── ProductDto.kt
│   ├── ShiftDto.kt
│   ├── DocumentDto.kt
│   ├── ... (all DTOs, no Retrofit)
└── build.gradle.kts                       (pure Kotlin/JVM)
```

---

## 13. Security Architecture (v1.1 Delta)

### 13.1 Updated Storage Security

| Asset | Protection | Mechanism (v1.0 → v1.1) |
|-------|-----------|--------------------------|
| Cashier credentials | Encrypted at rest | SQLCipher 4.5.4 → **same**, with key rotation |
| Auth tokens | Hardware-backed | Android Keystore / KeyMaster → **same**, plus revocation registry |
| Sync timestamps | Unencrypted | SyncPrefs → **same** |
| Product catalog | Encrypted (now) | Room (non-sensitive) → **SQLCipher** (rotated with main key) |
| Fiscal data | Encrypted in transit | TLS 1.3 + mTLS → **same**, with hot-reloadable cert |
| Cert private key | Hardware-backed | Android Keystore cert key → **same**, with alias rotation |
| DB encryption key | Hardware-backed | Android Keystore alias `vitbon_db_key_v1` → **aliases `v1, v2, ...`** with rotation history |

### 13.2 Updated mTLS Trust Model

```
┌──────────────────────────────────────────────────────────────┐
│  Trust chain (v1.1)                                          │
│                                                              │
│  System CA Store (Android)                                   │
│    └── VITBON Sub-CA (pinned by name)                        │
│          └── Device Cert (rotated every 90 days)             │
│                                                              │
│  Revocation:                                                 │
│  - Device certs: CRL checked at handshake + daily poll       │
│  - Auth tokens: RevocationRegistry (push + poll)             │
│  - DB keys: Key rotation history in audit log                │
└──────────────────────────────────────────────────────────────┘
```

### 13.3 v1.1 Threats Addressed

| Threat | v1.0 Status | v1.1 Mitigation |
|--------|------------|-----------------|
| Stolen device | PIN-protected, but token still valid until expiry | Revocation push + remote wipe |
| Compromised DB key | No rotation possible | Key rotation with audit trail |
| Compromised mTLS cert | Manual reinstall required | Hot-reload via CertHotReloader |
| Replay attack on token | Tokens have expiry but no revocation | RevocationRegistry + AuthInterceptor |
| ФН unplugged mid-sale | Inconsistent state, possible data loss | InFlightOperationGuard + replay |
| Sandbox URLs leaked to prod | Single URL config | Build-time + runtime separation |

---

## 14. Quality Gate Compliance

- [x] **Components clearly defined with boundaries** — All v1.1 components placed in v1.0 layer model with explicit talk-to rules in Section 3.1.
- [x] **Data flow direction explicit** — Each of 7 features has a dedicated data flow diagram (Sections 4.5, 5.5, 6.5, 7.5, 8.6, 9.7, 10.6).
- [x] **Build order implications noted** — Dependency graph in Section 11.1 with numbered build order table in Section 11.2.
- [x] **No architectural regressions to v1.0** — Section 1.2 explicitly states layer-boundary invariants are preserved verbatim.
- [x] **Security/cert boundaries clear** — Section 13 covers updated storage security, mTLS trust model, and threat mitigations.
- [x] **Sandbox/production separation clear** — Section 4 establishes compile-time flavor + runtime override, with explicit production safety.
- [x] **New vs modified components explicit** — Section 1.1 table summarizes; each feature section enumerates both.

---

## 15. Key Findings Summary

| Topic | Finding | Implication for v1.1 Roadmap |
|-------|---------|-------------------------------|
| **Three orthogonal subsystems** | Environment, Security Lifecycle, Operational Testing — all additive, none restructure layers | v1.1 can ship in 5 phases without breaking v1.0 |
| **`:core-network-models` extraction** | Required for `:loadtest` (JVM) to share DTOs | New pure-Kotlin module, low risk |
| **Cert hot-reload via dynamic KeyManager** | OkHttp reads KeyManager per handshake, so rotation is transparent | No OkHttp client rebuild needed; massive simplification |
| **ФН replacement requires shift closed** | All in-flight operations must complete or fail before swap | ShiftStateMachine integration is critical; can't be skipped |
| **Key rotation is non-destructive until file swap** | Re-encrypt into a new DB, then atomic swap | Safe to fail mid-rotation; old DB intact |
| **SSE for revocation push** | Simpler than WS, works through proxies, auto-reconnects | Right choice over FCM (no Google Play Services dep) |
| **`:testing` module is sandbox-only** | Excluded from release builds at Gradle level | Production safety guaranteed by build system |
| **InFlightOperationGuard is sync-layer concern** | Coordinates with WorkManager retry, not with fiscal directly | Lives in `core/sync/` despite the name |
| **Sandbox is a license feature** | `SANDBOX_OPT_IN` license gates sandbox URLs | Prevents accidental enablement on customer devices |
| **24h stress test uses FakeFiscalCore** | No real ККТ in test; `FakeFiscalCore` already exists from Phase 7 | Reduces test complexity, eliminates hardware risk |

---

## 16. Risks and Mitigations (v1.1)

| Risk | Severity | Mitigation |
|------|----------|------------|
| Key rotation corrupts DB | 🔴 High | Re-encryptor uses `sqlcipher_export()` into a new file; old DB intact until success; tests on dev devices first |
| ФН replacement during sale | 🔴 High | `InFlightOperationGuard` replays PENDING operations; ShiftStateMachine requires shift closed first |
| Cert rotation breaks in-flight requests | 🟡 Medium | OkHttp's per-handshake KeyManager read; new cert applies on next request, in-flight requests complete with old cert |
| Revocation push missed (offline) | 🟡 Medium | SyncDownWorker pulls revocations on every run; RevocationRegistry is checked on every request |
| Load test flakes CI | 🟡 Medium | SLA assertions have retry tolerance; load test runs nightly, not on PR |
| Sandbox URLs leak to prod | 🔴 High | `BuildFlavorRegistry` checks at startup; production builds throw on sandbox URL resolution |
| 24h stress test reveals memory leak | 🟡 Medium | `OfflineMetricsCollector` snapshots every 5min; report highlights any growth >10% |
| SSE connection drops | 🟢 Low | OkHttp auto-reconnects; RevocationRegistry survives restart (Room-persisted) |

---

## 17. Open Questions for v1.1 Planning

| # | Question | Owner | Resolution |
|---|----------|-------|------------|
| Q1 | Where does the initial mTLS cert come from? Factory provisioning? Bootstrap QR code? | Security | Decide by Phase v1.1-2 kickoff |
| Q2 | Does "DEVICE_STOLEN" trigger data wipe, or just logout? | Product/Security | Product decision; affects `RemoteWipeUseCase` scope |
| Q3 | Does the load test need to simulate network failure mid-test? | QA | Decide by Phase v1.1-4 kickoff |
| Q4 | Is key rotation automatic (e.g., every 365 days) or only manual? | Security | Recommend manual-only for v1.1, automatic in v1.2 |
| Q5 | What is the offline grace period for the new ФН? (How long can the device sell with no ФН?) | Product/Legal | Regulatory answer needed; default 0 (block) for v1.1 |

---

## 18. References

- **v1.0 Architecture**: `.planning/research/ARCHITECTURE.md`
- **v1.0 Summary**: `.planning/research/SUMMARY.md`
- **v1.0 Project context**: `.planning/PROJECT.md`
- **v1.0 State**: `.planning/STATE.md`
- **v1.0 Milestones**: `.planning/MILESTONES.md`
- **v1.0 Audit**: `.planning/milestones/v1.0-MILESTONE-AUDIT.md`
- **SQLCipher docs**: https://www.zetetic.net/sqlcipher/sqlcipher-api/#sqlcipher_export
- **OkHttp X509KeyManager**: https://square.github.io/okhttp/5.x/okhttp/okhttp3/-x509-key-manager/
- **Android Keystore key rotation**: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec

---

*Research generated: 2026-06-21*  
*Milestone: v1.1 Production Readiness*  
*Status: Architecture research complete — ready for `/gsd-discuss-phase 1` (Environment & Sandbox)*
