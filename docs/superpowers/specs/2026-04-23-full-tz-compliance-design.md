# Full TZ Compliance Design

Date: 2026-04-23
Owner: vitbon-kassa-87y
Scope: End-to-end implementation plan to reach production-grade compliance with the provided technical specification for Android cashier software.

## 1. Objective and success criteria

### Objective
Bring the current Android + backend system to full operational compliance with the TZ, prioritizing regulatory and fiscal correctness first, then functional breadth.

### Global success criteria
1. All TZ clauses are mapped to implemented behavior, tests, and operational evidence.
2. Fiscal operations run on real MSPOS-K and Нева 01Ф hardware with FFD runtime behavior.
3. Security/auth/roles/audit are enforced in runtime, not only declared in docs.
4. Status-based blocking policy is deterministic and tested.
5. Optional modules (ЧЗ, ЕГАИС, age verification) are production-behavior complete and feature-gated without reinstall.
6. Documentation and OpenAPI match runtime behavior.

## 2. Current-state summary (as baseline)

### Implemented baseline
- Core cashier flows exist in Android (sale/return/correction/shift/X-report/cash drawer).
- Local Room storage and cloud sync skeleton exist.
- License check with blocked mode and reports/statuses allowlist exists.
- Optional modules are feature-flagged in navigation.
- Backend has products delta + deletions, checks/documents/report APIs, and license persistence.

### Critical gaps to close
- Real KKT SDK integrations are stubs.
- FFD detection/build/retry pipeline is not fully enforced in runtime.
- Backend auth/security is demo-level and not aligned with BearerAuth declaration.
- RBAC and audit logging are incomplete.
- Status matrix enforcement is partial.
- `deletedIds` are received on Android sync but not applied to local product removal.

## 3. Delivery strategy: phased compliance program

Work is decomposed into six sequential phases to reduce risk and avoid cross-domain regression.

- Phase A (P0): KKT + FFD production runtime
- Phase B (P0): Security, auth, RBAC, audit
- Phase C (P0): Status policy and operation blocking matrix
- Phase D (P0/P1): Optional modules (ЧЗ/ЕГАИС/возраст)
- Phase E (P1): Inventory/cloud/reporting completion
- Phase F (P1): Documentation, acceptance, release evidence

Rationale: fiscal correctness and legal-risk controls have highest blast radius and must be stabilized before broad feature completion.

## 4. Traceability matrix: TZ clause → implementation phase

### 2.1 Base cashier functionality
- 2.1.1 Sale: A + E
- 2.1.2 Return: A + E
- 2.1.3 Correction checks: A
- 2.1.4 Shift open/close: A
- 2.1.5 X-report: A
- 2.1.6 Cash drawer in/out: A

### 2.2 Fiscal registrars (MSPOS-K, Нева 01Ф)
- Runtime adapter integration and error handling: A

### 2.3 Cloud inventory
- Local catalog/offline behavior: E
- Bidirectional sync and schedule/startup behavior: E
- Acceptance/writeoff/inventory documents: E

### 2.4 Reporting
- Sales, movement, fiscal reports, returns report: E

### 2.5 FFD 1.05 / 1.2
- Version detection, per-document field policy, format-recovery loop, switch restrictions: A

### 2.6 Status monitoring
- Status indicators and operational blocking policy: C

### 2.7 Users and administration
- Auth, roles, admin scopes, audit log sync: B

### 3 Optional modules
- 3.1 ЧЗ: D
- 3.2 ЕГАИС + age verification: D

### 4 Security and reliability
- Encryption/root policy/offline buffering/logging export: B + E

### 5 Documentation package
- Admin manual, cashier manual, marking/egais manual, API spec: F

### 6 Development stages
- Fully covered by phases A–F execution order.

### 7 Notes and constraints
- Remote updates, FFD switch limitations, external API keys/certs, module toggling, 7-day grace: enforced across A/B/C/D/F.

## 5. Phase A design (active phase): KKT + FFD

## 5.1 Scope
Deliver production-grade fiscal runtime for MSPOS-K and Нева 01Ф, including deterministic FFD behavior for all fiscal operations.

## 5.2 Architecture changes

### A. Fiscal adapter hardening
- Replace stub protocol calls with vendor SDK integrations in:
  - `MSPOSKFiscalCore`
  - `Neva01FFiscalCore`
- Keep shared `FiscalCore` interface stable for upper layers.

### B. FFD runtime subsystem
Introduce explicit components:
1. `FfdVersionResolver`
   - Reads version from fiscal core (`1.05` or `1.2`), handles unavailable state.
2. `FfdPolicyStore`
   - Stores last confirmed version, source (`auto|manual`), timestamp, lock status.
3. `FiscalPayloadBuilder`
   - Uses resolved version to produce strict field sets without excess tags.
4. `FiscalOperationOrchestrator`
   - Unified execution entrypoint for sale/return/correction/cash in/out/x/z/open shift.

### C. Error normalization and recovery
- Introduce `FiscalErrorMapper` to map SDK/vendor errors into domain errors.
- Recovery rule for format errors:
  1. detect `FORMAT_INVALID`
  2. force FFD re-read
  3. rebuild payload
  4. one controlled retry
  5. fail with explicit error and audit event

## 5.3 Runtime rules

### Startup rules
1. App startup triggers FFD detection.
2. If unavailable (FN not activated), require admin manual selection with immutable-lock semantics once first fiscal document succeeds.

### Shift rules
1. Before fiscal print, verify shift state.
2. If shift closed, auto-open before first fiscal check.
3. If shift >24h, enforce close guidance and allowed transitions per policy.

### Per-operation rules
Before each fiscal operation:
1. Preflight status
2. Resolve/validate FFD version
3. Build operation payload for that FFD
4. Execute SDK call
5. Handle mapped error policy

## 5.4 Testing strategy for Phase A

### Unit tests
- FFD resolver behavior (detected/manual/unknown)
- Payload field inclusion per FFD 1.05 vs 1.2
- Error mapper classification
- Retry behavior exactly once on format error

### Integration tests (SDK-abstraction level)
- Orchestrator flows for all operation types
- Shift closed → auto-open → print
- FFD mismatch recovery flow

### Hardware acceptance (mandatory)
For each of MSPOS-K and Нева 01Ф:
- sale
- return (full and partial)
- correction (income and expense)
- cash in/out
- x-report
- close shift (z-report)
- startup with FFD detection
- format-error recovery scenario

Evidence format:
- timestamp, device model, operation, result, fiscal sign/fd/fn, FFD version, operator

## 5.5 Phase A definition of done
1. All fiscal operations execute against both real SDKs and devices.
2. FFD runtime behavior is enforced before every fiscal print.
3. Format-error rebuild/retry loop passes tests and hardware checks.
4. No stub path remains active for production flavor.
5. Regression suites pass (Android unit/integration + targeted backend checks if touched).

## 6. Phase B design: security, auth, RBAC, audit

### Scope
Replace demo authentication and weak local-secret handling with enforceable security boundaries.

### Key deliverables
1. Backend auth hardening
   - Replace demo PIN token with production token lifecycle (issue/validate/revoke).
   - Enforce auth on protected routes via runtime security configuration.
2. RBAC enforcement
   - Define action-level role matrix.
   - Enforce on both backend route access and Android action entrypoints.
3. Audit logging
   - Record sensitive/business-critical actions locally and sync to backend.
   - Include actor, device, action, payload summary, outcome, timestamp.
4. Data protection
   - Encrypt sensitive local storage (tokens/credentials/critical settings).
   - Define root-device policy behavior and user/admin handling.

### Definition of done
- Unauthorized requests are rejected by backend runtime.
- Role violations are blocked and tested.
- Audit stream is complete and queryable.
- Local sensitive data is not stored in plain prefs.

## 7. Phase C design: statuses and blocking matrix

### Scope
Convert status display into deterministic operational policy.

### Key deliverables
1. Formal matrix per status source:
   - internet
   - cloud server
   - OFD backlog/connectivity
   - license status
   - ЧЗ module status
   - ЕГАИС module status
2. Enforcement points:
   - UI action availability
   - domain use case guards
   - backend-side guardrails where applicable
3. Grace-period correctness:
   - startup + periodic checks
   - 7-day offline allowance from last successful validation

### Definition of done
- Every operation has a tested allow/deny decision for each relevant status combination.

## 8. Phase D design: optional modules production behavior

### Scope
Deliver production-grade optional modules with strict feature gating.

### Key deliverables
1. ЧЗ
   - DataMatrix scan pipeline
   - validation and disposal flows
   - error blocks that prevent invalid sale completion
2. ЕГАИС
   - UTM connectivity and transactional flow
   - alcohol sale and keg opening operations
3. Age verification
   - dedicated route + API integration
   - explicit deny path blocks sale
   - verification audit trail
4. Activation model
   - dynamic on/off via features without reinstall

### Definition of done
- Optional flows are unavailable when disabled, fully enforced when enabled.

## 9. Phase E design: inventory and reporting completion

### Scope
Complete inventory consistency and period-accurate reporting.

### Key deliverables
1. Sync correctness
   - apply `deletedIds` to local product removal
   - idempotent push/pull behavior
   - conflict and retry strategy
2. Documents
   - acceptance/writeoff/inventory full roundtrip
3. Reports
   - period/date filters affect real calculations
   - movement report covers required operation classes
   - returns report completeness

### Definition of done
- Inventory and reports reconcile against source transactions for controlled test datasets.

## 10. Phase F design: documentation and release readiness

### Scope
Deliver compliance evidence package matching runtime behavior.

### Key deliverables
1. Update manuals:
   - administrator
   - cashier
   - marking/egais
2. Align OpenAPI with implemented endpoints and security behavior.
3. Produce hardware and e2e verification evidence bundle.
4. Final traceability table: TZ clause → code/test/evidence.

### Definition of done
- Documentation has no contract drift against runtime behavior.

## 11. Execution model and controls

1. Work tracked via beads epic/subtasks with explicit dependencies.
2. Each implementation unit follows RED → GREEN → REFACTOR.
3. Independent code-review pass required after each major unit.
4. Verification gate required before closure claims.
5. No phase closure without objective evidence.

## 12. Risks and mitigations

1. Vendor SDK instability
   - Mitigation: adapter isolation + error mapper + hardware regression harness.
2. Scope inflation across phases
   - Mitigation: strict per-phase DoD and dependency locking.
3. Security hardening regressions
   - Mitigation: contract tests and role-negative tests.
4. Contract drift (OpenAPI vs runtime)
   - Mitigation: mandatory spec sync check in Phase F.

## 13. Immediate next action

Proceed to writing-plans for executable task breakdown of Phase A (KKT + FFD) and begin implementation from the first ready subtask.