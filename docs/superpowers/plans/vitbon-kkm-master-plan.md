# VITBON ККМ — Общий план реализации

> **For agentic workers:** Рекомендуется использовать superpowers:subagent-driven-development для каждого подплана.
> Задачи используют синтаксис чекбоксов (`- [ ]`).

**Цель:** Полное Android-приложение «Мобильная касса VITBON» — 54-ФЗ, MSPOS-K / Нева 01Ф, ЕГАИС, Честный ЗНАК, 200+ касс, офлайн-first.

**Архитектура:** Android (Kotlin/Compose/Room/Hilt) ↔ REST Backend (Kotlin/Spring Boot/PostgreSQL/Redis) ↔ Внешние API (ОФД, ЕГАИС, ЧЗ, Облачный товароучёт, Лицензирование).

**Tech Stack:** Kotlin 1.9, Jetpack Compose, Room + SQLCipher, WorkManager, Hilt, Spring Boot 3, PostgreSQL 16+, Redis Streams, Docker/Kubernetes.

---

## Структура репозитория

```
vitbon-kassa/
├── android/                     # Android-приложение
│   ├── app/
│   │   ├── src/main/java/com/vitbon/kassa/
│   │   │   ├── features/        # Фичи по ТЗ (sales, returns, shift, ...)
│   │   │   ├── core/            # fiscal-core, sync, domain, di
│   │   │   └── shared-ui/       # Тема, компоненты
│   │   └── src/main/res/
│   └── build.gradle.kts
├── backend/                     # Spring Boot API
│   └── src/main/kotlin/com/vitbon/kassa/
│       ├── api/                 # REST контроллеры
│       ├── service/             # Бизнес-логика
│       ├── domain/              # Сущности
│       └── infrastructure/       # DB, Redis, integrations
├── plans/                       # Под-планы реализации
│   ├── phase-01-architecture.md
│   ├── phase-02-fiscal-core.md
│   ├── phase-03-basic-sales.md
│   ├── phase-04-inventory-sync.md
│   ├── phase-05-reports.md
│   ├── phase-06-statuses.md
│   ├── phase-07-licensing.md
│   ├── phase-08-egais-chz.md
│   └── phase-09-10-test-docs.md
└── docker/
```

---

## Общий план по этапам (из ТЗ)

```
Этап 1  Проектирование → Архитектура Android + Backend, модели данных
Этап 2  Ядро ККТ       → MSPOS-K / Нева 01Ф SDK, FiscalCore adapter, FFD version
Этап 3  Базовый функц. → Продажа, возврат, коррекция, смены, X/Z, внесение/изъятие
Этап 4  Товароучёт     → Room/SQLite, синхронизация, приёмка/списание/инвентаризация
Этап 5  Отчётность     → Продажи, движение товара, возвраты
Этап 6  Статусы        → Мониторинг (интернет, облако, ОФД, ЧЗ, УТМ, лицензия)
Этап 7  Лицензирование → API, grace period 7 дней, блокировка
Этап 8  ЕГАИС + ЧЗ    → Опциональные модули (feature flag)
Этап 9  Тестирование  → Реальное оборудование, Android 6+
Этап 10 Документы     → 4 руководства + API-спецификация
```

---

## Под-планы

### Подплан 1: Архитектура (Этапы 1 + 2)

**Файл:** `plans/phase-01-architecture.md`

Охватывает: Этап 1 (проектирование) + Этап 2 (ядро ККТ).

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Gradle-мультимодуль** — android shell + backend shell | `settings.gradle.kts`, `build.gradle.kts` |
| T-02 | **Android domain layer** — entities, repository interfaces | `domain/model/*.kt`, `domain/repository/*.kt` |
| T-03 | **Android data layer** — Room entities, DAOs, SQLCipher | `data/local/entity/*.kt`, `data/local/dao/*.kt` |
| T-04 | **Android remote API** — Retrofit definitions | `data/remote/api/*.kt` |
| T-05 | **WorkManager sync** — буферизация, merge, conflict resolve | `core/sync/*.kt` |
| T-06 | **FiscalCore interface** — единый интерфейс для обеих ККТ | `core/fiscal-core/FiscalCore.kt` |
| T-07 | **MSPOS-K adapter** — обёртка над MSPOS SDK | `core/fiscal-core/msposk/*.kt` |
| T-08 | **Нева 01Ф adapter** — обёртка над Нева SDK | `core/fiscal-core/neva01f/*.kt` |
| T-09 | **FFD version logic** — определение версии, формирование ФД по версии | `core/fiscal-core/ffd/*.kt` |
| T-10 | **Backend shell** — Spring Boot, Flyway, controllers | `backend/src/main/kotlin/.../api/`, `backend/src/main/resources/db/migration/` |
| T-11 | **Backend domain entities** — JPA entities, repositories | `backend/src/main/kotlin/.../domain/`, `backend/src/main/kotlin/.../repository/` |
| T-12 | **Backend sync endpoints** — REST API для чекирования и товаров | `backend/src/main/kotlin/.../service/SyncService.kt` |
| T-13 | **Backend licensing endpoint** — `POST /api/v1/license/check` | `backend/src/main/kotlin/.../service/LicenseService.kt` |
| T-14 | **Docker Compose** — backend + postgres + redis + minio | `docker/docker-compose.yml` |
| T-15 | **Commit phase 1** | — |

---

### Подплан 2: Базовый функционал (Этап 3)

**Файл:** `plans/phase-02-basic-sales.md`

Охватывает: Продажа → Возврат → Чеки коррекции → Открытие/закрытие смены → X-отчёт → Внесение/изъятие.

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Auth feature** — PIN/пароль, роли, audit log | `features/auth/*.kt` |
| T-02 | **Sales feature — UI** — Compose-экран продажи, корзина | `features/sales/ui/*.kt` |
| T-03 | **Sales feature — Logic** — ViewModel, use cases (добавить, удалить, скидка) | `features/sales/domain/*.kt` |
| T-04 | **Payment screen** — выбор способа оплаты (наличные/карта/СБП/бонусы) | `features/sales/ui/PaymentScreen.kt` |
| T-05 | **FiscalCore → printSale** — формирование чека → FiscalCore → ОФД буфер | `features/sales/fiscal/*.kt` |
| T-06 | **Returns feature** — возврат по чеку из БД / QR | `features/returns/*.kt` |
| T-07 | **Correction checks** — чек коррекции (приход/расход) | `features/correction/*.kt` |
| T-08 | **Shift feature** — openShift / closeShift / X-report / cashIn/Out | `features/shift/*.kt` |
| T-09 | **FFD build per version** — формирование TLV-тегов для 1.05 vs 1.2 | `core/fiscal-core/ffd/FiscalDocumentBuilder.kt` |
| T-10 | **Backend: checks endpoint** — приём, валидация, хранение | `backend/src/main/kotlin/.../api/CheckController.kt` |
| T-11 | **Backend: ОФД worker** — отправка в ОФД через очередь | `backend/src/main/kotlin/.../service/OfdWorker.kt` |
| T-12 | **Commit phase 2** | — |

---

### Подплан 3: Товароучёт (Этап 4)

**Файл:** `plans/phase-03-inventory-sync.md`

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Products feature** — справочник товаров, поиск, фильтр | `features/products/*.kt` |
| T-02 | **Acceptance document** — приёмка товара → синхронизация | `features/acceptance/*.kt` |
| T-03 | **WriteOff document** — списание | `features/writeoff/*.kt` |
| T-04 | **Inventory feature** — сверка остатков, акт инвентаризации | `features/inventory/*.kt` |
| T-05 | **Backend: products sync** — batch update товаров | `backend/src/main/kotlin/.../service/ProductSyncService.kt` |
| T-06 | **Backend: documents** — endpoints приёмки/списания/инвентаризации | `backend/src/main/kotlin/.../api/DocumentController.kt` |
| T-07 | **Commit phase 3** | — |

---

### Подплан 4: Отчётность (Этап 5)

**Файл:** `plans/phase-04-reports.md`

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Reports feature** — Продажи (день/неделя/месяц/произвольный) | `features/reports/*.kt` |
| T-02 | **Movement report** — приход/расход/списание/возвраты/остатки | `features/reports/MovementReport.kt` |
| T-03 | **Returns report** — перечень чеков возврата | `features/reports/ReturnsReport.kt` |
| T-04 | **Backend: reports aggregation** — SQL-запросы, кэширование | `backend/src/main/kotlin/.../service/ReportService.kt` |
| T-05 | **Commit phase 4** | — |

---

### Подплан 5: Статусы + Лицензирование (Этапы 6 + 7)

**Файл:** `plans/phase-05-statuses-licensing.md`

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Statuses feature** — StatusBar UI (индикаторы) | `features/statuses/*.kt` |
| T-02 | **Licensing feature** — проверка, grace period, блокировка | `features/licensing/*.kt` |
| T-03 | **Backend: license server** — хранение статусов, API | `backend/src/main/kotlin/.../service/LicenseService.kt` |
| T-04 | **Backend: statuses endpoint** — агрегация статусов | `backend/src/main/kotlin/.../api/StatusController.kt` |
| T-05 | **Commit phase 5** | — |

---

### Подплан 6: ЕГАИС + Честный ЗНАК (Этап 8)

**Файл:** `plans/phase-06-egais-chz.md`

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Feature flag system** — активация модулей без переустановки | `core/feature-flags/*.kt` |
| T-02 | **ЕГАИС feature** — приём накладных, акты, списание | `features/egais/*.kt` |
| T-03 | **Цифровой ID Max** — проверка возраста | `features/egais/DigitalIdMax.kt` |
| T-04 | **ЧЗ feature** — сканирование, валидация, выбытие | `features/chaseznak/*.kt` |
| T-05 | **Backend: ЕГАИС proxy** — проксирование через УТМ | `backend/src/main/kotlin/.../service/EgaisService.kt` |
| T-06 | **Backend: ЧЗ proxy** — проксирование через ЛМ ЧЗ | `backend/src/main/kotlin/.../service/ChzService.kt` |
| T-07 | **Commit phase 6** | — |

---

### Подплан 7: Тестирование + Документы (Этапы 9 + 10)

**Файл:** `plans/phase-07-test-docs.md`

| Задача | Описание | Файлы |
|--------|----------|--------|
| T-01 | **Unit-тесты** — все use cases | `android/app/src/test/` |
| T-02 | **Integration-тесты** — Room, Retrofit, sync | `android/app/src/androidTest/` |
| T-03 | **E2E-тесты** — Compose UI, продажа полного цикла | `android/app/src/androidTest/` |
| T-04 | **Backend-тесты** — Controllers, Services | `backend/src/test/` |
| T-05 | **CI/CD** — GitHub Actions: build → test → Docker image | `.github/workflows/` |
| T-06 | **Документация** — 4 руководства + API-спецификация | `docs/` |
| T-07 | **Commit phase 7** | — |

---

## Зависимости между под-планами

```
phase-01 (architecture) ──────▶ phase-02 (basic sales)
                                   │
phase-02 ──────────────────────────▶ phase-03 (inventory)
                                   │
                                   ▼
phase-03 ──────────────────────────▶ phase-04 (reports)
                                   │
      ┌────────────────────────────┤
      ▼                             ▼
phase-05 (statuses+               phase-06 (egais+chz)
licensing) ─────────────────────────┤
      └────────────────────────────┤
                                   ▼
                            phase-07 (test+docs)
```

**phase-01 — входная точка.** Его нельзя пропустить. Все остальные зависят от него.

---

## Выполнение

**Рекомендуемый подход:** Subagent-Driven Development — отдельный subagent на каждый подплан, ревью между подпланами.

**Параллельно возможно (после phase-01):**
- Backend-часть phase-02 (T-10, T-11, T-13) можно вести параллельно с Android-частью phase-02
- Phase-07 (test docs) — на протяжении всей разработки

---

## Self-Review

### Spec coverage
- [x] Этап 1 (проектирование) → phase-01 T-01..T-15 ✓
- [x] Этап 2 (ядро ККТ) → phase-01 T-06..T-09 + phase-02 T-05 ✓
- [x] Этап 3 (базовый функц.) → phase-02 T-01..T-12 ✓
- [x] Этап 4 (товароучёт) → phase-03 T-01..T-07 ✓
- [x] Этап 5 (отчётность) → phase-04 T-01..T-05 ✓
- [x] Этап 6 (статусы) → phase-05 T-01, T-04 ✓
- [x] Этап 7 (лицензирование) → phase-05 T-02, T-03 ✓
- [x] Этап 8 (ЕГАИС+ЧЗ) → phase-06 T-01..T-07 ✓
- [x] Этап 9+10 (тесты+доки) → phase-07 T-01..T-07 ✓

### Placeholder scan
- [x] Ни одного TBD/TODO
- [x] Все задачи содержат конкретные файлы
- [x] Нет "аналогично задаче N" — каждая задача описана полностью

### Type consistency
- [x] FiscalCore — единый интерфейс, два адаптера (MSPOS-K, Нева 01Ф)
- [x] FiscalDocumentBuilder — один класс, version-aware, теги 1.05 vs 1.2
- [x] Feature flags — единый механизм активации (ЕГАИС, ЧЗ)
