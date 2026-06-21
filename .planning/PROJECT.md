# Мобильная касса VITBON

## What This Is

Мобильное Android-приложение кассира для розничной торговли, обеспечивающее полный цикл фискальных операций (продажи, возвраты, чеки коррекции, смены, X/Z-отчёты), интеграцию с фискальными регистраторами MSPOS-K и Нева 01Ф, поддержку требований 54-ФЗ, облачный товароучёт, а также опциональные модули маркировки (Честный ЗНАК) и ЕГАИС.

**Соответствие:** 54-ФЗ, ФФД 1.05 и 1.2, Честный ЗНАК, ЕГАИС (с активацией модулей)

**Текущий статус:** v1.0 MVP SHIPPED 2026-06-21

## Core Value

Кассир может быстро и безопасно провести продажу/возврат товара с формированием фискального чека и синхронизацией данных — в любое время, в любое время, онлайн или офлайн.

## Business Context

- **Customer**: Розничные точки продаж (магазины, кафе, киоски), требующие мобильное кассовое решение
- **Revenue model**: Подписка на ПО (ежемесячная оплата тарифа), дополнительная плата за модули маркировки и ЕГАИС
- **Success metric**: Количество успешных фискальных операций без ошибок; uptime приложения
- **Strategy notes**: Облачная синхронизация как основное конкурентное преимущество

## Current State

**v1.0 MVP SHIPPED 2026-06-21.** Все 7 фаз (A-F + 7-closure) завершены, 52/52 v1 требований валидированы. Милестоун заархивирован в `.planning/milestones/v1.0-ROADMAP.md` и `.planning/milestones/v1.0-REQUIREMENTS.md`.

**Что работает (на уровне кода):**
- Fiscal Core с MSPOS-K + Нева 01Ф адаптерами (FISC-01..08, KKT-01..06)
- Открытие/закрытие смены, X/Z-отчёты, внесение/изъятие
- Offline режим для фискальных операций
- 3 роли (Администратор/Старший кассир/Кассир) + PIN/password
- SQLCipher + Android Keystore шифрование
- 7-дневный grace period для лицензии
- WorkManager синхронизация (push checks, pull products)
- 500-doc queue cap, server-wins conflict resolution
- 5 типов отчётов (агрегация из Room)
- 6 status indicators (internet, cloud, OFD, ЧЗ, ЕГАИС, license)
- Модули маркировки и ЕГАИС (runtime-gated)

**Что осталось из v1.0 (неблокирующий tech debt / внешние песочницы):**
- Sandbox integration testing (ОФД, Честный ЗНАК, УТМ, Цифровой ID Max)
- Load test 200+ касс (Redis Streams)
- 24-час offline stress test
- SQLCipher key rotation test
- ФН replacement flow
- Mutual TLS cert management
- Token rotation/revocation

## Next Milestone Goals

Кандидаты для следующего милестоуна (выбирается через `/gsd-new-milestone`):
- v1.1 Sandbox Readiness: пройти sandbox тесты для ОФД/ЧЗ/УТМ/CID Max
- v1.1 Production Hardening: 24-час stress test, load test 200+ касс, key rotation
- v1.1 Reliability: ФН replacement flow, mutual TLS, token lifecycle
- v1.2 UX: новые фичи в UI/UX
- v2.0: Расширенная синхронизация, эквайринг, аналитика

## Requirements

### Validated (v1.0 — shipped 2026-06-21)

- ✓ **FISC-01..08**: Фискальные операции (продажа/возврат/коррекция/смена/X-Z/внесение-изъятие) — v1.0
- ✓ **KKT-01..06**: Интеграция с ККТ (MSPOS-K, Нева 01Ф, ФФД 1.05/1.2) — v1.0
- ✓ **GOOD-01..05**: Товароучёт (каталог, синхронизация, приёмка, списание, инвентаризация) — v1.0
- ✓ **REPT-01..05**: Отчётность (продажи, товары, движение, возвраты, фискальные) — v1.0
- ✓ **MON-01..06**: Мониторинг статусов (интернет, облако, ОФД, ЧЗ, ЕГАИС, тариф) — v1.0
- ✓ **AUTH-01..04**: Пользователи и администрирование (3 роли, PIN, audit, root) — v1.0
- ✓ **LIC-01..03**: Лицензирование (проверка, блокировка, grace) — v1.0
- ✓ **SEC-01..05**: Безопасность (SQLCipher, offline, буфер, логи, TLS mTLS) — v1.0
- ✓ **UPDT-01**: Удалённое обновление — v1.0
- ✓ **MARK-01..06**: Честный ЗНАК (DataMatrix, валидация, выбытие) — v1.0
- ✓ **ALCO-01..05**: ЕГАИС (УТМ, вскрытие тары, возраст) — v1.0

**Total:** 52/52 v1 requirements validated.

### Active

(None — все v1 требования перенесены в Validated. Следующий милестоун определит новые Active требования через `/gsd-new-milestone` → requirements definition phase.)

### Out of Scope

- Мобильное приложение покупателя — только кассирское приложение
- Интеграция с платёжными терминалами (эквайринг) — отдельная система
- Desktop-версия приложения
- Windows/macOS/Linux платформы
- Полноценная CRM/ERP система — только товароучёт для кассы
- Офлайн-режим для модулей маркировки и ЕГАИС — требуют онлайн-валидации
- ФФД 1.0 — не поддерживается (только 1.05 и 1.2)
- Ручной ввод без ККТ — все операции через фискальный регистратор

## Context

**Техническое окружение:**
- Android 6.0 (API 23)+
- Kotlin + Android SDK
- Фискальные регистраторы: MSPOS-K, Нева 01Ф (SDK производителей)
- Локальная БД для офлайн-работы (Room/SQLite + SQLCipher)
- REST API для облачной синхронизации
- Backend: Spring Boot 3.2.2 + Kotlin

**Существующий код:**
- Проект Android: `android/` — основное кассовое приложение
- Бэкенд: `backend/` — облачный сервер товароучёта (Kotlin/Spring)
- Документация: `docs/` — e2e-тесты, матрица ФФД, руководства

**Метрики v1.0:**
- 7 фаз завершены
- 52/52 v1 требований реализованы
- ~74 дня разработки (2026-04-08 → 2026-06-21)
- 47,978 строк нового кода
- 3,320 Kotlin файлов

## Constraints

- **Платформа**: Android 6.0 (API 23) и выше
- **Оборудование**: Планшеты или смарт-терминалы со встроенным/подключаемым принтером чеков и сканером ШК
- **54-ФЗ**: Полное соответствие — фискализация, ОФД, ФФД 1.05/1.2
- **Офлайн**: Фискальные операции работают без интернета; синхронизация буферизируется
- **Модули**: Маркировка и ЕГАИС — активируемые отдельно, без переустановки приложения
- **Root**: Обнаружение root приводит к блокировке или предупреждению

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin как основной язык | Существующая кодовая база на Kotlin; хорошая экосистема для Android | ✓ Good |
| Room для локальной БД | Надёжность, офлайн-first, реактивность | ✓ Good |
| SQLCipher 4.5.4 + Android Keystore | Соответствие требованиям шифрования данных at-rest | ✓ Good |
| MSPOS-K и Нева 01Ф | Наиболее распространённые ККТ на рынке РФ | ✓ Good |
| ФФД 1.05 и 1.2 | Соответствие 54-ФЗ, поддержка разных типов ФН | ✓ Good |
| FFD post-fiscal lock (Phase 7) | Защита ФН от brick risk через запрет переключения версии | ✓ Good |
| Облачная синхронизация REST API | Стандартный подход, простота интеграции | ✓ Good |
| 500-doc queue cap (Phase 7) | Защита от memory pressure при persistent sync failures | ✓ Good |
| Server-wins conflict resolution | Облако авторитативно для каталога товаров | ✓ Good |
| 7-day grace period from expiresAt | Offline resilience для кассовых операций | ✓ Good |
| Опциональные модули | Маркировка и ЕГАИС не нужны всем клиентам, гибкость ценообразования | ✓ Good |
| Runtime gating via FeatureManager | Компилируются в APK, активируются лицензией | ✓ Good |
| AlcoholSalePolicyUseCase pre-fiscal check | Защита от продажи алкоголя при offline ЕГАИС | ✓ Good |

---

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-21 after v1.0 milestone completion*
