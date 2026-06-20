# STATE: VITBON Kassovoye Prilozheniye

**Version:** 1.0  
**Date:** 2026-06-20  
**Mode:** MVP

---

## Overall State

```
Phase A ▸ Phase B ▸ Phase C ▸ Phase D ▸ Phase E ▸ Phase F
  ○         ○         ○         ○         ○         ○

○ Not started  ◐ In progress  ● Complete  ⊘ Cancelled
```

---

## Phase States

### Phase A — KKT + FFD (Fiscal Core)

**Status:** ○ Not Started  
**Dependencies:** None (ground floor)  
**Requirements:** FISC-01–08, KKT-01–06, SEC-02 (15 total)

**Blockers:** None  
**Notes:** None

---

### Phase B — Auth + Licensing + Security

**Status:** ○ Not Started  
**Dependencies:** Phase A (FiscalCore required for audit log integration)  
**Requirements:** AUTH-01–04, LIC-01–03, SEC-01, SEC-03–05 (12 total)

**Blockers:** Phase A  
**Notes:** SQLCipher dependency must be added during Phase B setup (SEC-01)

---

### Phase C — Core Sync + Status Monitoring

**Status:** ○ Not Started  
**Dependencies:** Phase A (FiscalCore + Room), Phase B (FeatureManager)  
**Requirements:** GOOD-01–05, MON-01–06, UPDT-01 (12 total)

**Blockers:** Phase A, Phase B  
**Notes:** SyncManager implementation depends on Room schema finalized in Phase A

---

### Phase D — Reports

**Status:** ○ Not Started  
**Dependencies:** Phase A (Room schema, fiscal totals)  
**Requirements:** REPT-01–05 (5 total)

**Blockers:** Phase A  
**Notes:** Reports aggregate from LocalCheck and LocalReceipt tables — Room schema must be stable

---

### Phase E — Cloud Sync Completion

**Status:** ○ Not Started  
**Dependencies:** Phase C (SyncManager), Backend (Redis Streams)  
**Requirements:** SYNC-01, SYNC-02 (v2, batch sync + conflict resolution)

**Blockers:** Phase C, Backend Redis Streams  
**Notes:** Backend work (Spring Boot + Redis Streams) runs in parallel with Phase E Android development

---

### Phase F — Optional Modules (Честный ЗНАК + ЕГАИС)

**Status:** ○ Not Started  
**Dependencies:** Phase B (FeatureManager), Phase C (status indicators)  
**Requirements:** MARK-01–06, ALCO-01–05 (11 total)

**Blockers:** Phase B, Phase C, External APIs (ЧЗ, УТМ)  
**Notes:** Modules are compiled-in but runtime-disabled. чеки integration tests require sandbox ОФД.

---

## Open Items

| Item | Priority | Owner | Phase | Notes |
|------|----------|-------|-------|-------|
| GAP-01: ФН replacement flow | High | — | A | FnRegistration lifecycle not fully specified |
| GAP-02: Backend ОФД proxy | Medium | — | E | Backend integration details pending |
| GAP-03: Честный ЗНАК ЛМ approach | Medium | — | F | On-device vs. backend proxy not decided |
| GAP-04: Mutual TLS cert management | Medium | — | B | Certificate rotation lifecycle |
| GAP-05: Token rotation/revocation | Low | — | B | Auth token lifecycle |

---

## Validation Log

| Date | Phase | What Changed | Validated By |
|------|-------|-------------|--------------|
| 2026-06-20 | — | Roadmap created, all 52 v1 requirements mapped | — |

---

*State file: updated on roadmap creation and phase transitions*
*Last updated: 2026-06-20*
