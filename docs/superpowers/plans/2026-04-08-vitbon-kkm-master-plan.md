# Vitbon ККМ — Master Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement each phase. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Полноценное Android-приложение кассы VITBON для РФ (54-ФЗ, MSPOS-K / Нева 01Ф, ЕГАИС, ЧЗ, 200+ касс)

**Architecture:** Hybrid offline-first — каждый терминал автономен (Room/SQLite), бэкенд — центр синхронизации (Kotlin/Spring Boot 3)

**Tech Stack:** Kotlin 1.9, Jetpack Compose, Room + SQLCipher, WorkManager, Hilt, Spring Boot 3, PostgreSQL, Redis Streams

---

## Фаза 1: Проектирование (архитектура + модели данных)

**Spec:** `docs/superpowers/specs/2026-04-08-vitbon-kkm-design.md`

### Task 1.1: Схема моделей данных

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__init_schema.sql`
- Create: `android/app/src/main/java/com/vitbon/kkm/data/local/entity/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/data/local/dao/*.kt`

**Backend schema:**
```sql
-- cashiers: id, name, pin_hash, role (ADMIN/SENIOR_CASHIER/CASHIER), created_at
-- shifts: id, cashier_id, device_id, opened_at, closed_at, total_cash, total_card
-- checks: id, local_uuid, shift_id, type (SALE/RETURN/CORRECTION/CASH_IN/CASH_OUT),
--          fiscal_sign, ofd_response, ffd_version, status, created_at, synced_at
-- check_items: id, check_id, product_id, name, quantity, price, discount, vat_rate, total
-- products: id, barcode, name, article, price, vat_rate, category_id, stock, egais_flag, chaseznak_flag
-- categories: id, name, parent_id
-- audit_log: id, cashier_id, action, details, timestamp
```

**Android Room entities:** `LocalCashier`, `LocalShift`, `LocalCheck`, `LocalCheckItem`, `LocalProduct`, `LocalCategory`, `AuditLogEntry`

---

### Task 1.2: REST API спецификация

**Files:**
- Create: `backend/src/main/kotlin/com/vitbon/kkm/api/v1/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/data/remote/api/*.kt`

```kotlin
// Auth
POST /api/v1/auth/login        // { pin: String } → { token, cashier }
POST /api/v1/auth/logout       // logout, invalidate token

// Checks
POST /api/v1/checks/sync       // batch upload: List<LocalCheck>
GET  /api/v1/checks?shiftId=&date=  // download checks (read replica)

// Products
GET  /api/v1/products?since=   // delta sync since timestamp
POST /api/v1/products/sync     // receive full catalog (for initial sync)

// Documents
POST /api/v1/documents/acceptance
POST /api/v1/documents/writeoff
POST /api/v1/documents/inventory

// Shifts
GET  /api/v1/shifts/{cashierId}
POST /api/v1/shifts            // open shift
PUT  /api/v1/shifts/{id}/close // close shift

// Reports
GET  /api/v1/reports/sales?from=&to=&cashierId=
GET  /api/v1/reports/movement?from=&to=&productId=
GET  /api/v1/reports/returns?from=&to=

// Status
GET  /api/v1/statuses          // ofd queue length, last sync, license status

// Licensing
POST /api/v1/license/check     // { deviceId } → { status, expiryDate, graceUntil }

// ЕГАИС (optional)
POST /api/v1/egais/incoming
POST /api/v1/egais/tara

// ЧЗ (optional)
POST /api/v1/chaseznak/sell
POST /api/v1/chaseznak/verify-age
```

---

### Task 1.3: Схема синхронизации

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/sync/SyncManager.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/sync/SyncDownWorker.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/sync/SyncUpWorker.kt`

```
SyncUp (push чеки):
  1. Check → Room (status=PENDING_SYNC)
  2. WorkManager SyncUpWorker → POST /api/v1/checks/sync
  3. On 200 OK → update status=SYNCED, synced_at
  4. On failure → retry with exponential backoff (max 5)

SyncDown (pull товары):
  1. WorkManager SyncDownWorker → GET /api/v1/products?since={lastSyncTs}
  2. Merge: server wins by updated_at
  3. Save to Room
  4. Interval: 30 sec when online

Conflict resolution: server_wins (updated_at)
Offline buffer: max 10 000 чеки
```

---

## Фаза 2: Ядро ККТ — MSPOS-K / Нева 01Ф SDK

### Task 2.1: FiscalCore интерфейс

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/FiscalCore.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/model/*.kt`

```kotlin
interface FiscalCore {
    suspend fun openShift(): FiscalResult
    suspend fun printSale(check: FiscalCheck): FiscalResult
    suspend fun printReturn(check: FiscalCheck): FiscalResult
    suspend fun printCorrection(doc: CorrectionDoc): FiscalResult
    suspend fun closeShift(): FiscalResult
    suspend fun printXReport(): FiscalResult
    suspend fun cashIn(amount: Money, comment: String?): FiscalResult
    suspend fun cashOut(amount: Money, comment: String?): FiscalResult
    suspend fun getStatus(): FiscalStatus
    suspend fun getFFDVersion(): FFDVersion  // "1.05" or "1.2"
}

sealed class FiscalResult {
    data class Success(val fiscalSign: String, val fnNumber: String) : FiscalResult()
    data class Error(val code: Int, val message: String) : FiscalResult()
}

enum class FFDVersion { V1_05, V1_2 }
```

---

### Task 2.2: MSPOS-K реализация FiscalCore

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKFiscalCore.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKCommand.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/msposk/MSPOSKResponse.kt`

**SDK:** Использовать MSPOS-K SDK (Android AAR от производителя). Обёртка должна:
1. Инициализировать SDK при старте приложения
2. Объединять команды SDK в `FiscalCore`-интерфейс
3. Маппить FFD-версию из SDK в `FFDVersion`
4. Обрабатывать ошибки (timeout 30 сек, retry при transient errors)

---

### Task 2.3: Нева 01Ф реализация FiscalCore

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/neva/Neva01FFiscalCore.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/neva/Neva01FCommand.kt`

**SDK:** Использовать Нева 01Ф SDK. Аналогичная обёртка.

---

### Task 2.4: FFD-адаптер (1.05 / 1.2)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilder.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/ffd/tag/*.kt`

```kotlin
class FiscalDocumentBuilder(private val version: FFDVersion) {
    fun buildSale(items: List<CheckItem>, payment: Payment, customer: Customer?): FiscalDocument
    fun buildReturn(originalCheckId: String, items: List<CheckItem>): FiscalDocument
    fun buildCorrection(type: CorrectionType, reason: String, amounts: Amounts): FiscalDocument
    fun buildCashIn(amount: Money, comment: String?): FiscalDocument
    fun buildCashOut(amount: Money, comment: String?): FiscalDocument
}
```

Теги ФФД 1.05: базовый набор (теги 1000-1080)
Теги ФФД 1.2: + теги 1125, 1187, 1008, 1234-1238

---

## Фаза 3: Базовый функционал кассира

### Task 3.1: Авторизация (пин-код)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/auth/data/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/auth/domain/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/auth/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/auth/presentation/Screen.kt`

```kotlin
@Composable
fun AuthScreen(
    onAuthSuccess: (Cashier) -> Unit,
    onAdminMode: () -> Unit
)
```

Экран: поле ввода 4-6-значного пина, кнопка входа. Ошибка — вибрация + сообщение.
Роль ADMIN → доступны настройки.

---

### Task 3.2: Продажа товара

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/sales/presentation/SalesScreen.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/sales/presentation/SalesViewModel.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/sales/domain/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/sales/domain/UseCase*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/sales/data/CheckoutRepository.kt`

**UI (Jetpack Compose):**
```
┌──────────────────────────────────┐
│  Кассир: Иванов  │  Смена: 12   │ ← статус-бар
├──────────────────────────────────┤
│  🔍 Поиск / сканер ШК           │
├──────────────────────────────────┤
│  Товар A     ×2    150₽   [✕]   │
│  Товар B     ×1    99₽    [✕]   │
│  ─────────────────────────────────│
│  Скидка 5%            -12₽       │
├──────────────────────────────────┤
│  ИТОГО: 237₽                     │
│  НДС 22%: 43₽                   │
├──────────────────────────────────┤
│  [💵 Наличные] [💳 Карта] [📱QR] │
│  [    ПРОДАТЬ    ]               │
└──────────────────────────────────┘
```

**Use Cases:**
- `ScanBarcodeUseCase` → найти товар → добавить в корзину
- `UpdateCartItemUseCase` → изменить количество / цену / скидку
- `ApplyGlobalDiscountUseCase` → % или сумма на весь чек
- `ProcessSaleUseCase` → сформировать FiscalDocument → FiscalCore → Room → Sync

**VatRate:** 22%, 10%, 0%, 5%, 7%, БЕЗ НДС — enum

---

### Task 3.3: Возврат товара

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/returns/presentation/ReturnScreen.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/returns/presentation/ReturnViewModel.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/returns/domain/*.kt`

**Поток:**
1. Сканировать QR-код чека ИЛИ ввести номер чека
2. Загрузить чек из Room (local) или с сервера
3. Выбрать позиции для возврата (чекбоксы)
4. Подтвердить сумму
5. `FiscalCore.printReturn()` → фискальный чек возврата
6. Сохранить в Room, синхронизировать

---

### Task 3.4: Чеки коррекции

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/correction/presentation/CorrectionScreen.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/correction/domain/*.kt`

```kotlin
@Composable
fun CorrectionScreen() {
    // Тип: ПРИХОД / РАСХОД
    // Основание (текст)
    // Номер чека коррекции (авто или вручную)
    // Суммы: нал, безнал
    // Кнопка "ПРОДОЛЖИТЬ"
    // → FiscalCore.printCorrection()
}
```

---

### Task 3.5: Смены (открытие / закрытие) + X-отчёт

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/shift/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/shift/domain/*.kt`

**Алгоритм старта (из ТЗ):**
```
1. Запросить getStatus() от FiscalCore
2. Если смена открыта И <24ч → OK, продолжить
3. Если смена открыта И >24ч → предложить закрыть смену
4. Если смена закрыта → кнопки "Открыть смену" / "Продолжить"
5. При первом фискальном чеке (если смена закрыта) → автооткрытие
```

---

### Task 3.6: Внесение / изъятие

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/cashdrawer/presentation/*.kt`

```kotlin
@Composable
fun CashDrawerScreen() {
    // Вкладки: ВНЕСЕНИЕ | ИЗЪЯТИЕ
    // Сумма (NumberPad)
    // Комментарий (опционально)
    // → FiscalCore.cashIn() / cashOut()
}
```

---

## Фаза 4: Товароучёт + облачная синхронизация

### Task 4.1: Локальная база товаров (Room)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/products/data/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/products/presentation/*.kt`

```kotlin
@Entity(tableName = "products")
data class LocalProduct(
    @PrimaryKey val id: String,
    val barcode: String?,
    val name: String,
    val article: String?,
    val price: Long,  // копейки
    val vatRate: VatRate,
    val categoryId: String?,
    val stock: Long,   // для весового: граммы
    val egaisFlag: Boolean,
    val chaseznakFlag: Boolean,
    val updatedAt: Long
)
```

---

### Task 4.2: SyncManager + WorkManager

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/sync/*.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` — добавить WorkManager providers

**WorkManager constraints:**
```kotlin
SyncUpWorker:
  constraints: NetworkType.CONNECTED
  backoffCriteria: Exponential
  inputData: { cashRegisterId, lastSyncTs }

SyncDownWorker:
  constraints: NetworkType.CONNECTED
  periodic: 30 секунд
  inputData: { lastProductSyncTs }
```

---

### Task 4.3: Документы товароучёта

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/acceptance/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/writeoff/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/inventory/presentation/*.kt`

**Приёмка:** сканер ШК → список товаров → количество → отправить в облако
**Списание:** выбрать товары → указать причину → списать
**Инвентаризация:** цикл подсчёт → факт vs учёт → расхождения → акт

---

## Фаза 5: Отчётность

### Task 5.1: Отчёты по продажам

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/reports/domain/*.kt`

```
Отчёт по продажам:
  - Период: смена / день / неделя / месяц / произвольный
  - Выручка: наличные / безналичные
  - Количество чеков
  - Средний чек
  - Детализация по товарам: название, кол-во, сумма
```

---

### Task 5.2: Движение товара + возвраты

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/reports/presentation/MovementReportScreen.kt`

```
Движение товара:
  - Остаток на начало
  - Приход (приёмка)
  - Расход (продажи)
  - Списание
  - Возвраты
  - Остаток на конец
  - Фильтр: товар / категория / дата
```

---

## Фаза 6: Мониторинг статусов

### Task 6.1: StatusPanel UI

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/statuses/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/statuses/domain/StatusChecker.kt`

```kotlin
data class SystemStatus(
    val internet: ConnectionStatus,      // AVAILABLE / LOST
    val cloudServer: ServiceStatus,     // OK / ERROR(lastSyncTime)
    val ofd: OfdStatus,                 // OK / QUEUE(count)
    val chaseznakModule: ModuleStatus,  // ACTIVE / INACTIVE (если модуль)
    val egaisModule: ModuleStatus,      // ACTIVE / INACTIVE (если модуль)
    val license: LicenseStatus          // ACTIVE / EXPIRED / GRACE(daysLeft)
)
```

**UI:** панель из 4-6 индикаторов в верхнем баре каждого экрана (icon + tooltip).
Подробный экран — по тапу.

---

## Фаза 7: Лицензирование

### Task 7.1: LicenseChecker

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/licensing/domain/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/licensing/presentation/*.kt`

```kotlin
class LicenseChecker(
    private val api: LicenseApi,
    private val prefs: SharedPreferences
) {
    suspend fun check(): LicenseStatus {
        // 1. POST /api/v1/license/check { deviceId }
        // ACTIVE → сохранить expiryDate
        // EXPIRED → если !network → grace period (7 дней)
        // GRACE_EXPIRED → BLOCKED
    }
}
```

**Blocking:** при `GRACE_EXPIRED` — блокировка всех операций кроме просмотра отчётов и настроек.
**Триггер:** запуск приложения + каждые 24ч при наличии сети.

---

## Фаза 8: Опциональные модули (ЕГАИС + ЧЗ)

### Task 8.1: FeatureFlag система

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/core/features/FeatureManager.kt`
- Modify: `.../data/remote/api/ConfigApi.kt` — endpoint для получения флагов

```kotlin
enum class FeatureFlag {
    EGAAIS_ENABLED,
    CHASEZNAK_ENABLED
}

class FeatureManager(configApi: ConfigApi, localPrefs: SharedPreferences) {
    suspend fun isEnabled(flag: FeatureFlag): Boolean
    fun isEnabledSync(flag: FeatureFlag): Boolean  // из SharedPrefs
}
```

Флаги приходят с бэкенда + кешируются локально.
Включение/выключение модуля — без переустановки приложения.

---

### Task 8.2: Модуль ЕГАИС

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/egais/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/egais/domain/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/egais/data/EgaisRepository.kt`

```
Функции:
  - Приёмка накладных от ЕГАИС (через УТМ)
  - Акт вскрытия тары
  - Списание алкоголя
  - Инвентаризация остатков ЕГАИС
  - Блокировка продажи алкоголя если УТМ недоступен
```

---

### Task 8.3: Модуль маркировки (Честный ЗНАК)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/chaseznak/presentation/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/chaseznak/domain/*.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/chaseznak/data/ChaseznakRepository.kt`

```
Функции:
  - Сканирование DataMatrix
  - Валидация кода (ЛМ ЧЗ / облако разрешительный режим)
  - Выбытие при продаже
  - Приёмка / возврат маркированного
  - Блокировка продажи при ошибке валидации
```

---

### Task 8.4: Цифровой ID Max (верификация возраста)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/egais/presentation/AgeVerificationScreen.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/egais/domain/AgeVerificationUseCase.kt`

```
Поток:
  1. Кассир нажимает "Проверить возраст"
  2. Сканирование QR-кода с паспорта (Цифровой ID Max)
  3. POST /api/v1/chaseznak/verify-age { qrData }
  4. Результат: ПОДТВЕРЖДЁН / НЕ ПОДТВЕРЖДЁН
  5. При неподтверждении → блокировка продажи + логирование
```

---

## Фаза 9: Тестирование

### Task 9.1: Unit-тесты

**Files:**
- Create: `android/app/src/test/java/com/vitbon/kkm/core/fiscal/ffd/FiscalDocumentBuilderTest.kt`
- Create: `android/app/src/test/java/com/vitbon/kkm/features/sales/domain/*.kt`
- Create: `backend/src/test/kotlin/com/vitbon/kkm/api/v1/*.kt`

**Покрытие:**
- FiscalDocumentBuilder: все комбинации ФФД 1.05 / 1.2
- Use cases: все бизнес-правила (скидки, НДС, валидация)
- Sync: merge-логика, конфликты

---

### Task 9.2: Интеграционные тесты

**Files:**
- Create: `android/app/src/androidTest/java/com/vitbon/kkm/core/sync/*.kt`
- Create: `backend/src/test/kotlin/com/vitbon/kkm/integration/*.kt`

**Backend:** TestContainers (PostgreSQL, Redis)
**Android:** FakeFiscalCore (mock SDK) для UI-тестов

---

### Task 9.3: E2E-тесты с реальным оборудованием

```
Сценарии:
  1. Продажа → ОФД → подтверждение
  2. Возврат → корректный fiscalSign
  3. Закрытие смены → Z-отчёт
  4. Офлайн → 10 чеков → восстановление связи → синхронизация
  5. ЕГАИС: приёмка накладной (test УТМ)
  6. ЧЗ: продажа маркированного (test ЛМ ЧЗ)
```

---

## Фаза 10: Документирование

### Task 10.1: Руководство администратора

**Files:**
- Create: `docs/manuals/administrator.md`

Содержание: установка, настройка ККТ, подключение облака, управление пользователями, активация модулей, статусы.

---

### Task 10.2: Руководство кассира

**Files:**
- Create: `docs/manuals/cashier.md`

Содержание: ежедневные операции — продажа, возврат, открытие/закрытие смены, X-отчёт, внесение/изъятие.

---

### Task 10.3: Руководство по маркировке и ЕГАИС

**Files:**
- Create: `docs/manuals/marking-egais.md`

---

### Task 10.4: API-спецификация (OpenAPI)

**Files:**
- Create: `backend/src/main/resources/api/openapi.yaml`

```yaml
openapi: 3.0
info:
  title: VITBON Cloud API
  version: 1.0.0
paths:
  /api/v1/auth/login: ...
  /api/v1/checks/sync: ...
  /api/v1/products: ...
  # etc.
```

---

## Task Order (dependency graph)

```
Task 1.1 → Task 1.2 → Task 1.3
                             │
                    ┌────────┴────────┐
                    ▼                 ▼
               Task 2.1          Task 4.1
                    │                 │
               Task 2.2 ── Task 2.3 ─┘
                    │
               Task 2.4
                    │
     ┌──────────────┴──────────────────┐
     ▼              ▼                 ▼
Task 3.1        Task 4.2          Task 7.1
     │              │                 │
     ▼              ▼                 ▼
Task 3.2        Task 4.3          Task 6.1
     │              │
     ▼              ▼
Task 3.3        Task 5.1
     │              │
     ▼              ▼
Task 3.4        Task 5.2
     │
     ▼
Task 3.5 ── Task 3.6
     │
Task 8.1 ── Task 8.2 ── Task 8.3 ── Task 8.4
     │
Task 9.1 ── Task 9.2 ── Task 9.3
     │
Task 10.1 ── Task 10.2 ── Task 10.3 ── Task 10.4
```

**Параллельные цепочки (можно выполнять параллельно через отдельные subagents):**
- Chain A: Tasks 1.1 → 1.2 → 1.3 → 2.1 → 2.2 → 2.3 → 2.4 → 3.1 → 3.2 → 3.3 → 3.4 → 3.5 → 3.6
- Chain B: Tasks 4.1 → 4.2 → 4.3
- Chain C: Tasks 7.1 → 6.1
- Chain D: Tasks 8.1 → 8.2 → 8.3 → 8.4
- Chain E: Tasks 9.1 → 9.2 → 9.3
- Chain F: Tasks 10.1 → 10.2 → 10.3 → 10.4
