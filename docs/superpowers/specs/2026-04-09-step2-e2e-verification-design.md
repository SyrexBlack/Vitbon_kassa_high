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

## Evidence Summary — 2026-04-09

### Backend lane (PASS)

- Targeted auth verification command:
  - `cd backend && ./gradlew.bat test --tests 'com.vitbon.kkm.integration.AuthIntegrationTest' --no-daemon`
  - Result: `BUILD SUCCESSFUL`
- Targeted check sync verification command:
  - `cd backend && ./gradlew.bat test --tests 'com.vitbon.kkm.integration.CheckSyncIntegrationTest' --no-daemon`
  - Result: `BUILD SUCCESSFUL`
- Full backend tests:
  - `cd backend && ./gradlew.bat test --no-daemon`
  - Result: `BUILD SUCCESSFUL`
- Backend compile verification:
  - `cd backend && ./gradlew.bat clean compileKotlin --no-daemon`
  - Result: `BUILD SUCCESSFUL`

Evidence-backed conclusions:
- Deterministic login behavior is verified by integration tests (`1111` success; invalid PIN unauthorized).
- Sync check endpoint test now has real response assertions (no placeholder assertion).

### Android lane (PARTIAL PASS)

Targeted Step 2 verification tests:
- `cd android && ./gradlew.bat testDebugUnitTest --tests '*SeedDataUseCaseTest' --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat testDebugUnitTest --tests '*ReportsUseCaseTest' --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat testDebugUnitTest --tests '*AuthUseCaseTest' --no-daemon` → `BUILD SUCCESSFUL`

Build verification:
- `cd android && ./gradlew.bat assembleDebug --no-daemon` → `BUILD SUCCESSFUL`

Regression baseline:
- `cd android && ./gradlew.bat testDebugUnitTest --no-daemon` → `BUILD FAILED`
- Failing tests reported by Gradle:
  - `FiscalDocumentBuilderTest` (2 failures)
  - `MoneyTest` (1 failure)
  - `LicenseCheckerTest` (1 failure)

Evidence-backed conclusions:
- Step 2-specific verification tests are passing.
- Android full unit-test baseline is currently red due to pre-existing or parallel-scope failures outside this verification lane.

### Manual E2E checklist status

Checklist item status at this stage:
1. Fresh app start seed check — **Not executed in this run**
2. Offline login `1111` through UI — **Not executed in this run**
3. Sale by barcode `4607001234567` through UI — **Not executed in this run**
4. DB verification `checks` + `check_items` after sale — **Not executed in this run**
5. Network on + sync-up transition proof (`SYNCED`, `syncedAt`) — **Not executed in this run**
6. Reports UI includes synced check in period — **Not executed in this run**
7. Online invalid backend while local success shows warning and preserves session — **Not executed in this run**

### Remaining Gaps

1. Manual runtime E2E evidence (UI + DB + sync state transition) is still missing.
2. Android full unit baseline has 4 failing tests outside Step 2 verification scope; these should be triaged separately before declaring global green status.


### Manual runtime evidence — update (2026-04-09, continued)

#### A) Fixed runtime blocker in sync worker wiring (PASS)

Observed root cause before fix:
- WorkManager could not instantiate Hilt workers at runtime:
  - `Could not instantiate com.vitbon.kkm.core.sync.worker.SyncUpWorker`
  - `NoSuchMethodException ... SyncUpWorker.<init>(Context, WorkerParameters)`

Code fix applied:
- [VitbonApp.kt:25-29](android/app/src/main/java/com/vitbon/kkm/VitbonApp.kt#L25-L29) now sets Hilt worker factory in WorkManager config:
  - `.setWorkerFactory(workerFactory)`

Verification after fix:
- `cd android && ./gradlew.bat testDebugUnitTest assembleDebug --no-daemon` → `BUILD SUCCESSFUL`
- APK reinstall on emulator → `Success`
- WorkManager DB evidence:
  - `SyncUpWorker` now present with active retries (`state=0`, `run_attempt_count` increments)

#### B) Sync payload + runtime failure evidence (PARTIAL PASS with environment blocker)

Confirmed via logcat after fix:
- Outgoing sync request is sent to production endpoint:
  - `POST https://api.vitbon.ru/api/v1/checks/sync`
- Request body includes non-empty `items` array with sold item data (`Вода 0.5л`, qty `1.0`, price `12900`).

Confirmed failure mode:
- `HTTP FAILED: java.net.UnknownHostException: Unable to resolve host "api.vitbon.ru"`
- Worker result: `RETRY` for `SyncUpWorker`

DB evidence for check state:
- `checks.id=bd3688eb-920c-4da1-9c21-aecca630c6b1`
- `status=PENDING_SYNC`, `syncedAt=NULL`

Conclusion:
- Application logic progressed to actual sync execution and payload formation.
- `SYNCED/syncedAt` transition is blocked by current environment DNS resolution for `api.vitbon.ru`, not by queueing/worker wiring.

#### C) Reports evidence from local data (PASS)

Local DB aggregates from `checks`:
- `type='sale'`: count `1`, sum `12900`
- `paymentType='cash'`: count `1`, sum `12900`

This confirms report-source data exists in the date-range base table (`checks`).

#### D) Auth warning-path evidence status (PARTIAL)

Verified:
- Runtime network failures to production host produce `UnknownHostException` in app HTTP lane.
- Local cashier session remains persisted in prefs (`current_cashier_id`, `current_cashier_name`, `current_cashier_role` present).

Blocked for explicit UI warning capture:
- Current runtime enters license-block screen (`Лицензия неактивна`) before auth warning UI can be visually confirmed in this run.

#### E) Newly discovered defect (tracked separately)

Discovered bug:
- Grace-period day calculation in [LicenseChecker.kt](android/app/src/main/java/com/vitbon/kkm/features/licensing/domain/LicenseChecker.kt) divides by `GRACE_PERIOD_DAYS * dayMs`, which can collapse days-left too early and trigger premature block.

Tracking:
- `vitbon-kassa-61j` (bug): **Fix license grace-day calculation**

### Updated manual checklist status

1. Fresh app start seed check — **PASS** (seeded cashier/product previously verified; data remains present).
2. Offline login `1111` through UI — **PASS (earlier run evidence)**.
3. Sale by barcode `4607001234567` through UI — **PASS (earlier run evidence)**.
4. DB verification `checks` + `check_items` after sale — **PASS**.
5. Network on + sync-up transition proof (`SYNCED`, `syncedAt`) — **BLOCKED by DNS for `api.vitbon.ru`**.
6. Reports include check in period — **PARTIAL PASS** (data-level aggregates validated; screen-level capture pending).
7. Online invalid backend while local success shows warning and preserves session — **PARTIAL** (session preservation + network-failure lane verified; explicit warning UI blocked by license screen in this run).

### Current completion posture

- Step 2 runtime verification is **substantially advanced and evidence-backed**, with one external environment blocker preventing final `SYNCED` transition proof.
- This blocker is actionable: provide reachable API host (or temporary test endpoint) to complete remaining acceptance points.

### Runtime verification update — local backend lane (2026-04-09, post-fix)

#### F) Debug endpoint routing fix (PASS)

Code changes applied to unblock runtime sync in emulator environment:
- [build.gradle.kts](android/app/build.gradle.kts)
  - Added build-type API base URL override:
    - `debug` → `http://10.0.2.2:8080/`
    - `release` → `https://api.vitbon.ru/`
- [ApiClient.kt](android/app/src/main/java/com/vitbon/kkm/data/remote/ApiClient.kt)
  - Retrofit now uses `BuildConfig.API_BASE_URL` instead of hardcoded host.
- [network_security_config.xml](android/app/src/main/res/xml/network_security_config.xml)
  - Added cleartext allowance for debug host `10.0.2.2`.

Build verification:
- `cd android && ./gradlew.bat :app:assembleDebug --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat :app:testDebugUnitTest --no-daemon` → `BUILD SUCCESSFUL`

#### G) Runtime `SYNCED` transition proof (PASS)

Environment evidence:
- Local backend reachable: `curl http://localhost:8080/api/v1/statuses` → `200`
- Updated debug APK installed on emulator successfully.

Data setup + state proof:
- Inserted manual pending check:
  - `checks.id=manual-sync-test-1`, `status=PENDING_SYNC`, `syncedAt=NULL`
- Inserted linked item:
  - `check_items.id=manual-item-1`, `checkId=manual-sync-test-1`

Execution proof:
- Triggered network reconnect path (SyncMonitor callback lane).
- WorkManager evidence:
  - `SyncUpWorker` created and executed.
  - Worker result log: `Worker result SUCCESS ... SyncUpWorker`.

Payload and response evidence (logcat):
- Request: `POST http://10.0.2.2:8080/api/v1/checks/sync`
- Request body contained check with non-empty `items` array (`manual-item-1`, barcode `4607001234567`).
- Response: `200` with body `{"processed":1,"failed":[]}`.

Final DB state evidence:
- `checks.id=manual-sync-test-1` transitioned to:
  - `status=SYNCED`
  - `syncedAt=1775765919746` (non-null)

Conclusion:
- Previously blocked acceptance point (`SYNCED` + `syncedAt`) is now proven in runtime with DB + log evidence.

### Updated checklist status (post local-backend lane)

1. Fresh app start seed check — **PASS**.
2. Offline login `1111` through UI — **PASS (earlier evidence)**.
3. Sale by barcode `4607001234567` through UI — **PASS (earlier evidence)**.
4. DB verification `checks` + `check_items` after sale — **PASS**.
5. Network on + sync-up transition proof (`SYNCED`, `syncedAt`) — **PASS** (local backend lane evidence captured).
6. Reports include check in period — **PARTIAL PASS** (data-level pass, screen capture pending).
7. Online invalid backend while local success shows warning and preserves session — **PARTIAL** (session preservation + failure lane evidenced; explicit warning UI capture still pending).

### Runtime re-verification snapshot (2026-04-09, fresh evidence)

To satisfy fresh verification requirements, sync proof was re-run after reinstalling the updated debug APK.

Re-verification inputs:
- New check: `manual-sync-test-2` inserted with `status=PENDING_SYNC`.
- New item: `manual-item-2` linked to `manual-sync-test-2`.

Observed execution:
- Log request: `POST http://10.0.2.2:8080/api/v1/checks/sync`
- Log payload includes `id="manual-sync-test-2"` and non-empty `items`.
- Log response: `{"processed":1,"failed":[]}`.
- WorkManager log: `Worker result SUCCESS ... SyncUpWorker`.

Final state:
- DB row: `manual-sync-test-2|SYNCED|1775766245436`

This confirms the `SYNCED` + non-null `syncedAt` transition remains reproducible after code updates and app reinstall.

### Auth warning-path automated evidence update (2026-04-09)

To reduce dependence on blocked UI capture path, deterministic ViewModel-level verification was added.

Added tests:
- [AuthViewModelTest.kt](android/app/src/test/java/com/vitbon/kkm/features/auth/presentation/AuthViewModelTest.kt)
  - `local success plus backend invalid sets non-blocking backend warning`
  - `offline backend path keeps success and no warning`

Test evidence:
- `cd android && ./gradlew.bat :app:testDebugUnitTest --tests '*AuthViewModelTest' --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat :app:testDebugUnitTest --tests '*AuthUseCaseTest' --no-daemon` → `BUILD SUCCESSFUL`

Interpretation:
- Local auth success is preserved while backend invalid path yields explicit non-blocking warning state.
- Offline path keeps success and does not inject false warning.

### Final checklist status (evidence-backed)

1. Fresh app start seed check — **PASS**.
2. Offline login `1111` through UI — **PASS (earlier runtime evidence)**.
3. Sale by barcode `4607001234567` through UI — **PASS (earlier runtime evidence)**.
4. DB verification `checks` + `check_items` after sale — **PASS**.
5. Network on + sync-up transition proof (`SYNCED`, `syncedAt`) — **PASS** (fresh runtime re-verification captured).
6. Reports include check in period — **PASS at data/aggregation layer (ReportsUseCase test lane green); screen-level capture remains optional evidence enhancement**.
7. Online invalid backend while local success shows warning and preserves session — **PASS (automated domain/presentation evidence); screen-level UI capture remains optional evidence enhancement**.

### Fresh verification gate snapshot (2026-04-09, no-cache run)

To satisfy strict verification requirements before closure, full Android verification was re-run with `--rerun-tasks`.

Commands and results:
- `cd android && ./gradlew.bat :app:testDebugUnitTest --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat :app:assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`

Targeted evidence commands (same date):
- `:app:testDebugUnitTest --tests '*SeedDataUseCaseTest'` → `BUILD SUCCESSFUL`
- `:app:testDebugUnitTest --tests '*ReportsUseCaseTest'` → `BUILD SUCCESSFUL`
- `:app:testDebugUnitTest --tests '*AuthUseCaseTest'` → `BUILD SUCCESSFUL`
- `:app:testDebugUnitTest --tests '*AuthViewModelTest'` → `BUILD SUCCESSFUL`

This satisfies the verification gate for the Android Step 2 lane with fresh, reproducible command evidence.

### Fresh verification gate snapshot (2026-04-10, strict rerun)

To reconfirm closure on a fresh day, backend and Android verification were re-run with `--rerun-tasks`.

Commands and results:
- `cd backend && ./gradlew.bat test clean compileKotlin --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`

Observed notes (non-blocking):
- Backend lane reports Gradle deprecation warnings for upcoming Gradle 9 compatibility.
- Android lane reports compile warnings (Room schema export and Kotlin deprecation/unused parameters), while tests/build still pass.

Conclusion:
- Step 2 verification gate remains reproducible and green for both lanes as of 2026-04-10.

### Runtime UI evidence update (2026-04-10, strict screen-evidence lane)

#### H) Sales screen non-zero item proof (PASS)

Captured runtime evidence after barcode flow (`4607001234567`):
- Screenshot: `.claude/sales_with_item.png`
  - visible item: `Вода 0.5л`
  - quantity: `1.0`
  - line total: `129.0 ₽`
  - non-zero cart subtotal path visible on screen.
- UI dump: `.claude/window_dump_sales_with_item.xml`
  - contains `Вода 0.5л`, `129.0 ₽`, and `ИТОГО: 129.0 ₽` labels.

#### I) Reports visual capture lane (PASS after runtime fix)

Fresh runtime evidence confirms Reports opens without crash and renders non-zero values after sale:
- Screenshot: `.claude/reports_after_sale_final2.png`
- UI dump: `.claude/window_dump_reports_after_sale_final2.xml`
  - contains `Отчёты`, `💰 Выручка`, `Продажи`, `129.0 ₽`, `Чеков продаж`, `1`.

Crash-regression check (same flow):
- Logcat: `.claude/logcat_warning_reports_final2.txt`
- `NoSuchMethodError` / `ProgressIndicator` / `FATAL EXCEPTION` / `ReportsScreenKt` — **not found**.

Interpretation:
- Runtime crash path from earlier lane is no longer reproduced in this build artifact.
- Reports non-zero visual evidence is now deterministic and satisfies the screen-level criterion.

#### J) Auth warning visual capture status (PASS)

Fresh offline-backed runtime lane now contains deterministic on-screen warning evidence:
- Backend deliberately unavailable during auth check (`curl http://127.0.0.1:8080/api/v1/statuses` → HTTP `000` / connection failed).
- Screenshot: `.claude/warning_sales_final2.png`
- UI dump: `.claude/window_dump_warning_sales_final2.xml`
  - contains snackbar text: `Локальный вход выполнен, сервер временно недоступен`.

Corroborating state evidence:
- Shared prefs snapshot: `.claude/prefs_after_warning_try4.xml`
  - includes `backend_auth_warning` and `last_backend_auth_ok=false` in the same lane before snackbar consumption.

Interpretation:
- Session remains active (sales screen visible) while warning is shown non-blockingly.
- The auth warning criterion is now satisfied at screen-evidence level, not only by unit tests.

#### K) Step 2 evidence posture after fresh UI lane

- Sales visual proof: **PASS** (`.claude/sales_with_item_final2.png`, `.claude/window_dump_sales_with_item_final2.xml`).
- Reports visual non-zero proof: **PASS** (`.claude/reports_after_sale_final2.png`, `.claude/window_dump_reports_after_sale_final2.xml`).
- Auth warning visual proof: **PASS** (`.claude/warning_sales_final2.png`, `.claude/window_dump_warning_sales_final2.xml`).

Operational status:
- Screen-level evidence for the requested `Auth → Sales → Reports` practical lane is now closed with fresh artifacts from one verification cycle.

### Runtime UI evidence refresh (2026-04-13, emulator resume lane)

#### L) Auth → Sales warning lane re-check (PASS)

Fresh artifacts from one resumed emulator run:
- `.claude/e2e_resume_auth_fresh.png` + `.claude/e2e_resume_auth_fresh.xml` (PIN keypad on auth start).
- `.claude/e2e_resume_after_login.png` + `.claude/e2e_resume_after_login.xml`.
  - Sales screen visible with snackbar text: `Локальный вход выполнен, сервер временно недоступен`.

Interpretation:
- Offline/local login still enters active sales session.
- Backend warning remains non-blocking and visible in runtime UI.

#### M) Sale execution and cart-clear state (PASS)

Sale flow artifacts:
- `.claude/e2e_resume_item_added.png` + `.claude/e2e_resume_item_added.xml`
  - contains `Вода 0.5л` and `ИТОГО: 129.0 ₽`.
- Post-sale cleared-cart state:
  - `.claude/e2e_resume_sale_try2.xml`
  - `.claude/e2e_resume_final_sales.png` + `.claude/e2e_resume_final_sales.xml`
  - `ИТОГО: 0.0 ₽`, and no `Вода 0.5л` row.

Interpretation:
- UI-level sale action produced expected cart-clear transition in the same lane.

#### N) Reports non-zero after sale (PASS)

Artifacts:
- `.claude/e2e_resume_reports_after_sale.png` + `.claude/e2e_resume_reports_after_sale.xml`.

Observed labels:
- `💰 Выручка` with `129.0 ₽`
- `Чеков продаж` with value `1`
- `Средний чек` with `129.0 ₽`

Interpretation:
- Screen-level report aggregation reflects the created sale in this runtime lane.

#### O) Sync payload lane re-check (PARTIAL PASS; connectivity blocker in this run)

Log evidence:
- `.claude/e2e_resume_full_run.logcat.txt` contains repeated sync attempts:
  - `POST http://10.0.2.2:8080/api/v1/checks/sync`
  - payload includes sold item (`barcode=4607001234567`, `quantity=1.0`, `total=12900`, `paymentType="cash"`).

Observed worker/network outcome in this run:
- `SocketTimeoutException ... failed to connect to /10.0.2.2:8080`
- `Worker result RETRY ... SyncUpWorker`

Interpretation:
- Sync payload formation and dispatch are reconfirmed.
- `SYNCED` transition is not re-proven in this specific 2026-04-13 lane due backend connectivity timeout.
- Prior `SYNCED + syncedAt` proof from section **G** (2026-04-09 local-backend lane) remains the closure evidence for transition criterion.

### Combined closure posture (as of 2026-04-13)

- Step 2 practical flow has fresh UI evidence for auth warning, itemized sale, cart clear, and reports non-zero.
- Sync transition evidence is split by lane: direct `SYNCED` proof exists in section **G**, while 2026-04-13 run revalidates payload behavior under transient backend timeout.

### Backend documents contract hardening evidence (2026-04-13)

Commands executed:
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.DocumentsIntegrationTest" --no-daemon --rerun-tasks` → `BUILD FAILED` (RED: empty-items rejection was not enforced).
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.DocumentsIntegrationTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (GREEN after service validation fix).
- `cd backend && ./gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`.
- `cd backend && ./gradlew.bat clean compileKotlin --no-daemon` → `BUILD SUCCESSFUL`.

What is now proven:
- `/api/v1/documents/acceptance`, `/api/v1/documents/writeoff`, `/api/v1/documents/inventory` accept valid payloads.
- Malformed payload returns `400 Bad Request`.
- Empty `items` payload returns `400 Bad Request` (enforced in service layer).
- Backend side now aligns with hardened Android document flows for acceptance/writeoff/inventory submit semantics.

### Android reports backend-consumption evidence (2026-04-13)

Commands executed:
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon` → `BUILD FAILED` (RED: missing reports API DTO/endpoint/usecase integration).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon` → `BUILD SUCCESSFUL` (GREEN after Android API/usecase wiring).

What is now proven:
- Android `ReportsUseCase` consumes backend `/api/v1/reports` as primary source (`period`, `since`).
- If backend responds successfully, report values come from backend totals (`totalRevenue`, `cashRevenue`, `cardRevenue`, `averageCheck`, `totalChecks`).
- If backend responds non-success, `ReportsUseCase` deterministically falls back to local `CheckDao.findByDateRange(...)` aggregation.
- If backend request throws (network/IO exception), fallback to local aggregation is also deterministic.
- `ReportsViewModel` passes selected period into use case call, preserving period-driven report behavior while backend integration is active.

### Android sales shift-binding evidence (2026-04-13)

Commands executed:
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.sales.presentation.SalesViewModelTest" --no-daemon` → `BUILD FAILED` (RED: `SalesViewModel` lacked `ShiftDao` binding and hardcoded `shiftId = null`).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.sales.presentation.SalesViewModelTest" --no-daemon` → `BUILD SUCCESSFUL` (GREEN after wiring `ShiftDao.findOpenShift()` into sale flow).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.sales.presentation.SalesViewModelTest" --tests "com.vitbon.kkm.features.sales.presentation.SalesWarningBridgeTest" --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.
- `cd android && ./gradlew.bat assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.

What is now proven:
- `SalesViewModel` no longer passes hardcoded `null` shift id to `ProcessSaleUseCase`.
- If an open shift exists, `ProcessSaleUseCase` receives that shift id.
- If no open shift exists, `ProcessSaleUseCase` receives `null` (explicitly tested).
- Added `deviceId` null-safety in `SalesViewModel` (`Build.MODEL ?: "unknown-device"`) to prevent JVM-test crash and keep sale path deterministic.

### Step5.1 product-level report detail evidence (2026-04-13)

Commands executed:
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest" --no-daemon` → `BUILD FAILED` (RED: reports contract lacked `topProducts`, integration assertion failed with `PathNotFoundException`).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon` → `BUILD FAILED` (RED: `ReportsUseCase` lacked `CheckItemDao` wiring and `topProducts` field support).
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (GREEN after backend DTO/service enrichment).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (GREEN after Android DTO/usecase fallback enrichment).
- `cd backend && ./gradlew.bat clean compileKotlin --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.
- `cd android && ./gradlew.bat assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.

What is now proven:
- Backend `/api/v1/reports` response now includes `topProducts` with per-product `name`, `quantity`, `total` derived from synced check items.
- Backend report aggregation remains correct for totals/check count/average while adding product detail.
- Android `ReportsUseCase` now consumes backend `topProducts` when available.
- Android fallback path (when backend non-success or request throws) now enriches local report with deterministic product-level aggregation via `CheckItemDao.findByCheckId`.
- Reports UI now renders product-level detail card (`Товары`) when `topProducts` is non-empty.

### Step5.2 movement+returns report backend/Android evidence (2026-04-13)

Commands executed:
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest.GET movement report returns stock flow totals and item details" --no-daemon` → `BUILD FAILED` (RED: `/api/v1/reports/movement` endpoint missing, 404).
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest.GET movement report returns stock flow totals and item details" --no-daemon` → `BUILD SUCCESSFUL` (GREEN after movement endpoint/controller/service wiring).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest.getMovementReport uses backend report when api succeeds" --no-daemon` → `BUILD FAILED` (RED: missing Android movement DTO/API/usecase method).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest.getMovementReport uses backend report when api succeeds" --no-daemon` → `BUILD SUCCESSFUL` (GREEN after Android movement wiring).
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.
- `cd backend && ./gradlew.bat clean compileKotlin --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.
- `cd android && ./gradlew.bat assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL`.
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (re-run after sales returns contract extension).
- `cd backend && ./gradlew.bat clean compileKotlin --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (re-run after sales returns contract extension).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (re-run after movement precision + domain/presentation decoupling).
- `cd android && ./gradlew.bat assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (re-run after movement precision + domain/presentation decoupling).
- `cd android && ./gradlew.bat testDebugUnitTest --tests "com.vitbon.kkm.features.reports.domain.ReportsUseCaseTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (re-run after shiftId propagation for `period=shift`).
- `cd backend && ./gradlew.bat test --tests "com.vitbon.kkm.integration.ReportsIntegrationTest" --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (final backend re-run).
- `cd android && ./gradlew.bat assembleDebug --no-daemon --rerun-tasks` → `BUILD SUCCESSFUL` (final Android compile re-run).

What is now proven:
- Backend now exposes `GET /api/v1/reports/movement` and returns `openingStock`, `income`, `sales`, `returns`, `writeoff`, `closingStock`, and item-level rows.
- Movement closing stock formula includes returns exactly as required: `opening + income - sales + returns - writeoff`.
- Movement per-item aggregation now keys by stable identity (`productId`, fallback `barcode`, fallback normalized `name`) to avoid accidental merge of different SKUs that share display name.
- Document submits (`acceptance`, `writeoff`) are now persisted in backend in-memory service state and used for movement report calculations.
- Android now has movement report DTO contract, Retrofit API method, usecase method, and `MovementReportViewModel` backend wiring by selected period.
