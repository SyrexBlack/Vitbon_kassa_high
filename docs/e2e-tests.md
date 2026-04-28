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
- [ ] Grace period: 7 дней при отсутствии сети

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
