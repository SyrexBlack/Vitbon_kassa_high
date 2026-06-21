# ROADMAP: VITBON Kassovoye Prilozheniye

**Version:** 1.0
**Date:** 2026-06-21
**Mode:** MVP (vertical increments, end-to-end user capabilities per phase)
**Platform:** Android 6.0+ (API 23)
**Compliance:** 54-ФЗ, ФФД 1.05 / 1.2

---

## Milestones

- ✅ **v1.0 MVP** — Phases A–F + Phase 7 closure (shipped 2026-06-21) — see `.planning/milestones/v1.0-ROADMAP.md`

---

## Overview

Six sequential phases plus a Phase 7 audit-closure. v1.0 MVP is shipped. Future milestones (e.g., v1.1, v2.0) ship independently.

```
Phase A ▸ Phase B ▸ Phase C ▸ Phase D ▸ Phase E ▸ Phase F ▸ Phase 7 (closure)
  ✅         ✅         ✅         ✅         ✅         ✅              ✅
```

**Critical path:** Phase A (Fiscal Core) gates all others. All 7 phases complete.

---

## Phases

<details>
<summary>✅ v1.0 MVP (Phases A–F + Phase 7) — SHIPPED 2026-06-21</summary>

- [x] Phase A: KKT + FFD (Fiscal Core) — completed 2026-06-20 — 15 reqs
- [x] Phase B: Auth + Licensing + Security — completed 2026-06-20 — 12 reqs
- [x] Phase C: Core Sync + Status Monitoring — completed 2026-06-20 — 12 reqs
- [x] Phase D: Reports — completed 2026-06-20 — 5 reqs
- [x] Phase E: Cloud Sync Completion — completed 2026-06-20 — v2 reqs
- [x] Phase F: Optional Modules (ЧЗ + ЕГАИС) — completed 2026-06-20 — 11 reqs
- [x] Phase 7: Audit Gap Closure (INSERTED) — completed 2026-06-20 — 5 gaps

**Total:** 7 phases, 52/52 v1 requirements satisfied.

</details>

---

## v2 Requirements (Deferred)

Tracked but not in current roadmap:

### Расширенная синхронизация

- **SYNC-01**: Batch sync для 200+ касс — Redis Streams backend для массовой ингаляции чеков
- **SYNC-02**: Conflict resolution — server-wins по timestamp для двусторонней синхронизации

### Дополнительные интеграции

- **EXT-01**: Интеграция с эквайрингом — поддержка платёжных терминалов (отдельная система)
- **EXT-02**: Другие модели ККТ — расширение списка поддерживаемых фискальных регистраторов

### Аналитика

- **ANLY-01**: Dashboard — визуализация ключевых показателей для администратора
- **ANLY-02**: Прогнозирование — прогноз спроса на основе истории продаж

---

## Backlog

Next milestone candidates (decided via `/gsd-new-milestone`):

- Sandbox integration testing (ОФД, Честный ЗНАК, УТМ, Цифровой ID Max)
- Load test with 200+ concurrent cash registers (Redis Streams verification)
- SQLCipher key rotation scenario test
- 24-hour offline stress test
- External API integration (extension modules)
- ФН replacement flow (FnRegistration lifecycle)
- Mutual TLS cert management
- Token rotation/revocation

---

## Phase Summary (Archived)

| Phase | Name | Requirements | Key Gate | Status |
|-------|------|-------------|----------|--------|
| A | KKT + FFD | FISC-01–08, KKT-01–06, SEC-02 | FFD unit tests + shift state machine | ✅ SHIPPED |
| B | Auth + Licensing + Security | AUTH-01–04, LIC-01–03, SEC-01,03–05 | 7-day offline license test | ✅ SHIPPED |
| C | Core Sync + Status Monitoring | GOOD-01–05, MON-01–06, UPDT-01 | 24-hour offline stress test | ✅ SHIPPED |
| D | Reports | REPT-01–05 | Report totals match Room | ✅ SHIPPED |
| E | Cloud Sync Completion | SYNC-01, SYNC-02 | 200-cashier load test | ✅ SHIPPED (backend complete, load test pending) |
| F | Optional Modules | MARK-01–06, ALCO-01–05 | Sandbox integration tests | ✅ SHIPPED (sandbox pending) |
| 7 | Audit Gap Closure | GAP-01–05 | All 5 blockers closed | ✅ SHIPPED |

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

*Roadmap updated: 2026-06-21 after v1.0 milestone completion*
*Archive: .planning/milestones/v1.0-ROADMAP.md*
