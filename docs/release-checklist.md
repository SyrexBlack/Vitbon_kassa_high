# Phase B Release Checklist — Physical POS

**Branch:** `feat/phase-b-security-auth-rbac-audit-pr`
**Commits:** `e60005c`, `2282a31`
**Schema migrations required:** `V5__add_auth_sessions_and_audit.sql`, `V6__widen_cashier_pin_hash.sql`, `V7__add_document_ownership.sql`

> **Prerequisite:** Run Flyway migrations **before** deploying the new JAR. `V6__widen_cashier_pin_hash` must apply before PBKDF2 login rotation activates, and `V7__add_document_ownership` must apply before movement/document reports are considered tenant-isolated.

---

## Backend

### Pre-deployment

- [ ] Run local automated gate: `powershell -ExecutionPolicy Bypass -File .\verify-phase-b.ps1`
- [ ] Run full backend test suite; confirm:
  - `SecurityRouteGuardIntegrationTest` — 8/8 passed
  - `AuthIntegrationTest` — 9/9 passed
  - `DocumentsIntegrationTest` / `ReportsIntegrationTest` / `FlywayMigrationTest` — passed
- [ ] Run Flyway migrate:
  ```bash
  flyway -locations=filesystem:src/main/resources/db/migration migrate
  ```
  Confirm `V5`, `V6`, and `V7` apply cleanly; record final schema checksum.
- [ ] **Rollback schema note:** If rolling back the JAR, `ALTER COLUMN pin_hash TYPE VARCHAR(64)` is safe **only if no PBKDF2 hashes exist in production** (data truncation risk). Use DB backup before altering.

### Post-deployment smoke — single cashier

- [ ] `POST /api/v1/auth/login { "pin": "...", "deviceId": "device-A" }` → HTTP 200, body contains `token`, `cashier`, `features`, `expiresAt`; token TTL = 8 hours
- [ ] Replay same token with `X-Device-Id: device-B` → HTTP 401, `{"reason":"DEVICE_MISMATCH"}`
- [ ] CASHIER role token → `GET /api/v1/statuses` → HTTP 403
- [ ] ADMIN role token → `GET /api/v1/statuses` → HTTP 200 with device-scoped telemetry payload
- [ ] `POST /api/v1/auth/logout` with valid token → HTTP 200; token no longer usable
- [ ] `POST /api/v1/checks/sync` replay with same `localUuid` from same cashier/device → HTTP 200, no duplicate row created
- [ ] Movement report for cashier/device A does not include documents or checks created by cashier/device B

### Post-deployment smoke — audit

- [ ] After each authorized request, query `audit_events` table:
  - `security.route_access` rows have non-null `actor_id`, `device_id`, `session_id`
- [ ] `POST /api/v1/audit/sync` with buffered `auth.emergency.*` events from the device → HTTP 200; rows appear in `audit_events` with `target=emergency_mode`, parsed `result/reason`, and `session_id IS NULL`
- [ ] While emergency ADMIN mode is active, attempt blocked sale/return/shift/cash-drawer operations; `audit_events` receives `action=auth.emergency.operation_denied` with `reason` matching the blocked operation code
- [ ] After each denial, query `audit_events`:
  - Missing bearer → `security.auth_deny`, `reason=MISSING_BEARER`
  - Device mismatch → `security.auth_deny`, `reason=DEVICE_MISMATCH`
  - Rate-limited → `security.auth_deny`, `reason=RATE_LIMITED`

### Post-deployment smoke — brute force

- [ ] Submit 4 invalid PIN attempts from same `X-Device-Id`; 4th attempt → HTTP 429; `audit_events` has `action=auth.login, result=DENY, reason=RATE_LIMITED`

### Post-deployment smoke — legacy hash rotation

- [ ] Insert test cashier with SHA-256 `pin_hash` (raw 64-char hex string); login with correct PIN; confirm hash rewritten to `pbkdf2$...` format and column length stays within `VARCHAR(1024)`

### Load test

- [ ] 10 concurrent `POST /api/v1/auth/login` for the same cashier from same device; after all complete, query `auth_sessions` — only 1 row with `status=ACTIVE`; all others have `status=REVOKED, revoke_reason=REPLACED_BY_NEW_LOGIN`

### Health and monitoring

- [ ] `GET /api/v1/health` → HTTP 200, `{"status":"UP","service":"vitbon-backend",...}`
- [ ] `GET /api/v1/health/live` → HTTP 200, `{"status":"ALIVE",...}`
- [ ] `GET /api/v1/health/ready` → HTTP 200, `{"status":"READY",...}`
- [ ] Rollback procedure documented in `backend/docs/monitoring.md` — verify Flyway migration version table is readable
- [ ] Confirm `GET /api/v1/license/check?deviceId=` returns ok or 403 (no 500)

---

## Android POS App

### Pre-deployment

- [ ] `./gradlew testDebugUnitTest` — confirm `AuthTokenStoreTest` 3/3 passed

### Physical POS — API 23 device (Android 6)

- [ ] Install APK; login as cashier; capture bearer token
- [ ] Install same APK version on second API 23 device; attempt request with token from device 1 → HTTP 401 `DEVICE_MISMATCH`
- [ ] Confirm `X-Device-Id` header present and non-null in OkHttp request logs for all authenticated endpoints
- [ ] Confirm Android app uses the same persisted secure `deviceId` for login, shifts, sales, and license check paths
- [ ] Logout; confirm token cleared from `AuthTokenStore` (SharedPreferences); subsequent requests carry no `Authorization` header

### Physical POS — API 23 device — rate limit UX

- [ ] Rapid-fire 4 invalid PIN entries on POS app → confirm UI displays throttling/locked message after 4th failure; device-scoped lock holds for configured session duration

### Physical POS — Neva 01F (real ФН)

> **Most important:** this is the primary field-validation item for Phase B. If this step fails, the PR must not ship to production.

- [ ] Perform a full fiscal sale through `Neva01FFiscalCore` bridge (runtime delegate, NOT stub)
- [ ] Confirm receipt written to ФН (Фискальный Накопитель) hardware — check ОФД status via `/api/v1/statuses`
- [ ] Confirm server-side `audit_events` row: `action=fiscal.sale`, `session_id` set, no adapter stub in call chain
- [ ] Confirm receipt data (INN, FN serial, document number) present in response and stored in local DB

> Tip: before starting physical smoke on MSPOS-K or Neva, run `powershell -ExecutionPolicy Bypass -File .\verify-phase-b.ps1 -RequireHardware` to confirm automated gates are green and `adb` sees the device.

### External integrations gate

- [ ] Run live contour runner and archive the generated report: `powershell -ExecutionPolicy Bypass -File .\verify-live-integrations.ps1 -BackendBaseUrl https://<backend-host>/ -AdminPin 9999 -ChaseznakCode "<test-datamatrix>" -AgeQrData "<test-age-qr>" -EgaisIncomingPayloadPath .\payloads\egais-incoming.xml -EgaisTaraPayloadPath .\payloads\egais-tara.xml -EnableMutatingRoutes`
- [ ] Confirm `.tmp_live_integrations_evidence.md` contains `PASS` for login, feature flags, `/api/v1/statuses`, `/api/v1/egais/status`, `/api/v1/chaseznak/validate`, `/api/v1/chaseznak/verify-age`; mutating routes may stay `PENDING` only until approved test payloads are provided
- [ ] `GET /api/v1/statuses` returns real telemetry payload: `ofdQueueLength`, `lastSyncTimestamp`, `cloudServerOk`, `licenseStatus`
- [ ] `GET /api/v1/egais/status` returns `{"available":true}` before running manual ЕГАИС smoke
- [ ] With optional `ЕГАИС` / `Честный ЗНАК` features enabled and degraded status (`internet=LOST` or `cloudServer=ERROR`), core sale flow remains available with warning, while opening the affected module is denied with explicit UX message
- [ ] `POST /api/v1/auth/login` returns feature flags matching server config for `ЕГАИС`, `Честный ЗНАК`, acquiring, and SBP
- [ ] `POST /api/v1/egais/*` returns real integration result instead of HTTP 501
- [ ] `POST /api/v1/chaseznak/validate`, `/sell`, `/verify-age` return real integration result instead of HTTP 501

### Inventory and stock accounting (vitbon-kassa-1rd.5.2, 1rd.5.3)

- [ ] `POST /api/v1/documents/sync` (inventory) → HTTP 200, saved locally with `PENDING_SYNC`, sent to API; if offline, saved with `PENDING_SYNC` for later retry
- [ ] `GET /api/v1/products` → returns product list with `stock` field
- [ ] Sale/receipt flow: `product.stock` decremented only after successful fiscal receipt (rollback on failure)
- [ ] Return flow: `product.stock` incremented on successful fiscal return
- [ ] Stock conflict detection: sale with insufficient stock → conflict surfaced to operator; product not found → conflict surfaced
- [ ] Ledger reconciliation: `StockMovement` records reflect all SALE/RETURN/INCOME/WRITEOFF operations; mismatches between product stock and ledger balance are flagged
- [ ] Inventory document retry: offline-created documents are re-submitted automatically via retry mechanism

### Rollback gate (Android)

- [ ] Redeploy previous APK; confirm app still reads and uses old-format tokens from SharedPreferences (backward compat of token storage preserved)

---

## Incident Response

| Symptom | First action |
|---------|-------------|
| `401 DEVICE_MISMATCH` on legitimate device | Check `X-Device-Id` header — ensure app sends non-null/non-blank value |
| `429 TOO_MANY_REQUESTS` for valid cashier | Check `audit_events` for `reason=RATE_LIMITED`; device should unlock after session-duration TTL |
| PBKDF2 login fails for cashier with legacy hash | Verify `V6__widen_cashier_pin_hash` migration applied; hash may be corrupt or empty |
| Documents from one касса appear in another movement report | Verify `V7__add_document_ownership` applied and backend build includes document ownership filtering |
| Concurrent re-login creates 2 active sessions | Minor race; resolved on next sequential login — acceptable for Phase B |
| `security.route_access` events missing `actor_id` | Session was created but cashier record may have been deleted after session start |
