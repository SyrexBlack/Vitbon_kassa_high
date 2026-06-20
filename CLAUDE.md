# CLAUDE.md — VITBON Kassovoye Prilozheniye

## Project Context

**Project:** VITBON Мобильная касса (Android POS system)  
**Platform:** Android 6.0+ (API 23)  
**Core Value:** Кассир может быстро и безопасно провести продажу/возврат товара с формированием фискального чека и синхронизацией данных — в любое время, в любом месте, онлайн или офлайн.  
**Compliance:** 54-ФЗ, ФФД 1.05 / 1.2

## Architecture

```
UI Layer (Compose) → Domain (Use Cases) → Data (Room/Retrofit) → Fiscal Core (KKT Adapters)
                                              ↓
                                        Sync Layer (WorkManager)
```

**Key modules:**
- `android/app/src/main/java/com/vitbon/kkm/` — основной код
- `core/fiscal/` — FiscalCore, MSPOS-K, Нева 01Ф adapters
- `core/sync/` — SyncManager, WorkManager workers
- `core/features/` — FeatureManager (runtime flags)

## Roadmap

| Phase | Name | Status |
|-------|------|--------|
| A | KKT + FFD | ○ Not Started |
| B | Auth + Licensing + Security | ○ Not Started |
| C | Core Sync + Status Monitoring | ○ Not Started |
| D | Reports | ○ Not Started |
| E | Cloud Sync Completion | ○ Not Started |
| F | Optional Modules (ЧЗ + ЕГАИС) | ○ Not Started |

## Next Step

Run `/gsd-discuss-phase 1` to start Phase A (KKT + FFD Fiscal Core).

## Key Files

- `.planning/PROJECT.md` — полный контекст проекта
- `.planning/REQUIREMENTS.md` — требования с REQ-ID
- `.planning/ROADMAP.md` — фазы и критерии успеха
- `.planning/STATE.md` — текущее состояние
- `.planning/research/` — исследования (STACK, FEATURES, ARCHITECTURE, PITFALLS, SUMMARY)

## Working with GSD

- Use `/gsd-*` commands for all phase planning and execution
- Planning docs are in `.planning/` directory
- All phases must pass their gate criteria before moving to next phase
- Auto-advance enabled: phases auto-progress after approval
