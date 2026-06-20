# PITFALLS.md — Android Kassovoye Prilozheniye (POS)

**Purpose:** Catalog of critical mistakes specific to Android cashier/POS applications for the Russian
market. Each pitfall is grounded in 54-ФЗ regulatory requirements, fiscal hardware constraints, or
ecosystem integration realities — not generic software-engineering advice.

**Scope:** Android-only cashier app (VITBON) with fiscal runtime, MSPOS-K / Нева 01Ф adapters,
cloud sync, and optional Честный ЗНАК / ЕГАИС modules.

**Downstream consumer:** Roadmap and planning. Each entry is actionable: detection signals, prevention
strategy, and the phase that should own the fix.

---

## P1 — Critical (blocks certification or causes fiscal data loss)

### P1.1: ФН lock-in — FFD version switched after first fiscal document

**Why it's critical:** 54-ФЗ and ФН firmware prohibit changing FFD version (1.05 ↔ 1.2) once the
FN has recorded any fiscal document. Switching causes the ФН to reject all subsequent operations,
producing a hard brick until re-registration.

**Warning signs:**
- `FfdVersionResolver` exposes a `setManualVersion()` method that writes to persistent storage.
- Any code path calls `setManualVersion()` after `fiscalStatus.currentFdNumber > 0`.
- FFD toggle UI exists and is accessible to the operator.

**Prevention strategy:**
- Make FFD version immutable after first fiscal document. Enforce at the `FfdVersionResolver`
  level: throw `IllegalStateException` on any attempt to persist a version change when
  `fiscalStatus.currentFdNumber > 0`.
- Add a unit test: `setManualVersion() after FFD=1.05 and currentFd=5 → throws`.
- The UI must never show an FFD switch control once the device is in use.

**Phase:** Phase A (KKT + FFD) — `FfdVersionResolver` contract.

---

### P1.2: Synthetic fiscal identifiers in production code path

**Why it's critical:** A fiscal document with a synthetic/placeholder fiscal sign (e.g., `MSP_12345`)
is legally invalid. It will be rejected by ОФД, corrupts the ФН's sequential integrity, and
constitutes a 54-ФЗ violation.

**Warning signs:**
- Test fixtures use strings prefixed `MSP_`, `NEVA_`, `SYNTH_` in fiscal-sign fields.
- `FiscalCore` or adapter returns `Result.Success` when the underlying ККТ call threw or returned
  null fiscal metadata.
- Fiscal-result validation only checks `!= null`, not format/length.

**Prevention strategy:**
- Fiscal-result validation must assert non-null AND numeric/length constraints:
  - ФП (fiscal sign): 10–20 hex digits
  - ФН: 16 digits
  - ФД: positive integer
- Add integration test: adapter returns stub metadata → validation throws `FiscalInvariantViolation`.
- Remove all synthetic-prefix constants from production source files (not just test files).

**Phase:** Phase A — adapter contract tests.

---

### P1.3: ФН resource exhaustion — unchecked ФН overflow before 13-month limit

**Why it's critical:** ФН stores up to ~250,000 fiscal documents. Exceeding capacity makes the ФН
unusable until replacement. Applications that silently ignore ФН capacity status will fail
catastrophically mid-shift.

**Warning signs:**
- No monitoring of `fiscalStatus.documentsRemaining` or `fiscalStatus.fnMemoryUsedPercent`.
- No UI warning below 5,000 documents remaining.
- No admin notification when ФН approaches end-of-life (≤30 days or ≤1,000 docs).

**Prevention strategy:**
- Read ФН resource counters on every shift-open and display them in the status bar.
- Emit `ShiftWarning.FN_CAPACITY_LOW` (≤5,000 docs or ≤30 days) as a non-blocking advisory.
- Block only when `fiscalStatus.canAcceptNewDocument == false`.
- Store `fnReplacedAt` and `fnRegistrationsCount` in local config to support ФН replacement flow.

**Phase:** Phase A + Phase C (status monitoring).

---

### P1.4: Честный ЗНАК — offline sale of mandatory-marked goods

**Why it's critical:** Since 2024, several product categories (tobacco, footwear, medicines, optics)
require online validation via Честный ЗНАК before each sale. An offline-capable POS that allows
the cashier to complete a marked-goods sale without validation will transmit invalid data to ОФД
and may face certificate revocation.

**Warning signs:**
- `MARK-02` (API validation) is gated only by a feature flag with no operational enforcement.
- `ScanDataMatrixUseCase` returns `ValidationResult.PENDING` and the sale continues.
- UI offers a "skip validation" button for marked goods.

**Prevention strategy:**
- When module is enabled: **every** DataMatrix scan for a category under разрешительный режим
  MUST call the validation API before the fiscal document is queued.
- If `ChaseznakApi.isAvailable() == false`: block the sale of all mandatory-marked goods;
  allow only non-marked goods to proceed.
- Never expose `PENDING → treat as SUCCESS` as a business logic path.
- Document explicitly in the out-of-scope section that offline-mode for marked goods is not
  supported.

**Phase:** Phase D (optional modules).

---

### P1.5: ЕГАИС УТМ stability — silent failures that don't surface to the cashier

**Why it's critical:** ЕГАИС requires each alcohol sale to be confirmed by УТМ within the
transaction. If УТМ is unreachable or returns an error, the sale must be blocked; if the
application only logs the failure and allows the fiscal receipt to print, the operator is
committing an ЕГАИС violation.

**Warning signs:**
- Alcohol sale path calls УТМ asynchronously (fire-and-forget) and proceeds regardless.
- `ALCO-01` integration returns `Unit` on the success path and `null` on failure, with no
  distinction in the calling use case.
- No retry logic for УТМ transient errors.

**Prevention strategy:**
- Alcohol sale flow must be fully synchronous with УТМ: validate → wait → confirm → fiscal receipt.
- On УТМ timeout (configurable, default 10s): block the sale, show "ЕГАИС недоступен".
- On УТМ error response: block the sale, show the exact error from УТМ.
- Add `UtmHealthCheck` to the MON-* status panel (MON-05).

**Phase:** Phase D.

---

### P1.6: License grace period counted from wrong baseline

**Why it's critical:** 54-ФЗ allows a 7-day offline grace period for fiscal operations after
a license expires. If the grace period is calculated from `lastSuccessfulCheck` instead of
`licenseExpiresAt`, the application will either block valid offline operations prematurely or
extend the grace period illegally.

**Warning signs:**
- `LicenseChecker` stores only `lastSuccessfulCheckTimestamp` with no `expiresAt` field.
- Grace period logic reads `now - lastCheck > 7 days` instead of `now > expiresAt + 7 days`.
- Backend does not return `expiresAt` in the license-check response.

**Prevention strategy:**
- `LicenseStatus` must contain both `expiresAt: Instant` and `lastSuccessfulCheck: Instant`.
- Grace period = `now.isAfter(expiresAt) && now.isBefore(expiresAt + 7 days)`.
- Backend API contract must include `expiresAt` in the license-check response.
- At startup and once per 24h: re-validate against backend. Cache result locally for offline.
- Grace period counter must be visible to the operator (days remaining) so they are not
  surprised by a sudden block.

**Phase:** Phase B (auth/licensing) — this is a security + compliance gap.

---

## P2 — High (causes operational outages or data corruption)

### P2.1: Fiscal adapter service binding — unchecked unbind/rebind cycle

**Why it's critical:** MSPOS-K and Нева 01Ф communicate via Android bound services. If the
service is killed by the system (low memory), rebinding without checking the previous session
state will lose in-flight operations, corrupt shift continuity, and produce ФН sequence gaps.

**Warning signs:**
- `MSPOSKFiscalCore.bind()` does not verify `isBound` before calling `bindService()`.
- No retry/backoff on `ServiceConnection.onServiceDisconnected()`.
- Unbind is called unconditionally without pending-operation drain.

**Prevention strategy:**
- Implement a service-lifecycle manager: bind → verify → execute → unbind only after drain.
- On `onServiceDisconnected`: wait with exponential backoff (1s, 2s, 4s) before rebind attempt.
- Track operation state machine: `IDLE → BINDING → BOUND → EXECUTING → UNBINDING`.
- Reject new operations during `BINDING` and `UNBINDING` states with `ServiceUnavailableException`.

**Phase:** Phase A.

---

### P2.2: Sync queue growth — unbounded `PENDING_SYNC` backlog

**Why it's critical:** When cloud sync fails persistently (no internet, backend down), the local
queue of unsynced fiscal documents grows indefinitely. Large queues cause memory pressure,
slow app restart, and — critically — make it impossible to generate accurate local reports
because the app cannot determine which checks are final.

**Warning signs:**
- `SyncManager` has no cap on `PENDING_SYNC` queue size.
- No periodic retry budget (e.g., max 3 retries per document before marking `SYNC_ERROR`).
- No UI indicator showing queue depth.

**Prevention strategy:**
- Cap queue depth at 500 documents. On overflow: block new sales with explicit message
  ("Синхронизация заблокирована: накопились неотправленные чеки").
- Implement retry budget: 3 attempts per document, then `SYNC_ERROR` with timestamp.
- Surface queue depth in MON-02 (cloud server status): show "N чеков ожидает синхронизации".
- Implement idempotency keys (`checkId`) so duplicate sends are safe.

**Phase:** Phase C + Phase E.

---

### P2.3: Shift state machine — open/close transitions not enforced atomically

**Why it's critical:** 54-ФЗ requires a strictly sequential shift lifecycle: open → N operations →
close. Gaps in the sequence (e.g., two consecutive `open shift` calls without close) create
orphan shift states, cause ФН to reject operations, and produce invalid fiscal reports.

**Warning signs:**
- `ShiftUseCase` does not validate the current shift state before executing `openShift()`.
- Shift state is read from local DB without verifying against ККТ/ФН status.
- `closeShift()` succeeds even when ККТ reports no open shift.

**Prevention strategy:**
- `ShiftState` enum: `CLOSED`, `OPEN`, `SUSPENDED`.
- State transitions enforced by `ShiftStateMachine`:
  - `CLOSED → OPEN` only.
  - `OPEN → CLOSED` only.
  - Any other sequence → `ShiftStateException`.
- Before each fiscal operation, verify ККТ-reported shift state matches local state.
- On MISMATCH: prefer ККТ as source of truth, sync local state to match.

**Phase:** Phase A + Phase C.

---

### P2.4: `deletedIds` not applied — phantom products in local catalog

**Why it's critical:** Backend sends `deletedIds` to prune removed products from the local catalog.
If not applied, the cashier will see and attempt to sell products that no longer exist, producing
a fiscal receipt with an invalid/nonexistent barcode that fails validation.

**Warning signs:**
- `SyncManager` receives `deletedIds` in the pull response but never calls `productDao.deleteById()`.
- Manual product deletion in backend does not disappear from the local tablet.
- Products marked deleted still appear in search results.

**Prevention strategy:**
- `SyncManager.pullProducts()` must apply `deletedIds` as the first step before inserting new data.
- Add unit test: `pullProducts(deletedIds=[p5]) + productDao has p5 → after sync, productDao p5 is null`.
- Add integration test: delete product on backend → sync → verify absent on Android.

**Phase:** Phase E (inventory/cloud completion).

---

### P2.5: ФН replacement — fiscal continuity lost when ФН is physically replaced

**Why it's critical:** Replacing a ФН requires a re-registration ceremony (registration number,
new INN, new ОФД contract). If the application does not track ФН replacement history and
continues using old ФН serial numbers, all subsequent fiscal documents will be rejected.

**Warning signs:**
- `FiscalConfig` stores only one `fnSerial` field.
- No lifecycle event for "FN replaced" that triggers a re-registration wizard.
- Old ФН documents and new ФН documents are mixed in the same local DB without a
  `fnSerial` discriminator.

**Prevention strategy:**
- `FiscalConfig` stores `fnHistory: List<FnRegistration>` where each entry has:
  `serial`, `registeredAt`, `reregistrationReason`, `isActive`.
- On `FiscalStatus.fnSerial` change: detect ФН replacement event, prompt admin for re-registration.
- All local fiscal documents reference the ФН serial they were created with.
- Reports query by `fnSerial` to avoid cross-contamination.

**Phase:** Phase A (with FnRegistration lifecycle) — explicitly a design gap identified in
full-tz-compliance-design.

---

### P2.6: Fiscal adapter — error mapper collapses all failures to generic `FiscalException`

**Why it's critical:** Recoverable failures (timeout, service-not-ready) and non-recoverable
failures (ФН overflow, invalid registration) must be distinguished. If both map to the same
exception type, the UI cannot show the right message and retry logic will loop on permanent
errors.

**Warning signs:**
- `FiscalErrorMapper` returns the same `FiscalException` for all vendor error codes.
- Error messages use strings like "Fiscal error occurred" with no error code.
- Unit tests assert only that `isFailure` is true, not the specific error type.

**Prevention strategy:**
- `FiscalError` sealed class with:
  - `Recoverable`: `ServiceNotReady`, `BindTimeout`, `TransportTimeout`, `RetryableStatus`
  - `NonRecoverable`: `FnOverflow`, `InvalidRegistration`, `UnsupportedOperation`,
    `TerminalFailure`
- Error mapping tests must cover all 7+ vendor error codes with explicit assertions on the
  resulting `FiscalError` subtype.
- `FiscalOperationOrchestrator` retries only on `Recoverable`, fails immediately on `NonRecoverable`.

**Phase:** Phase A.

---

## P3 — Medium (causes degraded UX or compliance risk)

### P3.1: FFD field policy — required tags silently omitted from fiscal documents

**Why it's critical:** 54-ФЗ requires specific tags per document type and FFD version. Missing
tags (e.g., 1005, 1034, 1191, 1026) cause ОФД rejection. Silent omission is worse than a
hard error because it passes local tests and fails only at the ОФД gateway.

**Warning signs:**
- `FiscalDocumentBuilder` builds documents with a variable-length `fields` map without
  schema validation.
- No test that asserts all required tags are present for every FFD version combination.
- `additionalInfo` is optional in the `FiscalCheck` constructor; callers may omit it.

**Prevention strategy:**
- Enforce required tags at the builder level with a schema: `requiredTags(ffdVersion, docType)`.
- Add a unit test that runs over all 9 FFD scenarios (from `ffd-evidence-matrix.md`) and
  asserts every required tag is present. This test must fail if any tag is removed.
- Treat missing required tag as a build error (throw in debug builds, fail-safe in production).

**Phase:** Phase A + Phase F (regression suite).

---

### P3.2: Root detection — false positives on legitimate devices cause support load

**Why it's critical:** Legitimate devices (LineageOS without root, emulators in CI, Samsung Knox
with custom firmware) trigger `RootRiskGuard` and block fiscal operations. This generates
support tickets and prevents valid cashiers from working. A false-positive block is worse than
a false-negative (which only reduces security).

**Warning signs:**
- `detectRwSystem()` uses an actual write-to-`/system` test on every startup.
- Detection is configured as `STRICT` (block on any single indicator).
- No UI explanation of why root was detected.

**Prevention strategy:**
- Require ≥2 independent indicators before blocking (reduce false-positive rate).
- Treat single-indicator detection as `WARNING` state: log, show advisory, allow fiscal ops.
- Block only on `≥2 indicators` or `Magisk package present` (high-confidence signal).
- Emulators used in CI must run with `ro.debuggable=0` to avoid `dangerous_props` trigger.
- Document known false-positive devices in the support manual.

**Phase:** Phase B.

---

### P3.3: Audit log — missing on critical operations, stored only locally

**Why it's critical:** In a dispute (chargeback, tax audit, regulatory inspection), the absence
of a tamper-evident audit log makes it impossible to prove what happened. Logs stored only in
the local app are easily deleted by a bad actor.

**Warning signs:**
- `AuditLogDao` exists but is not called from any use case.
- Only successful operations are logged; failures and blocked operations are not.
- Audit log is not included in the sync payload to the backend.

**Prevention strategy:**
- Audit log must record: `actor`, `deviceId`, `action`, `payloadHash`, `outcome`, `timestamp`.
- Record both successes and explicit blocks (failed auth, root-blocked, license-blocked).
- Include audit log entries in the sync queue with the same priority as fiscal documents.
- Backend must store audit log entries with device timestamp for tamper-evidence.

**Phase:** Phase B.

---

### P3.4: EncryptedSharedPreferences — migration failure leaves app in broken state

**Why it's critical:** On an upgrade from plain SharedPreferences to EncryptedSharedPreferences,
if the migration logic throws an exception (e.g., corrupted data, device in low-memory), the
app may fail to start or silently lose license/sync state.

**Warning signs:**
- `PrefsMigration.migrateLicenseData()` wraps all reads in a single try-catch that swallows
  the exception and leaves encrypted prefs empty.
- No migration test with malformed input data.
- No rollback path: if migration fails, the old data is not preserved.

**Prevention strategy:**
- Migration must be idempotent and have a fallback: on any exception, the app continues
  with the plain-prefs data (degraded security, better availability).
- Add `PrefsMigrationTest` with: valid data, empty data, corrupted data — all three must
  leave the app functional.
- Log migration outcome (success/failure) to audit log.
- Run migration as part of `LicenseChecker.init`, not at app startup, to isolate failure scope.

**Phase:** Phase B.

---

### P3.5: 24-hour shift age — warning treated as error or ignored entirely

**Why it's critical:** 54-ФЗ does not hard-block at exactly 24 hours, but ККТ/ФН behavior
becomes unpredictable past this point. Treating the warning as a hard block prevents legitimate
late-night operations; ignoring it allows dangerous accumulated state.

**Warning signs:**
- `shiftAgeHours > 24` triggers `Result.failure()` — blocks the sale.
- `shiftAgeHours > 24` has no UI indication and no warning snackbar.
- No `SHIFT_TOO_OLD` result type distinction.

**Prevention strategy:**
- Use a two-tier result: `FiscalRuntimeResult.Success` carries a `warnings: List<String>` field.
- On `shiftAgeHours > 24`: return success with warning "Смена открыта N часов. Закройте смену."
- UI shows a non-blocking snackbar. The sale proceeds.
- If `shiftAgeHours > 30` (extended overstay): escalate to hard block with mandatory admin
  acknowledgment.

**Phase:** Phase A (implementation was partially planned but not yet closed).

---

### P3.6: DataMatrix scan — malformed codes accepted and passed to fiscal builder

**Why it's critical:** A DataMatrix code with invalid GS-1 syntax or truncated identity string
will pass local format validation but fail at the Честный ЗНАК API, causing the fiscal
receipt to be printed with an invalid mark code that ОФД rejects.

**Warning signs:**
- `ScanDataMatrixUseCase` returns raw scan bytes without GS-1 parsing validation.
- No regex/structure validation on the parsed `gtin + serial` fields.
- Unit tests use well-formed DataMatrix fixtures only.

**Prevention strategy:**
- Implement GS-1 parsing in `DataMatrixParser`: validate application identifier sequence,
  extract GTIN (14 digits), serial (up to 20 alphanumeric), expiry/production codes.
- Reject and rescan if: GTIN length ≠ 14, serial missing or > 20 chars, unknown AI prefix.
- Add test with malformed DataMatrix fixtures (truncated, wrong AI, wrong check digit).
- честныйзнак API error "INVALID_CODE_FORMAT" must surface as a user-facing scan failure,
  not a generic sync error.

**Phase:** Phase D.

---

## P4 — Low (technical debt that compounds)

### P4.1: No FFD version detection at startup — assumes last-known version is current

**Why it's critical:** If the ФН was replaced or re-registered with a different FFD version,
the app will continue using the cached version from SharedPreferences, producing
incompatible fiscal documents.

**Prevention:** `FfdVersionResolver` must read the actual version from ККТ/ФН on every app
startup before any fiscal operation. Cache only for display; never for document construction.

**Phase:** Phase A.

---

### P4.2: MSPOS-K and Нева 01Ф adapters diverge in error handling behavior

**Why it's critical:** If the two adapters implement different recovery policies (e.g., MSPOS-K
retries 3 times, Нева retries 1), the app behavior is unpredictable across device types and
the fiscal operation contract is not portable.

**Prevention:** All fiscal adapters must implement `FiscalCore` with identical recovery rules
defined by `FiscalError.Recoverable` / `NonRecoverable`. Add adapter contract tests that
assert both adapters behave identically for each error code subclass.

**Phase:** Phase A.

---

### P4.3: Backend auth — Bearer token stored without rotation/revocation lifecycle

**Why it's critical:** A leaked or compromised token grants indefinite access to fiscal and
customer data. Without rotation, revocation, and expiry, the token is effectively permanent.

**Prevention:** Implement token lifecycle: issue time + TTL (1h), refresh token (7 days),
revocation endpoint. On app lock (wrong PIN 5×), revoke all tokens for that deviceId.

**Phase:** Phase B.

---

## Quality Gate Checklist

- [x] Pitfalls are specific to Android Kassovoye Prilozheniye / 54-ФЗ domain — not generic
      software advice (e.g., "write unit tests" is excluded; "FFD field policy enforced at
      builder level" is included).
- [x] Prevention strategies are actionable — each includes specific implementation signals
      (test names, class names, method signatures) that a developer can act on.
- [x] Phase mapping included for all P1 and P2 items — tied to the existing phase model
      (A–F) from `full-tz-compliance-design`.
- [x] No duplicates — each pitfall covers a distinct failure mode.
- [x] Severity is grounded in blast radius — P1 = fiscal data loss or regulatory violation;
      P2 = operational outage or local data corruption; P3 = degraded UX or compliance risk;
      P4 = compounding technical debt.
