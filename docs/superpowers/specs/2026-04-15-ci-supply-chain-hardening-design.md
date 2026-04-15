# CI Supply-Chain Hardening Design

## Context and Goal

Детерминированный CI gate уже стабилизирован и защищён required checks. Следующий системный риск — supply-chain drift по GitHub Actions (движение tag-версий и потенциальный компромисс tag reference). Цель: усилить воспроизводимость и контроль обновлений Actions через immutable pinning и управляемый update-поток.

## Scope

### In Scope

- Переход `uses:` в `/.github/workflows/ci-deterministic.yml` с version tags (`@v...`) на commit SHA pins (`@<40-hex>`).
- Добавление Dependabot-конфига для `github-actions` обновлений.
- Сохранение текущего deterministic-поведения workflow и стабильных required check names.
- Фиксация внешнего evidence в существующий Step 7 spec.

### Out of Scope

- Расширение набора CI jobs.
- Изменение branch protection policy.
- Внедрение дополнительных security scanners (CodeQL, osv-scanner и т.д.) в рамках этого шага.

## Design Options Considered

### Option A (Approved): SHA pinning + Dependabot for github-actions

- В workflow — только immutable SHA.
- Dependabot регулярно предлагает обновления action-версий через PR.
- Обновление SHA выполняется осознанно через review+CI.

Плюсы:
- Максимальная воспроизводимость и предсказуемость исполнения.
- Снижение риска supply-chain drift по action tags.
- Контролируемый update cadence.

Минусы:
- Нужен процесс сопровождения pin-updates.
- SHA менее читабельны без комментариев/PR контекста.

### Option B: SHA pinning without Dependabot

Плюсы:
- Минимальная внешняя автоматика.

Минусы:
- Высокий риск устаревания action-пинов.
- Обновления зависят от ручной дисциплины.

### Option C: Version tags + Dependabot only

Плюсы:
- Простейшая эксплуатация.

Минусы:
- Меньшая жёсткость модели доверия к supply-chain.

## Approved Approach

Принят **Option A**: immutable SHA pinning в deterministic workflow + Dependabot для `github-actions`.

## Architecture

### Workflow hardening

Файл:
- `/.github/workflows/ci-deterministic.yml`

Изменение:
- Все `uses:` переходят с `@v...` на `@<commit-sha>`.
- Job names и команды исполнения не меняются.

### Dependency update lane

Файл:
- `/.github/dependabot.yml`

Конфигурация:
- `package-ecosystem: github-actions`
- `directory: "/"`
- `schedule: weekly`

Назначение:
- Регулярно сигнализировать о новых версиях actions через PR.
- Дальнейший процесс: review → обновление pin SHA → required checks → merge.

## Verification Strategy

1. Workflow schema/readability:
   - `gh workflow view .github/workflows/ci-deterministic.yml --repo SyrexBlack/Vitbon_kassa_high`
2. PR validation:
   - required checks green:
     - `backend-tests`
     - `android-unit-tests`
     - `android-assemble-debug`
3. Post-merge validation:
   - deterministic gate на `master` остаётся green.

## Acceptance Criteria

1. В `ci-deterministic.yml` для внешних actions используются SHA pins (`@<40-hex>`), а не version tags.
2. В репозитории существует `.github/dependabot.yml` с weekly updates для `github-actions`.
3. PR с hardening проходит все required checks.
4. После merge в `master` run `CI Deterministic Gate` успешен.
5. Evidence добавлен в `docs/superpowers/specs/2026-04-14-step7-ci-deterministic-design.md`.

## Risks and Mitigations

- Риск: неверный SHA (typo) ломает workflow.
  - Митигация: PR-required checks + проверка через `gh workflow view`.
- Риск: Dependabot PR noise.
  - Митигация: weekly cadence и ручной review/батчинг.
- Риск: отставание pin-версий.
  - Митигация: регулярный Dependabot сигнал + branch protection enforcement.

## Definition of Done

- Hardening PR merged.
- Required checks green на PR и post-merge run.
- Evidence зафиксирован в Step 7 spec.
