# Step 7 — Deterministic CI Gate Design

## Context and Goal

После закрытия Step 6 практический E2E lane подтверждён артефактами, но следующий системный риск — регрессии без раннего автоматического сигнала на ветке. Цель Step 7 v1 — ввести узкий, воспроизводимый и нефлейковый CI-gate, который проверяет базовую интеграционную целостность Android+backend на каждом `push`/`pull_request` в основные ветки.

Фокус на deterministic-проверках: backend tests, Android unit tests, Android debug assemble. Эмуляторные и UI/E2E проверки осознанно выведены за пределы v1.

## Scope

### In Scope

- Один GitHub Actions workflow для deterministic gate.
- Триггеры:
  - `pull_request` в `main` и `master`
  - `push` в `main` и `master`
- Обязательные job'ы:
  1. `backend-tests`
  2. `android-unit-tests`
  3. `android-assemble-debug`
- Gradle cache для ускорения повторных прогонов.
- Публикация тестовых/диагностических артефактов при падении.
- Явные, стабильные имена job'ов для дальнейшего branch protection.

### Out of Scope (v1)

- Emulator/androidTest/Compose UI tests.
- Matrix по Android API levels / нескольким JDK.
- Release signing и publish.
- Coverage thresholds и quality gates уровня статического анализа.

## Design Options Considered

### Option A (Recommended): Single deterministic workflow

Один workflow-файл с тремя job'ами, где backend и android-проверки разведены по ответственности, но собираются в один итоговый статус.

Плюсы:
- Минимальная операционная сложность.
- Высокая воспроизводимость.
- Быстрый rollout и прозрачная диагностика.

Минусы:
- Не покрывает UI/E2E path.
- Один workflow со временем может разрастись (решаемо дальнейшим split по мере усложнения).

### Option B: Split workflows by subsystem

Отдельный workflow для backend и отдельный для android.

Плюсы:
- Независимые пайплайны, проще ownership по командам.

Минусы:
- Сложнее управлять единым required check.
- Более высокий риск расхождений по policy/cache/config.

### Option C: Build-only initial gate

Проверять только compile/assemble без tests.

Плюсы:
- Максимально быстро.

Минусы:
- Слабая защитная ценность; пропускает логические регрессии.

## Approved Approach

Принят **Option A**: один deterministic workflow с тремя обязательными job'ами.

## Architecture and Job Boundaries

### Workflow file

- `/.github/workflows/ci-deterministic.yml`

### Job 1 — backend-tests

- Цель: верифицировать backend unit/integration тестовый слой.
- Среда: GitHub-hosted runner (Windows для единообразия с `.bat` командами репозитория).
- Working directory: `backend`.
- Команда:
  - `./gradlew.bat test --no-daemon`

### Job 2 — android-unit-tests

- Цель: верифицировать Android JVM unit tests.
- Working directory: `android`.
- Команда:
  - `./gradlew.bat testDebugUnitTest --no-daemon`

### Job 3 — android-assemble-debug

- Цель: гарантировать compile/package целостность debug APK после unit-проверок.
- Working directory: `android`.
- Зависимость:
  - `needs: [android-unit-tests]`
- Команда:
  - `./gradlew.bat assembleDebug --no-daemon`

### Dependency model

- `backend-tests` и `android-unit-tests` могут исполняться параллельно.
- `android-assemble-debug` запускается после успешного `android-unit-tests`.
- Общий workflow — failed, если любой обязательный job failed.

## Data and Failure Flow

1. `push`/`pull_request` запускает workflow.
2. Каждый job поднимает JDK и Gradle cache.
3. Job выполняет одну deterministic команду.
4. При fail:
   - фиксируется exit code,
   - загружаются отчёты/логи как artifacts,
   - workflow получает failed status.
5. При success всех job — workflow green.

## Artifact and Observability Policy

При падении job загружать:
- backend:
  - `backend/build/reports/tests/**`
  - `backend/build/test-results/**`
- android:
  - `android/app/build/reports/tests/**`
  - `android/app/build/test-results/**`
  - (опционально) `android/build/reports/**`, если доступно

Требование: артефакты должны позволять root-cause анализ без локального воспроизведения в типовых случаях.

## Security and Robustness Considerations

- Без секретов в v1: deterministic gate не требует signing creds.
- Версии JDK фиксируются в workflow (без floating latest в рамках одного файла).
- Команды выполняются через Gradle wrapper проекта.
- Запрещены обходы проверки (`continue-on-error` на обязательных job).

## Acceptance Criteria

Step 7 v1 считается принятым, когда одновременно выполнено:

1. В репозитории существует `/.github/workflows/ci-deterministic.yml`.
2. Workflow запускается на `push` и `pull_request` в `main`/`master`.
3. Job'ы `backend-tests`, `android-unit-tests`, `android-assemble-debug` выполняются в CI.
4. На текущем `master` workflow проходит green.
5. При искусственной ошибке в одном из слоёв CI показывает red и сохраняет диагностические артефакты.
6. Имена job'ов стабильны и пригодны для branch protection.

## Risks and Mitigations

- Риск: длительный runtime pipeline.
  - Митигация: Gradle caching, параллельный запуск backend+android unit.
- Риск: platform-specific нестабильность runner.
  - Митигация: единый runner OS для v1, расширение матрицы только после стабилизации.
- Риск: ложное ощущение полноты покрытия.
  - Митигация: явная фиксация, что emulator/UI tests не входят в v1 scope.

## Non-Goals Reminder

Этот дизайн **не** завершает полный Step 7. Он запускает первую, контролируемую итерацию quality gate, на которую в следующих подпроектах можно безопасно нарастить:
- emulator/androidTest lane,
- quality metrics,
- release verification.

## Implementation Evidence (Step 7 v1)

Executed locally on current branch:
- `cd backend && ./gradlew.bat test --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat testDebugUnitTest --no-daemon` → `BUILD SUCCESSFUL`
- `cd android && ./gradlew.bat assembleDebug --no-daemon` → `BUILD SUCCESSFUL`

Workflow file created:
- `.github/workflows/ci-deterministic.yml`

Deterministic jobs present:
- `backend-tests`
- `android-unit-tests`
- `android-assemble-debug`

## External Validation Evidence (GitHub)

Repository:
- `SyrexBlack/Vitbon_kassa_high`

### Green baseline proof (all deterministic jobs succeed)

- Run: `24436566216`
- URL: `https://github.com/SyrexBlack/Vitbon_kassa_high/actions/runs/24436566216`
- Trigger: push to `master`
- Result:
  - `backend-tests` ✅
  - `android-unit-tests` ✅
  - `android-assemble-debug` ✅
- Artifacts present:
  - `backend-test-artifacts`
  - `android-unit-test-artifacts`
  - `android-assemble-debug-artifacts`

### Controlled red proof (intentional backend failure)

- Run: `24437524243`
- URL: `https://github.com/SyrexBlack/Vitbon_kassa_high/actions/runs/24437524243`
- Trigger: push to `master` with temporary command `gradlew.bat test definitelyNotATask --no-daemon`
- Result:
  - `backend-tests` ❌ (expected)
  - `android-unit-tests` ✅
  - `android-assemble-debug` ✅
- Failed-step evidence (`gh run view --log-failed`):
  - `Task 'definitelyNotATask' not found in root project 'vitbon-backend' and its subprojects.`
- Artifact behavior under forced early failure:
  - Android artifacts uploaded
  - backend artifact upload step executed, but test report paths were absent (warning: no files found)

### Restore proof (workflow returned to green after rollback)

- Run: `24438169632`
- URL: `https://github.com/SyrexBlack/Vitbon_kassa_high/actions/runs/24438169632`
- Trigger: push to `master` after restoring `Run backend tests` command back to `gradlew.bat test --no-daemon`
- Result:
  - `backend-tests` ✅
  - `android-unit-tests` ✅
  - `android-assemble-debug` ✅
- Artifacts present:
  - `backend-test-artifacts`
  - `android-unit-test-artifacts`
  - `android-assemble-debug-artifacts`

### Local artifact snapshots used during verification

- `.claude/ci-artifacts/24436566216/`
- `.claude/ci-artifacts/24437524243/`

Acceptance criteria coverage from external evidence:
- AC4 (`master` workflow green) — satisfied by runs `24436566216` and `24438169632`.
- AC5 (artificial error produces red + diagnostics) — satisfied by run `24437524243`.
