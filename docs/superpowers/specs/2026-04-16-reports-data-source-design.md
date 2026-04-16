# Reports Data Source Indicator Design

## Goal
Сделать источник данных на экране «Отчёты» явным для пользователя, чтобы устранить неоднозначность при расхождении удалённых агрегатов и локальной БД (особенно после reinstall/reset).

## Context and Problem
Текущее поведение в [ReportsUseCase.kt](android/app/src/main/java/com/vitbon/kkm/features/reports/domain/ReportsUseCase.kt) — remote-first:
- при успешном `api.getSalesReport(...)` сразу возвращается remote-отчёт;
- fallback на локальный Room (`checkDao/checkItemDao`) выполняется только при non-success/exception.

Из-за этого пользователь может видеть ненулевые метрики при пустой локальной БД, не понимая, что источник — облако.

## Scope
Включено:
1. Явный признак источника в доменной модели отчёта.
2. Передача признака до UI.
3. Отображение источника чипом под фильтрами периода.
4. Unit coverage для remote/fallback веток.

Не включено:
- изменения backend API;
- переработка алгоритма расчётов отчёта;
- новые экранные сценарии за пределами Reports;
- глобальные сетевые индикаторы в других модулях.

## Approaches Considered

### A) Source в доменной модели (RECOMMENDED)
Добавить `ReportDataSource` в `SalesReport`, выставлять значение в `ReportsUseCase`.

Плюсы:
- точный источник фиксируется там, где принимается решение remote/fallback;
- простая тестируемость;
- минимальный runtime ambiguity.

Минусы:
- требуется расширение модели и адаптация мест конструирования.

### B) Source вычислять только в UI/ViewModel
Пытаться вывести источник из сетевого состояния/косвенных признаков.

Плюсы:
- меньше изменений в домене.

Минусы:
- риск рассинхронизации с фактическим источником;
- хрупкость и слабая проверяемость.

### C) Wrapper-результат (report + metadata)
Ввести отдельный тип результата вместо расширения `SalesReport`.

Плюсы:
- гибкость для будущих метаданных.

Минусы:
- избыточно для текущей задачи (YAGNI).

## Selected Architecture
Выбран подход A.

### Domain
- Добавляется `enum class ReportDataSource { REMOTE, LOCAL }`.
- `SalesReport` расширяется полем `source: ReportDataSource`.
- В `ReportsUseCase.getSalesReport(...)`:
  - при `remoteResponse.isSuccessful && body != null` → `source = REMOTE`;
  - в fallback ветке по Room → `source = LOCAL`.

### ViewModel
- `ReportsViewModel` продолжает отдавать `SalesReport` через существующий state.
- Дополнительная бизнес-логика во ViewModel не вводится.

### UI
В [ReportsScreen.kt](android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/ReportsScreen.kt):
- под строкой period-filter chips добавляется чип источника данных;
- текст:
  - `Источник: Облако` для REMOTE;
  - `Источник: Локально` для LOCAL;
- чип рендерится только когда `report != null`;
- стиль:
  - REMOTE — нейтральный/позитивный тон;
  - LOCAL — subdued/secondary tone (не error).

## Testing Strategy

### Unit Tests
Обновить [ReportsUseCaseTest.kt](android/app/src/test/java/com/vitbon/kkm/features/reports/domain/ReportsUseCaseTest.kt):
1. В тесте успешного API-ответа проверить `report.source == REMOTE`.
2. В тесте fallback по non-success проверить `report.source == LOCAL`.
3. В тесте fallback по exception проверить `report.source == LOCAL`.

### UI Verification
Проверить вручную:
1. Онлайн: открыть Reports и убедиться в чипе `Источник: Облако`.
2. Оффлайн (или с принудительным fallback): убедиться в чипе `Источник: Локально`.

## Risks and Mitigations
- Риск: пропустить одну из точек создания `SalesReport` после добавления поля.
  - Митигируется компилятором + unit тестами.
- Риск: визуальная перегрузка Reports экрана.
  - Митигируется компактным placement под фильтрами и отсутствием новых блоков.

## Acceptance Criteria
1. `SalesReport` содержит явное поле источника данных.
2. `ReportsUseCase` выставляет REMOTE/LOCAL корректно по ветке выполнения.
3. На экране Reports под фильтрами периода отображается чип `Источник: ...`.
4. Unit-тесты фиксируют source для remote success и local fallback веток.
5. Существующие расчёты сумм/чеков/топ-товаров не изменены.
