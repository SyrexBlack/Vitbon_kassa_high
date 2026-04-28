# Phase B Release Checklist — Physical POS

**Branch:** `feat/phase-b-security-auth-rbac-audit-pr`
**Commits:** `e60005c`, `2282a31`
**Schema migrations required:** `V5__add_auth_sessions_and_audit.sql`, `V6__widen_cashier_pin_hash.sql`

> **Prerequisite:** Run Flyway migrations **before** deploying the new JAR. `V6__widen_cashier_pin_hash` must apply before PBKDF2 login rotation activates.

---

## Backend

### Pre-deployment

- [ ] Run full backend test suite; confirm:
  - `SecurityRouteGuardIntegrationTest` — 8/8 passed
  - `AuthIntegrationTest` — 9/9 passed
- [ ] Run Flyway migrate:
  ```bash
  flyway -locations=filesystem:src/main/resources/db/migration migrate
  ```
  Confirm `V5` and `V6` apply cleanly; record final schema checksum.
- [ ] **Rollback schema note:** If rolling back the JAR, `ALTER COLUMN pin_hash TYPE VARCHAR(64)` is safe **only if no PBKDF2 hashes exist in production** (data truncation risk). Use DB backup before altering.

### Post-deployment smoke — single cashier

- [ ] `POST /api/v1/auth/login { "pin": "...", "deviceId": "device-A" }` → HTTP 200, body contains `token`, `cashier`, `features`, `expiresAt`; token TTL = 8 hours
- [ ] Replay same token with `X-Device-Id: device-B` → HTTP 401, `{"reason":"DEVICE_MISMATCH"}`
- [ ] CASHIER role token → `GET /api/v1/statuses` → HTTP 403
- [ ] ADMIN role token → `GET /api/v1/statuses` → HTTP 200
- [ ] `POST /api/v1/auth/logout` with valid token → HTTP 200; token no longer usable

### Post-deployment smoke — audit

- [ ] After each authorized request, query `audit_events` table:
  - `security.route_access` rows have non-null `actor_id`, `device_id`, `session_id`
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

---

## Android POS App

### Pre-deployment

- [ ] `./gradlew testDebugUnitTest` — confirm `AuthTokenStoreTest` 3/3 passed

### Physical POS — API 23 device (Android 6)

- [ ] Install APK; login as cashier; capture bearer token
- [ ] Install same APK version on second API 23 device; attempt request with token from device 1 → HTTP 401 `DEVICE_MISMATCH`
- [ ] Confirm `X-Device-Id` header present and non-null in OkHttp request logs for all authenticated endpoints
- [ ] Logout; confirm token cleared from `AuthTokenStore` (SharedPreferences); subsequent requests carry no `Authorization` header

### Physical POS — API 23 device — rate limit UX

- [ ] Rapid-fire 4 invalid PIN entries on POS app → confirm UI displays throttling/locked message after 4th failure; device-scoped lock holds for configured session duration

### Physical POS — Neva 01F (real ФН)

> **Most important:** this is the primary field-validation item for Phase B. If this step fails, the PR must not ship to production.

- [ ] Perform a full fiscal sale through `Neva01FFiscalCore` bridge (runtime delegate, NOT stub)
- [ ] Confirm receipt written to ФН (Фискальный Накопитель) hardware — check ОФД status via `/api/v1/statuses`
- [ ] Confirm server-side `audit_events` row: `action=fiscal.sale`, `session_id` set, no adapter stub in call chain
- [ ] Confirm receipt data (INN, FN serial, document number) present in response and stored in local DB

### Rollback gate (Android)

- [ ] Redeploy previous APK; confirm app still reads and uses old-format tokens from SharedPreferences (backward compat of token storage preserved)

---

## Incident Response

| Symptom | First action |
|---------|-------------|
| `401 DEVICE_MISMATCH` on legitimate device | Check `X-Device-Id` header — ensure app sends non-null/non-blank value |
| `429 TOO_MANY_REQUESTS` for valid cashier | Check `audit_events` for `reason=RATE_LIMITED`; device should unlock after session-duration TTL |
| PBKDF2 login fails for cashier with legacy hash | Verify `V6__widen_cashier_pin_hash` migration applied; hash may be corrupt or empty |
| Concurrent re-login creates 2 active sessions | Minor race; resolved on next sequential login — acceptable for Phase B |
| `security.route_access` events missing `actor_id` | Session was created but cashier record may have been deleted after session start |
