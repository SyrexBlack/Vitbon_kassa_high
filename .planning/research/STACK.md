# STACK.md — Android Kassovoye Prilozheniye (VITBON)

**Version:** 1.0  
**Date:** 2026-06-20  
**Status:** Prescriptive baseline — verify all versions against official docs before committing  
**Confidence scale:** 🟢 High (production-proven), 🟡 Medium (reliable but with caveats), 🔴 Low (emerging/declining)

---

## 1. Language & Build Tooling

### 1.1 Kotlin
| | |
|---|---|
| **Version** | `1.9.22` ( pinned in `android/build.gradle.kts` ) |
| **Confidence** | 🟢 High |
| **Rationale** | Stable 1.9 LTS track. Kotlin `2.0.x` is released but tooling (IDE plugins, KSP compatibility, Compose compiler) is still maturing. Lock to 1.9 until Compose compiler 2.0 and KSP 2.0 have 3+ months of ecosystem hardening. |
| **What NOT to use** | Kotlin `2.0+` for this project in 2026 H1 — the Compose compiler plugin rewrite (`compose-compiler` → IR-based `kotlin-plugin`) requires migration of all `android.composeOptions` blocks. Track [KT-69600](https://youtrack.jetbrains.com/issue/KT-69600). |
| **Migration trigger** | When Compose BOM `2025.01.00+` (Compose 2.0 stable) ships and all Compose libraries have 2.0-compatible versions. Expected: late 2026. |

### 1.2 Android Gradle Plugin (AGP)
| | |
|---|---|
| **Version** | `8.2.2` ( pinned in `android/build.gradle.kts` ) |
| **Confidence** | 🟢 High |
| **Rationale** | Stable, well-tested with Kotlin 1.9. AGP `8.3+` adds better R8 desugaring and configuration cache improvements. AGP `8.5.x` is the current stable as of mid-2026. Upgrade path is straightforward: bump, verify `assembleDebug` + `connectedDebugAndroidTest` pass. |
| **What NOT to use** | AGP `9.0.x` — requires Gradle `8.7+`, JDK 21+ toolchain, and major configuration changes. Not needed for this project. |
| **Upgrade target** | `8.5.0` — drop-in replacement, enables parallel R8 classfile processing, faster incremental builds. |

### 1.3 KSP (Kotlin Symbol Processing)
| | |
|---|---|
| **Version** | `1.9.22-1.0.17` ( matches Kotlin version ) |
| **Confidence** | 🟢 High |
| **Rationale** | Required for Room, Hilt, and any annotation processing. Version must match Kotlin minor+patch exactly. KSP `2.0` requires Kotlin `2.0` — stay on `1.9.x` line until Kotlin 2.0 adoption is confirmed. |

---

## 2. UI Layer

### 2.1 Jetpack Compose
| | |
|---|---|
| **BOM Version** | `2024.01.00` ( pinned in `android/app/build.gradle.kts` ) |
| **Compose Compiler** | `1.5.8` ( matches Kotlin 1.9.22 ) |
| **Confidence** | 🟢 High |
| **Rationale** | Compose BOM `2024.01.00` is the stable baseline the project already uses. All Compose Material3, Navigation, ViewModel, and Lifecycle libraries are co-bundled at known-compatible versions. Skipping BOM and pinning individual versions causes semantic-version mismatches. |
| **What NOT to use** | Mixing Compose versions from different BOMs. Never pin `ui:1.5.0` while `material3:1.2.0` comes from BOM — this causes runtime crashes from ABI incompatibilities. |
| **Upgrade trigger** | When Compose 2.0 BOM (`2025.xx`) is released with stable Material3 2.0, migrate Kotlin and KSP first, then Compose. |

### 2.2 Compose Navigation
| | |
|---|---|
| **Version** | `2.7.6` |
| **Confidence** | 🟢 High |
| **Rationale** | Stable, type-safe nav routes via `NavType`. Compatible with Compose BOM `2024.01.00`. |

### 2.3 Material Design 3
| | |
|---|---|
| **Version** | `1.1.2` ( from Compose BOM ) |
| **Confidence** | 🟢 High |
| **Rationale** | Material3 is stable for production. The project uses it for the cash register UI. Avoid Material2 — ecosystem has moved on. Material3 `1.3.0+` adds more components (DatePicker, search bar); consider bumping when Compose BOM advances. |

---

## 3. Local Database (Offline-First)

### 3.1 Room
| | |
|---|---|
| **Version** | `2.6.1` ( pinned in `android/app/build.gradle.kts` ) |
| **KSP** | `room-compiler:2.6.1` |
| **Confidence** | 🟢 High |
| **Rationale** | Room is the standard Android ORM for offline-first apps. It has first-class Kotlin coroutines support (`room-ktx`), Flow-based reactive queries, automatic migration management via `FallbackRoomDatabase`, and integrates with SQLCipher for encryption. It is actively maintained by Google (now Android team). |
| **Why NOT SQLite directly** | Raw SQLite requires manual cursor management, no compile-time query validation, no reactive streams. Room catches SQL errors at compile time via the processor — SQLite does not. |
| **Why NOT Realm** | Realm (MongoDB Realm SDK) has significant overhead: larger APK size (~10 MB vs Room's ~500 KB), slower cold starts, and less natural Kotlin coroutines integration. Realm's object model requires extending `RealmObject`, which pollutes the domain layer. For a fiscal app where offline reliability is paramount, Room's WAL-mode SQLite under the hood is battle-tested. |
| **Encryption** | Use `SQLCipher` with Room via `SupportFactory` (see §3.2). |

### 3.2 SQLCipher (Room Encryption)
| | |
|---|---|
| **Library** | `net.zetetic:android-database-sqlcipher:4.5.4` + `androidx.sqlite:sqlite-ktx:2.4.0` |
| **Confidence** | 🟢 High |
| **Rationale** | Required for SEC-01 (encryption of local storage). SQLCipher is the FIPS 140-2 validated SQLite encryption extension. The key must be stored in Android Keystore, never in SharedPreferences or code. |
| **Configuration** | ```kotlin<br/>val passphrase = keyStore.getKey("db_key", ...)<br/>val factory = SupportFactory(passphrase.getEncoded())<br/>Room.databaseBuilder(app, AppDb::class.java, "vitbon.db")<br/>    .openHelperFactory(factory)<br/>    .build()``` |
| **What NOT to use** | Android `SQLiteDatabase` encryption (deprecated). SQLCipher 4.x + Room 2.6.x is the current supported path. |

### 3.3 DataStore (Preferences)
| | |
|---|---|
| **Library** | `androidx.datastore:datastore-preferences:1.0.0` |
| **Confidence** | 🟢 High |
| **Rationale** | For small key-value settings (grace period counter, last sync timestamp, feature flags). Replace SharedPreferences entirely — DataStore is Kotlin-first, supports Coroutines Flow, and handles migration automatically. |

---

## 4. Dependency Injection

### 4.1 Hilt
| | |
|---|---|
| **Version** | `2.50` (Dagger Hilt — pinned in both `android/build.gradle.kts` and `android/app/build.gradle.kts` ) |
| **Hilt Navigation Compose** | `1.1.0` |
| **Hilt WorkManager** | `1.1.0` |
| **Confidence** | 🟢 High |
| **Rationale** | Hilt is the canonical DI solution for Android. It generates `DaggerAppComponent` at compile time, integrates with ViewModel via `@HiltViewModel`, with WorkManager via `@HiltWorker`, and with Navigation Compose. The alternatives are Koin (runtime-only, slower, no compile-time graph validation) and manual DI (error-prone at scale). For a 200+ cash register deployment, compile-time validation is non-negotiable. |
| **What NOT to use** | Koin — convenient for small apps but lacks compile-time correctness guarantees. At 200+ cash registers, a runtime DI failure means 200 devices with a crash on boot. |
| **Upgrade path** | Hilt `2.51+` adds better incremental annotation processing. Bump together with Kotlin upgrade. |

---

## 5. Async & Concurrency

### 5.1 Kotlin Coroutines
| | |
|---|---|
| **Version** | Bundled with `kotlinx-coroutines-core:1.7.3` ( from test dependencies ) |
| **Confidence** | 🟢 High |
| **Rationale** | Coroutines are the standard for async in Kotlin Android. Used for all I/O (Room, Retrofit, WorkManager), FiscalCore calls, and UI state management (StateFlow, SharedFlow). No alternative — Flow is built into the language. |

### 5.2 WorkManager
| | |
|---|---|
| **Version** | `2.9.0` ( pinned in `android/app/build.gradle.kts` ) |
| **Confidence** | 🟢 High |
| **Rationale** | WorkManager is the only reliable background scheduler that survives app restarts, device reboots, and Doze mode. Required for the sync layer (GOOD-02: push checks up, pull products down). `PeriodicWorkRequest` with 30-second minimum interval for sync. `OneTimeWorkRequest` for immediate sync on network restoration. |
| **What NOT to use** | `Handler`/`Looper`, `ScheduledExecutorService`, or `AlarmManager` — none survive Doze mode reliably. WorkManager is the platform-supported solution. |

---

## 6. Networking & API

### 6.1 Retrofit + OkHttp
| | |
|---|---|
| **Retrofit** | `2.9.0` |
| **OkHttp** | `4.12.0` |
| **Gson Converter** | `2.9.0` |
| **Scalars Converter** | `2.9.0` |
| **Logging Interceptor** | `4.12.0` |
| **Confidence** | 🟢 High |
| **Rationale** | Retrofit 2.9 + OkHttp 4.12 is the de-facto standard for Android REST clients. Retrofit generates type-safe API interfaces at compile time. OkHttp provides connection pooling, transparent GZIP, interceptors for auth tokens, and the logging interceptor for debug network tracing. |
| **Mutual TLS** | Configure via custom `TrustManager` + `KeyManager` in an OkHttp `SSLSocketFactory`. Required for ЕГАИС УТМ and ОФД connections. |
| **What NOT to use** | Volley — legacy, no coroutines support out of the box. Apache HttpClient — deprecated on Android API 23+. Ktor Client — viable but ecosystem (documentation, community, Firebase integrations) is smaller. Retrofit + OkHttp is the safe choice. |

### 6.2 Why NOT GraphQL
| | |
|---|---|
| **Decision** | ❌ Do NOT adopt GraphQL for this project |
| **Rationale** | GraphQL adds complexity (schema definition, codegen, Apollo/Ktor client, cache management) without clear benefit for this use case. The API is essentially CRUD + sync: push checks, pull products. REST is simpler, more debuggable, cacheable via HTTP semantics, and the backend is already Spring MVC REST. Caching is better handled at the Room/WorkManager layer (sync down → Room → UI), not at the network layer. |
| **When to revisit** | Only if the mobile app grows to need highly flexible, client-driven queries (e.g., dynamic reporting filters). REST with query parameters covers 95% of this. |

### 6.3 API Architecture Pattern
| | |
|---|---|
| **Pattern** | Repository + UseCase (Clean Architecture) |
| **Remote** | Retrofit interface → OkHttp network layer |
| **Local** | Room DAO → SQLCipher persistence |
| **Sync** | WorkManager → Repository (remote-first, local fallback) |
| **Confidence** | 🟢 High |
| **Rationale** | The repository pattern abstracts data sources. The caller (ViewModel) never knows if data comes from Room or Retrofit. This is essential for offline-first: repository always writes to Room; WorkManager pulls from remote and upserts to Room. |

---

## 7. Fiscal Printers (MSPOS-K / Нева 01Ф)

### 7.1 MSPOS-K
| | |
|---|---|
| **Integration** | Android Service binding (`com.vitbon.kkm.MSPOSKService`) |
| **Protocol** | FFD 1.2 (native), FFD 1.05 (supported via FiscalDocumentBuilder) |
| **Confidence** | 🟢 High |
| **Rationale** | MSPOS-K exposes a vendor-provided Android service (`com.vitbon.kkm.MSPOSKService`) that the FiscalCore adapter binds to. This is the primary fiscal I/O path. The project's `MSPOSKFiscalCore` adapter wraps this service. |
| **Protocol** | Proprietary vendor binary protocol over AIDL/Binder. No public SDK — integration contract with manufacturer provides the service interface. |

### 7.2 Нева 01Ф
| | |
|---|---|
| **Integration** | **Delegation to MSPOS-K Service** (no standalone SDK as of 2026-06-20) |
| **Protocol** | Same MSPOS-K binder interface (different device branding) |
| **Confidence** | 🟡 Medium |
| **Rationale** | Per ADR `docs/superpowers/specs/2026-05-27-neva01f-sdk-justification-design.md`, the Нева 01Ф embeds the same fiscal service as MSPOS-K. `Neva01FFiscalCore` delegates to `RealNeva01FProtocol` → `RealMSPOSKProtocol`. This is the correct production path given available vendor artifacts. |
| **Migration** | When the Нева vendor delivers a standalone SDK, implement a new `RealNeva01FProtocol` and replace the delegate. The `Neva01FProtocol` interface is the abstraction boundary — callers are unchanged. |

### 7.3 FiscalCore Adapter Pattern
| | |
|---|---|
| **Interface** | `FiscalCore` — `openShift`, `printSale`, `printReturn`, `printCorrection`, `closeShift`, `printXReport`, `cashIn`, `cashOut`, `getStatus`, `getFFDVersion` |
| **Implementations** | `MSPOSKFiscalCore`, `Neva01FFiscalCore` |
| **FFD Builder** | `FiscalDocumentBuilder` — version-aware TLV tag assembly for FFD 1.05 vs 1.2 |
| **Confidence** | 🟢 High |
| **Rationale** | The adapter pattern isolates all vendor-specific SDK calls. Swapping MSPOS-K for any future ККТ requires only a new `FiscalCore` implementation — no changes to sales, return, or shift features. |

---

## 8. Barcode Scanning (DataMatrix)

### 8.1 CameraX + ML Kit Barcode Scanning
| | |
|---|---|
| **CameraX** | `camera-camera2:1.3.4`, `camera-lifecycle:1.3.4`, `camera-view:1.3.4` |
| **ML Kit** | `com.google.mlkit:barcode-scanning:17.3.0` |
| **Confidence** | 🟢 High |
| **Rationale** | CameraX provides a stable camera abstraction that works across Android 6.0+ devices (critical for API 23 minimum). ML Kit barcode scanning is Google's on-device ML model — fast, accurate, supports DataMatrix (GS1), and works offline. No network call needed for scanning itself. |
| **What NOT to use** | ZXing (`com.journeyapps:zxing-android-embedded`) — older, less accurate on DataMatrix codes, requires manual camera lifecycle management. ML Kit outperforms ZXing on rotated, low-light, and partial DataMatrix codes. |
| **Hardware scanner** | If a physical USB/Bluetooth scanner is connected, capture via `KeyEvent` (`onKeyDown`) instead of CameraX. The app must detect scanner input vs. keyboard input by timing (`< 50ms` between chars = scanner). |

---

## 9. External Integrations (Backend Proxies)

### 9.1 Cloud Sync (REST API)
| | |
|---|---|
| **Backend** | Kotlin + Spring Boot 3.2.2 ( already in project ) |
| **Database** | PostgreSQL 16+ |
| **Queue** | Redis Streams (batch check ingestion) |
| **ORM** | Spring Data JPA + Hibernate |
| **Migrations** | Flyway |
| **Confidence** | 🟢 High |
| **Rationale** | Backend stack is already in `backend/build.gradle.kts`. Spring Boot 3.2.x is stable. For 200+ cash registers, PostgreSQL with read replicas handles reporting reads. Redis Streams provides ordered, persistent queuing for check uploads (OFD submission) with at-least-once delivery guarantees. |
| **Upgrade target** | Spring Boot 3.4.x (2026 stable) — verify after release, expected improvements in native compilation support. |

### 9.2 ОФД (Fiscal Operator)
| | |
|---|---|
| **Integration** | Backend-side proxy — Android sends fiscal documents to backend, backend sends to ОФД |
| **Protocol** | OFD provider-specific (usually HTTPS REST or SOAP) |
| **Concurrency** | Max 20 parallel requests per OFD rate limit |
| **Confidence** | 🟡 Medium |
| **Rationale** | ОФД integration is on the backend, not the Android app. The app only sends fiscal documents to the backend; the backend handles ОФД protocol details. This keeps the Android app ОФД-agnostic. |

### 9.3 Честный ЗНАК (Marking)
| | |
|---|---|
| **API** | `POST /api/v1/chaseznak/validate` — backend proxy validates DataMatrix |
| **Flow** | Android scans → sends to backend → backend calls Честный ЗНАК API → returns status |
| **Local Module** | `features/chaseznak/` — enabled via feature flag |
| **Confidence** | 🟡 Medium |
| **Rationale** | Direct Честный ЗНАК API integration from Android is not standard — the backend acts as proxy (stores API keys, handles rate limits, caches results). The ЛМ (Local Module) ЧЗ may also run as a separate Android service on the device; if present, the app queries it instead of the backend. |
| **API details** | [markirovka.ru](https://markirovka.ru/business/integration/api/) — check current API version. The backend proxy routes `/api/v1/chaseznak/*` to the official ЧЗ API. |

### 9.4 ЕГАИС / УТМ
| | |
|---|---|
| **Integration** | Backend-side proxy — backend proxies to УТМ (Универсальный Транспортный Модуль) |
| **Local Module** | `features/egais/` — enabled via feature flag |
| **Protocol** | УТМ uses HTTP over TLS with client certificates |
| **Confidence** | 🟡 Medium |
| **Rationale** | ЕГАИС УТМ runs as a separate Windows/Linux service on the premises. The backend connects to УТМ (or a УТМ-proxy service). Android app sends alcohol sales to backend, backend forwards to УТМ. This isolates the complex ЕГАИС TLS/certificate logic from the mobile app. |
| **Digital ID Max** | QR code → backend `/api/v1/chaseznak/verify-age` → Max API → result |
| **API reference** | [egais.ru](https://egais.ru/) — УТМ documentation |

### 9.5 License Server
| | |
|---|---|
| **Endpoint** | `POST /api/v1/license/check` |
| **Grace period** | 7 days — counter stored in encrypted SharedPreferences/DataStore |
| **Check frequency** | At app start + every 24 hours via WorkManager |
| **Blocking** | App blocks fiscal operations on EXPIRED; allows reports and settings |
| **Confidence** | 🟢 High |
| **Rationale** | Standard license check pattern. The backend stores subscription status per cash register. Local grace period ensures fiscal continuity during brief network outages. |

---

## 10. Security

### 10.1 Android Security Stack
| Component | Library/Approach | Confidence |
|---|---|---|
| **Key storage** | Android Keystore (hardware-backed when available) | 🟢 High |
| **Local DB encryption** | SQLCipher 4.5.4 + Room `SupportFactory` | 🟢 High |
| **Network encryption** | TLS 1.2+ (OkHttp default), mTLS for ЕГАИС/ОФД | 🟢 High |
| **Root detection** | `RootBeer` library — check `su` binary, `test-keys` build tag, Magisk痕迹 | 🟡 Medium |
| **Audit log** | Room table + sync to backend | 🟢 High |
| **Preferences** | `security-crypto:1.1.0-alpha06` (`EncryptedSharedPreferences`) | 🟢 High |

### 10.2 Root Detection Decision
| | |
|---|---|
| **Decision** | Warn, do not hard-block |
| **Rationale** | Blocking on root causes support tickets from legitimate users who root for personal use. For a fiscal app, the real protection is TLS mutual auth + hardware-backed keystore + SELinux enforcing. Root detection can be a soft warning logged to the audit trail. |

---

## 11. CI/CD

### 11.1 GitHub Actions

#### Deterministic Gate (`ci-deterministic.yml`)
| | |
|---|---|
| **Triggers** | `push` to main/master, `pull_request` |
| **Jobs** | `backend-tests` (Windows, JDK 17, Gradle) → `android-unit-tests` (Windows, JDK 17, Android SDK) → `android-assemble-debug` (Windows, JDK 17, Android SDK) |
| **Timeout** | 25 min backend, 40 min Android unit, 40 min assemble |
| **Artifacts** | JUnit XML reports, APK, build logs |
| **Confidence** | 🟢 High |
| **Rationale** | Already implemented. Windows runners match the dev environment. Sequential dependencies (unit tests → assemble) fail fast. Concurrency group cancels in-progress runs on new commits. |

#### Emulator Smoke (`ci-emulator-smoke.yml`)
| | |
|---|---|
| **Trigger** | `schedule: "17 2 * * *` (daily at 02:17 UTC), `workflow_dispatch` |
| **Runner** | `ubuntu-latest` with KVM (`ReactiveCircus/android-emulator-runner@v70f4dee`) |
| **Emulator** | API 34, x86_64, Google APIs, Pixel 6 profile |
| **Test** | `connectedDebugAndroidTest` — all instrumented tests |
| **Confidence** | 🟢 High |
| **Rationale** | Runs on schedule to catch device-level regressions. KVM enables hardware acceleration. API 34 is the target SDK. Pixel 6 profile is representative of modern devices. `no-snapshot` ensures clean boot each run. |

### 11.2 Firebase App Distribution
| | |
|---|---|
| **Plugin** | `com.google.firebase:firebase-appdistribution-gradle` (optional, not yet in project) |
| **Confidence** | 🟡 Medium |
| **Rationale** | Firebase App Distribution is the standard for beta Android distribution — faster than Play internal testing tracks, supports tester groups, automatic crash reporting. Recommended for distributing pre-release builds to QA and external testers. |
| **What NOT to use** | Manual APK sharing (Slack/email) — no version control, no crash reports. |
| **Migration** | Add `google-services.json`, apply plugin, configure `appDistribution` block in `app/build.gradle.kts`. |
| **Alternative** | [Appaloosa](https://www.appaloosa-store.com/) — enterprise-friendly, GDPR-compliant EU hosting. Use if Firebase is blocked by enterprise firewall policy. |

### 11.3 Dependabot
| | |
|---|---|
| **Config** | `.github/dependabot.yml` — weekly GitHub Actions updates |
| **Confidence** | 🟢 High |
| **Rationale** | Already configured. Add Android library updates: `package-ecosystem: "gradle"` pointing to `android/` directory. Add backend: `package-ecosystem: "gradle"` for `backend/`. |

---

## 12. Summary Table

| Layer | Technology | Version | Confidence | In Project |
|---|---|---|---|---|
| **Language** | Kotlin | 1.9.22 | 🟢 High | ✅ Yes |
| **Build** | AGP | 8.2.2 | 🟢 High | ✅ Yes |
| **Build** | KSP | 1.9.22-1.0.17 | 🟢 High | ✅ Yes |
| **DI** | Hilt | 2.50 | 🟢 High | ✅ Yes |
| **UI** | Compose BOM | 2024.01.00 | 🟢 High | ✅ Yes |
| **UI** | Compose Compiler | 1.5.8 | 🟢 High | ✅ Yes |
| **Navigation** | Navigation Compose | 2.7.6 | 🟢 High | ✅ Yes |
| **Local DB** | Room | 2.6.1 | 🟢 High | ✅ Yes |
| **DB Encryption** | SQLCipher | 4.5.4 | 🟢 High | ❌ Add |
| **Preferences** | DataStore | 1.0.0 | 🟢 High | ❌ Add |
| **Networking** | Retrofit | 2.9.0 | 🟢 High | ✅ Yes |
| **Networking** | OkHttp | 4.12.0 | 🟢 High | ✅ Yes |
| **Async** | Coroutines | 1.7.3 | 🟢 High | ✅ Yes |
| **Background** | WorkManager | 2.9.0 | 🟢 High | ✅ Yes |
| **Barcode** | CameraX | 1.3.4 | 🟢 High | ✅ Yes |
| **Barcode** | ML Kit | 17.3.0 | 🟢 High | ✅ Yes |
| **Security** | security-crypto | 1.1.0-alpha06 | 🟢 High | ✅ Yes |
| **Backend** | Spring Boot | 3.2.2 | 🟢 High | ✅ Yes |
| **Backend DB** | PostgreSQL | 16+ | 🟢 High | ❌ Infra |
| **Backend Queue** | Redis Streams | 7+ | 🟢 High | ❌ Infra |
| **Migrations** | Flyway | (from Spring) | 🟢 High | ✅ Yes |
| **Fiscal** | MSPOS-K Service | — | 🟢 High | ✅ Partial |
| **Fiscal** | Нева 01Ф delegation | — | 🟡 Medium | ✅ Partial |
| **Marking** | ЧЗ API (backend proxy) | — | 🟡 Medium | ❌ Backend |
| **ЕГАИС** | УТМ (backend proxy) | — | 🟡 Medium | ❌ Backend |
| **CI** | GitHub Actions | — | 🟢 High | ✅ Yes |
| **Distribution** | Firebase App Distribution | — | 🟡 Medium | ❌ Add |
| **Dependency updates** | Dependabot | — | 🟢 High | ✅ Partial |

---

## 13. Gaps to Fill

| # | Gap | Priority | Action |
|---|---|---|---|
| G-01 | SQLCipher integration with Room | **High** | Add `net.zetetic:android-database-sqlcipher:4.5.4` + configure `SupportFactory` |
| G-02 | DataStore migration (replace SharedPreferences) | **Medium** | Replace grace period counter, last sync timestamp |
| G-03 | Firebase App Distribution | **Medium** | Add plugin, `google-services.json`, appDistribution block |
| G-04 | Dependabot for Gradle | **Medium** | Add `package-ecosystem: "gradle"` for `android/` and `backend/` |
| G-05 | Hilt upgrade to 2.51+ | **Low** | After Kotlin 2.0 migration confirmed |
| G-06 | AGP upgrade to 8.5.0 | **Low** | Straightforward bump, verify build |
| G-07 | Compose BOM upgrade | **Low** | Track Compose 2.0 BOM release, migrate when stable |

---

## 14. What NOT to Use and Why

| Technology | Why Avoid |
|---|---|
| **Realm** | Larger APK, runtime DI model, pollutes domain layer with `RealmObject`. Room is lighter, compile-time safe, and Google-supported. |
| **GraphQL** | Overkill for this API shape. REST covers all sync use cases. GraphQL adds codegen, cache complexity, and a second client library. |
| **Koin** | Runtime-only DI — bugs surface at runtime on 200 devices. Hilt's compile-time graph is non-negotiable for a fiscal production app. |
| **ZXing** | Less accurate on DataMatrix, no ML-based improvements. ML Kit outperforms on real-world receipt scanning conditions. |
| **AlarmManager** | Does not survive Doze. Use WorkManager for reliable background scheduling. |
| **Apache HttpClient** | Deprecated on Android API 23+. OkHttp 4.x is the standard. |
| **SharedPreferences** | No coroutines support, no type safety, no migration API. Use DataStore Preferences. |
| **Volley** | Legacy, no coroutines, no OkHttp interceptor model. Retrofit is strictly superior. |
| **Kotlin 2.0** (now) | Compose compiler not yet fully stable on 2.0 IR. Lock to 1.9.x until ecosystem catches up. |
| **AGP 9.x** | Requires Gradle 8.7+, JDK 21+. No new features needed from 9.x for this project. |

---

*Sources: Existing project code (`android/build.gradle.kts`, `backend/build.gradle.kts`), architecture spec (`docs/superpowers/specs/2026-04-08-vitbon-kkm-design.md`), Neva 01Ф ADR (`docs/superpowers/specs/2026-05-27-neva01f-sdk-justification-design.md`), e2e tests (`docs/e2e-tests.md`), release checklist (`docs/release-checklist.md`), GitHub Actions workflows (`.github/workflows/`), manuals (`docs/manuals/`).*
