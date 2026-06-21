# VITBON Kassovoye Prilozheniye — Features Analysis (v1.1 Production Readiness)

**Дата:** 2026-06-21
**Версия:** 1.1
**Статус:** Исследовательский документ
**Потребитель:** Определение требований (requirements definition) для v1.1
**Предыдущая версия:** `FEATURES.md` (v1.0, 2026-06-20) — заархивирована в `.planning/milestones/`

---

## Обзор

Документ анализирует **новые** v1.1 features, которые **НЕ были частью v1.0** (см. `FEATURES.md` от 2026-06-20 для v1.0 baseline). Фокус — production-readiness и hardening:

1. **Sandbox-интеграция** с внешними регуляторными системами (ОФД, ЧЗ, УТМ ЕГАИС, Цифровой ID Max)
2. **Нагрузочные/стресс-тесты** (200+ касс, 24-час offline)
3. **Операционные сценарии** (ФН replacement, key rotation, mTLS, токены)

v1.0 уже покрывает: фискальные операции, ККТ-интеграцию, офлайн-режим, аутентификацию, лицензирование, мониторинг, синхронизацию, отчётность, модули маркировки и ЕГАИС в коде. v1.1 — это **верификация через песочницы** + **операционная устойчивость**.

---

## Условные обозначения сложности

| Уровень | Обозначение | Описание |
|---------|-------------|----------|
| Низкая | 🟢 | Реализуется в рамках существующих модулей, без внешних зависимостей |
| Средняя | 🟡 | Требует новой интеграции или внешней координации |
| Высокая | 🔴 | Сложная внешняя зависимость (регулятор, песочница, инфраструктура) |

---

## 1. TABLE STAKES (для v1.1 — Production-Ready касса 54-ФЗ)

> **Критерий:** Без этих проверок и операционных сценариев касса не может быть допущена к production. Это baseline для зрелого фискального ПО на рынке РФ.

### 1.1 Sandbox-интеграция с ОФД (ОФД-API)

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| SAND-OFD-01 | Подключение к ОФД sandbox (тестовая среда ОФД-API) | 🔴 | Backend proxy, ОФД-credentials для песочницы |
| SAND-OFD-02 | Smoke-тест: отправка чека продажи в ОФД sandbox, проверка ответа | 🟡 | FiscalCore, sync-queue |
| SAND-OFD-03 | Smoke-тест: отправка чека возврата, отчёта о закрытии смены (Z-отчёт) | 🟡 | FiscalCore |
| SAND-OFD-04 | Проверка receipt validation (корректность TLV-структуры ФФД 1.05/1.2) | 🟡 | ОФД API, FFD-валидатор |
| SAND-OFD-05 | Тест retry-политики (повторная отправка при сетевом сбое) | 🟡 | WorkManager, sync-queue |
| SAND-OFD-06 | Тест негативных сценариев (просроченный ФН, ошибки ФФД, таймауты) | 🟡 | FiscalCore error model |

**Что предоставляет песочница ОФД (типовая реализация):**
- Тестовый URL вида `sandbox-api.ofd.ru/v1/` или аналог (зависит от ОФД)
- Тестовые credentials (ИНН, ОГРН, ФН, device-id)
- Повтор ФД, не уходящих в реальную ФНС
- Mock-валидация ФФД-структуры
- Полный цикл: отправка → подтверждение → fetch статуса → ack

**Что возвращается в кассу:**
- Статус отправки чека (delivered / queued / error)
- Код фискального документа (ФД)
- Номер смены
- Таймстемп сервера
- Код ошибки (если есть) — по нему синк-логика решает, нужно ли переотправлять

**Table-stakes поведение:**
- Должна быть отдельная опция в BuildConfig/конфиге для переключения на sandbox URL
- Песочница должна быть **отключаема** в release-сборках (только через debug-флаг)
- Все 4 типа фискальных документов (sell, refund, correction, Z-report) должны быть покрыты тестом
- В логах синхронизации должен быть виден отличимый признак sandbox-режима (чтобы случайно не отправить боевой ФД в test endpoint)

---

### 1.2 Sandbox-интеграция с Честный ЗНАК (ГС1/ЦРПТ)

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| SAND-CZ-01 | Подключение к test-bed ЧЗ (sandboxapi.crpt.ru или аналог) | 🔴 | Backend proxy, ИНН/ОГРН в песочнице |
| SAND-CZ-02 | Валидация DataMatrix в песочнице (валидные / невалидные коды) | 🟡 | MARK-модуль |
| SAND-CZ-03 | Smoke-тест выбытия маркированного товара | 🔴 | MARK-модуль, ФН-чек |
| SAND-CZ-04 | Smoke-тест приёмки маркированного товара | 🔴 | MARK-модуль |
| SAND-CZ-05 | Проверка обработки невалидных КМ (ошибка ЧЗ, таймаут, сеть) | 🟡 | MARK-error model |
| SAND-CZ-06 | Проверка offline-блокировки (MARK-blocked при отсутствии сети) | 🟡 | MARK-policy, connectivity |

**Что предоставляет песочница ЧЗ:**
- **ГИС МТ** (test-bed) — `sandbox.gis-mt.crpt.ru`
- Тестовые коды маркировки, известные валидные/невалидные
- Token endpoint с ограниченным временем жизни
- Mock-ответы на: проверка КМ, выбытие, возврат, приёмка
- Симулированные задержки (иногда 2-5 сек) и периодические ошибки 503

**Что возвращается в кассу:**
- Код статуса КМ: `READY_FOR_USE` / `SOLD` / `RETIRED` / `NOT_FOUND`
- При выбытии: подтверждение, что КМ внесён в реестр
- При ошибке: код `KM_NOT_FOUND`, `KM_EXPIRED`, `INTERNAL_ERROR` и т.д.

**Table-stakes поведение:**
- Все запросы к ЧЗ — **синхронные** перед фискальным чеком (per Finding 19 в v1.0 SUMMARY)
- При недоступности песочницы — offline-block на маркированный товар
- Тестовые КМ должны быть явно в whitelist (для CI)
- Песочница отключаема в production через `FeatureManager` / config

---

### 1.3 Sandbox-интеграция с УТМ (ЕГАИС)

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| SAND-UTM-01 | Подключение к тестовому УТМ (ЦРПТ-тест или локальный mock) | 🔴 | УТМ API, RSA-сертификат для песочницы |
| SAND-UTM-02 | Smoke-тест продажи алкоголя (АП + чек УТМ + фискальный чек) | 🔴 | ALCO-модуль, УТМ |
| SAND-UTM-03 | Smoke-тест акта вскрытия тары (для кег) | 🔴 | ALCO-модуль |
| SAND-UTM-04 | Проверка отрицательных остатков в песочнице | 🟡 | УТМ, ALCO-policy |
| SAND-UTM-05 | Тест синхронности УТМ-ответа перед фискализацией | 🟡 | ALCO-error model |

**Что предоставляет песочница УТМ:**
- Локальный Docker-контейнер с mock-УТМ (типовой подход) или удалённый testbed
- Тестовый RSA-сертификат (ФС РАР) для песочницы
- Симуляция справок А/Б, отрицательных остатков
- HTTP API совместимое с боевым УТМ (порт 8080 по умолчанию)

**Что возвращается в кассу:**
- Статус акта продажи (`Accepted` / `Rejected` с причиной)
- Справка А/Б (XML)
- Код акта
- Таймстемп

**Table-stakes поведение:**
- УТМ-запросы **только синхронные** (per Finding 19)
- При недоступности УТМ — offline-block на алкоголь (`AlcoholSalePolicyUseCase`)
- Mock-УТМ может жить в Docker, но продакшен-конфиг должен явно указывать боевой URL

---

### 1.4 Sandbox-интеграция с Цифровой ID Max

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| SAND-CID-01 | Подключение к test API Цифровой ID Max | 🔴 | API credentials, OAuth-токен |
| SAND-CID-02 | Smoke-тест: QR → токен → валидация возраста | 🔴 | ALCO-модуль, УТМ |
| SAND-CID-03 | Проверка возврата «возраст подтверждён» (для алкоголя) | 🟡 | CID API |
| SAND-CID-04 | Проверка ошибки (несовершеннолетний / QR невалиден / таймаут) | 🟡 | CID-error model |
| SAND-CID-05 | Тест фолбэка: отказ продажи при недоступности API | 🟡 | ALCO-policy |

**Что предоставляет песочница Цифровой ID Max:**
- Test API endpoint (зависит от вендора Max, типично dev/uat)
- Тестовые QR-токены (валидный взрослый / валидный несовершеннолетний / просроченный)
- Mock-ответы с задержками
- OAuth-флоу с test-credentials

**Что возвращается в кассу:**
- Возраст (true/false для ≥18)
- Причина отказа
- Код операции
- TTL токена

**Table-stakes поведение:**
- Каждый запрос к CID — **синхронный** перед печатью чека
- При недоступности API — **отказ продажи** (защита от продажи алкоголя несовершеннолетним)
- Логирование каждого запроса для аудита

---

### 1.5 ФН (фискальный накопитель) replacement flow

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| FN-REP-01 | Закрытие архива ФН (полный Z-отчёт, передача в ОФД) | 🔴 | FiscalCore, ФН SDK, ОФД |
| FN-REP-02 | Снятие Z-отчёта, получение `closingDate` ФН | 🟡 | FiscalCore |
| FN-REP-03 | Проверка: все ли ФД переданы в ОФД (`documentsRemaining` = 0) | 🟡 | FiscalCore, ОФД sync |
| FN-REP-04 | Физическая замена ФН (оператор + мастер ЦТО) | 🟢 | UI-инструкция |
| FN-REP-05 | Регистрация/перерегистрация ККТ с новым ФН | 🔴 | FiscalCore, ККТ |
| FN-REP-06 | Перерегистрация: причина смены (`change_fn` = 12 по приказу ФНС) | 🟡 | FiscalCore, FFD builder |
| FN-REP-07 | Обновление локальных метаданных ФН в Room (серийник, дата) | 🟢 | Room |
| FN-REP-08 | Синхронизация с облаком: новые параметры ККТ | 🟡 | Backend API |
| FN-REP-09 | Audit log запись смены ФН (для регулятора) | 🟡 | Audit log |
| FN-REP-10 | Тест: что произойдёт при попытке переключить ФФД версию во время замены (должно блокироваться) | 🟡 | FfdVersionResolver |

**Операторские шаги (типовой процесс):**
```
1. Снять Z-отчёт → закрыть смену
2. Дождаться отправки всех ФД в ОФД (status = "доставлено")
3. Закрыть архив ФН (команда ККТ)
4. Физически заменить ФН
5. Перерегистрация ККТ (команда с указанием причины)
6. Проверить ФФД-версию (должна остаться 1.05 или 1.2)
7. Открыть новую смену → контрольный чек
```

**Сложность: 🔴** — процесс регулируется ФНС (приказ ФНС по форматам ФД), требует участия ЦТО, окно недоступности кассы. После первой регистрации ККТ, ФФД-версия иммутабельна (per Finding 1 в PITFALLS).

**Table-stakes поведение:**
- UI-flow с пошаговой инструкцией для кассира/администратора
- Блокировка любых операций продажи во время замены
- Проверка, что все ФД дошли до ОФД (нельзя закрыть ФН, если есть неотправленные)
- Валидация: `change_fn` причина = 12 в карточке перерегистрации
- Уведомление старшего кассира / администратора

---

### 1.6 SQLCipher key rotation

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| SQLC-01 | Плановая ротация ключа (например, раз в 90 дней) | 🟡 | SQLCipher 4.5.4, Room |
| SQLC-02 | Управляемая миграция: открыть БД со старым ключом → PRAGMA rekey → сохранить новый | 🟡 | SQLCipher `rekey` PRAGMA |
| SQLC-03 | Безопасное сохранение нового ключа в Android Keystore | 🟡 | Android Keystore |
| SQLC-04 | Crash-recovery во время rotation (БД не должна остаться в inconsistent state) | 🔴 | SQLCipher, транзакции |
| SQLC-05 | Audit log: «старый ключ X, новый ключ Y, время, инициатор» | 🟢 | Audit |
| SQLC-06 | Self-test: после rotation — все запросы к Room работают, отчёты совпадают | 🟡 | Room DAO tests |

**Алгоритм (типовой):**
```
1. Получить новый ключ K_new (через Keystore или derive from passphrase)
2. Открыть БД со старым ключом K_old
3. Выполнить транзакцию:
   PRAGMA key = 'K_old';
   PRAGMA rekey = 'K_new';   // атомарно перешифровывает все страницы
4. Обновить K_old → K_new в Keystore
5. Переоткрыть БД с K_new для проверки
6. Записать в audit log
```

**Сложность: 🔴** — `PRAGMA rekey` в SQLCipher 4.x работает атомарно, но:
- Должна быть обёрнута в try/catch с rollback
- Нельзя ротировать во время активной транзакции продажи
- Должен быть тест на crash-recovery (kill -9 во время rekey)

**Table-stakes поведение:**
- В CI: rotation выполняется на каждом PR (smoke)
- В production: rotation по расписанию + ручной триггер из админ-панели
- Self-test после rotation: счётчики чеков, остатки товаров, лицензия — всё совпадает
- Логирование rotation events (для compliance audit)

---

### 1.7 Mutual TLS (mTLS) certificate management

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| MTLS-01 | Хранение клиентского сертификата + private key в Android Keystore | 🟡 | Android Keystore, BKS |
| MTLS-02 | Certificate pinning с rotation (смена server cert без downtime) | 🟡 | OkHttp, cert manager |
| MTLS-03 | Hot-reload сертификата без перезапуска приложения | 🔴 | OkHttp Client rebuild, cert provider |
| MTLS-04 | Pre-expiry warning (за 30/15/7 дней до истечения) | 🟢 | Cert metadata, Date logic |
| MTLS-05 | Управление двумя сертификатами одновременно (старый + новый для rotation period) | 🟡 | OkHttp `KeyManager` |
| MTLS-06 | Тест: что произойдёт при отзыве серверного сертификата (CRL/OCSP) | 🟡 | OkHttp, cert chain validation |
| MTLS-07 | Audit log: выдача, ротация, отзыв клиентского сертификата | 🟢 | Audit |

**Типовой флоу rotation:**
```
Старая схема:
- cert_old действует с T0 до T0 + 90d
- За 7 дней до expiry:
  1. Запросить cert_new в backend CA
  2. Получить cert_new + private key
  3. Установить оба в KeyManager: cert_old, cert_new
  4. Постепенно обновить на backend (cert_new становится primary)
  5. После T0 + 90d: удалить cert_old
```

**Сложность: 🔴** — `KeyManager` OkHttp не поддерживает hot-reload нативно, нужно пересобирать `OkHttpClient`. Варианты:
- Перезапуск WorkManager-цепочки (drastic, прерывает sync)
- Вращающийся `KeyManager` (singleton с synchronized swap)
- Scheduled cert-refresh через WorkManager

**Table-stakes поведение:**
- Cert expiry должен быть виден в status-индикаторе
- При отзыве серверного cert — оперативное уведомление в кассу + admin alert
- mTLS key никогда не покидает Android Keystore

---

### 1.8 Token rotation / revocation

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| TOK-01 | Refresh-токен с TTL (например, 7 дней) | 🟡 | Auth, Backend OAuth |
| TOK-02 | Автоматический refresh перед истечением (за 5 мин) | 🟢 | OkHttp Authenticator |
| TOK-03 | Revocation propagation (если сервер отозвал токен — клиент получает 401 и re-auth) | 🟡 | OkHttp interceptor, Auth |
| TOK-04 | Хранение токенов в EncryptedSharedPreferences / Keystore | 🟢 | security-crypto |
| TOK-05 | Принудительный logout при revoked refresh token (требует PIN ввод) | 🟡 | Auth flow |
| TOK-06 | Тест: revoked access token → 401 → refresh → retry с новым | 🟡 | Backend mock |
| TOK-07 | Тест: revoked refresh token → 401 → переход в login screen | 🟡 | Backend mock |

**Типовой флоу:**
```
1. Login → получаем access_token (TTL 15m) + refresh_token (TTL 7d)
2. Каждый запрос: Authorization: Bearer access_token
3. За 5 мин до expiry: автоматический refresh
4. Сервер может отозвать:
   - access token: 401 → auto-refresh → retry
   - refresh token: 401 → re-auth required
5. Logout: revoke refresh token на сервере, удалить локально
```

**Сложность: 🟡** — стандартный OAuth2 flow. Особенности:
- Background workers (sync) должны иметь свои refresh-handlers
- Revocation propagation — серверная сторона (типовой подход: 401 + `WWW-Authenticate: Token revoked`)
- EncryptedSharedPreferences для хранения refresh token

**Table-stakes поведение:**
- Refresh token никогда не отправляется в незашифрованном виде
- При revoked refresh — обязательный re-login (даже если access ещё жив)
- Audit log для каждого token operation

---

## 2. TESTING & STRESS (Операционные тесты для v1.1)

> **Критерий:** Эти тесты подтверждают production-readiness. Без них v1.0 — «работает на 1 кассе», v1.1 — «работает на 200+ кассах в проде».

### 2.1 Load test: 200+ concurrent cash registers

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| LOAD-01 | Сценарий: 200 касс одновременно открывают смену | 🔴 | Redis Streams, backend scaling |
| LOAD-02 | Сценарий: 200 касс одновременно фискализируют чек | 🔴 | Redis Streams, ОФД-proxy |
| LOAD-03 | Сценарий: 200 касс одновременно закрывают смену (Z-отчёт) | 🔴 | Redis Streams, ОФД-proxy |
| LOAD-04 | Сценарий: 200 касс одновременно пулят товары | 🔴 | Redis Streams, Postgres |
| LOAD-05 | Метрики: P50/P95/P99 latency синхронизации | 🟡 | Prometheus, Grafana |
| LOAD-06 | Метрики: throughput (чеков/сек) | 🟡 | Prometheus |
| LOAD-07 | Метрики: error rate по endpoint'ам | 🟡 | Prometheus |
| LOAD-08 | Метрики: Redis Stream lag (consumer group lag) | 🟡 | Redis CLI |
| LOAD-09 | Метрики: Postgres connection pool saturation | 🟡 | HikariCP metrics |
| LOAD-10 | Метрики: OOM / GC pauses на backend | 🟡 | JVM metrics |

**Типовой сценарий (k6 / Gatling / JMeter):**
```
1. 200 virtual users (касс)
2. Каждый:
   - login (1 раз)
   - open shift
   - 100 продаж (в цикле, sleep 5s между)
   - close shift (Z-отчёт)
3. Длительность: 30 минут
4. Метрики: latency, error rate, lag
```

**Ключевые метрики:**
- P95 latency синхронизации чека < 500ms
- P99 latency < 1s
- Error rate < 0.1%
- Redis Stream lag < 1000 messages
- Zero data loss (golden rule: «чеки не теряются»)

**Сложность: 🔴** — требует staging-окружения, идентичного production, плюс развёрнутые ОФД-proxy / mock-ОФД. На реальном ОФД не тестируем.

---

### 2.2 24-hour offline stress test

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| OFF-01 | Сценарий: 24 часа без сети, непрерывные продажи | 🟡 | Room, FiscalCore |
| OFF-02 | Метрика: среднее количество продаж за смену offline (типично 50-200) | 🟢 | Room stats |
| OFF-03 | Метрика: размер очереди `PENDING_SYNC` (должен < 500) | 🟢 | Room query |
| OFF-04 | Метрика: размер БД после 24h offline (должен < 100 MB) | 🟢 | File size |
| OFF-05 | Метрика: RAM usage приложения (должен < 200 MB) | 🟢 | Android Profiler |
| OFF-06 | Метрика: время открытия чека (должен < 200ms) | 🟢 | Benchmark |
| OFF-07 | Метрика: время формирования Z-отчёта локально | 🟢 | Benchmark |
| OFF-08 | Assertion: ни одна продажа не потеряна | 🔴 | Room invariant |
| OFF-09 | Assertion: все отчёты за период offline — корректны | 🟡 | Room aggregation |
| OFF-10 | Assertion: license grace period не сломался | 🟡 | License check |
| OFF-11 | Assertion: при восстановлении сети sync прошёл за разумное время | 🟡 | WorkManager |
| OFF-12 | Тест: 24 часа offline + затем восстановление + 100% sync в течение 5 мин | 🟡 | E2E test |

**Типовой сценарий (Robolectric / emulator):**
```
1. Запустить эмулятор (1 касса)
2. Включить airplane mode
3. Создать 200 продаж (10 разных товаров × 20)
4. Подождать 24 часа (или ускорить через эмуляцию времени)
5. Проверить:
   - Все 200 чеков в Room
   - Queue depth = 200
   - БД < 100 MB
   - Один X-отчёт + один Z-отчёт
6. Включить сеть
7. Проверить:
   - Sync завершён за 5 мин
   - Все чеки в backend
   - Reports совпадают
```

**Сложность: 🟡** — локальный Robolectric-test, не требует backend. Удлинённый по времени (24ч = 24ч в real или fast-forward).

---

### 2.3 Mutual TLS / OAuth E2E тесты

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| MTLS-T-01 | E2E: mTLS handshake успешен | 🟡 | OkHttp mock server |
| MTLS-T-02 | E2E: невалидный серверный cert → connection refused | 🟡 | Self-signed cert |
| MTLS-T-03 | E2E: cert expiry → alert + блок операций | 🟡 | Time-bombed cert |
| MTLS-T-04 | E2E: cert rotation без downtime | 🔴 | Blue/green deploy |
| TOK-T-01 | E2E: refresh token flow | 🟡 | Mock OAuth server |
| TOK-T-02 | E2E: revoked access token → auto-refresh | 🟡 | Mock OAuth server |
| TOK-T-03 | E2E: revoked refresh token → forced re-login | 🟡 | Mock OAuth server |

**Сложность: 🟡..🔴** — стандартные OAuth2-флоу + cert-rotation. Требует mock OAuth сервера (типично `oauth2-mock-server`).

---

## 3. DIFFERENTIATORS (конкурентные преимущества v1.1)

> **Критерий:** Не обязательны, но формируют зрелость продукта. Многие конкуренты экономят на этом.

### 3.1 Операционная надёжность (observability)

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| OP-01 | Crashlytics / Sentry интеграция (только non-fiscal логи) | 🟢 | Firebase |
| OP-02 | Метрики в status bar: queue depth, time-since-last-sync, cert-expiry | 🟡 | UI, DataStore |
| OP-03 | Диагностический пакет (логи, БД stats) для отправки в support | 🟡 | Local FS, export |
| OP-04 | Алерты при аномалиях (queue overflow, cert expiring, ФН low) | 🟡 | Local rules + push to admin |

### 3.2 Zero-downtime rotation

| ID | Функция | Сложность | Зависимости |
|----|---------|-----------|-------------|
| ZDR-01 | mTLS cert rotation без остановки sync | 🔴 | См. MTLS-03 |
| ZDR-02 | SQLCipher key rotation в фоне | 🔴 | См. SQLC-04 |
| ZDR-03 | Backend deploy без потери in-flight чеков (Redis Streams ack) | 🟡 | Backend rolling update |

---

## 4. ANTI-FEATURES (сознательно НЕ строим для v1.1)

| ID | Анти-фича | Причина |
|----|-----------|---------|
| AF-V11-01 | Реальная интеграция с production ОФД (только sandbox) | В v1.1 — верификация, не production traffic |
| AF-V11-02 | Реальная интеграция с production ЧЗ | Sandbox-only, чтобы не «выбывать» тестовые КМ |
| AF-V11-03 | Реальный УТМ ЕГАИС | Тест через mock-УТМ |
| AF-V11-04 | Автоматическая смена ФН (без оператора) | Регуляторика: ФН-смена требует ЦТО |
| AF-V11-05 | Self-update сертификатов без admin approval | Аудит trail |
| AF-V11-06 | «Магическое» авто-исправление corrupted БД | Данные фискальные — лучше stop and ask |
| AF-V11-07 | Тестирование на реальных чеках в проде | Отдельный staging / UAT env |
| AF-V11-08 | Замена mTLS сертификата без сохранения старого на overlap period | Нарушает zero-downtime |

---

## 5. МАТРИЦА СЛОЖНОСТИ И ЗАВИСИМОСТЕЙ

```
Граф зависимостей v1.1:

┌─────────────────────────────────────────────────────────────────────────┐
│                       SANDBOX INTEGRATION                                │
│                                                                         │
│   SAND-OFD-01..06 ──► FiscalCore ──► Backend proxy ──► ОФД sandbox    │
│   SAND-CZ-01..06  ──► MARK module ──► Backend proxy ──► ЧЗ sandbox    │
│   SAND-UTM-01..05 ──► ALCO module ──► Local mock-УТМ / Test УТМ        │
│   SAND-CID-01..05 ──► ALCO + UTM ──► Max ID sandbox                    │
│                                                                         │
│   Все SAND-* зависят от v1.0 модулей (FISC-*, KKT-*, MARK-*, ALCO-*) │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    OPERATIONAL FLOWS                                     │
│                                                                         │
│   FN-REP-* ──► FiscalCore + Room + Audit + Backend sync                │
│   SQLC-*   ──► SQLCipher + Keystore + Audit                            │
│   MTLS-*   ──► OkHttp + Android Keystore + WorkManager                 │
│   TOK-*    ──► OkHttp Authenticator + EncryptedSharedPrefs             │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    STRESS / LOAD                                         │
│                                                                         │
│   LOAD-*  ──► Backend (Redis Streams) + ОФД proxy + Postgres           │
│   OFF-*   ──► Room + FiscalCore (local-only, не требует backend)       │
│   MTLS-T  ──► Mock server + OkHttp                                     │
│   TOK-T   ──► Mock OAuth + OkHttp                                      │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│               ЗАВИСИМОСТИ ОТ v1.0                                        │
│                                                                         │
│   SAND-OFD  → FISC-01..08, KKT-01..06 (FiscalCore)                     │
│   SAND-CZ   → MARK-01..06 (Честный ЗНАК module)                        │
│   SAND-UTM  → ALCO-01..05 (ЕГАИС module)                               │
│   SAND-CID  → ALCO-01..05 (ЕГАИС age check)                            │
│   FN-REP    → FISC-01..08, FfdVersionResolver, KKT-01..06              │
│   SQLC-*    → SEC-01..05 (SQLCipher integration)                       │
│   MTLS-*    → SEC-05 (TLS mTLS из v1.0)                                │
│   TOK-*     → AUTH-01..04 (auth flow)                                  │
│   LOAD-*    → Backend (D-01..05, Redis Streams)                         │
│   OFF-*     → TS-20..22 (offline capabilities)                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 6. ДОПОЛНИТЕЛЬНЫЕ ЗАМАНИЯ

### 6.1 Регуляторные акценты (54-ФЗ, ФФД)

| Аспект | Что проверить в v1.1 |
|--------|----------------------|
| **ФН replacement** | Причина перерегистрации = 12 (`change_fn`); ФФД-версия иммутабельна (per Finding 1 PITFALLS) |
| **ОФД delivery** | Sandbox-проверка, что все типы ФД (sell, refund, correction, Z) принимаются |
| **ЧЗ integration** | Синхронная валидация КМ (per Finding 19) + offline-block |
| **ЕГАИС integration** | Синхронная валидация акта + offline-block на алкоголь |
| **mTLS** | Сертификат устройства — способ аутентификации в ОФД/ЦРПТ по 54-ФЗ приказам |
| **Audit log** | Все операции (продажа, возврат, замена ФН, смена ключа) — в audit log для ФНС-проверок |

### 6.2 Сложность и приоритеты

| Группа | Высокая сложность (🔴) | Приоритет |
|--------|----------------------|-----------|
| SAND-OFD, SAND-CZ, SAND-UTM, SAND-CID | Все sandbox-интеграции | **P0** — без них нельзя пройти acceptance |
| FN-REP | ФН replacement | **P0** — операционная необходимость |
| LOAD-200+ | Нагрузочный тест | **P0** — без него не знаем, держит ли backend |
| OFF-24h | Offline stress | **P1** — критично, но локально |
| SQLC | Key rotation | **P1** — плановая процедура |
| MTLS | Cert management | **P1** — security critical |
| TOK | Token rotation | **P1** — стандартный OAuth flow |

### 6.3 Типовой порядок реализации

```
Этап 1: Sandbox-интеграции
  → SAND-OFD (высший приоритет — все 4 типа ФД)
  → SAND-CZ (маркировка)
  → SAND-UTM (ЕГАИС)
  → SAND-CID (возрастной контроль)

Этап 2: Операционные сценарии
  → FN-REP (UI flow + проверки)
  → SQLC (rotation + crash-recovery тест)
  → MTLS (cert management + hot-reload)
  → TOK (refresh/revocation)

Этап 3: Stress / Load
  → OFF-24h (локально, на эмуляторе)
  → LOAD-200+ (на staging backend)
  → MTLS-T + TOK-T (mock servers)

Этап 4: Observability
  → OP-* (status bar, alerts, diagnostics)
  → ZDR-* (zero-downtime rotation)
```

### 6.4 Зависимости от v1.0 (карта)

| v1.1 фича | Критичные v1.0 зависимости | Неблокирующие v1.0 зависимости |
|-----------|----------------------------|----------------------------------|
| SAND-OFD | FISC-01..08, KKT-01..06 | MON-01..06 (status ОФД) |
| SAND-CZ | MARK-01..06 | UPDT-01 (auto-update ЧЗ API) |
| SAND-UTM | ALCO-01..05 | — |
| SAND-CID | ALCO-01..05 | — |
| FN-REP | FISC-01..08, KKT-01..06, FfdVersionResolver | LIC-01..03 (license при простое), AUTH (admin role) |
| SQLC | SEC-01..05 (SQLCipher) | Audit (AUTH-04) |
| MTLS | SEC-05 (mTLS) | UPDT-01 (cert distribution) |
| TOK | AUTH-01..04 | SEC-05 (secure storage) |
| LOAD | Backend (D-01..05, Redis Streams) | MON-01..06 |
| OFF-24h | TS-20..22, FISC-01..08 | LIC-42 (grace period) |

---

## Quality Gate Checklist

- [x] Категории чёткие (sandbox, operational, stress, differentiators, anti-features)
- [x] Сложность отмечена для каждой функции (🟢/🟡/🔴)
- [x] Зависимости между v1.1-функциями и v1.0-функциями идентифицированы
- [x] Sandbox-специфичные флоу описаны (что песочница даёт, что возвращает)
- [x] Регуляторные акценты 54-ФЗ/ФФД отмечены (FN-REP, SAND-*, mTLS)
- [x] Anti-features обоснованы
- [x] Приоритизация по этапам реализации (P0/P1)

---

## Сводка v1.0 → v1.1

| Что было в v1.0 | Что добавляется в v1.1 | Эффект |
|------------------|-------------------------|--------|
| FiscalCore + ККТ адаптеры | Sandbox-тесты против ОФД | **Подтверждение ФФД-совместимости** |
| MARK модуль в коде | Sandbox-тесты против ЧЗ | **Подтверждение маркировки в проде** |
| ALCO модуль в коде | Sandbox/mock-УТМ + Цифровой ID Max | **Подтверждение ЕГАИС-цепочки** |
| SQLCipher интеграция | Key rotation flow + crash-recovery test | **Защита от потери ключа** |
| mTLS в OkHttp | Cert management + hot-reload | **Zero-downtime rotation** |
| OAuth implicit (если был) | Refresh/revocation flow | **Управляемый token lifecycle** |
| Backend 200+ касс (D-04) | Нагрузочный тест с метриками | **Подтверждение SLA** |
| Offline 24ч claim | Стресс-тест 24ч с assertions | **Доказательство offline-готовности** |
| — | ФН replacement flow | **Операционный сценарий** |

---

*Документ подготовлен на основе: PROJECT.md v1.1, FEATURES.md v1.0, SUMMARY.md v1.0, отраслевых best-practices для фискального ПО в РФ (54-ФЗ, ФФД 1.05/1.2, sandbox-провайдеры).*  
*Дата генерации: 2026-06-21*
