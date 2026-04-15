# Step 7 v2 — Emulator Smoke Lane Design

## Context and Goal

Step 7 v1 закрыл deterministic CI gate для backend/Android unit/build и стабилизировал branch protection. Следующий системный пробел — отсутствие автоматического runtime instrumentation-сигнала через Android emulator lane. Цель Step 7 v2: ввести минимальный, воспроизводимый nightly smoke-проход `connectedDebugAndroidTest` с диагностическими артефактами, не ломая текущий required deterministic gate.

## Scope

### In Scope

- Новый отдельный workflow для emulator smoke lane.
- Триггеры:
  - `schedule` (nightly)
  - `workflow_dispatch` (ручной запуск)
- Один job: `android-emulator-smoke`.
- Запуск `connectedDebugAndroidTest` на CI-эмуляторе.
- Upload instrumentation-артефактов (reports/results/logs) при любом исходе.
- Политика наблюдения стабильности перед переводом в required check.

### Out of Scope

- Изменение текущего `ci-deterministic.yml` required-contract.
- Немедленный перевод emulator lane в required.
- Расширенный matrix по API levels/devices на первом шаге.
- Масштабный рефактор androidTest suite.

## Design Options Considered

### Option A (Approved): Separate emulator workflow (nightly + manual)

Отдельный workflow `ci-emulator-smoke.yml` с одним emulator job.

Плюсы:
- Изоляция от быстрого deterministic gate.
- Более чистая диагностика flaky instrumentation-ошибок.
- Контролируемый rollout без влияния на merge velocity.

Минусы:
- Дополнительный workflow в сопровождении.
- Потребуется явный policy для последующего required-перевода.

### Option B: Embed emulator job into deterministic workflow

Плюсы:
- Один workflow-файл.

Минусы:
- Higher coupling: долгий/нестабильный job смешивается с required deterministic path.
- Риск деградации developer feedback cycle.

### Option C: Emulator boot/build only (without tests)

Плюсы:
- Быстрый запуск.

Минусы:
- Низкая защитная ценность, не проверяется instrumentation runtime path.

## Approved Approach

Принят **Option A**: отдельный emulator smoke workflow с этапом наблюдения стабильности.

## Architecture

### Workflow file

- `/.github/workflows/ci-emulator-smoke.yml`

### Trigger model

- `schedule`: nightly запуск.
- `workflow_dispatch`: ручной запуск для диагностики.

### Job model

- Job ID/name: `android-emulator-smoke`
- Runner: `ubuntu-latest` (стандартный hosted Linux lane для Android emulator action).
- Environment setup:
  - checkout
  - JDK 17
  - Android SDK components/emulator provisioning
- Execution:
  - `./gradlew connectedDebugAndroidTest --no-daemon`
- Observability:
  - upload artifacts на success/failure:
    - `android/app/build/reports/androidTests/**`
    - `android/app/build/outputs/androidTest-results/**`
    - `android/app/build/outputs/logs/**` (если присутствуют)

### Isolation principle

- Emulator lane отделён от `ci-deterministic.yml`.
- На первом этапе check не включается в required branch protection.

## Stability Observation Policy

### Observation phase

- Длительность: 7 дней.
- Check `android-emulator-smoke` остаётся non-required.

### Promotion criteria to required

Перевод в required допустим, если одновременно выполнено:
1. Минимум 6 из 7 nightly прогонов — green.
2. Последние 3 прогона подряд — green.
3. При падениях есть диагностические артефакты, достаточные для root-cause анализа.

### If criteria not met

- Check остаётся non-required.
- Фиксируются причины нестабильности.
- После исправлений повторяется цикл наблюдения.

## Verification Strategy

1. Workflow корректно читается GitHub:
   - `gh workflow view .github/workflows/ci-emulator-smoke.yml --repo SyrexBlack/Vitbon_kassa_high`
2. Manual run succeeds at least once:
   - `workflow_dispatch` запуск завершается green.
3. Artifacts are available after run:
   - reports/results/logs доступны в run artifacts.
4. Nightly observation evidence collected for promotion decision.

## Acceptance Criteria

1. В репозитории есть `/.github/workflows/ci-emulator-smoke.yml`.
2. Workflow поддерживает nightly и manual triggers.
3. Job `android-emulator-smoke` выполняет `connectedDebugAndroidTest`.
4. Артефакты instrumentation lane публикуются при любом исходе.
5. Явно зафиксирована policy перевода lane в required после наблюдения.

## Risks and Mitigations

- Риск: flakiness emulator infrastructure.
  - Митигация: отдельный workflow + артефакты для root-cause + phased promotion.
- Риск: долгий runtime.
  - Митигация: smoke-first scope (минимальный набор тестов), без matrix на v1.
- Риск: ложное чувство покрытия.
  - Митигация: явная фиксация, что это smoke lane, не полный E2E matrix.

## Definition of Done

- Workflow создан и запущен вручную минимум один раз.
- Артефакты подтверждают наблюдаемость при падении/успехе.
- Стартовал nightly observation период.
- Решение о required-переводе принимается только по формальным критериям стабильности.
