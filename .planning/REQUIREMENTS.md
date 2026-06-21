# Requirements — v1.1 Production Readiness (VITBON)

**Milestone:** v1.1 Production Readiness
**Date:** 2026-06-21
**Source:** `.planning/research/{STACK,FEATURES,ARCHITECTURE,PITFALLS,SUMMARY}-v1.1.md`
**Consumer:** `.planning/ROADMAP.md` (next step)
**Confidence:** 🟢 HIGH overall

---

## Что v1.1 **не** делает

- **Не добавляет customer-facing features** — это milestone production-readiness, не feature milestone.
- **Не ломает v1.0 layer boundaries** — 5-слойная архитектура (UI/Domain/Data/Fiscal Core/Sync) сохраняется.
- **Не использует production endpoints ОФД/ЧЗ/УТМ** — только sandbox-окружения.
- **Не заменяет ФН автоматически** — операция требует ЦТО (per AF-V11-04).
- **Не включает OCSP/CRL stapling** — overlap-period cert rotation покрывает 90% revocation.
- **Не реализует SSE** для token revocation — polling 60s для v1.1.

---

## v1.1 Requirements (Active)

> **Cardinal rule:** no v1.0 path may regress. Все v1.0 требования (FISC-*, KKT-*, GOOD-*, REPT-*, MON-*, AUTH-*, LIC-*, SEC-*, UPDT-*, MARK-*, ALCO-*) остаются в силе.

### 1. Sandbox Integration (SAND) — table-stakes, 4 external systems

#### 1.1 Sandbox ОФД (SAND-OFD-01..06)

- [ ] **SAND-OFD-01**: Подключение к ОФД sandbox (per-provider URL, тестовые credentials ИНН/ОГРН/ФН/device-id) — **Phase G**
- [ ] **SAND-OFD-02**: Smoke-тест: отправка чека продажи в ОФД sandbox, проверка ответа — **Phase G**
- [ ] **SAND-OFD-03**: Smoke-тест: отправка чека возврата, отчёта о закрытии смены (Z-отчёт) — **Phase G**
- [ ] **SAND-OFD-04**: Проверка receipt validation (корректность TLV-структуры ФФД 1.05/1.2) — **Phase G**
- [ ] **SAND-OFD-05**: Тест retry-политики (повторная отправка при сетевом сбое) — **Phase G**
- [ ] **SAND-OFD-06**: Тест негативных сценариев (просроченный ФН, ошибки ФФД, таймауты) — **Phase G**

#### 1.2 Sandbox Честный ЗНАК (SAND-CZ-01..06)

- [ ] **SAND-CZ-01**: Подключение к test-bed ЧЗ (`sandboxapi.crpt.ru` + тестовые ИНН/ОГРН/token-OMSID) — **Phase G**
- [ ] **SAND-CZ-02**: Валидация DataMatrix в песочнице (валидные / невалидные коды) — **Phase G**
- [ ] **SAND-CZ-03**: Smoke-тест выбытия маркированного товара — **Phase G**
- [ ] **SAND-CZ-04**: Smoke-тест приёмки маркированного товара — **Phase G**
- [ ] **SAND-CZ-05**: Проверка обработки невалидных КМ (ошибка ЧЗ, таймаут, сеть) — **Phase G**
- [ ] **SAND-CZ-06**: Проверка offline-блокировки (MARK-blocked при отсутствии сети) — **Phase G**

#### 1.3 Sandbox УТМ ЕГАИС (SAND-UTM-01..05)

- [ ] **SAND-UTM-01**: Подключение к mock УТМ (Docker `utm-mock:latest` или testbed) — **Phase G**
- [ ] **SAND-UTM-02**: Smoke-тест акта продажи алкоголя — **Phase G**
- [ ] **SAND-UTM-03**: Проверка обработки ошибок УТМ (timeout, format error, 429) — **Phase G**
- [ ] **SAND-UTM-04**: Проверка версионности УТМ (поддержка 4.x, 5.x) — **Phase G**
- [ ] **SAND-UTM-05**: Проверка TLS-pin к УТМ (НЕ путать с production ФСРАР CA, P1.7) — **Phase G**

#### 1.4 Sandbox Цифровой ID Max (SAND-CID-01..05)

- [ ] **SAND-CID-01**: Подключение к CID Max sandbox (тестовый API key, тестовый возраст) — **Phase G**
- [ ] **SAND-CID-02**: Проверка age check (age:17 → запрещено, age:25 → разрешено) — **Phase G**
- [ ] **SAND-CID-03**: Тест token-bucket (rate limit) — **Phase G**
- [ ] **SAND-CID-04**: Тест отзыва сессии (revocation) — **Phase G**
- [ ] **SAND-CID-05**: Тест обработки ошибок API (5xx, timeout) — **Phase G**

### 2. Operational Hardening (closes v1.0 GAP items)

#### 2.1 ФН Replacement (FN-REP-01..10, closes GAP-01)

- [ ] **FN-REP-01**: UI wizard пошаговой замены ФН (close shift → power off → swap → register) — **Phase K**
- [ ] **FN-REP-02**: `FnRegistration` entity + DAO + Repository — **Phase K**
- [ ] **FN-REP-03**: `FnReplacementStateMachine` (atomic ACTIVE → CLOSING → REPLACED) — **Phase K**
- [ ] **FN-REP-04**: `InFlightOperationGuard` (blocks new sales during CLOSING) — **Phase K**
- [ ] **FN-REP-05**: PENDING_SYNC queue must be empty before swap (P1.5) — **Phase K**
- [ ] **FN-REP-06**: Z-отчёт must be generated before swap — **Phase K**
- [ ] **FN-REP-07**: New ФН registration via existing FnRegistrationController — **Phase K**
- [ ] **FN-REP-08**: `fnSerial` recorded per fiscal document (post-replacement) — **Phase K**
- [ ] **FN-REP-09**: `FnCapacityIndicator` (warning when ФН 80%+ full) — **Phase K**
- [ ] **FN-REP-10**: `FiscalCore.replaceFn()` interface extension — **Phase K**

#### 2.2 SQLCipher Key Rotation (SQLC-01..06)

- [ ] **SQLC-01**: 90-day key rotation (manual + scheduled trigger) — **Phase I**
- [ ] **SQLC-02**: Migration: open DB with K_old → `PRAGMA rekey` → save K_new (atomic) — **Phase I**
- [ ] **SQLC-03**: `sqlcipher_export()` to sidecar file before atomic swap (P1.2) — **Phase I**
- [ ] **SQLC-04**: `wal_checkpoint(TRUNCATE)` before rekey — **Phase I**
- [ ] **SQLC-05**: Verify new key works BEFORE deleting old key from Keystore — **Phase I**
- [ ] **SQLC-06**: Audit log: `KEY_ROTATION` event (old alias, new alias, timestamp, initiator) — **Phase I**
- [ ] **SQLC-07**: Crash-recovery test (kill -9 mid-rotation; DB must be usable) — **Phase I**
- [ ] **SQLC-08**: `KeyRotationManager` + `KeyRotationDao` + `RotateDbKeyUseCase` — **Phase I**
- [ ] **SQLC-09**: `KeyRotationScreen` (admin trigger) — **Phase I**
- [ ] **SQLC-10**: Self-test after rotation: check counters, balances, license — **Phase I**

#### 2.3 Mutual TLS Certificate Management (MTLS-01..07)

- [ ] **MTLS-01**: Client cert + private key storage in Android Keystore (BKS or EncryptedSharedPreferences) — **Phase J**
- [ ] **MTLS-02**: Cert pinning with rotation (SHA-256 backup + primary) — **Phase J**
- [ ] **MTLS-03**: Hot-reload cert без перезапуска (custom `X509KeyManager` wrapper, P2.6) — **Phase J**
- [ ] **MTLS-04**: Pre-expiry warning (за 30/15/7 дней до истечения) — **Phase J**
- [ ] **MTLS-05**: Two-cert overlap period (old + new during rotation) — **Phase J**
- [ ] **MTLS-06**: `CertVault` + `DynamicKeyManager` + `CertHotReloader` (WorkManager 6h periodic) — **Phase J**
- [ ] **MTLS-07**: Audit log: `CERT_ROTATION` event — **Phase J**
- [ ] **MTLS-08**: mTLS clock skew ±15min (P1.4) — **Phase J**
- [ ] **MTLS-09**: `BootReceiver` NTP sync at device boot — **Phase J**
- [ ] **MTLS-10**: "Diagnose connection" admin tool — **Phase J**

#### 2.4 Token Rotation / Revocation (TOK-01..07)

- [ ] **TOK-01**: Refresh-token with TTL (7 days), access-token TTL 15min — **Phase J**
- [ ] **TOK-02**: Auto-refresh 5 min before expiry (OkHttp `Authenticator`) — **Phase J**
- [ ] **TOK-03**: Refresh-token-reuse-detection (P2.2) — **Phase J**
- [ ] **TOK-04**: EncryptedSharedPreferences для refresh_token — **Phase J**
- [ ] **TOK-05**: Forced re-login при revoked refresh token — **Phase J**
- [ ] **TOK-06**: Revocation propagation via polling 60s (SSE deferred to v1.2) — **Phase J**
- [ ] **TOK-07**: Audit log: `TOKEN_REFRESH` / `TOKEN_REVOKED` events — **Phase J**
- [ ] **TOK-08**: Background workers (SyncUp) have own refresh-handlers — **Phase J**
- [ ] **TOK-09**: Hardware Keystore (StrongBox) where available (P4.3) — **Phase J**

### 3. Load & Stress Testing

#### 3.1 Load Test 200+ Cash Registers (LOAD-01..10)

- [ ] **LOAD-01**: k6 scenario: 200 virtual users (cash registers) login + open shift + 100 sales + close shift — **Phase K**
- [ ] **LOAD-02**: 10-min warmup (P2.1) — **Phase K**
- [ ] **LOAD-03**: Per-device credentials (P2.5: Tomcat=400 threads) — **Phase K**
- [ ] **LOAD-04**: Quota-aware sharding (P2.5: split by IP for ЧЗ) — **Phase K**
- [ ] **LOAD-05**: Metrics: P50/P95/P99 latency (Prometheus `micrometer-registry-prometheus`) — **Phase K**
- [ ] **LOAD-06**: Metrics: throughput (чеков/сек) — **Phase K**
- [ ] **LOAD-07**: Metrics: error rate per endpoint — **Phase K**
- [ ] **LOAD-08**: Metrics: Redis Stream consumer group lag — **Phase K**
- [ ] **LOAD-09**: Metrics: Postgres connection pool saturation (HikariCP) — **Phase K**
- [ ] **LOAD-10**: Metrics: JVM OOM / GC pauses — **Phase K**
- [ ] **LOAD-11**: `infra/loadtest/baseline.json` with thresholds; alert on >20% regression — **Phase K**
- [ ] **LOAD-12**: Network-failure mid-test scenario (Q7) — **Phase K**
- [ ] **LOAD-13**: Docker Compose harness (`infra/loadtest/docker-compose.yml`) — **Phase K**
- [ ] **LOAD-14**: Grafana dashboard for load-test metrics — **Phase K**

#### 3.2 24-Hour Offline Stress Test (OFF-01..12)

- [ ] **OFF-01**: Scenario: 24h без сети, непрерывные продажи (Robolectric) — **Phase H**
- [ ] **OFF-02**: Avg sales per offline shift (50-200 typical) — **Phase H**
- [ ] **OFF-03**: Queue depth PENDING_SYNC < 500 — **Phase H**
- [ ] **OFF-04**: DB size after 24h < 100 MB — **Phase H**
- [ ] **OFF-05**: RAM usage < 200 MB — **Phase H**
- [ ] **OFF-06**: Sale open time < 200ms — **Phase H**
- [ ] **OFF-07**: Z-report local generation time — **Phase H**
- [ ] **OFF-08**: No sales lost (Room invariant) — **Phase H**
- [ ] **OFF-09**: Reports correct for offline period — **Phase H**
- [ ] **OFF-10**: License grace period not broken — **Phase H**
- [ ] **OFF-11**: Network restore → sync within 5 min — **Phase H**
- [ ] **OFF-12**: 100% sync in 5 min after restore — **Phase H**
- [ ] **OFF-13**: Doze resilience: `setExpedited()` for catch-up sync (API 31+, fallback for API 23-30) — **Phase H**
- [ ] **OFF-14**: Validation on ≥3 OEM ROMs (Pixel + Xiaomi + Samsung, P1.3) — **Phase H**
- [ ] **OFF-15**: Forced mid-rotation at hour 12 (interacts with SQLC-07) — **Phase H**
- [ ] **OFF-16**: `wal_checkpoint(TRUNCATE)` after each sync (P3.1) — **Phase H**

#### 3.3 mTLS / OAuth E2E Tests (MTLS-T-01..04, TOK-T-01..03)

- [ ] **MTLS-T-01**: E2E mTLS handshake successful (mock server) — **Phase J**
- [ ] **MTLS-T-02**: Invalid server cert → connection refused — **Phase J**
- [ ] **MTLS-T-03**: Cert expiry → alert + operations block — **Phase J**
- [ ] **MTLS-T-04**: Cert rotation без downtime (blue/green deploy) — **Phase J**
- [ ] **TOK-T-01**: E2E refresh token flow — **Phase J**
- [ ] **TOK-T-02**: E2E revoked access token → auto-refresh — **Phase J**
- [ ] **TOK-T-03**: E2E revoked refresh token → forced re-login — **Phase J**
- [ ] **MTLS-T-05**: `oauth2-mock-server:1.3.2` integration — **Phase J**

### 4. P1 Pitfall Mitigations (must be addressed)

- [ ] **PITFALL-P1.1**: `FakeFiscalCore` lives in `src/debug/java`; release builds cannot link. `FiscalCoreSanityCheck` at startup. — **Phase G**
- [ ] **PITFALL-P1.2**: `KeyRotationOrchestrator` 2-phase commit (P1.2) — **Phase I**
- [ ] **PITFALL-P1.3**: `setExpedited()` + OEM whitelist in operator onboarding — **Phase H**
- [ ] **PITFALL-P1.4**: mTLS clock skew ±15min; server ±10min; NTP sync — **Phase J**
- [ ] **PITFALL-P1.5**: `FnReplacementStateMachine` atomic; wizard requires shift CLOSED + queue empty + Z + new registration — **Phase K**
- [ ] **PITFALL-P1.6**: Sandbox 429 quota handling (backoff + per-IP sharding) — **Phase G**
- [ ] **PITFALL-P1.7**: УТМ TLS-pin separate from production ФСРАР CA — **Phase G**

### 5. Observability (P2 differentiators, optional but recommended)

- [ ] **OP-01**: Status bar metrics (queue depth, time-since-last-sync, cert-expiry, ФН capacity) — **Phase K**
- [ ] **OP-02**: Diagnostic pack (logs, DB stats) для support — **Phase K**
- [ ] **OP-03**: Local alerts (queue overflow, cert expiring, ФН low) — **Phase K**

---

## Production-Readiness Gates (must pass for v1.1 release)

| # | Check | Pass Criteria | Maps to REQ-IDs |
|---|-------|---------------|-----------------|
| 1 | No fake-core in release | `apk-inspect` finds no `FakeFiscalCore`; `release+sandbox` crashes with `FiscalInvariantViolation` | PITFALL-P1.1 |
| 2 | All 4 sandboxes pass | 100% fiscal docs (sell/refund/correction/Z) accepted by ОФД; ЧЗ validates DataMatrix; УТМ accepts алкоголь акт; ЦИМ returns age | SAND-OFD-*, SAND-CZ-*, SAND-UTM-*, SAND-CID-* |
| 3 | 24h offline on 3+ OEMs | Queue <500; sync within 60s; DB <100MB; no crashes on Pixel + Xiaomi + Samsung | OFF-*, PITFALL-P1.3 |
| 4 | Key rotation crash-safe | Mid-rotation `kill -9` leaves DB usable; rotation <60s; mid-rotation in 24h run | SQLC-*, PITFALL-P1.2 |
| 5 | Load test p99 <5s | p99 sync <5s; Redis lag <60s; Tomcat not saturated; mTLS p99 <2s; zero data loss | LOAD-*, PITFALL-P1.4 |
| 6 | ФН replacement atomic | Concurrent sale + replacement → sale rejected; OFD queue = 0 required; `fnSerial` per check | FN-REP-*, PITFALL-P1.5 |
| 7 | mTLS clock skew ±15min | 5 devices ±20min still sync; intermediate CA bundled; hot-reload no abort; NTP via BootReceiver | MTLS-*, PITFALL-P1.4 |
| 8 | Token revocation <5s | Refresh-token-reuse-detection works; propagation <5s; hardware Keystore where available | TOK-*, PITFALL-P4.3 |
| 9 | WAL + storage bounded | `wal_checkpoint(TRUNCATE)` after each sync; DB <100MB after 24h | OFF-04, OFF-16 |
| 10 | Audit append-only | Room `BEFORE UPDATE` ABORT trigger; KEY_ROTATION/CERT_ROTATION/TOKEN_* logged | SQLC-06, MTLS-07, TOK-07 |

---

## v1.0 GAP items closed by v1.1

| v1.0 GAP | Closed by v1.1 REQ |
|----------|--------------------|
| GAP-01: ФН replacement flow | FN-REP-01..10 (Phase K) |
| GAP-02: Sandbox integration testing | SAND-OFD/CZ/UTM/CID-* (Phase G) |
| GAP-03: mTLS hot-reload | MTLS-03 (Phase J) |
| GAP-04: Token lifecycle | TOK-01..09 (Phase J) |
| GAP-05: 24h offline validation | OFF-01..16 (Phase H) |

---

## Out of Scope (explicitly deferred)

| ID | Item | Reason | Target |
|----|------|--------|--------|
| **DEFER-V11-01** | SSE для token revocation | Polling 60s покрывает SLA; SSE adds long-lived connection complexity | v1.2 |
| **DEFER-V11-02** | OCSP/CRL stapling | Overlap-period rotation покрывает 90% revocation (P3.3, P4.2) | v1.2+ |
| **DEFER-V11-03** | Self-update cert without admin approval | Audit trail (AF-V11-05) | v1.2 |
| **DEFER-V11-04** | Crashlytics / Sentry integration (fiscal logs) | Privacy + regulatory concerns; non-fiscal logs только | v1.2 |
| **DEFER-V11-05** | Zero-downtime backend deploy (Redis Streams ack) | Требует canary + traffic shifting infra; вне scope production-readiness | v1.2 |
| **DEFER-V11-06** | "Magic" auto-repair corrupted DB | Фискальные данные — лучше stop and ask (AF-V11-06) | v1.2+ |

---

## Anti-Features (consciously NOT building)

| ID | Anti-feature | Reason |
|----|--------------|--------|
| **AF-V11-01** | Production ОФД traffic | v1.1 — verification, not production traffic |
| **AF-V11-02** | Production ЧЗ traffic | Sandbox only, чтобы не "выбывать" тестовые КМ |
| **AF-V11-03** | Real УТМ ЕГАИС | Test через mock-УТМ |
| **AF-V11-04** | Auto ФН replacement | Регуляторика: ФН-смена требует ЦТО |
| **AF-V11-05** | Self-update cert without admin approval | Audit trail |
| **AF-V11-06** | "Magic" auto-repair corrupted DB | Данные фискальные — лучше stop and ask |
| **AF-V11-07** | Тестирование на реальных чеках в проде | Отдельный staging/UAT env |
| **AF-V11-08** | mTLS cert rotation без overlap period | Нарушает zero-downtime |

---

## Open Questions (decision points for roadmap)

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

---

## Traceability (filled by ROADMAP.md)

> Empty — to be populated by Step 10 (Create Roadmap). Each REQ-ID above will be assigned to a phase and a sub-plan.

---

## Summary

- **Total v1.1 requirements:** ~95 (active) + 7 P1 pitfall mitigations + 3 observability (P2)
- **v1.0 GAP items closed:** 5/5
- **Phases proposed (G/H/I/J/K):** 5 phases (см. SUMMARY-v1.1.md §3)
- **Production-readiness gates:** 10 (все must-pass)
- **Anti-features:** 8 (explicit exclusions)
- **Deferred to v1.2:** 6 items

---

*Generated: 2026-06-21*
*Source: SUMMARY-v1.1.md (after Step 11 of /gsd-new-milestone)*
*Next: ROADMAP.md (Step 10 of /gsd-new-milestone)*
