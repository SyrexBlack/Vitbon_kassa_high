# PITFALLS-v1.1 — Production Readiness Hardening & Testing

**Domain:** Android fiscal POS (54-ФЗ) — adding hardening/testing/sandbox features to an already-shipping v1.0 system  
**Researched:** 2026-06-21  
**Confidence:** HIGH (pitfalls grounded in v1.0 PITFALLS.md, STATE.md open items, and regulatory realities of 54-ФЗ / ФФД 1.05/1.2 / Честный ЗНАК / ЕГАИС)  
**Downstream consumer:** Phase planner for v1.1 Production Readiness (sandbox integration, 24h stress, 200+ load, key rotation, ФН replacement, mTLS, token lifecycle)

---

## Executive Summary — Top 5 Hardening Pitfalls

| # | Pitfall | Severity | Why it bites v1.1 specifically |
|---|---------|----------|--------------------------------|
| 1 | Sandbox test data leaks into production code path | **P1 Critical** | Synthetic fiscal signs (P1.2) return through a test-only `FakeFiscalCore` if wiring is wrong |
| 2 | SQLCipher key-zeroization race corrupts open transactions | **P1 Critical** | In-flight sync writes hit a half-zeroed key → permanent WAL corruption |
| 3 | 24h Doze/App Standby silences SyncUpWorker → silent queue overflow | **P1 Critical** | v1.0's 500-doc cap is rendered useless if WorkManager is deferring indefinitely |
| 4 | Mutual TLS clock skew blocks 200+ clients at peak | **P1 Critical** | All devices synced via NTP miss `notBefore/notAfter` window; ОФД proxy returns 403 storm |
| 5 | ФН replacement wizard triggered mid-operation → ФН sequence gap | **P1 Critical** | GAP-01 from v1.0 — still unaddressed, now blocking production gate |

**Recovery cost if any of the above occur:** HIGH — all require on-site engineer visit or ФН re-registration.

---

## P1 — Critical (blocks production certification or causes fiscal data loss)

### P1.1: Sandbox test fixtures leak into production code path via `FiscalCoreFactory`

**What goes wrong:**
v1.0 ships `di/FiscalCoreFactory.kt` with a private `FakeFiscalCore` used for debug builds and instrumented tests. v1.1 adds sandbox integration (ОФД / ЧЗ / УТМ / Цифровой ID Max) and runs integration tests that bind the same factory. If `createFiscalCore()` is ever called with a sandbox-flag that doesn't gate correctly, the device can boot with `FakeFiscalCore` and emit synthetic fiscal signs (`MSP_12345`) into the live ФН.

**Why it happens:**
- Build types (`debug`, `release`) and sandbox flags (`isSandbox = true`) become entangled. A missing `else` branch defaults to fake.
- Tests mock `FiscalCore` directly instead of going through the factory, so the production wiring is never exercised in CI.
- `BuildConfig.IS_SANDBOX` is read in the wrong process (e.g., application vs. background worker).

**How to avoid:**
- `FiscalCoreFactory.createFiscalCore()` returns the production adapter by default; sandbox adapter is selected by a single sealed class `FiscalCoreVariant { PRODUCTION, SANDBOX_OFD, SANDBOX_CHZ }` passed explicitly.
- Compile-time guard: `FakeFiscalCore` constructor is `internal` and lives in `src/debug/java`. Release builds literally cannot link to it.
- Add a `FiscalCoreSanityCheck` invoked at app startup: if `BuildConfig.DEBUG == false && BuildConfig.IS_SANDBOX == false` and `FiscalCore is FakeFiscalCore` → crash with `FiscalInvariantViolation`.
- Integration test: `release build assembled with sandbox flag → install → attempt sale → assert FiscalError.NonRecoverable`.

**Warning signs:**
- `FakeFiscalCore` is referenced from any non-test `.kt` file.
- `BuildConfig.IS_SANDBOX` reads in more than 2 places.
- CI matrix does not include a `release+sandbox` variant.

**Phase to address:** Phase G (Sandbox Integration) — first gate must prove no fake-core contamination.

---

### P1.2: SQLCipher key rotation — zeroization race corrupts in-flight transactions

**What goes wrong:**
v1.0 uses `SupportFactory` with a key from Android Keystore. v1.1 introduces periodic key rotation (90-day cadence per regulatory recommendation). If a `SyncUpWorker` holds an open Room transaction when `rotateKey()` is invoked (writes happen, then journal is committed to a database file already moved/rekeyed), SQLCipher's WAL grows corrupted and the next app start fails to open the database → total loss of unsynced checks.

**Why it happens:**
- Room transactions are not stopped during key rotation.
- SQLCipher `PRAGMA rekey` requires exclusive access — concurrent readers/writers cause silent corruption.
- The new key is committed to Keystore before the database is fully rewritten; a crash mid-rotation leaves an invalid key binding.

**How to avoid:**
- `KeyRotationOrchestrator` must (1) acquire a single `Mutex` per `RoomDatabase` instance, (2) cancel all `WorkManager` workers touching the DB, (3) call `Room.databaseBuilder.fallbackToDestructiveMigration` is OFF — must use manual SQLite backup-and-restore, (4) verify the new key works before deleting the old one.
- Implement a two-phase commit: write new key to Keystore → rekey DB → verify read+write → delete old key. Any failure reverts to old key.
- Add a startup check: if the DB cannot be opened with the expected key, attempt the previous key (last 3 stored) before declaring corruption.
- 24h stress test must include a forced key rotation at hour 12 with active sync workers.

**Warning signs:**
- `Room.databaseBuilder` is not wrapped in a synchronization primitive.
- No WAL checkpoint (`PRAGMA wal_checkpoint(TRUNCATE)`) before rekey.
- `KeyStore.deleteEntry()` called without first verifying the new DB is functional.

**Phase to address:** Phase I (Key Rotation Test) — must run as part of 24h offline stress (Phase H).

---

### P1.3: 24-hour offline stress — Android Doze / App Standby defers SyncUpWorker indefinitely

**What goes wrong:**
v1.0 uses WorkManager with `PeriodicWorkRequest` (30s minimum) and `OneTimeWorkRequest` on network restoration. v1.1's 24h offline stress test puts the device in airplane mode for 24h, then restores network. Under Doze mode (API 23+), maintenance windows are rare (typically once every 15min when stationary, much less when idle). The 500-doc queue cap can be hit even on a single quiet shift if WorkManager defers sync by hours.

**Why it happens:**
- v1.0's `SyncUpWorker` uses default `NetworkType.CONNECTED` constraint — fine when online, useless for catch-up after long offline.
- App is in App Standby bucket `WORKING_SET` or `RARE` — deferral time grows linearly with bucket age.
- Battery optimization (Doze + App Standby combined) can defer a periodic worker for hours on OEM-customized ROMs (Xiaomi MIUI, Huawei EMUI, Samsung).

**How to avoid:**
- Use `setExpedited()` for the post-network-restoration catch-up sync — expedited workers run within ~10 minutes regardless of Doze (API 31+) or fallback to foreground service on older APIs.
- For 24h stress: pre-flight check that `WorkManager.getInstance().getWorkInfosForUniqueWork("catch-up-sync")` shows `RUNNING` within 60s of network restoration.
- Add `MON-02` indicator that surfaces Doze deferral time: if `lastSync > 30min` while network is `CONNECTED`, show "Синхронизация отложена Doze".
- Document OEM battery whitelist requirements in the operator onboarding manual (the app must be added to "Не оптимизировать").

**Warning signs:**
- `PeriodicWorkRequest.Builder` does not call `.setExpedited()` for catch-up.
- No foreground service fallback for API <31 devices.
- 24h test passes on Pixel emulator but fails on Xiaomi/Huawei device.

**Phase to address:** Phase H (24h Offline Stress) — this is THE central test of v1.1.

---

### P1.4: Mutual TLS clock skew — 200+ clients fail cert validation simultaneously

**What goes wrong:**
SEC-05 requires mTLS for cloud connection. v1.1 introduces cert management (rotation, OCSP stapling). If any of the 200+ devices has a clock skewed by >5 minutes (common on devices without NTP sync, or after a dead battery), `notBefore` validation fails on the server side → all TLS handshakes fail → sync stops fleet-wide. Server CPU spikes from the 403 storm before certs are revoked.

**Why it happens:**
- Android `NetworkTimeUpdateService` requires internet to sync — but internet comes *through* the cloud API that's being validated. Chicken-and-egg.
- Devices in offline storage / new out-of-box have clocks from factory (often wrong by hours if CMOS battery died).
- OEM ROMs may not run NTP sync at all on cellular-only devices.

**How to avoid:**
- Implement `CertificatePinner` with `notBefore` tolerance of ±15 minutes (industry standard for mobile).
- Server-side: clock skew tolerance of ±10 minutes; log but don't 403 on minor skew, only on expired (`notAfter < now`).
- Add `BootReceiver` that triggers NTP sync immediately after device boot using a public NTP pool (`ru.pool.ntp.org` for RU devices).
- Add a "Diagnose connection" admin tool that surfaces current device time + server time + skew in seconds.
- Pre-flight in load test: deliberately skew 5 devices' clocks by +20min and -20min, verify they still sync.

**Warning signs:**
- `OkHttpClient.certificatePinner()` not configured with skew tolerance.
- No `BootReceiver` triggering time sync.
- Server-side TLS config rejects requests with `notBefore` in future.

**Phase to address:** Phase J (mTLS Cert Management) — gate is OCSP + skew + 200-client load.

---

### P1.5: ФН replacement mid-shift — FnRegistration state corrupted, fiscal sequence gap

**What goes wrong:**
v1.0 documented GAP-01 (ФН replacement flow) but didn't implement it. v1.1 must add the wizard. If the wizard is triggered while a sale is in flight (cashier starts sale → admin triggers ФН replacement from settings → cashier clicks "Pay"), the new ФН serial gets associated with an in-progress sale → ФН sequence gap (new ФН expects document #1, receives document #47) → catastrophic.

**Why it happens:**
- ФН replacement requires Z-report first (close current shift) + new fiscal registration (ФН re-registration ceremony with new INN/ОФД contract).
- Without atomic state management, "FN replaced" event can race with `ProcessSaleUseCase.execute()`.
- v1.0's `FiscalConfig` stores a single `fnSerial` field — no history, no active/inactive discrimination.

**How to avoid:**
- `FnRegistration` lifecycle is a state machine: `ACTIVE → CLOSING → REPLACED`. Transitions are atomic via Room transaction.
- `ProcessSaleUseCase` checks `FnRegistration.current.isAcceptingOperations() == true` before every fiscal op. If false → reject with explicit "ФН заменяется".
- Replacement wizard requires: (1) shift must be CLOSED, (2) all PENDING_SYNC checks must be flushed to cloud, (3) Z-report printed, (4) new ФН physically installed, (5) new registration performed — all in a single admin-only wizard.
- `LocalCheck` records `fnSerial` at insert time (not at update time). Reports group by `fnSerial`.
- OFD document delivery window must be respected: documents created within 30 days must be delivered before ФН archival.

**Warning signs:**
- `FiscalConfig` still has a single `fnSerial` field (no list of registrations).
- "Заменить ФН" button accessible from main screen (not gated to admin).
- No atomic guard preventing `printSale()` during `REPLACING` state.

**Phase to address:** Phase K (ФН Replacement) — explicit v1.0 GAP-01 closure.

---

### P1.6: Sandbox ОФД quota exhaustion — Честный ЗНАК validation stuck behind 429

**What goes wrong:**
ОФД sandbox and Честный ЗНАК test environment both impose rate limits (typically 60 req/min for sandbox). During load testing 200+ cash registers sending marked-goods sales concurrently, sandbox returns 429 (Too Many Requests) → app interprets as "ЧЗ unavailable" → marks all marked goods sales as blocked → massive false rejections in test report.

**Why it happens:**
- Sandbox quotas are designed for *integration* testing, not *load* testing. Quotas are per-IP, per-API-key, per-day.
- Sandbox error response codes may differ from production (sandbox returns 429 with `Retry-After`, production returns 503).
- App treats all 4xx/5xx the same way ("API недоступен") — losing information about whether retry would help.

**How to avoid:**
- Sandbox integration tests run sequentially per device, not concurrently. Use device sharding: 200 devices → 10 devices × 20 shards.
- `ChaseznakApi` and `OFDClient` parse `Retry-After` header explicitly; respect it.
- Add a separate error class `ValidationError.QUOTA_EXCEEDED` distinct from `ValidationError.SERVICE_DOWN`.
- Use ЧЗ test environment's "infinite quota" tier if available (most sandbox APIs have it).
- Document the quota explicitly in the sandbox integration runbook: "X requests/min/IP, Y requests/day/key".

**Warning signs:**
- Sandbox test results show 90%+ failure rate under "load".
- No `Retry-After` header parsing in OkHttp interceptors.
- Same API key used by all 200 test devices.

**Phase to address:** Phase G (Sandbox Integration) — must be rate-limit-aware from the start.

---

### P1.7: УТМ (ЕГАИС) sandbox — TLS handshake to test CA fails because prod CA is hardcoded

**What goes wrong:**
УТМ in production speaks to `utm.fsrar.ru` with the ФСРАР CA cert. Sandbox УТМ uses a different test CA. v1.0 hardcoded the prod CA pin; v1.1 sandbox tests fail TLS handshake because the test cert doesn't match the pin.

**Why it happens:**
- `OkHttpClient.certificatePinner().add("utm.fsrar.ru", "sha256/...")` is a hardcoded prod pin.
- No mechanism to switch pins between sandbox and production.
- УТМ uses mutual TLS with a client certificate per organization; sandbox issues a different test cert.

**How to avoid:**
- Pin management is environment-driven: `BuildConfig.UTM_HOST` and `BuildConfig.UTM_PIN` both switch together.
- Test certs are accepted only when `BuildConfig.IS_SANDBOX == true`.
- Add a `SslContextFactory` that returns the appropriate `SSLSocketFactory` per environment.
- Sandbox integration test must include TLS handshake to test УТМ, not just HTTP-level mocking.

**Warning signs:**
- УТМ client only works with prod CA.
- Switching `UTM_HOST` alone causes TLS failure.
- Sandbox tests use `MockWebServer` instead of real test УТМ.

**Phase to address:** Phase G (Sandbox Integration).

---

## P2 — High (causes operational outages or data corruption)

### P2.1: Redis Streams backpressure — 200+ clients overwhelm consumer group

**What goes wrong:**
Backend uses Redis Streams for ordered, persistent check queuing. With 200+ cash registers uploading concurrently, consumer group lag grows. If a single consumer dies, XCLAIM must reassign pending entries; without proper consumer-group sizing, messages stall in PEL (Pending Entries List) and ack never arrives.

**Why it happens:**
- Default `XREADGROUP` block timeout is too short (100ms) → busy-loop on consumer.
- Consumer count is fixed (e.g., 4) regardless of load.
- Idle consumer reassignment uses default `min-idle-time` (60s) — too short for 24h stress, leads to duplicate processing.

**How to avoid:**
- Consumer count scales with backlog: target `lag / processing_rate * 1.5`.
- `XREADGROUP BLOCK 5000` minimum; `COUNT 100` per batch.
- `XCLAIM` with `min-idle-time 300000` (5min) — allows graceful restart without false reassignment.
- Add Prometheus metric `redis_stream_consumer_lag_seconds`; alert at >60s.
- Load test must verify ack time stays <1s p99 under 200 concurrent clients.

**Warning signs:**
- No `XCLAIM` task in backend.
- `min-idle-time` left at default.
- No backlog monitoring.

**Phase to address:** Phase L (Load Test 200+).

---

### P2.2: Token revocation propagation delay — 200+ clients retain revoked refresh token

**What goes wrong:**
v1.0 has Bearer token auth (P4.3 — token lifecycle was tech debt). v1.1 adds revocation. If revocation propagation is async (e.g., via Redis pub/sub), a revoked token may still be accepted by some backend replicas for up to 30s. With 200+ clients, replay window enables stolen token use.

**Why it happens:**
- Refresh tokens have long TTL (7 days).
- Revocation list is checked only at refresh time, not per-request.
- Backend is horizontally scaled; token cache is per-instance with TTL.

**How to avoid:**
- Use `refresh_token_reuse_detection` (RFC 6749 + draft-ietf-oauth-security-topics): when a refresh token is used twice, revoke the entire token family.
- Per-request JWT validation includes `jti` checked against a revocation set in Redis with sub-second replication.
- Token TTL: access 15min, refresh 7 days, refresh rotation on every use.
- Add propagation delay metric; alert if >5s.

**Warning signs:**
- Refresh tokens used more than once without rotation.
- Revocation set is per-instance only.
- No `jti` claim in JWT.

**Phase to address:** Phase M (Token Lifecycle).

---

### P2.3: License grace period miscalculation during 24h stress — false-positive license block

**What goes wrong:**
LIC-03 grace period is 7 days from `expiresAt`. During 24h offline stress, if the test crosses `expiresAt + 7 days` (unlikely but possible with expired test license), the app blocks fiscal operations offline. More realistically: clock drift during stress test (see P1.4) causes `expiresAt` to appear already passed → block.

**Why it happens:**
- v1.0's `LicenseChecker` reads `expiresAt` from local cache, not from a fresh server response.
- Clock drift during stress test shifts `now` relative to `expiresAt`.
- Backend returns `expiresAt` in `Instant` but Android device time is in `System.currentTimeMillis()`; timezone conversion errors.

**How to avoid:**
- License check uses server-relative time when available (`X-Server-Time` header).
- Stress test license is deliberately extended past `expiresAt` to verify grace logic.
- Add explicit log: "License check: now=X, expiresAt=Y, delta=Z days" for forensic debugging.

**Warning signs:**
- Stress test shows license block mid-test.
- Grace period logic uses `System.currentTimeMillis()` directly without server-time sync.

**Phase to address:** Phase H (24h Stress) — must include license edge cases.

---

### P2.4: OFD document delivery window missed during ФН archival

**What goes wrong:**
54-ФЗ requires ФН documents to be delivered to ОФД within 30 days (or 5 days for some categories). During ФН replacement (P1.5), the old ФН is archived. If any documents are still PENDING_OFD_DELIVERY on the old ФН, they must be delivered to ОФД *before* ФН archival — otherwise they become undeliverable.

**Why it happens:**
- `SyncUpWorker` uploads checks to backend, backend pushes to ОФД. This is a multi-hop pipeline with its own retry logic.
- ФН replacement wizard doesn't check the OFD delivery queue.
- Old ФН may have 5,000+ undelivered checks; OFD API has its own rate limit.

**How to avoid:**
- `FnReplacementWizard` requires OFD delivery queue to be empty (or within last 30 days) before allowing replacement.
- Surface "N чеков ожидают отправки в ОФД" warning before Z-report of old shift.
- Add `OFD_DELIVERY_LAG` indicator in `MON-03`.

**Warning signs:**
- "Заменить ФН" wizard has no OFD queue check.
- `MON-03` shows only OFD connection status, not undelivered count.

**Phase to address:** Phase K (ФН Replacement).

---

### P2.5: Connection pool exhaustion — 200+ clients saturate backend Tomcat threads

**What goes wrong:**
Spring Boot 3.2.2 default `server.tomcat.threads.max=200`. With 200+ clients each holding a long-lived mTLS connection + REST sync every 30s, thread pool saturates. Requests queue → 60s timeout → client retries → thundering herd.

**Why it happens:**
- Default Tomcat thread pool sized for development.
- mTLS handshake is CPU-intensive (RSA-2048); 200 simultaneous handshakes can stall the JVM.
- No connection rate limiting per device.

**How to avoid:**
- `server.tomcat.threads.max=400` for production.
- mTLS session resumption (TLS 1.3) reduces handshake CPU by 80%.
- Per-device rate limit: 60 req/min per `deviceId`.
- Add circuit breaker (Resilience4j) on OFD proxy calls.

**Warning signs:**
- p99 sync latency >5s under load.
- No thread pool monitoring.
- No TLS session resumption.

**Phase to address:** Phase L (Load Test 200+).

---

### P2.6: Hot-reload of mTLS cert without restart — in-flight requests abort

**What goes wrong:**
mTLS cert rotation without app restart (rolling deployment) requires reloading `KeyStore` and `TrustManager` mid-flight. In-flight OkHttp calls hold references to the old `SSLSocketFactory` → TLS handshake fails for new requests after rotation → 401/403 storm.

**Why it happens:**
- `OkHttpClient` is a singleton; once built, swapping `sslSocketFactory()` requires client rebuild.
- Active connections aren't migrated; they fail on next read.
- Cert rotation script doesn't drain in-flight requests first.

**How to avoid:**
- Build a new `OkHttpClient` on cert rotation; old client drains within 30s.
- Use a `ConnectionPool` with explicit `evictAll()` after 60s grace.
- Cert rotation runs during scheduled maintenance window (low traffic).
- App-side: cert cache TTL of 24h; refresh on `BackgroundWorker` once per day.

**Warning signs:**
- `OkHttpClient` rebuild not tested under load.
- No drain timer after cert swap.

**Phase to address:** Phase J (mTLS Cert Management).

---

### P2.7: Sandbox license server returns different grace logic than production

**What goes wrong:**
v1.1 tests license flow against sandbox license server. Sandbox returns grace period of 1 day for testing; production returns 7 days. App caches the response and applies 1-day grace in production → premature block.

**Why it happens:**
- Sandbox and prod license configs diverge.
- App treats all license responses identically.
- `LicenseStatus.gracePeriodDays` field comes from server, not hardcoded.

**How to avoid:**
- License config (grace period days) is hardcoded in app; server returns only `expiresAt`.
- Sandbox uses a separate `LicenseServerClient` with explicit test fixtures.
- Document the contract: server returns `expiresAt`, app applies 7-day grace.

**Warning signs:**
- Sandbox grace period ≠ production grace period.
- App reads grace days from server response.

**Phase to address:** Phase G (Sandbox Integration) — must validate license contract.

---

## P3 — Medium (causes degraded UX or compliance risk)

### P3.1: SQLCipher WAL growth during 24h offline — storage pressure

**What goes wrong:**
SQLCipher with default `journal_mode=WAL` accumulates a `-wal` file during long offline periods. After 24h offline with 500-doc queue cap, the WAL file can grow to tens of MB → app data directory fills up → next sync fails on disk full.

**Why it happens:**
- WAL is only truncated on `PRAGMA wal_checkpoint(TRUNCATE)`.
- Each sync worker run may do small write transactions, keeping the WAL active.
- App data directory has OEM-imposed quota (some Xiaomi devices cap at 200MB).

**How to avoid:**
- `SyncUpWorker` calls `PRAGMA wal_checkpoint(TRUNCATE)` after each successful batch.
- Storage pressure indicator in `MON-02`: "X MB из Y MB использовано".
- 24h stress test must monitor app data directory size; alert if >100MB.

**Warning signs:**
- No `wal_checkpoint` call in sync worker.
- Storage quota not monitored.

**Phase to address:** Phase H (24h Stress).

---

### P3.2: Room migration during 24h offline — schema version mismatch crash

**What goes wrong:**
v1.1 may ship a Room schema migration. If the app is updated *during* the 24h offline period (silent background update), the next launch finds v2 schema but app code is v1 → crash on first DAO access.

**Why it happens:**
- `UPDT-01` allows remote updates, but migration ordering isn't enforced.
- Auto-update from Play Store can land mid-stress-test.
- `fallbackToDestructiveMigration()` wipes fiscal data — catastrophic.

**How to avoid:**
- All Room migrations are tested with `MigrationTestHelper` covering v1→v2, v2→v3, etc.
- App refuses to start if migration is missing; surfaces "Обновите приложение".
- 24h stress test disables auto-update.
- `fallbackToDestructiveMigration()` is **never** enabled for fiscal tables.

**Warning signs:**
- `Room.databaseBuilder.fallbackToDestructiveMigration()` not commented out.
- No migration tests.

**Phase to address:** Phase H (24h Stress) + Phase G.

---

### P3.3: mTLS cert chain validation — intermediate CA missing in trust store

**What goes wrong:**
Device's `KeyStore` has the leaf cert + root CA but missing intermediate CA → chain validation fails → TLS handshake fails → sync stops. Common when cloud provider rotates their intermediate CA.

**Why it happens:**
- Apps bundle only the leaf cert, not the full chain.
- Android system trust store changes between versions.
- Cloud provider rotates intermediate CA without notice.

**How to avoid:**
- Bundle the full chain (leaf + intermediate + root) in `res/raw/`.
- `CertificatePinner` includes both intermediate and root pins.
- Add `NetworkSecurityConfig` with explicit `<pin-set>` for the cloud domain.
- Cert rotation runbook includes intermediate CA verification step.

**Warning signs:**
- Only leaf cert in trust store.
- No intermediate CA in `CertificatePinner`.

**Phase to address:** Phase J (mTLS).

---

### P3.4: Цифровой ID Max sandbox — rate limits + age data validity

**What goes wrong:**
Sandbox Цифровой ID Max API has different rate limits and returns fake age data. If integration tests rely on sandbox returning `age >= 18`, production API may have stricter validation (e.g., requires photo ID) → real-world age check fails.

**Why it happens:**
- Sandbox is for *protocol* testing, not *data accuracy*.
- Sandbox may skip biometric verification that production requires.
- App doesn't distinguish sandbox vs prod error responses.

**How to avoid:**
- Sandbox tests verify only the request/response *protocol*, not the business outcome.
- Document explicitly: "Sandbox ЦИМ returns synthetic age data; do not test age threshold logic against sandbox."
- Production verification uses real test users with consent.

**Warning signs:**
- Sandbox test for age verification marked as "passed".
- App treats sandbox `age=18` as proof of working logic.

**Phase to address:** Phase G (Sandbox Integration).

---

### P3.5: Load test produces false negatives — backend not warmed up

**What goes wrong:**
Load test of 200+ clients runs immediately after backend deploy. JIT compilation not warmed, DB connection pool cold, Redis cache empty. First 100 requests show 5xx → test fails → team re-runs → test passes second time.

**Why it happens:**
- Spring Boot JVM needs warmup (~5min of traffic) to reach steady-state performance.
- DB pool starts at minimum size; grows on demand.
- No warmup phase in load test script.

**How to avoid:**
- Load test includes 10min warmup phase with synthetic low-rate traffic.
- Report only metrics after warmup (e.g., drop first 10min from results).
- Spring Boot Actuator pre-warms via `/actuator/health` calls.

**Warning signs:**
- First 5min of load test report included in metrics.
- No warmup phase.

**Phase to address:** Phase L (Load Test 200+).

---

## P4 — Low (technical debt that compounds)

### P4.1: Sandbox test fixtures remain in production APK

**What goes wrong:**
Debug-only test classes (e.g., `FakeFiscalCore`, `MockChaseznakApi`) leak into release APK if `src/debug/java` isn't strictly separated from `src/main/java`.

**Prevention:**
- Use `src/debug/java` and `src/release/java` source sets exclusively.
- ProGuard rules strip `Fake*` classes by name pattern.
- APK size check in CI: release APK should not increase by >50KB after sandbox code is "removed".

**Phase to address:** Phase G (Sandbox Integration) — must include APK inspection step.

---

### P4.2: OCSP stapling not implemented — cert revocation lag

**What goes wrong:**
If cloud cert is compromised, revocation via CRL can take hours to propagate. Without OCSP stapling, clients have no way to verify cert validity in real-time.

**Prevention:**
- Backend enables OCSP stapling on the TLS termination.
- OkHttp validates OCSP response if available; falls back to CRL.
- Cert rotation runbook includes OCSP verification.

**Phase to address:** Phase J (mTLS).

---

### P4.3: Token storage in EncryptedSharedPreferences — not hardware-backed

**What goes wrong:**
v1.0 stores refresh tokens in `EncryptedSharedPreferences`. On devices without hardware-backed Keystore (some budget Android devices), encryption keys are software-only → extractable with root → token theft.

**Prevention:**
- Use `MasterKey.Builder` with `KeyScheme.AES256_GCM` and `setUserAuthenticationRequired(true)` where supported.
- Devices without hardware Keystore must be in the "warning" list, not blocked.
- Document minimum OS version for hardware-backed Keystore (Android 7.0+ for most OEMs).

**Phase to address:** Phase M (Token Lifecycle).

---

## Technical Debt Patterns (v1.1-specific)

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Reuse prod cert for sandbox | No cert provisioning | Sandbox tests pollute prod trust store | **Never** |
| Skip 24h stress, rely on unit tests | Faster delivery | P1.3 Doze failures in production | **Never** for fiscal POS |
| Hardcode mTLS pin (no rotation) | Simpler config | P1.4 fleet-wide sync failure after cert expiry | **Never** |
| Mock ОФД sandbox with MockWebServer | Fast tests | P1.7 TLS handshake bugs only surface in prod | **Never** for full integration |
| Disable Doze optimization in tests | Tests run fast | Tests don't reflect real device behavior | **Acceptable** for unit tests only |
| Single-threaded load test | Simple script | P2.1 Redis Streams misconfiguration undetected | **Never** for production gate |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| **ОФД sandbox** | Treat 429 as terminal error | Parse `Retry-After`, exponential backoff, quota tracking |
| **Честный ЗНАК test env** | Same API key for all 200 test devices | Per-device test credentials, rate-limit-aware test orchestration |
| **УТМ test CA** | Hardcoded prod CA pin | Environment-driven pin + test CA accepted only when `IS_SANDBOX` |
| **Цифровой ID Max sandbox** | Test age threshold logic | Test protocol only; verify logic in production-like environment |
| **Backend Redis Streams** | Single consumer, default `XREADGROUP` block | Consumer group sized to lag, `BLOCK 5000`, `min-idle-time 300000` |
| **License server sandbox** | Use sandbox grace period in prod | Hardcode grace in app; sandbox returns only `expiresAt` |
| **Spring Boot thread pool** | Default `tomcat.threads.max=200` | 400 for prod; mTLS session resumption enabled |

---

## Performance Traps (v1.1-specific)

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| **Unbounded consumer lag** | Sync latency p99 >30s | Consumer group scales with backlog; alert at lag >60s | >200 clients with default 4-consumer group |
| **WAL file growth** | App data directory >100MB | `wal_checkpoint(TRUNCATE)` after each sync batch | 24h offline with active writes |
| **mTLS CPU saturation** | p99 handshake >2s | TLS 1.3 session resumption; per-device rate limit | >100 simultaneous new handshakes |
| **OCSP validation latency** | Sync p99 +500ms | OCSP stapling on backend; cache valid responses 24h | Without stapling |
| **Token JWT verification CPU** | Auth p99 +50ms | Redis-backed `jti` revocation set with 60s TTL | >50 req/s with revocation check |
| **SQLCipher rekey CPU** | First sync after rotation +5s | Rekey during scheduled maintenance; verify before delete | Hot rotation during peak |

---

## Security Mistakes (v1.1-specific)

| Mistake | Risk | Prevention |
|---------|------|------------|
| **Sandbox flag toggled in prod build** | Test endpoints reachable from prod | Compile-time guard: sandbox code in `src/sandbox/java` only |
| **mTLS cert in `res/raw/` unencrypted** | Cert extractable on rooted device | Hardware-backed Keystore for client cert |
| **OCSP response cached forever** | Stale validation after compromise | Max 24h cache; force refresh on cert rotation |
| **Token revocation set not replicated** | Replay window during partition | Redis with sync replication; alert on replication lag >1s |
| **License `expiresAt` from client clock** | Grace period miscalculated on clock skew | Server-time sync; tolerate ±10min skew |
| **SQLCipher key in plain DataStore** | Key extractable on rooted device | MasterKey in Android Keystore; AOSP-grade hardware backing |

---

## UX Pitfalls (v1.1-specific)

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| **"Sandbox mode" toggle visible to cashier** | Confusion, accidental enable | Admin-only, behind PIN, with confirmation dialog |
| **"Replace ФН" button on main screen** | Accidental trigger by cashier | Admin-only, requires shift CLOSED + queue empty |
| **Load test errors shown to user as "Сервер недоступен"** | False alarm during maintenance | Differentiate: "Сервер на обслуживании (до HH:MM)" |
| **Cert expiry warning only at expiry moment** | Sudden sync block | Warn 14 days, 7 days, 1 day before expiry |
| **24h stress test indicator in cashier UI** | Confusion | Test mode UI is admin-only and visually distinct |

---

## "Looks Done But Isn't" Checklist

- [ ] **Sandbox integration:** Often missing OCSP validation, TLS handshake to real test CA, quota handling — verify against actual sandbox endpoints (not `MockWebServer`)
- [ ] **24h offline stress:** Often missing OEM-specific Doze behavior — verify on ≥3 OEM ROMs (Pixel, Samsung, Xiaomi)
- [ ] **Load test 200+:** Often missing warmup phase — verify first 10min excluded from SLA metrics
- [ ] **SQLCipher key rotation:** Often missing crash recovery — verify mid-rotation crash leaves DB usable
- [ ] **ФН replacement:** Often missing OFD delivery check — verify queue empty before wizard proceeds
- [ ] **mTLS cert management:** Often missing intermediate CA — verify full chain bundled
- [ ] **Token rotation/revocation:** Often missing refresh-token-reuse-detection — verify token family revocation

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| **P1.1 Sandbox leak into prod** | HIGH | Emergency OTA update with `FakeFiscalCore` removed; audit all fiscal docs from affected devices |
| **P1.2 SQLCipher key corruption** | HIGH | Restore from pre-rotation backup; recreate DB from cloud sync (last 500 docs may be lost) |
| **P1.3 Doze-induced sync lag** | MEDIUM | Add OEM to whitelist guide; release patch with expedited worker |
| **P1.4 mTLS clock skew** | MEDIUM | Force NTP sync via admin tool; relax server-side skew tolerance |
| **P1.5 ФН replacement race** | HIGH | ФН re-registration ceremony on-site (ФНС visit required) |
| **P1.6 Sandbox quota 429** | LOW | Reschedule test; use sandbox infinite-quota tier |
| **P1.7 УТМ TLS pin mismatch** | MEDIUM | Push cert update via Firebase App Distribution; restart app fleet |
| **P2.1 Redis Streams lag** | MEDIUM | Scale consumer count; replay PEL |
| **P2.2 Token replay** | HIGH | Force token rotation fleet-wide; investigate breach |

---

## Pitfall-to-Phase Mapping (v1.1 Production Readiness)

> Phases G-M are proposed for the v1.1 milestone. See `v1.1-MILESTONE-AUDIT.md` (when generated) for final phase breakdown.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| P1.1 Sandbox leak | Phase G (Sandbox Integration) | `FiscalCoreSanityCheck` crash test in release+sandbox build |
| P1.2 SQLCipher rotation | Phase I (Key Rotation) + Phase H (24h stress) | Mid-rotation crash test + 24h run with rotation at hour 12 |
| P1.3 Doze deferral | Phase H (24h Stress) | 24h test on Xiaomi device with battery optimization enabled |
| P1.4 mTLS clock skew | Phase J (mTLS) + Phase L (Load Test) | Skew ±20min on 5 devices; verify sync |
| P1.5 ФН replacement | Phase K (ФН Replacement) | Atomic guard test; race condition test (sale + replacement concurrent) |
| P1.6 Sandbox quota | Phase G (Sandbox Integration) | Rate-limit-aware test orchestration; 429 handling test |
| P1.7 УТМ TLS pin | Phase G (Sandbox Integration) | Real test УТМ handshake (not mock) |
| P2.1 Redis Streams | Phase L (Load Test) | p99 ack <1s under 200 concurrent clients |
| P2.2 Token revocation | Phase M (Token Lifecycle) | Refresh-token-reuse-detection test |
| P2.3 License grace | Phase H (24h Stress) | License crossing `expiresAt` during stress test |
| P2.4 OFD delivery window | Phase K (ФН Replacement) | Wizard blocks until queue empty |
| P2.5 Thread pool | Phase L (Load Test) | p99 latency <5s under load |
| P2.6 Hot cert reload | Phase J (mTLS) | Cert rotation during sync; verify no in-flight abort |
| P2.7 License sandbox divergence | Phase G (Sandbox Integration) | Sandbox vs prod grace period parity check |
| P3.1 WAL growth | Phase H (24h Stress) | App data dir <100MB after 24h |
| P3.2 Room migration | Phase H (24h Stress) | Disable auto-update during test; migration test in CI |
| P3.3 Intermediate CA | Phase J (mTLS) | Full chain in `res/raw/`; intermediate in `CertificatePinner` |
| P3.4 ЦИМ sandbox data | Phase G (Sandbox Integration) | Protocol-only test in sandbox; logic test in prod-like env |
| P3.5 Load test cold start | Phase L (Load Test) | 10min warmup; exclude from SLA metrics |
| P4.1 Sandbox in APK | Phase G (Sandbox Integration) | APK inspection step in CI |
| P4.2 OCSP stapling | Phase J (mTLS) | Backend enables stapling; OkHttp validates |
| P4.3 Token storage | Phase M (Token Lifecycle) | Hardware-backed Keystore where available |

---

## Production-Readiness Gate Criteria (proposed)

For v1.1 to be declared "production-ready", ALL of the following must pass:

1. **Sandbox Integration (Phase G):**
   - [ ] ОФД sandbox: 100% of fiscal doc types pass with correct tags
   - [ ] ЧЗ sandbox: marked-goods sale flow validated
   - [ ] УТМ sandbox: alcohol sale flow validated
   - [ ] ЦИМ sandbox: age check protocol validated
   - [ ] All quotas respected; no 429 storms
   - [ ] No `FakeFiscalCore` in release APK (grep + APK size check)

2. **24h Offline Stress (Phase H):**
   - [ ] Queue depth stays <500 throughout
   - [ ] Sync resumes within 60s of network restoration
   - [ ] App data dir <100MB after 24h
   - [ ] License grace logic correct across `expiresAt` boundary
   - [ ] No crashes on Xiaomi/Samsung/Pixel (≥3 OEMs)
   - [ ] Room migration tested with v1 → v1.1 schema change

3. **SQLCipher Key Rotation (Phase I):**
   - [ ] Mid-rotation crash leaves DB usable
   - [ ] Rotation completes within 60s
   - [ ] Sync workers resume after rotation

4. **Load Test 200+ (Phase L):**
   - [ ] p99 sync latency <5s
   - [ ] Redis Streams lag <60s
   - [ ] Backend thread pool not saturated
   - [ ] mTLS handshake p99 <2s

5. **ФН Replacement (Phase K):**
   - [ ] Atomic guard prevents mid-sale replacement
   - [ ] OFD delivery queue must be empty
   - [ ] `FnRegistration` lifecycle tracked
   - [ ] Reports group by `fnSerial`

6. **mTLS (Phase J):**
   - [ ] Clock skew tolerance ±15min (client) / ±10min (server)
   - [ ] Intermediate CA bundled
   - [ ] Hot-reload without restart works
   - [ ] OCSP stapling validated

7. **Token Lifecycle (Phase M):**
   - [ ] Refresh-token-reuse-detection working
   - [ ] Revocation propagation <5s
   - [ ] Hardware-backed Keystore where available

---

## Sources

- `.planning/PROJECT.md` (v1.0 shipped, v1.1 scope)
- `.planning/research/PITFALLS.md` (v1.0 baseline — P1.2, P1.5, P2.5, P4.3 directly inform v1.1)
- `.planning/research/SUMMARY.md` (Findings 16-20)
- `.planning/STATE.md` (open items GAP-01 through GAP-05 — all addressed in v1.1)
- `.planning/milestones/v1.0-REQUIREMENTS.md` (SEC-05 mTLS, LIC-01..03, MARK-01..06, ALCO-01..05)
- `.planning/milestones/v1.0-ROADMAP.md` (Phase G-M proposed for v1.1)
- 54-ФЗ regulatory text (ОФД delivery 30-day window)
- ФН firmware behavior (sequence gap on replacement)
- УТМ ФСРАР documentation (test CA, prod CA distinction)
- Честный ЗНАК sandbox documentation (quota tiers)
- WorkManager + Doze behavior (API 23+, OEM variations)
- SQLCipher 4.5.4 rekey semantics
- OkHttp 4.12.0 + mTLS + OCSP documentation

---

*Pitfalls research for: VITBON v1.1 Production Readiness — sandbox integration, 24h stress, load test, key rotation, ФН replacement, mTLS, token lifecycle*  
*Researched: 2026-06-21*  
*Confidence: HIGH — grounded in v1.0 shipped state, regulatory requirements, and known ecosystem behaviors*
