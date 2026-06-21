# VITBON v1.1 Production Readiness — Research Summary

**Project:** VITBON Мобильная Касса (Android POS, 54-ФЗ)
**Milestone:** v1.1 Production Readiness
**Researched:** 2026-06-21
**Confidence:** 🟢 HIGH overall
**Consumers:** requirements definition → roadmap creation

---

## §1. Executive Summary

VITBON v1.0 MVP shipped 2026-06-21 with 52/52 requirements validated across 7 phases. **v1.1 is a production-readiness milestone, not a feature milestone.** Scope: close the 5 v1.0 GAP items (ФН replacement, mTLS, token lifecycle, sandbox integration) + add 3 validation capabilities (load test 200+ касс, 24h offline stress, key rotation). No new customer-facing features, no breaking layer changes.

The v1.0 5-layer architecture (UI → Domain → Data → Fiscal Core → Sync) **remains untouched**. v1.1 adds three orthogonal subsystems: `core/hardening/` (environment config), `data/security/` extensions (key/cert/token rotation), and out-of-process test modules (`:testing` Android, `:loadtest` JVM). **Cardinal rule: no v1.0 path may regress.**

**Five critical success conditions for v1.1:**

1. **No fake-core leak** — `FakeFiscalCore` must be excluded from release builds by source-set, not flag (P1.1).
2. **24h Doze resilience proven** — `setExpedited()` for catch-up, validated on ≥3 OEM ROMs (P1.3).
3. **Key rotation is non-destructive until file swap** — `sqlcipher_export()` into sidecar, atomic swap on success (P1.2).
4. **ФН replacement requires shift closed + OFD queue empty** — `InFlightOperationGuard` atomic guard (P1.5, GAP-01).
5. **Load test 200+ касс is non-flaky** — 10min warmup + per-device credentials + quota-aware sharding (P2.1, P2.5, P3.5).

**What v1.0 already provides (don't re-research):** FiscalCore + ККТ adapters, Room + SQLCipher, WorkManager sync, LicenseChecker (7-day grace), FeatureManager, 6 status indicators, MARK + ALCO module skeletons. See `SUMMARY.md` Findings 1-19 for v1.0 baseline.

---

## §2. Key Findings by Dimension

### §2.1 From Stack Research (STACK-v1.1.md)

| # | Tooling Decision | Version / Component | Rationale |
|---|------------------|---------------------|-----------|
| 1 | **Load test runner: k6** | Grafana k6 v0.49+, JS scripts | Use k6: Apache 2.0, Go-runtime, no Scala-DSL barrier. Reject JMeter (Java-GUI slow) and Gatling (Scala overhead). |
| 2 | **Metrics: Prometheus + Grafana, self-hosted** | `micrometer-registry-prometheus` + `spring-boot-starter-actuator` | Spring-native, no vendor lock-in. Reject Datadog/Grafana Cloud (cost). |
| 3 | **SQLCipher key rotation: `PRAGMA rekey` native** | SQLCipher 4.5.4 (already in v1.0) | No new deps. Decision: `sqlcipher_export()` into sidecar, atomic swap on success (non-destructive until file move). |
| 4 | **mTLS hot-reload: custom `X509KeyManager` wrapper** | OkHttp 4.12.0 (already in v1.0) | OkHttp reads KeyManager per handshake. Decision: dynamic wrapper, not client rebuild (avoids in-flight abort, P2.6). |
| 5 | **24h offline: Robolectric + WorkManager-testing** | Robolectric 4.11.1+, `work-testing:2.9.0` | Use `Shadows.systemClock.advanceBy(Duration.ofHours(24))`. Reject `PowerManager.isInteractive=true` mock in prod (P1.3). |
| 6 | **OAuth mock: `oauth2-mock-server`** | `com.github.tietang:oauth2-mock-server:1.3.2` | RFC 6749 compliant, MIT. Decision: **polling 60s** for v1.1, defer SSE to v1.2. |
| 7 | **Cert issuance: Bouncy Castle** | `bcpkix-jdk18on:1.78`, 90-day validity | Reject OCSP/CRL stapling for v1.1 — overlap-period rotation covers 90% of revocation (P3.3, P4.2). |

### §2.2 From Features Research (FEATURES-v1.1.md)

| ID | Feature | Complexity | Why table-stakes |
|----|---------|-----------|------------------|
| **SAND-OFD-01..06** | ОФД sandbox (4 fiscal doc types) | 🔴 | v1.0 fiscal flow unvalidated against external ОФД. 54-ФЗ compliance unverified. |
| **SAND-CZ-01..06** | Честный ЗНАК sandbox (DataMatrix + выбытие) | 🔴 | MARK module untested against real ЧЗ API. |
| **SAND-UTM-01..05** | УТМ ЕГАИС sandbox (mock in Docker) | 🔴 | ALCO module sync (Finding 19) — must validate against test УТМ, not MockWebServer (P1.7). |
| **FN-REP-01..10** | ФН replacement + OFD delivery gate | 🔴 | GAP-01 from v1.0. P1.5 + P2.4 require atomic guard + OFD queue empty check. |
| **SQLC-01..06** | SQLCipher key rotation + crash-recovery | 🔴 | 90-day rotation; P1.2 requires 2-phase commit + WAL checkpoint + Keystore verify before old-key delete. |
| **LOAD-01..10** | 200+ касс load test (k6) | 🔴 | Backend functionally unproven at scale. P2.1+P2.5 require consumer sizing + Tomcat=400 + warmup. |
| **OFF-01..12** | 24h offline stress (Robolectric) | 🟡 | Validates v1.0's "24h offline" claim — queue <500, DB <100MB, no data loss. |

Secondary P1: **MTLS-01..07** (cert mgmt + hot-reload), **TOK-01..07** (refresh/revocation), **SAND-CID-01..05**. Anti-features explicitly excluded: real ОФД/ЧЗ/УТМ prod traffic, auto-ФН-swap, FCM push.

### §2.3 From Architecture Research (ARCHITECTURE-v1.1.md)

**No layer boundary changes.** Five integration points:

1. **`core/hardening/` (new cross-cutting)** — `EnvironmentConfig`, `EndpointResolver`, `HardeningFlags`, `BuildFlavorRegistry`, `TestHooks`. Read by all 5 layers; contains no business logic. Establishes sandbox/prod URL separation.
2. **`data/security/cert/` + `core/sync/cert/`** — `CertVault`, `DynamicKeyManager`, `CertHotReloader`. OkHttp's per-handshake KeyManager read enables hot-reload without rebuild.
3. **`data/security/rotation/`** — `KeyRotationManager`, `Reencryptor`, `RotationState`. Two-phase commit: new key to Keystore → rekey → verify → delete old.
4. **`domain/fiscal/` + `core/sync/`** — `FnReplacementStateMachine` (enforces FFD immutability, shift closure) + `InFlightOperationGuard` (coordinates WorkManager retry). `FiscalCore.replaceFn()` interface extension.
5. **`:core-network-models` (new pure-Kotlin)** — Extracts Retrofit DTOs so `:loadtest` (JVM) shares them without Android deps.

**New components: ~14. Modified: 9. Layer invariants preserved verbatim.**

### §2.4 From Pitfalls Research (PITFALLS-v1.1.md)

Top 5 P1 pitfalls (all block production certification):

1. **P1.1 — Sandbox test fixtures leak via `FiscalCoreFactory`.** `FakeFiscalCore` lives in `src/debug/java`; release builds literally cannot link. `FiscalCoreSanityCheck` at startup. → **Phase G first gate.**
2. **P1.2 — SQLCipher key-zeroization race corrupts in-flight transactions.** `KeyRotationOrchestrator`: (1) acquire DB mutex, (2) cancel WorkManager touching DB, (3) `wal_checkpoint(TRUNCATE)` before rekey, (4) verify new key before old delete. 24h stress includes forced rotation at hour 12. → **Phases I + H.**
3. **P1.3 — Doze defers SyncUpWorker → queue overflow.** `setExpedited()` for catch-up; pre-flight `getWorkInfosForUniqueWork("catch-up-sync")` shows RUNNING within 60s; OEM whitelist in operator onboarding. → **Phase H central test.**
4. **P1.4 — mTLS clock skew blocks 200+ clients.** `CertificatePinner` ±15min, server ±10min, `BootReceiver` NTP sync, "Diagnose connection" admin tool. Load test skews 5 devices ±20min. → **Phases J + K.**
5. **P1.5 — ФН replacement mid-shift → sequence gap.** Atomic state machine `ACTIVE → CLOSING → REPLACED`; `ProcessSaleUseCase` checks `isAcceptingOperations()`; wizard requires shift CLOSED + PENDING_SYNC empty + Z-report + new registration. → **Phase K (GAP-01 closure).**

Other P1: P1.6 (sandbox 429 quota), P1.7 (УТМ TLS pin). P2 risks: P2.1 (Redis Streams lag), P2.2 (token revocation), P2.4 (OFD delivery window), P2.5 (Tomcat threads), P2.6 (cert hot-reload abort).

---

## §3. Proposed Phase Structure (5 Phases)

### Phase G: Environment & Sandbox Foundation 🔴 L

**Goal:** Compile-time + runtime separation between prod and sandbox; pass all 4 sandbox integration tests.

**Requirements:** SAND-OFD-01..06, SAND-CZ-01..06, SAND-UTM-01..05, SAND-CID-01..05, P1.1, P1.6, P1.7, P2.7, P3.4, P4.1.

**Stack additions:** `core/hardening/`, `BuildConfig.ENVIRONMENT`, per-service `OFD_BASE_URL`, `SslContextFactory` (env-driven pins), test УТМ Docker image.

**Pitfalls addressed:** P1.1, P1.6, P1.7, P2.7, P3.4, P4.1.

**Dependencies:** None — first phase. **Ordering rationale:** Binary gate (P1.1 must crash on leak); every later phase needs env separation.

### Phase H: 24h Offline Stress + Operational Reliability 🟡 M-L

**Goal:** Prove v1.0 offline survives 24h with sync recovery on ≥3 OEM ROMs.

**Requirements:** OFF-01..12, P1.3, P2.3, P3.1, P3.2.

**Stack additions:** `:testing` Android lib (`OfflineStressHarness`, `SaleSimulator`, `NetworkGate`, `OfflineMetricsCollector`), Robolectric 4.11.1+, `work-testing:2.9.0`, `doze-bypass` rig, `OFD_DELIVERY_LAG` MON indicator.

**Pitfalls addressed:** P1.3 (central test), P2.3, P3.1 (WAL), P3.2 (Room migration).

**Dependencies:** Phase G (env separation). **Ordering rationale:** Runs in parallel with I/J dev; gates final release.

### Phase I: SQLCipher Key Rotation 🔴 L

**Goal:** Online + offline key rotation with crash-recovery; 90-day cadence + admin manual trigger.

**Requirements:** SQLC-01..06, P1.2, AF-V11-06.

**Stack additions:** `KeyRotationManager`, `Reencryptor` (`sqlcipher_export()`), `KeyRotationDao`, `RotateDbKeyUseCase`, `KeyRotationScreen`.

**Pitfalls addressed:** P1.2 (zeroization race — 2-phase commit + WAL checkpoint + verify before delete).

**Dependencies:** v1.0 SEC-01..05. **Ordering rationale:** Standalone; before Phase L so 24h stress includes forced rotation at hour 12.

### Phase J: Security Lifecycle (mTLS + Token Rotation) 🔴 L

**Goal:** mTLS cert provisioning + hot-reload; OAuth2 refresh + revocation.

**Requirements:** MTLS-01..07, TOK-01..07, MTLS-T-01..04, TOK-T-01..03, P1.4, P2.2, P2.6, P3.3, P4.2, P4.3.

**Stack additions:** `CertVault`, `DynamicKeyManager`, `CertHotReloader` (WorkManager 6h periodic), `RevocationRegistry` (Room), `RevocationPushReceiver` (defer SSE, use polling 60s), `oauth2-mock-server:1.3.2`, Bouncy Castle `bcpkix-jdk18on:1.78`.

**Pitfalls addressed:** P1.4, P2.2 (refresh-token-reuse-detection), P2.6 (in-flight abort), P3.3 (intermediate CA), P4.2 (OCSP defer), P4.3 (hardware Keystore).

**Dependencies:** Phase G. **Ordering rationale:** Security-critical; before Phase K so load test exercises real mTLS path.

### Phase K: Fiscal Reliability (ФН Replacement + LOAD) 🔴 XL

**Goal:** Close GAP-01 (ФН replacement) + prove 200+ касс load.

**Requirements:** FN-REP-01..10, LOAD-01..10, P1.5, P2.1, P2.4, P2.5, P3.5.

**Stack additions:** `FnRegistration` entity + DAO + Repository, `FnReplacementStateMachine`, `InFlightOperationGuard`, `FnRegistrationUseCase`, `FnReplacementScreen`, `FnCapacityIndicator`, `FiscalCore.replaceFn()` interface, `:loadtest` JVM module (`LoadTestOrchestrator`, `LoadTestAgent`, `CheckGenerator`, `MetricsCollector`, `RedisDepthProbe`), `docker-compose.loadtest.yml`, `infra/loadtest/baseline.json`, Prometheus + Grafana, `server.tomcat.threads.max=400`, TLS 1.3 session resumption.

**Pitfalls addressed:** P1.5, P2.1, P2.4, P2.5, P3.5.

**Dependencies:** Phases G + I + J. **Ordering rationale:** Final production gate — requires all upstream stable. ФН flow + load harness = highest-severity items.

### Phase Ordering Rationale

```
G (Sandbox) → J (Security) ──→ K (ФН + Load) [final gate]
   │              │                    ↑
   └──► H (24h Stress) ────► I (Key Rotation) ──┘
```

- **G first** (env separation foundational; P1.1 binary gate).
- **H parallel to I/J** (slow wall-clock, tests v1.0 claims during dev).
- **I before K** (24h stress includes mid-rotation crash test).
- **J before K** (load test exercises real mTLS path).
- **K last** (final gate, requires all upstream).

---

## §4. Critical Path & Dependencies

**Must-be-done-first (sequential):**

1. `core/hardening/` skeleton + `BuildConfig.ENVIRONMENT` (Phase G.1)
2. `EndpointResolver` + `DefaultEndpointResolver` (Phase G.2)
3. `FiscalCoreSanityCheck` + `FakeFiscalCore` source-set isolation (P1.1 gate)
4. `InFlightOperationGuard` interface design (Phase K.0, spec'd with G)

**Parallelizable:**

- H (24h stress) ‖ I (key rotation) — different modules (testing vs data/security).
- J (mTLS) ‖ I (key rotation) — different layers (cert vs rotation).
- K.ФН (Android) ‖ K.load (JVM) — share no code.

**Blocks what:**

- G → J, K.load, H, I (env separation everywhere)
- J → K.load (mTLS path exercised)
- I → H.mid-rotation (forced rotation at hour 12)
- K → release/v1.1 (final gate)

---

## §5. Production-Readiness Gate Criteria

| # | Check | Pass Criteria | Feature IDs |
|---|-------|---------------|-------------|
| 1 | **No fake-core in release** | `apk-inspect` finds no `FakeFiscalCore`; `release+sandbox` crashes with `FiscalInvariantViolation` | P1.1, P4.1 |
| 2 | **All 4 sandboxes pass** | 100% fiscal docs (sell/refund/correction/Z) accepted by ОФД; ЧЗ validates DataMatrix; УТМ accepts алкоголь акт; ЦИМ returns age | SAND-OFD-01..06, SAND-CZ-01..06, SAND-UTM-01..05, SAND-CID-01..05 |
| 3 | **24h offline on 3+ OEMs** | Queue <500; sync within 60s; DB <100MB; no crashes on Pixel + Xiaomi + Samsung | OFF-01..12, P1.3, P3.1, P3.2 |
| 4 | **Key rotation crash-safe** | Mid-rotation `kill -9` leaves DB usable; rotation <60s; mid-rotation in 24h run | SQLC-01..06, P1.2 |
| 5 | **Load test p99 <5s** | p99 sync <5s; Redis lag <60s; Tomcat not saturated; mTLS p99 <2s; zero data loss | LOAD-01..10, P2.1, P2.5, P3.5 |
| 6 | **ФН replacement atomic + OFD-empty** | Concurrent sale + replacement → sale rejected; OFD queue = 0 required; `fnSerial` per check | FN-REP-01..10, P1.5, P2.4 |
| 7 | **mTLS clock skew ±15min** | 5 devices ±20min still sync; intermediate CA bundled; hot-reload no abort; NTP via BootReceiver | MTLS-01..07, P1.4, P2.6, P3.3 |
| 8 | **Token revocation <5s** | Refresh-token-reuse-detection works; propagation <5s; hardware Keystore where available | TOK-01..07, P2.2, P4.3 |
| 9 | **WAL + storage bounded** | `wal_checkpoint(TRUNCATE)` after each sync; DB <100MB after 24h; quota indicator in MON-02 | OFF-04, P3.1 |
| 10 | **Audit append-only** | Room `BEFORE UPDATE` ABORT trigger; KEY_ROTATION/CERT_ROTATION/TOKEN_* logged | SQLC-05, MTLS-07, TOK-04 |

---

## §6. Open Questions

| # | Question | Decision by | Default for v1.1 |
|---|----------|-------------|------------------|
| Q1 | Which ОФД sandbox provider? | Phase G kickoff | Engage Taxcom first; provider-agnostic adapter |
| Q2 | Where does initial mTLS cert come from? | Phase J kickoff | Factory provisioning; QR fallback for replacements |
| Q3 | Does `DEVICE_STOLEN` trigger wipe or logout? | Phase J kickoff | Logout only for v1.1; wipe deferred to v1.2 |
| Q4 | Auto key rotation or admin-only? | Phase I kickoff | Admin-only manual for v1.1 |
| Q5 | SSE vs polling for revocation push? | Phase J kickoff | **Polling 60s for v1.1**; SSE deferred to v1.2 |
| Q6 | Offline grace for new ФН after replacement? | Phase K kickoff | **0 — block sales** until new ФН registered |
| Q7 | Load test simulates network failure mid-test? | Phase K kickoff | Yes, as separate scenario |
| Q8 | ЧЗ sandbox "infinite quota" tier? | Phase G kickoff | If yes, use for load test; if no, shard by IP |
| Q9 | OCSP/CRL stapling in v1.1? | Phase J kickoff | No — overlap-period rotation covers 90% |
| Q10 | УТМ sandbox URL — community mock vs provider? | Phase G kickoff | Community Docker mock (no official ФСРАР) |

**Risks/assumptions to validate:**

- **R1:** ОФД sandbox URLs change post-contract — dynamic config + provider-agnostic adapter.
- **R2:** `setExpedited()` requires API 31+; API 23-30 needs foreground service fallback.
- **R3:** Xiaomi/Huawei Doze varies by ROM — assume worst-case for SLA.
- **R4:** k6 baseline drift — maintain `baseline.json` with P95 thresholds; alert on >20% regression.
- **R5:** ФН replacement requires ЦТО on-site — **not** self-service; document in operator manual.

---

## §7. Confidence Assessment

| Dimension | Confidence | Why | What could invalidate |
|-----------|-----------|-----|----------------------|
| **Stack** | 🟢 HIGH | All v1.1 additions extend v1.0-vetted libs (OkHttp, SQLCipher, EncryptedSharedPreferences, WorkManager). k6 + Prometheus + Bouncy Castle + Robolectric are industry-standard. | ОФД sandbox URLs change post-contract; Bouncy Castle CVE → upgrade. |
| **Features** | 🟢 HIGH | IDs (SAND-*, FN-REP-*, SQLC-*, MTLS-*, TOK-*, LOAD-*, OFF-*) trace to specific 54-ФЗ, ФФД, ЧЗ, ЕГАИС regulatory behaviors. | Sandbox quotas lower than estimated; ЦИМ data validity (P3.4). |
| **Architecture** | 🟢 HIGH | v1.1 explicitly preserves v1.0 layer boundaries (ARCHITECTURE §1.2). New components additive (`core/hardening/`) or surgical (interface extensions). | `DynamicKeyManager` may behave differently on OEM-customized TLS stacks; validate ≥3 OEMs. |
| **Pitfalls** | 🟢 HIGH | 22 pitfalls grounded in v1.0 PITFALLS.md + STATE.md GAP items + 54-ФЗ realities. Recovery strategies specified. | New pitfalls may surface in Phase H on OEM ROMs not in v1.1 matrix. |
| **Overall** | 🟢 HIGH | Production-readiness milestone; no green-field; all dimensions trace to v1.0 shipped state. | n/a |

### Gaps to Address During Planning/Execution

- **G1:** Final ОФД provider selection — gate Phase G.1 (sandbox credentials).
- **G2:** Backend CA root cert provisioning — gate Phase J.1.
- **G3:** WorkManager `setExpedited()` on API <31 fallback — gate Phase H.2.
- **G4:** Sandbox infinite-quota tier availability (ЧЗ/УТМ) — gate Phase K.2.
- **G5:** Operator manual draft for OEM whitelist (battery optimization) — gate Phase H deployment.

---

## §8. Sources

**Primary (HIGH):**

- `.planning/research/SUMMARY.md` v1.0 — Findings 1-19
- `.planning/research/STACK-v1.1.md` (2026-06-21)
- `.planning/research/FEATURES-v1.1.md` (2026-06-21)
- `.planning/research/ARCHITECTURE-v1.1.md` (2026-06-21)
- `.planning/research/PITFALLS-v1.1.md` (2026-06-21)
- `.planning/PROJECT.md` v1.1 section
- 54-ФЗ + ФФД 1.05/1.2 regulatory text

**Secondary (MEDIUM):**

- Sandbox endpoint URLs (per-provider, contract-dependent) 🟡
- WorkManager Doze behavior on Xiaomi MIUI / Huawei EMUI 🟡
- Community УТМ mock Docker image availability 🟡

**Tertiary (LOW — needs validation):**

- ЧЗ sandbox "infinite quota" tier (Q8) 🔴
- OCSP stapling necessity (Q9) 🔴

---

*Research synthesized: 2026-06-21*
*Milestone: v1.1 Production Readiness*
*Status: Synthesis complete — ready for requirements definition and roadmap creation*
