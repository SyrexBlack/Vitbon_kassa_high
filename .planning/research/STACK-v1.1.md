# STACK.md — v1.1 Production Readiness (VITBON Mobile Cash Register)

**Версия:** 1.1
**Дата:** 2026-06-21
**Статус:** Дополнение к v1.0 STACK — только новые компоненты для hardening/sandbox/load testing
**Базовый документ:** `.planning/research/STACK.md` (v1.0, 2026-06-20) — НЕ пересматривается здесь
**Потребитель:** requirements definition (v1.1) → roadmap planning
**Confidence scale:** 🟢 High (production-proven), 🟡 Medium (reliable but with caveats), 🔴 Low (emerging/declining)

---

## Предусловия

Все v1.0 решения остаются в силе (см. `STACK.md` от 2026-06-20):
- Kotlin `1.9.22` + Compose BOM `2024.01.00` + Room `2.6.1` + SQLCipher `4.5.4`
- Spring Boot `3.2.2` + PostgreSQL `16` + Redis Streams
- Android Keystore для секретов, WorkManager для sync
- FiscalCore интерфейс + MSPOS-K/Нева 01Ф адаптеры
- FeatureManager runtime gating

v1.1 **дополняет** стек следующими компонентами:

---

## 1. Sandbox-окружения (ОФД / ЧЗ / УТМ / Цифровой ID Max)

### 1.1 ОФД sandbox

| | |
|---|---|
| **Endpoint (типовая конфигурация)** | `sandbox-api.ofd.ru/v1/` или аналог конкретного ОФД-провайдера (Taxcom, OFD.ru, Platforma OFD, 1С-OFD, Kontur) |
| **Credentials** | Тестовые ИНН, ОГРН, ФН, device-id, group-id — НЕ боевые |
| **Активация** | License flag `SANDBOX_OFD_OPT_IN` (per-cash-register) И BuildConfig `ENVIRONMENT=sandbox` (defense-in-depth) |
| **Confidence** | 🟡 Medium — точные URL/Credentials получаем при заключении договора с ОФД-провайдером |
| **Зачем** | Без боевого ОФД нельзя прогнать end-to-end fiscal flow: smoke-check → OФД → подтверждение → fetch статуса. Без sandbox тестов SAND-OFD-01..06 невыполнимы. |
| **Интеграция с v1.0** | Backend controller `OfdProxyController` (Spring Boot) переключается по `application-sandbox.yml`. На Android: `BuildConfig.OFD_BASE_URL` + `LicenseContext.sandboxEnabled`. |
| **What NOT to use** | Боевой ОФД в CI — даже для smoke. Никогда не мокать ОФД внутри FiscalCore (см. PITFALLS-v1.1 §P1.1: sandbox test fixtures leak via FiscalCoreFactory). |

### 1.2 Честный ЗНАК sandbox (ГС1/ЦРПТ)

| | |
|---|---|
| **Endpoint** | `sandboxapi.crpt.ru` (или `api.crpt.ru:8002` test) + тестовые ИНН/ОГРН + token-OMSID |
| **Активация** | License flag `SANDBOX_CHZ_OPT_IN` + BuildConfig `ENVIRONMENT=sandbox` |
| **Confidence** | 🟡 Medium — ЦРПТ выдаёт доступ по заявке, требуется договор с ГС1/ЦРПТ |
| **Что покрывает** | SAND-CZ-01..06: DataMatrix валидация, выбытие, приёмка, offline-блок, обработка невалидных КМ |
| **Интеграция с v1.0** | Backend `ChaseznakController` уже существует (v1.0, Phase F). Добавить профиль `application-sandbox.yml` с тестовыми ключами подписи. |
| **What NOT to use** | Mock-DataMatrix без прогона через ЦРПТ-API — сломает валидацию (см. PITFALLS-v1.1 §P2.5 sandbox license divergence). |

### 1.3 УТМ ЕГАИС (testbed)

| | |
|---|---|
| **Endpoint (типовой подход)** | Локальный mock УТМ в Docker (`utm-mock:latest`) ИЛИ удалённый testbed ЕГАИС |
| **CA pin** | Самоподписанный CA из `utm-mock` (НЕ АПТ ФСРАР production CA) — важно не перепутать, иначе УТМ-CLIENT упадёт (PITFALLS-v1.1 §P1.7) |
| **Активация** | License flag `SANDBOX_UTM_OPT_IN` + BuildConfig `ENVIRONMENT=sandbox` |
| **Confidence** | 🟡 Medium — ФСРАР не предоставляет публичного sandbox; типично используют community-maintained mock |
| **Что покрывает** | SAND-UTM-01..05: акт продажи, ошибки, таймауты, версионность УТМ |
| **Интеграция с v1.0** | Backend `EgaisController` (v1.0, Phase F) + `EgaisService`. На Android: `EgaisRemoteDataSource.baseUrl` берётся из BuildConfig. |
| **What NOT to use** | Production ФСРАР CA на test-сборках. Mock без правильного формата ТТН/Акта — УТМ вернёт generic-ошибку, теряем сигнал. |

### 1.4 Цифровой ID Max (sandbox)

| | |
|---|---|
| **Endpoint** | `sandbox-api.max.ru/v1/id/` или аналог (зависит от VK/CID Max API) |
| **Credentials** | Тестовые API-key + тестовый возраст пользователя (`age: 17` — запрещено, `age: 25` — разрешено) |
| **Активация** | License flag `SANDBOX_CID_MAX_OPT_IN` + BuildConfig `ENVIRONMENT=sandbox` |
| **Confidence** | 🟡 Medium — VK API для CID Max может менять endpoint'ы; требуется верификация по официальной документации |
| **Что покрывает** | SAND-CID-01..05: age check (разрешить/запретить), token-bucket, отзыв сессии, обработка ошибок API |
| **Интеграция с v1.0** | Новый backend `CidMaxController` (v1.1). Android-клиент: `CidMaxRemoteDataSource` (новый модуль `:feature-cid-max`). |
| **What NOT to use** | Hardcoded mock-ответы без прохода через sandbox — потеряем валидацию API-контракта. |

---

## 2. Load Testing Infrastructure (200+ cash registers)

### 2.1 Load Test Tooling

| | |
|---|---|
| **Инструмент** | **k6** (Grafana k6 v0.49+) |
| **Язык сценариев** | JavaScript ES6 (k6 scripting) |
| **Confidence** | 🟢 High — k6 стандарт де-факто для HTTP-load testing, активно поддерживается Grafana |
| **Почему k6, не JMeter** | JMeter — Java-GUI, медленный старт, плохо версионируется в Git. k6 — Go-runtime, скрипты в JS, нативный Docker-режим, лучше для CI. |
| **Почему не Gatling** | Gatling хорош, но Scala-DSL создаёт порог входа; для команды v1.1 (Android + Spring backend) JavaScript-скрипты привычнее. |
| **Где запускается** | CI-stage `loadtest-200cashes.yml` (GitLab/GitHub Actions), выделенная VM с 4 vCPU / 8 GB RAM |
| **Лицензия** | Apache 2.0 (open source). Enterprise-фичи (cloud, distributed) — out of scope. |

### 2.2 Метрики и Observability

| | |
|---|---|
| **Metrics collection** | **Prometheus** (`micrometer-registry-prometheus` для Spring Boot) |
| **Backend metrics endpoint** | `GET /actuator/prometheus` (Spring Boot Actuator) |
| **Visualization** | **Grafana** (open source, on-premise) — дашборды: P95/P99 latency, throughput, error rate, Redis lag |
| **Confidence** | 🟢 High — стандарт для Spring Boot приложений |
| **Ключевые метрики для v1.1** | `http_server_requests_seconds{quantile="0.95"}`, `redis_stream_lag`, `hikaricp_connections_usage`, `jvm_memory_used_bytes`, `fiscal_document_processed_total` |
| **Alerts** | Prometheus Alertmanager → PagerDuty (или Telegram webhook для разработки) |

### 2.3 Load Test Harness (Docker Compose)

| | |
|---|---|
| **Файл** | `infra/loadtest/docker-compose.yml` |
| **Компоненты** | `k6` (load generator) + `mock-ofd` (1 контейнер) + `mock-chz` (1 контейнер) + `mock-utm` (1 контейнер) + `nginx-rate-limit` (для имитации 429-ответов) |
| **Сетевая конфигурация** | Изолированная `loadtest_net` Docker network, лимиты по CPU/RAM (имитация реального backend'а) |
| **Confidence** | 🟢 High — стандартный паттерн reproducible load test environment |
| **Why** | Без воспроизводимого harness'а 200 cash scenario будет flaky (разные моки → разные результаты). |
| **What NOT to use** | Запуск load test против shared dev-staging'а — соседние разработчики получают DOS. |

### 2.4 TLS / mTLS для Load Test

| | |
|---|---|
| **Cert issuance** | **mkcert** (v1.4.4+) для dev-среды + внутренний CA скрипт (`infra/loadtest/gen-certs.sh`) |
| **Cert format** | `client.p12` (PKCS#12) + `server.crt` + `server.key` |
| **Confidence** | 🟢 High — mkcert стандарт для dev-TLS |
| **Why** | Production cert от Let's Encrypt не использовать в load test — нерационально, и revocation сломает все тесты разом. |
| **What NOT to use** | Self-signed без `CertificatePinner` bypass в k6 — connection refused на каждом запросе. |

---

## 3. SQLCipher Key Rotation Tooling

### 3.1 Key Rotation Algorithm

| | |
|---|---|
| **Метод** | `PRAGMA rekey` (SQLCipher 4.5.4 native) — атомарная перешифровка всех страниц БД |
| **Когда ротировать** | Каждые 90 дней (настраивается) + ручной триггер из admin-панели |
| **Confidence** | 🟢 High — `PRAGMA rekey` production-proven в SQLCipher с 4.0 |
| **Реализация** | Транзакция Room + `db.openHelper.writableDatabase.execSQL("PRAGMA rekey = '...'")` |
| **Crash-recovery** | До rotation: `sqlcipher_export()` в sidecar-файл, atomic swap после успеха |
| **Where** | `core/security/SqlCipherKeyRotator.kt` (новый компонент, см. ARCHITECTURE-v1.1 §2) |

### 3.2 Key Storage

| | |
|---|---|
| **Хранение** | Android Keystore (`MasterKey` + `EncryptedSharedPreferences`) — уже валидировано в v1.0 (SEC-01) |
| **Backup** | Текущий ключ дублируется в encrypted-backup файл (rotation откатывается при crash) |
| **Confidence** | 🟢 High — `androidx.security:security-crypto:1.1.0-alpha06` (или stable) уже в v1.0 |
| **What NOT to use** | SharedPreferences plain text (запрещено SEC-01). Keystore без hardware backing (на старых устройствах — `StrongBox` fallback). |

### 3.3 Audit Integration

| | |
|---|---|
| **Audit table** | `audit_events` (уже существует в v1.0) — новая категория `KEY_ROTATION` |
| **Поля** | `old_key_alias`, `new_key_alias`, `timestamp`, `initiator` (operator PIN), `result` (success/rolled-back) |
| **Confidence** | 🟢 High — используем существующую audit-инфраструктуру v1.0 |

---

## 4. ФН (Fiscal Storage Unit) Replacement Tooling

### 4.1 ФН Diagnostics

| | |
|---|---|
| **Инструмент** | `fiscalcore-diag` Android binary (только для service-engineer'ов, debug build) |
| **Что показывает** | Состояние ФН (заполненность %, ФФД-версия, expiry date, серийный номер, ресурс) |
| **Confidence** | 🟡 Medium — специфика производителей ФН (MSPOS-K, Нева 01Ф) различается |
| **Где** | `tools/fiscalcore-diag/` отдельный Gradle module, не входит в release APK |
| **Why** | Без диагностики оператор не знает, когда ФН близок к заполнению. Без ручного «что внутри ФН» инженер не может принять решение о замене. |

### 4.2 ФН Replacement Flow

| | |
|---|---|
| **Backend support** | Существующие `FnRegistrationController` + `FnRegistrationService` (v1.0, Phase 7 GAP-01 closure) |
| **UI** | `FnReplaceScreen` (новый, Compose) — пошаговый wizard: «закрыть смену → выключить ККТ → заменить ФН → зарегистрировать → новый ФН готов» |
| **Confidence** | 🟡 Medium — flow зависит от ККТ-производителя (MSPOS-K, Нева 01Ф) |
| **What NOT to use** | UI без явного «старый ФН закрыт, архив отправлен в ОФД» step. Пропуск этого шага = потеря архива (PITFALLS-v1.1 §P1.5). |

---

## 5. Mutual TLS (mTLS) Certificate Management

### 5.1 Certificate Storage

| | |
|---|---|
| **Хранение private key** | Android Keystore (если устройство поддерживает) + EncryptedSharedPreferences fallback |
| **Cert format** | X.509 PEM для server, PKCS#12 (`.p12`) для client |
| **Confidence** | 🟢 High — стандарт TLS-стека Android |
| **What NOT to use** | Bouncy Castle напрямую (overhead). Хранение `.p12` plain на filesystem (SEC-04 нарушение). |

### 5.2 OkHttp mTLS Hot-Reload

| | |
|---|---|
| **Реализация** | `RotatingKeyManager` (singleton) — synchronized swap `X509KeyManager` без пересоздания `OkHttpClient` |
| **Библиотека** | `com.squareup.okhttp3:okhttp:4.12.0` (уже в v1.0) — `SSLSocketFactory` создаётся с `KeyManager[]` массивом, замена KeyManager подхватывается при следующем handshake |
| **Confidence** | 🟡 Medium — это custom-обёртка, требует тестирования на race conditions |
| **Why custom** | Native OkHttp `KeyManager` не поддерживает hot-reload; стандартный подход — пересоздание `OkHttpClient`, но это рвёт текущие соединения. |
| **Where** | `core/network/TlsConfigProvider.kt` (новый, см. ARCHITECTURE-v1.1 §2) |

### 5.3 Cert Rotation Tooling

| | |
|---|---|
| **Backend CA** | Spring Boot + Bouncy Castle (`bcpkix-jdk18on:1.78`) — внутренний CA для клиентских сертификатов |
| **Cert issuance** | REST API: `POST /api/v1/certs/issue` с CSR (PKCS#10) → возврат `.p12` |
| **Cert validity** | 90 дней (настраивается) |
| **Cert metadata в Android** | `EncryptedSharedPreferences` — `cert_alias`, `cert_expiry`, `cert_serial` |
| **Confidence** | 🟢 High — стандартный PKI flow |

### 5.4 OCSP / CRL (опционально для v1.1)

| | |
|---|---|
| **Реализация** | OkHttp `CertificatePinner` + `CertificateChainCleaner` с OCSP check (опционально) |
| **Confidence** | 🔴 Low — OCSP stapling требует server-side support; CRL может быть медленным |
| **Decision для v1.1** | НЕ включаем OCSP/CRL по умолчанию. Cert rotation с overlap-period (cert_old + cert_new одновременно) покрывает 90% revocation-cases. |
| **Why not** | PITFALLS-v1.1 §P3.3: OCSP stapling adds complexity for marginal benefit. |

---

## 6. Auth Token Rotation / Revocation

### 6.1 Token Storage

| | |
|---|---|
| **Хранение** | `EncryptedSharedPreferences` (`androidx.security:security-crypto:1.1.0-alpha06`) — уже в v1.0 |
| **Tokens** | `access_token` (TTL 15 мин), `refresh_token` (TTL 7 дней) |
| **Confidence** | 🟢 High — стандарт Android security-crypto |

### 6.2 Refresh Flow

| | |
|---|---|
| **Refresh trigger** | OkHttp `Authenticator` (auto-refresh при 401) + WorkManager `PeriodicWorkRequest` каждые 5 мин для background-refresh |
| **Refresh endpoint** | `POST /api/v1/auth/refresh` (Spring Boot, OAuth2 RFC 6749 compliant) |
| **Confidence** | 🟢 High — стандарт OAuth2 |

### 6.3 Revocation Propagation

| | |
|---|---|
| **Server-side** | `POST /api/v1/auth/revoke` инвалидирует refresh_token в БД + Redis blacklist для access_token (TTL = access_token TTL) |
| **Client-side detection** | При 401 + `WWW-Authenticate: Token revoked` → forced re-login (НЕ auto-refresh) |
| **Push propagation (опционально)** | SSE (`GET /api/v1/auth/events`) — сервер шлёт `token_revoked` event; клиент чистит токены и редиректит на login |
| **Fallback (без SSE)** | Polling `GET /api/v1/auth/token-status` каждые 60 сек |
| **Confidence** | 🟡 Medium — SSE требует long-lived HTTP connection; fallback polling проще, но менее reactive |
| **Decision для v1.1** | Polling-based (60 сек). SSE — в v1.2 если потребуется. |

### 6.4 Mock OAuth Server для E2E тестов

| | |
|---|---|
| **Инструмент** | `oauth2-mock-server` (Java/embedded, MIT license) |
| **Endpoints** | `/token`, `/revoke`, `/introspect` — RFC 6749 compliant |
| **Confidence** | 🟢 High — стандарт для OAuth2 E2E |
| **Где** | `test/oauth2-mock-server/` Gradle module, используется в `MTLS-T-*` и `TOK-T-*` тестах |

---

## 7. 24-Hour Offline Stress Test Tooling

### 7.1 Network Simulation

| | |
|---|---|
| **Инструмент** | `androidx.test:core:1.5.0` + `androidx.work:work-testing:2.9.0` + `Robolectric` (4.11.1+) |
| **Airplane mode** | `WifiManager.setWifiEnabled(false)` + `ConnectivityManager` mock |
| **Confidence** | 🟢 High — Robolectric стандарт для Android JVM-тестов |
| **Time fast-forward** | `Shadows.systemClock.advanceBy(Duration.ofHours(24))` — но **только в Robolectric**, в эмуляторе не работает |
| **What NOT to use** | Espresso + реальный airplane mode — слишком медленно (24 часа) |

### 7.2 Doze Mode Bypass

| | |
|---|---|
| **Инструмент** | `adb shell dumpsys battery unplug` + `adb shell dumpsys deviceidle whitelist +<package>` |
| **Где** | `OFF-12` test setup (24-час offline + restore) |
| **Confidence** | 🟡 Medium — Doze behavior меняется между Android-версиями; тест чувствителен к эмулятор-конфигу |
| **What NOT to use** | `PowerManager.isInteractive = true` mock в production-коде — нельзя, иначе реальные кассы потеряют sync (PITFALLS-v1.1 §P1.3). |

### 7.3 Storage / RAM Assertions

| | |
|---|---|
| **БД size** | `File(app.databasesDir, "vitbon.db").length()` — assert < 100 MB |
| **Queue depth** | `RoomDb.pendingSyncDao().count()` — assert < 500 |
| **RAM** | `Debug.getMemoryInfo()` — assert < 200 MB |
| **Confidence** | 🟢 High — простые file/Room API |
| **What NOT to use** | Android Profiler в CI — нет UI, нельзя автоматизировать. |

---

## 8. Security Hardening Tooling

### 8.1 Certificate Pinning

| | |
|---|---|
| **Библиотека** | `okhttp` built-in `CertificatePinner` (уже в v1.0) |
| **Pins** | SHA-256 fingerprint backup-pin + primary-pin (для rotation overlap) |
| **Confidence** | 🟢 High — production standard |
| **What NOT to use** | `TrustManager` который accepts all (security anti-pattern). |

### 8.2 Root Detection Hardening

| | |
|---|---|
| **Библиотека** | `RootBeer` (v0.1.0) — расширение v1.0 `RootRiskGuard.kt` |
| **Дополнительные indicators** | Magisk Manager, Xposed Framework, Frida server, busybox в PATH |
| **Confidence** | 🟡 Medium — root detection — это гонка вооружений; `RootBeer` ловит 90% случаев |
| **Why** | v1.0 уже имеет `RootRiskGuard`; v1.1 расширяет indicator coverage. |
| **What NOT to use** | Активное «выключение» кассы при root — ломает support-сценарии (инженер с root-инструментами). Soft-warning + audit-log достаточно. |

### 8.3 Audit Log Hardening

| | |
|---|---|
| **Storage** | `LocalAuditBufferRepository` (уже в v1.0) + `EncryptedSharedPreferences` для метаданных |
| **Append-only** | Room trigger `BEFORE UPDATE` → `RAISE(ABORT, 'audit immutable')` |
| **Confidence** | 🟢 High — стандарт fiscal compliance (54-ФЗ) |
| **What NOT to use** | Logs в cloud-only — нарушение SEC-01 (offline-first fiscal integrity). |

---

## 9. CI / Pipeline Additions

### 9.1 Sandbox test stage

| | |
|---|---|
| **Trigger** | `merge_request` → `develop` branch (NOT `master`) |
| **Steps** | Start docker-compose sandbox → run E2E SAND-* tests → teardown |
| **Timeout** | 30 мин max |
| **Confidence** | 🟢 High — стандарт CI pattern |

### 9.2 Load test stage

| | |
|---|---|
| **Trigger** | `nightly` cron + manual на `release/*` branches |
| **Steps** | Spin up dedicated VM (4 vCPU / 8 GB) → run k6 scenario 200 cash × 30 мин → export metrics to Prometheus → compare to baseline |
| **Baseline storage** | `infra/loadtest/baseline.json` (committed) |
| **Threshold** | P95 latency regression > 20% → block release |
| **Confidence** | 🟡 Medium — baseline maintenance требует discipline |

### 9.3 24-hour offline stress stage

| | |
|---|---|
| **Trigger** | `nightly` cron (но результат — только next morning) |
| **Alternative** | Manual на dedicated test rig (24 часа реального времени) |
| **Confidence** | 🟡 Medium — 24-час тесты дорогие, запуск 1 раз / sprint |

---

## 10. Инструменты, которые НЕ добавляем в v1.1

| Tool | Почему НЕ |
|------|-----------|
| JMeter | k6 лучше для CI + JS-скрипты |
| Gatling | Scala-DSL — порог входа |
| Real ОФД / ЧЗ / УТМ / CID Max | Боевые endpoints только в production; sandbox обязателен для тестов |
| HashiCorp Vault | Overkill для cash register; EncryptedSharedPreferences + Keystore достаточно |
| OCSP / CRL stapling | Доп. сложность, marginal benefit (см. §5.4) |
| FCM (Firebase Cloud Messaging) | Vendor lock-in + privacy concerns; SSE/polling достаточно |
| Grafana Cloud / Datadog | Self-hosted Prometheus + Grafana достаточно |
| OWASP ZAP automated scan | Уже есть в Phase B (SEC-01..05); для v1.1 — точечные ручные пентесты |

---

## 11. Зависимости от v1.0 (обязательно учитывать)

| v1.0 компонент | Как v1.1 использует |
|----------------|---------------------|
| **FeatureManager** | Гейтит `SANDBOX_*_OPT_IN` (runtime switch для sandbox) |
| **Audit log** | Логирует `KEY_ROTATION`, `CERT_ROTATION`, `TOKEN_*` события |
| **FiscalCore interface** | Sandbox НЕ мокает внутри (PITFALLS-v1.1 §P1.1); sandbox ОФД — на уровне Backend proxy |
| **SyncManager** | Load test SAND-*-02/05 проверяет retry-логику SyncUpWorker под нагрузкой |
| **LicenseChecker** | 24-час offline тест OFF-10 валидирует grace period |
| **Compose UI** | Новые `FnReplaceScreen`, `KeyRotationScreen`, `CertStatusIndicator` — стандартный Compose-паттерн |
| **SQLCipher 4.5.4** | `PRAGMA rekey` — нативный, не требует новых библиотек |
| **OkHttp 4.12.0** | `Authenticator` + `CertificatePinner` — встроенные, не требуют новых зависимостей |
| **Spring Boot 3.2.2** | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` — добавляются в `build.gradle.kts` backend'а |

---

## 12. v1.0 Findings (1-19) — как влияют на v1.1 STACK

Из `SUMMARY.md` v1.0:

| Finding | v1.1 Stack implication |
|---------|------------------------|
| **#2 — SQLCipher non-negotiable** | Key rotation tooling (PRAGMA rekey) — нативный, без новых зависимостей |
| **#3 — WorkManager only reliable scheduler** | 24-час offline тест: WorkManager — must not be silently deferred (PITFALLS §P1.3) |
| **#7 — ЕГАИС/ЧЗ high-risk** | Sandbox testbed обязателен, real API — только production |
| **#9 — Cloud sync is differentiator** | Load test 200+ касс валидирует cloud-sync-устойчивость |
| **#11 — 5-layer architecture** | Sandbox / load test — out-of-process (`:testing`, `:loadtest`), не ломают layers |
| **#13 — FFD version immutable** | ФН replacement flow не должен менять FFD-версию (PITFALLS §P1.5) |
| **#19 — Synchronous regulatory calls** | Sandbox environment должен имитировать sync-семантику (не ускорять) |

---

## 13. Итоговый bill of materials (новое для v1.1)

### Android (новое)

```
// k6 для load test
// (НЕ в production APK; только test runner)

// SQLCipher key rotation — нативный, без новых зависимостей

// Mutual TLS — нативный OkHttp + custom RotatingKeyManager
implementation("com.squareup.okhttp3:okhttp:4.12.0")  // уже в v1.0

// OAuth2 token storage — EncryptedSharedPreferences
implementation("androidx.security:security-crypto:1.1.0-alpha06")  // уже в v1.0

// Root detection — расширение v1.0
implementation("com.scottyab:rootbeer:0.1.0")

// Тестовое
testImplementation("org.robolectric:robolectric:4.11.1+")
testImplementation("androidx.work:work-testing:2.9.0")
testImplementation("androidx.test:core:1.5.0")
```

### Backend (новое)

```kotlin
// Prometheus metrics
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("org.springframework.boot:spring-boot-starter-actuator")

// Cert issuance
implementation("org.bouncycastle:bcpkix-jdk18on:1.78")

// OAuth2 mock server (test)
testImplementation("com.github.tietang:oauth2-mock-server:1.3.2")
```

### Infrastructure (новое)

```yaml
# infra/loadtest/docker-compose.yml
# - k6 (latest)
# - nginx-rate-limit
# - mock-ofd (custom image)
# - mock-chz (custom image)
# - mock-utm (community image)
# - prometheus (scrape config для backend metrics)
# - grafana (loadtest dashboard)
```

---

## 14. Метрики «stack-готовности» к v1.1

- [ ] k6 scenario запускается в CI, baseline latency < 500ms P95
- [ ] SQLCipher `PRAGMA rekey` работает в unit-тесте без crash-recovery
- [ ] OkHttp mTLS handshake проходит с self-signed cert
- [ ] OAuth2 mock server отвечает на `/token`, `/revoke`
- [ ] 24-час offline Robolectric-тест проходит за < 5 мин JVM-времени
- [ ] Sandbox ОФД endpoint резолвится (mock или реальный)
- [ ] Prometheus `/actuator/prometheus` отдаёт `http_server_requests_seconds`
- [ ] RootBeer не падает на стандартном эмуляторе (true-negative baseline)

---

*Этот документ — дополнение к v1.0 STACK.md. Версии и зависимости могут уточняться при requirements/roadmap-этапах.*
