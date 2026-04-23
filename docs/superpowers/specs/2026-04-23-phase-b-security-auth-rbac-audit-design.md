# Phase B Security/Auth/RBAC/Audit Design

Date: 2026-04-23
Owner: vitbon-kassa-0xt
Scope: Implement Phase B (P0) from full TZ compliance program with strict online-first authentication, server-owned sessions, deterministic RBAC, emergency admin mode, and backend-source audit.

## 1. Objective and constraints

### Objective
Replace demo-level auth and weak runtime enforcement with a production security baseline that is testable, auditable, and compatible with fiscal/compliance requirements.

### Fixed decisions (approved)
1. Auth mode: strict online-first.
2. Session model: opaque access token + server-side session persistence.
3. Emergency mode: ADMIN-only, 15 minutes, settings/diagnostics only, no fiscal or sales operations.
4. Audit model: backend is source of truth; Android keeps only local transport buffer.
5. Concurrent sessions: one active session per cashier; new login revokes previous session.

## 2. Current-state baseline and gap

### Backend baseline
- `AuthService` currently uses demo constants (`DEMO_PIN`, `DEMO_TOKEN`) and no real token lifecycle.
- `AuthController` only forwards login/logout and strips bearer prefix.
- OpenAPI declares bearer auth for many endpoints, but runtime enforcement is not currently aligned with strict session validation.

### Android baseline
- `AuthUseCase` authenticates locally by hashed PIN (`cashierDao.findByPinHash`) and only then calls backend as best-effort.
- Token and role are persisted in plain `SharedPreferences` keys (e.g., `auth_token`, `current_cashier_role`).
- Operational guardrails are not centrally enforced as strict role/session policy.

### Security gap summary
The system currently has policy declarations but lacks deterministic runtime enforcement boundaries for session validity, RBAC deny paths, and auditable high-risk events.

## 3. Architecture (hard-cutover)

### 3.1 Backend security subsystem

Introduce explicit backend auth/session components:
1. Session issuance service (opaque token generation, high entropy).
2. Session validator (token hash lookup, expiry/revocation checks).
3. Auth filter/interceptor for protected routes.
4. Session revocation service (logout + forced revoke on re-login).

Data model additions:
- `auth_sessions`
  - `id` (UUID)
  - `cashier_id` (UUID/string aligned with existing cashier identity model)
  - `device_id` (string)
  - `token_hash` (string)
  - `issued_at` (timestamptz)
  - `expires_at` (timestamptz)
  - `revoked_at` (timestamptz nullable)
  - `revoke_reason` (string nullable)

Single-session rule:
- On successful login, revoke any existing active session for same cashier before issuing new token.

Validation rule:
- Protected endpoints require token resolving to active, non-expired, non-revoked session.
- Fail states return 401 with deterministic error payload.

### 3.2 Android auth subsystem

Replace local-first flow with backend-first flow:
1. `authenticate(pin)` calls backend login first.
2. If login fails/unreachable, standard cashier login fails.
3. Local PIN lookup is removed as primary entry path for normal mode.

Token persistence hardening:
- Move auth token storage from plain shared prefs to encrypted local storage abstraction.
- Keep role and current cashier data sourced from latest validated backend session context.

Compatibility cut:
- Remove `validateWithBackendBestEffort` behavior and warning-based partial success semantics.

## 4. RBAC model and enforcement

### 4.1 Role model
Canonical roles remain:
- `ADMIN`
- `SENIOR_CASHIER`
- `CASHIER`

### 4.2 Enforcement layers

1. **Backend route-level enforcement**
   - Every protected endpoint must verify both valid session and required role.
   - Role mismatch returns 403.

2. **Android domain-level guards**
   - Sensitive use cases (sales/returns/corrections/shift/cash in/out/admin settings) enforce role checks before execution.
   - UI hiding is treated as convenience only; domain guard is authoritative on client.

3. **Policy matrix artifact**
   - Define one explicit allow/deny matrix for high-risk operations and keep tests mapped to matrix rows.

## 5. Emergency ADMIN mode

### Activation conditions
- Backend unavailable at login time.
- User identifies as ADMIN via configured emergency credential path.

### Limits
- TTL exactly 15 minutes from grant.
- Allowed scope: settings and diagnostics only.
- Explicitly denied scope:
  - sale/return/correction
  - shift open/close/x-report
  - cash in/out
  - synchronization actions that mutate business/fiscal state

### Expiry behavior
- Automatic session invalidation and return to auth screen when TTL expires.

### Audit requirement
Every emergency enter/exit/deny event is mandatory audit output.

## 6. Audit architecture

### 6.1 Backend source-of-truth table

Add `audit_events` with fields:
- `event_id` (UUID)
- `actor_id`
- `actor_role`
- `device_id`
- `session_id`
- `action`
- `target`
- `result` (`SUCCESS|DENY|FAIL`)
- `reason` (nullable)
- `created_at`

### 6.2 Android local buffer
- Android writes minimal local audit buffer entries when offline/transiently disconnected.
- Buffer is transport-only and purged after successful backend ingestion acknowledgment.
- No long-term local audit authority.

### 6.3 Mandatory audited actions
- login success/failure
- logout
- forced session revoke on re-login
- RBAC denies
- emergency mode enter/exit
- emergency-mode denied operation attempts

## 7. Data migrations and repository changes

### Migration strategy
- Add `V5__add_auth_sessions_and_audit.sql`.
- Optional `V6__add_role_policies.sql` only if matrix is DB-driven (otherwise keep policy in code for Phase B and avoid premature configuration complexity).

### Repository layer
Introduce repositories for sessions and audit entities following existing `JpaRepository` style and timestamp query patterns used in project.

## 8. API/OpenAPI alignment

### Required contract updates
1. Document opaque bearer tokens (not JWT-specific behavior).
2. Add explicit error responses for:
   - 401 invalid/expired/revoked session
   - 403 role forbidden
3. Update auth endpoint semantics:
   - login invalidates previous active session for same cashier
   - logout revokes active session

### Backward-compatibility stance
Phase B is hard-cutover; no dual auth semantics retained.

## 9. Testing strategy

### Backend integration tests
Extend and add tests around:
- login success/failure and session issuance
- single-session revocation behavior
- protected endpoint unauthorized (no token/invalid token/revoked token)
- role forbidden paths (403)
- audit event persistence for required action set

### Android unit/domain tests
Update tests for:
- strict online-first auth behavior (no local-success fallback)
- emergency admin TTL and scope allow/deny rules
- role guards in fiscal/business use cases
- secure token persistence abstraction behavior

### Regression gates
1. Backend targeted integration suite for auth/security/audit.
2. Full Android unit suite (`:app:testDebugUnitTest`).
3. Any touched backend regression suite.

## 10. Rollout sequence

1. Backend sessions + audit + auth filter + tests.
2. Android auth cutover + secure storage + role/emergency guards + tests.
3. OpenAPI and documentation sync.
4. Verification evidence capture and phase closure.

## 11. Definition of Done (Phase B)

1. Demo auth paths removed from runtime.
2. No valid backend session => no normal cashier access.
3. One active session per cashier enforced and tested.
4. Emergency ADMIN mode constrained to 15-minute diagnostics/settings scope and audited.
5. RBAC allow/deny behavior enforced on backend and Android domain guards.
6. Backend audit trail complete for mandatory actions; Android local audit acts only as sync buffer.
7. Test suites for new security behavior pass with evidence.
8. OpenAPI reflects actual runtime auth/session/forbidden behavior.

## 12. Risks and mitigations

1. **Operational lockout risk during cutover**
   - Mitigation: emergency ADMIN mode with tight scope and TTL.
2. **Role drift between client and server**
   - Mitigation: backend authoritative role checks + client guard tests.
3. **Audit loss during transient offline periods**
   - Mitigation: durable local buffer with retry and ack-based purge.
4. **Contract drift**
   - Mitigation: explicit OpenAPI update and integration tests for 401/403 semantics.

## 13. Immediate next step

Write executable implementation plan for Phase B (task-by-task, TDD-first), then decompose into beads sub-tasks with dependencies and execute in isolated worktree.
