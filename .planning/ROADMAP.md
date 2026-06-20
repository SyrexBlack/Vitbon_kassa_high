# ROADMAP: VITBON Kassovoye Prilozheniye

**Version:** 1.0  
**Date:** 2026-06-20  
**Mode:** MVP (vertical increments, end-to-end user capabilities per phase)  
**Platform:** Android 6.0+ (API 23)  
**Compliance:** 54-ФЗ, ФФД 1.05 / 1.2

---

## Overview

Six sequential phases. Each phase delivers a working, testable increment. Phases A–D form the v1.0 MVP. Phase E ships post-MVP for enterprise scale. Phase F ships optional modules independently.

```
Phase A ▸ Phase B ▸ Phase C ▸ Phase D ▸ Phase E ▸ Phase F
  KKT+FFD  Auth+Lic  Sync+Mon  Reports  Cloud+  Optional
```

**Critical path:** Phase A (Fiscal Core) gates all others. Phases B–D can run in parallel after A is complete (team permitting).

---

## Phase A — KKT + FFD (Fiscal Core)

**Goal:** Fiscal operations work end-to-end through a KKT adapter. The app can conduct sales, returns, corrections, and shift management offline.

### Requirements

| ID | Requirement |
|----|-------------|
| FISC-01 | Продажа товара — scan, search, weight, discounts, НДС selection, receipt |
| FISC-02 | Возврат товара — by receipt/QR, partial per-item return |
| FISC-03 | Чеки коррекции — приход/расход with error description |
| FISC-04 | Открытие/закрытие смены — via fiscal core, block ops when closed |
| FISC-05 | X-отчёт — intermediate without closing shift |
| FISC-06 | Z-отчёт — generated automatically at shift close |
| FISC-07 | Внесение наличных — cash in with amount and comment |
| FISC-08 | Изъятие наличных — cash out (инкассация) with amount and comment |
| KKT-01 | MSPOS-K support — print, statuses, shifts, reports |
| KKT-02 | Нева 01Ф support — via MSPOS-K service delegation |
| KKT-03 | ФФД version detection — at startup and after shift open |
| KKT-04 | FFD-aware document building — fields per version (1.05 / 1.2) |
| KKT-05 | ФФД lock — no UI toggle to switch version after first document |
| KKT-06 | ККТ error handling — recoverable vs. non-recoverable, retry on recoverable |
| SEC-02 | Offline fiscal ops — receipts through ФН independent of internet |

### Success Criteria

| # | Criterion (observable behavior) |
|---|----------------------------------|
| A-1 | Cashier opens app, selects cashier PIN, shift opens — Z-купон prints |
| A-2 | Cashier scans barcode → product found → added to cart → "Продажа" → fiscal receipt printed by MSPOS-K |
| A-3 | Cashier initiates return by scanning receipt QR → product selected → partial return → fiscal receipt printed |
| A-4 | Cashier requests X-отчёт → report prints without closing shift |
| A-5 | Cashier performs cash in/out → fiscal document printed with amount and comment |
| A-6 | App operates fully offline — all above operations succeed with no internet |
| A-7 | ККТ error (paper out) is displayed to cashier with retry option; recoverable errors do not lose data |
| A-8 | FFD version is detected and displayed on startup; app prevents any FFD version toggle |

### Gate

Unit tests + integration tests for all FFD 1.05 and 1.2 scenarios. Shift state machine verified atomic.

---

## Phase B — Auth + Licensing + Security

**Goal:** Users authenticate with PIN/password. Role-based access is enforced. License status is checked. Local data is encrypted at rest.

### Requirements

| ID | Requirement |
|----|-------------|
| AUTH-01 | Roles — Администратор (full access), Старший кассир (ops), Кассир (sales + returns) |
| AUTH-02 | Authentication — PIN or password |
| AUTH-03 | Audit log — actor, deviceId, action, outcome, timestamp; local + cloud sync |
| AUTH-04 | Root detection — block or warn (≥2 indicators for block) |
| LIC-01 | License check — at app launch and daily |
| LIC-02 | License block — all ops blocked except reports and settings |
| LIC-03 | Grace period — 7 days after expiresAt (not lastCheck) |
| SEC-01 | Local storage encryption — SQLCipher 4.5.4 + Room SupportFactory + Android Keystore |
| SEC-03 | Sync buffering — data queued when offline, sent on reconnect |
| SEC-04 | Operation logging — sales, errors, statuses with export capability |
| SEC-05 | TLS mutual authentication — for cloud connection |

### Success Criteria

| # | Criterion (observable behavior) |
|---|----------------------------------|
| B-1 | Cashier enters 4-digit PIN → authenticated with correct role → restricted operations show access denied |
| B-2 | Unauthenticated user cannot access any screen beyond license notice |
| B-3 | Admin creates new cashier with role Кассир → cashier logs in with new PIN → only permitted operations visible |
| B-4 | License expired → app shows blocking screen → reports and settings accessible → all sales/returns blocked |
| B-5 | Device is rooted → app shows warning → user can acknowledge but cannot proceed past warning |
| B-6 | Sales and returns continue for 7 days after license expires with no network connection |
| B-7 | After 8th day without network → operations blocked even if grace period technically passes |
| B-8 | Audit log shows all cashiers' operations with timestamps; export to file works |

### Gate

Full offline license expiry flow tested for 7-day grace period. SQLCipher key rotation scenario tested.

---

## Phase C — Core Sync + Status Monitoring

**Goal:** Product catalog syncs from cloud. Sales data syncs to cloud. Status bar shows connectivity state for all subsystems. Remote updates supported.

### Requirements

| ID | Requirement |
|----|-------------|
| GOOD-01 | Local product catalog — name, article, barcode, price, stock, НДС rate, category; offline sales |
| GOOD-02 | Two-way sync REST API — push sales, pull product/price/stock changes |
| GOOD-03 | Goods acceptance — document created on POS, sent to cloud |
| GOOD-04 | Goods write-off — spoilage, shortage document |
| GOOD-05 | Inventory — stock count vs. expected, discrepancy report |
| MON-01 | Internet status — indicator on/off, network ops blocked when offline |
| MON-02 | Cloud server status — available/unavailable, last sync time, retry button |
| MON-03 | ОФД status — connection, queue depth, pending check count |
| MON-04 | Честный ЗНАК module status — installed/available (only when module active) |
| MON-05 | УТМ ЕГАИС status — installed/available (only when module active) |
| MON-06 | Tariff payment status — active/expired/grace; grace period countdown |
| UPDT-01 | Remote app update — Firebase App Distribution or equivalent |

### Success Criteria

| # | Criterion (observable behavior) |
|---|----------------------------------|
| C-1 | Product added in cloud → appears on POS within 30 seconds (network available) |
| C-2 | Price changed in cloud → POS shows new price on next sale without app restart |
| C-3 | Sale completed offline → queued in SyncUpWorker → uploaded within 30 sec of network restore |
| C-4 | Sync queue reaches 500 docs → warning shown → oldest items retried, excess blocked |
| C-5 | Status bar shows all six indicators (internet, cloud, ОФД, ЧЗ, ЕГАИС, license) with correct real-time state |
| C-6 | Admin opens inventory → counts physical stock → enters counts → discrepancy report generated → sent to cloud |
| C-7 | Admin taps "Повторить синхронизацию" → manual sync triggered → timestamp updated |
| C-8 | Remote update available → admin notified → app updated without reinstall |

### Gate

24-hour offline stress test. 500-document queue cap verified. All six status indicators tested with each failure mode.

---

## Phase D — Reports

**Goal:** All reporting views are functional and accurate. Data aggregates from local Room (no dependency on cloud for reports).

### Requirements

| ID | Requirement |
|----|-------------|
| REPT-01 | Sales reports — shift/day/week/month/custom period; revenue (cash/non-cash), check count, average receipt |
| REPT-02 | Product/category breakdown — units sold, revenue per product and category |
| REPT-03 | Goods movement report — receipts, sales, write-offs, returns, inventory, opening/closing stock |
| REPT-04 | Return report — count, sum, list of return receipts for period |
| REPT-05 | Fiscal reports — Z-отчёт (from fiscal core), X-отчёт (from fiscal core) |

### Success Criteria

| # | Criterion (observable behavior) |
|---|----------------------------------|
| D-1 | Cashier opens sales report for current shift → sees correct revenue, check count, average receipt matching Room aggregation |
| D-2 | Admin opens report for last 30 days → all six report types generate with data |
| D-3 | Product breakdown shows each item with units and revenue matching individual receipts |
| D-4 | Return report lists every return receipt with amount and timestamp |
| D-5 | Goods movement report shows opening stock → + receipts → − sales → − write-offs → closing stock for each product |
| D-6 | X-отчёт and Z-отчёт figures match the fiscal core's accumulated totals exactly |
| D-7 | Reports generate correctly from offline Room data with no cloud dependency |

### Gate

Report totals verified to match Room aggregation (sum of LocalCheck records). X/Z report figures match fiscal core accumulated totals.

---

## Phase E — Cloud Sync Completion

**Goal:** Enterprise-grade two-way sync at scale. Backend supports 200+ concurrent cash registers. Conflict resolution ensures data consistency.

### Requirements

| ID | Requirement |
|----|-------------|
| GOOD-02 | Full two-way sync with conflict resolution — server-wins by timestamp |
| — | Batch sync for 200+ cash registers — Redis Streams backend for high-volume check ingestion |
| UPDT-01 | Remote update — enterprise deployment via Firebase App Distribution |

> Note: SYNC-01 (batch sync via Redis Streams) and SYNC-02 (conflict resolution) from v2 requirements ship in Phase E.

### Success Criteria

| # | Criterion (observable behavior) |
|---|----------------------------------|
| E-1 | 200 concurrent cash registers sync checks simultaneously → all uploaded within 60 seconds |
| E-2 | Conflict (same receipt edited on cloud and POS) → server version wins → POS updated on next pull |
| E-3 | deletedIds from cloud are applied before inserts during sync down |
| E-4 | Batch product catalog update (1000+ items) → all cash registers receive within 5 minutes |

### Gate

Load test with 200 concurrent cash registers. Conflict resolution scenario tested. deletedIds ordering verified.

---

## Phase F — Optional Modules (Честный ЗНАК + ЕГАИС)

**Goal:** Marked goods and alcohol sales are fully validated and compliant. Modules are compiled-in, runtime-gated. Cannot sell marked goods offline.

### Requirements

| ID | Requirement |
|----|-------------|
| MARK-01 | DataMatrix scanning — CameraX + ML Kit, validation via Честный ЗНАК API |
| MARK-02 | Code validation — in circulation, not sold, not expired; online-only |
| MARK-03 | Marked goods sale — item-by-item or group write-off in receipt |
| MARK-04 | Validation error handling — sale blocked until resolved |
| MARK-05 | Marked goods acceptance — scan codes on receipt |
| MARK-06 | Marked goods return — re-enter code into circulation |
| ALCO-01 | УТМ integration — local УТМ address config, alcohol sale fixation |
| ALCO-02 | Keg tapping — act of opening for keg sales |
| ALCO-03 | Age verification — scan QR from Digital ID Max, API verification |
| ALCO-04 | Sale block — alcohol sale blocked if age not confirmed |
| ALCO-05 | Logging — date, result, verification ID |

### Success Criteria

| # | Criterion (observable behavior) |
|---|----------------------------------|
| F-1 | Cashier scans DataMatrix → Честный ЗНАК API called → product info displayed or error shown |
| F-2 | Marked goods sold online → correct codes written off in Честный ЗНАК system |
| F-3 | Marked goods sold offline → sale blocked with message "Требуется проверка кода" |
| F-4 | Alcohol scanned → УТМ called → sale recorded in ЕГАИС → fiscal receipt printed |
| F-5 | Keg of beer sold → tapping act generated and sent to УТМ |
| F-6 | Customer presents Digital ID Max QR → scanned → API called → age confirmed/blocked |
| F-7 | Age not confirmed → alcohol sale blocked with message |
| F-8 | Module disabled in settings → all marking and alcohol features invisible in UI |

### Gate

Integration tests against sandbox ОФД + Честный ЗНАК test environment. УТМ sandbox tested. Age verification API tested with mock responses.

---

## Phase Summary

| Phase | Name | Requirements | Key Gate |
|-------|------|-------------|----------|
| A | KKT + FFD | FISC-01–08, KKT-01–06, SEC-02 | FFD unit tests + shift state machine |
| B | Auth + Licensing + Security | AUTH-01–04, LIC-01–03, SEC-01,03–05 | 7-day offline license test |
| C | Core Sync + Status Monitoring | GOOD-01–05, MON-01–06, UPDT-01 | 24-hour offline stress test |
| D | Reports | REPT-01–05 | Report totals match Room |
| E | Cloud Sync Completion | SYNC-01, SYNC-02 | 200-cashier load test |
| F | Optional Modules | MARK-01–06, ALCO-01–05 | Sandbox integration tests |

---

## Dependencies

```
FiscalCore ────────── Phase A ──────► Phases B, C, D, E (all depend)
Room DB ───────────── Phase A ──────► Phases A, B, C, D (data layer)
WorkManager ──────── Phase A ──────► Phase C (sync scheduler)
FeatureManager ───── Phase B ──────► Phase F (runtime gate)
MSPOS-K SDK ──────── Phase A ──────► All (hardware adapter)
```

---

*Roadmap created: 2026-06-20*
*Based on: REQUIREMENTS.md v1, research/SUMMARY.md, config.json (granularity: standard)*
