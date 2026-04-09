# MVP Step 2 — E2E Verification Design (Auth → Sales → Fiscal → Sync → Reports)

## Context and Goal

Step 2 implementation is complete at code level, but MVP readiness depends on evidence, not assumptions. The goal of this design is to define an evidence-driven verification protocol for the first practical flow:

1. Local-first authorization
2. Sale with fiscal operation
3. Sync up with check items payload
4. Status transition to `SYNCED`
5. Reports reading full date-range checks

The design prioritizes reproducible proof while keeping scope aligned with shell-stage MVP constraints.

## Scope

### In Scope

- Verify hybrid auth behavior:
  - local PIN gate works offline
  - backend validation is best-effort
  - invalid backend response shows warning without terminating local session
- Verify sale persistence and fiscal path:
  - check is stored locally
  - check items are stored locally
  - fiscal result is reflected in local check state
- Verify sync-up behavior:
  - `CheckDto.items` is populated from local `check_items`
  - failed checks move to `ERROR`
  - successful checks move to `SYNCED` with `syncedAt`
- Verify reports behavior:
  - report aggregates use date-range checks, not only pending sync checks
- Verify deterministic backend demo login:
  - PIN `1111` succeeds
  - non-demo PIN returns `401`

### Out of Scope

- Full UI automation across all screens
- Security hardening beyond demo constraints
- Architecture expansion outside Step 2 MVP flow

## Verification Strategy

### Approach A (Recommended): Semi-automated evidence protocol

Use a hybrid protocol:

- Automated build and test verification for repeatability
- Targeted manual E2E smoke for cross-layer behavior not fully covered by existing tests
- Database and log evidence for each critical state transition

This provides strong confidence with minimal additional complexity.

### Alternatives Considered

1. Fully automated UI E2E (rejected for now)
   - Higher confidence potential, but non-trivial setup/flake overhead for shell stage
2. Manual-only checklist (rejected for now)
   - Fast, but weak reproducibility and weaker regression protection

## System Under Verification

### Android Components

- Auth domain and presentation:
  - local PIN auth, background backend validation, warning surface
- Sales use case:
  - local check + local check items persistence
  - fiscal invocation
- Sync manager/service:
  - pending check upload with item payload
  - state transitions to `ERROR` / `SYNCED`
- Reports use case:
  - date-range selection over checks
- Bootstrap seed:
  - one-time seed for demo cashier/product

### Backend Components

- Auth service and controller:
  - deterministic login behavior for demo PIN
  - HTTP 401 for invalid PIN

## Evidence Matrix

Each acceptance point is considered complete only with command output or state evidence.

1. Build evidence
   - Android: `assembleDebug` success
   - Backend: `clean compileKotlin` success

2. Auth evidence
   - Local demo cashier exists via seed
   - Offline PIN `1111` local auth success
   - Online invalid backend response produces warning and preserves session

3. Sale persistence evidence
   - New row in `checks`
   - Related rows in `check_items` with matching `checkId`

4. Sync evidence
   - Outgoing payload contains non-empty `items` for synced check
   - Successful upload transitions check to `SYNCED`
   - `syncedAt` is populated
   - Failed upload path transitions failed check to `ERROR`

5. Reports evidence
   - Report date-range includes newly created synced check
   - Aggregates are based on full range, not pending-only subset

6. Backend auth evidence
   - `1111` returns valid login payload
   - non-`1111` returns `401`

## Execution Plan Decomposition

The verification work is decomposed into linear sub-tasks:

1. Verify baseline build health (Android + Backend)
2. Verify backend deterministic auth behavior
3. Verify seed and local auth prerequisites
4. Verify sale persistence (`checks` + `check_items`)
5. Verify sync payload + status transitions
6. Verify report aggregation over date range
7. Capture evidence and summarize pass/fail + gaps

## Failure Handling Rules

- If a verification command fails, do not mark acceptance criteria as complete.
- If a mismatch appears between expected and observed behavior, treat it as a defect and create a separate tracked issue.
- If a step is blocked by environment/setup, record the exact blocker and continue with independent verification steps.

## Test and Verification Commands

### Build

- Android:
  - `cd android && ./gradlew.bat assembleDebug --no-daemon`
- Backend:
  - `cd backend && ./gradlew.bat clean compileKotlin --no-daemon`

### Backend auth behavior

- Run backend tests focused on auth integration, or direct API checks against local backend:
  - expected success for PIN `1111`
  - expected `401` for non-demo PIN

### Android behavior checks

- Run relevant unit tests for auth/sync where available
- Run manual smoke for cross-component flow and inspect local DB state

## Acceptance Criteria

Step 2 E2E verification is accepted when all are true:

1. Android and backend builds are green.
2. Backend deterministic auth behavior is proven (`1111` pass, invalid PIN `401`).
3. Sale creates check and check items in local DB.
4. Sync pushes item payload and updates states (`SYNCED`/`ERROR`) correctly.
5. Reports include checks by date range (not pending-only logic).
6. Evidence is captured in terminal output and linked to each criterion.

## Risks and Mitigations

- Risk: manual E2E steps are under-documented.
  - Mitigation: enforce evidence matrix and checklist ordering.
- Risk: sync behavior appears successful without checking payload content.
  - Mitigation: validate item presence explicitly in payload/state outcome.
- Risk: false confidence from compilation-only checks.
  - Mitigation: require runtime/data-layer verification before completion claims.
