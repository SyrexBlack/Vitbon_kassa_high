# E2E Тесты — VITBON ККМ

## Стенд

- Устройства: MSPOS-K, Нева 01Ф
- Версия Android: 6.0+
- Версия ПО: 1.0.0

## Тестовые учётные данные

| Роль | ПИН |
|------|-----|
| Администратор | 9999 |
| Кассир | 1234 |

## Сценарии E2E

### 1. Продажа → ОФД

**Шаги:**
1. Авторизация: ПИН `1234`
2. Открыть смену
3. Сканировать ШК товара `4601234567890`
4. Нажать «ПРОДАТЬ»
5. Дождаться печати чека

**Ожидаемый результат:**
- Чек напечатан
- Фискальный признак (ФП) содержит 10+ символов
- Статус чека в ЛК ОФД: «Принят»

---

### 2. Возврат

**Шаги:**
1. Из чека (шаг 1) нажать «Вернуть» или ввести номер чека
2. Выбрать позиции
3. Нажать «Оформить возврат»

**Ожидаемый результат:**
- Чек возврата напечатан
- ФП возврата ≠ ФП продажи
- В ОФД: 2 чека (продажа + возврат)

---

### 3. Закрытие смены

**Шаги:**
1. Открыть смену (если закрыта)
2. Пробить 3–5 чеков
3. Нажать «Закрыть смену»
4. Подтвердить

**Ожидаемый результат:**
- Z-отчёт напечатан
- Счётчики сброшены
- В ОФД: все чеки смены переданы

---

### 4. Офлайн-режим (10 чеков без сети → синхронизация)

**Шаги:**
1. Пробить 10 чеков
2. Отключить Wi-Fi / мобильные данные
3. Подождать 60 сек
4. Включить сеть
5. Проверить статус синхронизации

**Ожидаемый результат:**
- Статус: все 10 чеков SYNCHRONIZED
- В ОФД: все 10 чеков отражены

---

### 5. ЕГАИС: приёмка накладной (test УТМ)

**Требования:** test-УТМ запущен, тестовый RSA-сертификат загружен

> Автоматически подтверждён только proxy contract: backend больше не должен возвращать HTTP 501 и обязан проксировать ответ живой интеграции. Полевая приёмка остаётся release-blocker до подключения реального ЕГАИС-контура.

**Шаги:**
1. Активировать модуль ЕГАИС
2. Перейти в раздел «ЕГАИС → Приёмка»
3. Загрузить тестовую накладную
4. Подтвердить приёмку

**Ожидаемый результат:**
- Статус накладной: «Принято» в ЕГАИС
- В приложении: уведомление об успехе

---

### 6. Честный ЗНАК: продажа маркированного (test ЛМ ЧЗ)

**Требования:** test ЛМ ЧЗ запущен

> Автоматически подтверждён только proxy/validation contract: backend больше не должен возвращать HTTP 501, а scan validation/disposal flow должен ходить через backend integration endpoint. Полевая приёмка остаётся release-blocker до подключения реального ЧЗ-контура.

**Шаги:**
1. Активировать модуль «Честный ЗНАК»
2. Открыть смену
3. Сканировать DataMatrix код тестового товара
4. Дождаться валидации (статус: OK)
5. Завершить продажу

**Ожидаемый результат:**
- Код маркирован как выбывший в системе ЧЗ
- Чек содержит теги 1162 / 1163

---

## Чеклист перед релизом

- [ ] Все E2E сценарии пройдены на MSPOS-K
- [ ] Все E2E сценарии пройдены на Нева 01Ф
- [ ] Чеки отражены в ЛК ОФД (проверка по ФП)
- [ ] ЕГАИС: накладные загружаются и подтверждаются
- [ ] ЧЗ: выбытие работает (test ЛМ)
- [ ] Лицензия: блокировка при просрочке
- [ ] Лицензия: неизвестное/нелицензированное устройство блокируется, а не считается активным
- [ ] Grace period: 7 дней при отсутствии сети

---

## Автоматическая верификация (2026-05-22)

### Unified local gate

Команда:
`powershell -ExecutionPolicy Bypass -File .\verify-phase-b.ps1`

Поведение:
- запускает backend test suite с `JAVA_HOME=C:\Program Files\Java\jdk-17`
- запускает Android `:app:testDebugUnitTest :app:assembleDebug`
- по умолчанию запускает Android `:app:assembleRelease`
- проверяет `adb.exe devices -l` и отдельно помечает hardware smoke как `PASS` или `PENDING`

Строгий режим для полевого smoke:
`powershell -ExecutionPolicy Bypass -File .\verify-phase-b.ps1 -RequireHardware`

Ожидаемый результат на машине без подключённой кассы:
- automated gates: `PASS`
- hardware smoke precheck: `PENDING`

Фактический snapshot (2026-05-22, current workspace):
- `verify-phase-b.ps1` → backend tests `PASS`
- `verify-phase-b.ps1` → Android `:app:testDebugUnitTest :app:assembleDebug` `PASS`
- `verify-phase-b.ps1` → Android `:app:assembleRelease` `PASS`
- `verify-phase-b.ps1` → `adb.exe devices -l` returns no devices, so hardware smoke precheck = `PENDING`

### Backend

Команда:
`backend\gradlew.bat test --no-daemon --console=plain`

Результат: `BUILD SUCCESSFUL`

Подтверждено автоматически:
- object-level binding для shifts/checks/reports/documents к session principal
- idempotent replay для `checks/sync` по `localUuid`
- per-item failure handling в `checks/sync`
- `UNLICENSED` для неизвестного устройства на backend
- `V7__add_document_ownership.sql` smoke-tested через `FlywayMigrationTest`

### Android

Команды:
`android\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain`

`android\gradlew.bat :app:assembleRelease --no-daemon --console=plain`

Результат: `BUILD SUCCESSFUL`

Подтверждено автоматически:
- debug и release сборки проходят
- Android клиент блокирует `UNLICENSED` вместо silent `ACTIVE`
- sales/shift paths используют сохранённый secure `deviceId`
- periodic sync worker переведён на 15 минут
- destructive Room fallback удалён при текущей schema version 1
- единый local runner `verify-phase-b.ps1` собирает backend/Android gates и сразу показывает, остался ли только hardware blocker
- backend proxy routes `/api/v1/egais/incoming`, `/api/v1/egais/tara`, `/api/v1/chaseznak/validate`, `/api/v1/chaseznak/sell`, `/api/v1/chaseznak/verify-age` больше не зафиксированы на HTTP `501`
- Android `ChaseznakRepository.validateCode()` использует backend `validate` flow вместо постоянного локального `ERROR`

### Live contour evidence runner

Для ЕГАИС/ЧЗ в live-контуре теперь есть отдельный runner:

`powershell -ExecutionPolicy Bypass -File .\verify-live-integrations.ps1 -BackendBaseUrl https://<backend-host>/ -AdminPin 9999 -ChaseznakCode "<test-datamatrix>" -AgeQrData "<test-age-qr>" -EgaisIncomingPayloadPath .\payloads\egais-incoming.xml -EgaisTaraPayloadPath .\payloads\egais-tara.xml -EnableMutatingRoutes`

Что делает:
- логинится под ADMIN и проверяет, что optional feature flags реально включены
- снимает evidence по `/api/v1/statuses` и `/api/v1/egais/status`
- прогоняет non-mutating ЧЗ probes: `/api/v1/chaseznak/validate` и `/api/v1/chaseznak/verify-age`
- по явному флагу `-EnableMutatingRoutes` дополнительно прогоняет `POST /api/v1/egais/incoming`, `/api/v1/egais/tara`, `/api/v1/chaseznak/sell`
- пишет markdown-отчёт в `.tmp_live_integrations_evidence.md`

Правила использования:
- без `-EnableMutatingRoutes` destructive routes не трогаются и остаются `PENDING`
- для полного release-evidence нужно передать согласованные contour payload’ы/test-коды
- если в summary есть `FAIL`, contour gate не пройден; если есть только `PENDING`, evidence неполный и blocker остаётся внешним

---

## Phase A verification log (2026-04-23)

### Runtime-targeted unit tests

Команда:
`java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --tests com.vitbon.kkm.core.fiscal.runtime.FfdPolicyStoreTest --tests com.vitbon.kkm.core.fiscal.runtime.FfdVersionResolverTest --tests com.vitbon.kkm.core.fiscal.runtime.FiscalErrorMapperTest --tests com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestratorTest --tests com.vitbon.kkm.core.fiscal.runtime.FiscalAdapterContractTest --no-daemon --console=plain`

Результат: `BUILD SUCCESSFUL`

### Full Android unit regression

Команда:
`java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:testDebugUnitTest --no-daemon --console=plain`

Результат: `BUILD SUCCESSFUL`

### Debug build

Команда:
`java -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android :app:assembleDebug --no-daemon --console=plain`

Результат: `BUILD SUCCESSFUL`

Примечание: в логах сборки присутствует предупреждение SDK XML version mismatch и предупреждение strip symbols для `libbarhopper_v3.so` / `libimage_processing_util_jni.so`; сборка и тесты завершены успешно.
