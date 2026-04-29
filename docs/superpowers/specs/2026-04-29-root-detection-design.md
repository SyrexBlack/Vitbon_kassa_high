# Root Detection & Runtime Policy — Design Spec

**Date:** 2026-04-29
**Task:** `vitbon-kassa-oyy` — Implement root detection and runtime policy
**Status:** Approved

---

## 1. Overview

Проверка Android-устройства на наличие root-доступа с оценкой риска и политикой RESTRICT: приложение работает, но fiscal-операции заблокированы.

## 2. Architecture

```
features/rootdetection/
  domain/
    RootDetector.kt         ← interface (абстракция для DI/тестов)
    RootCheckResult.kt      ← sealed class (CLEAN/DETECTED + metadata)
    RootPolicyEnforcer.kt   ← маппинг результата → AppBlockingState
  data/
    SystemRootChecker.kt    ← реализация с 6 методами детекции
```

**Разделение ответственности:**
- `RootDetector` — только детекция (stateless, pure logic)
- `RootPolicyEnforcer` — правила что делать с результатом
- `RootRiskGuard` (Singleton) — кеш + перепроверка

## 3. Components

### 3.1 RootCheckResult

```kotlin
sealed class RootCheckResult {
    object Clean : RootCheckResult()
    data class Detected(val indicators: List<RootIndicator>) : RootCheckResult()
}
data class RootIndicator(val type: String, val detail: String)
```

### 3.2 RootDetector (interface)

```kotlin
interface RootDetector {
    suspend fun check(context: Context): RootCheckResult
}
```

### 3.3 SystemRootChecker (implementation)

6 методов детекции. Результат: `Clean` если индикаторов 0, иначе `Detected(indicators)`.

| # | Метод | Что проверяет | Ожидаемые false-positive |
|---|-------|---------------|--------------------------|
| 1 | `detectSuBinary()` | `/system/xbin/su`, `/system/bin/su` | LineageOS, эмуляторы |
| 2 | `detectMagiskApp()` | package `com.topjohnwu.magisk` | — |
| 3 | `detectDangerousProps()` | `ro.debuggable=1`, `ro.secure=0` | эмуляторы debug |
| 4 | `detectRwSystem()` | запись в `/system` | кастомные прошивки |
| 5 | `detectTestKeys()` | `ro.build.tags` содержит `test-keys` | userdebug сборки |
| 6 | `detectRiruZygisk()` | файлы в `/data/misc/zaru`, live library в `/proc/self/maps` | — |

### 3.4 RootPolicyEnforcer

Маппинг result → AppBlockingState:

```kotlin
object RootPolicyEnforcer {
    fun toBlockingState(result: RootCheckResult): AppBlockingState {
        return when (result) {
            is Clean -> AppBlockingState.Unblocked
            is Detected -> AppBlockingState.Blocked(
                "Устройство скомпрометировано: обнаружен root. Fiscal-операции заблокированы. Код для поддержки: ROOT-${result.indicators.size}"
            )
        }
    }
}
```

### 3.5 RootRiskGuard

- **Cache**: SharedPreferences (`root_risk_cached`, `root_risk_ts`)
- **StateFlow**: `blockingState: StateFlow<AppBlockingState>`
- **Startup flow**: читаем кеш → emit в StateFlow → запускаем async проверку → обновляем кеш
- **Re-check interval**: каждый запуск приложения (on-demand, не background job)

## 4. Points of Integration

### 4.1 Startup Gate

`AuthViewModel` при инициализации читает `RootRiskGuard.blockingState`:

```kotlin
val rootBlockingState by rootRiskGuard.observeBlockingState().collectAsState()
```

Если `AppBlockingState.Blocked`:
- Показывать экран "Устройство скомпрометировано" вместо AuthScreen
- Кнопка "Написать в поддержку" с кодом ошибки

### 4.2 Operational Gate

`FiscalOperationOrchestrator` перед каждой fiscal-операцией:

```kotlin
class FiscalOperationOrchestrator {
    private val rootRiskGuard: RootRiskGuard

    suspend fun execute(op: FiscalOperation): Result {
        val blockState = rootRiskGuard.getCurrentBlockingState()
        if (blockState is AppBlockingState.Blocked) {
            return Result.failure(SecurityBlockedException(blockState.reason))
        }
        // ...
    }
}
```

## 5. DI Integration

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object RootDetectionModule {
    @Provides
    @Singleton
    fun provideRootDetector(): RootDetector = SystemRootChecker()

    @Provides
    @Singleton
    fun provideRootRiskGuard(
        @ApplicationContext context: Context,
        detector: RootDetector,
        prefs: SharedPreferences
    ): RootRiskGuard = RootRiskGuard(context, detector, prefs)
}
```

## 6. Testing

| Тест | Что покрывает |
|------|---------------|
| `RootDetectorTest` | Мок контекст → проверка каждого из 6 методов → assert Clean/Detected |
| `RootPolicyEnforcerTest` | Clean → Unblocked; 1 indicator → Blocked; 3 indicators → Blocked with code |
| `RootRiskGuardTest` | Кеш читается, StateFlow обновляется, перепроверка не дублирует |

## 7. Files to Create

```
features/rootdetection/
  domain/
    RootCheckResult.kt         ← sealed class + data class
    RootDetector.kt            ← interface
    RootPolicyEnforcer.kt      ← object
  data/
    SystemRootChecker.kt       ← implementation
RootRiskGuard.kt               ← singleton с кешем и StateFlow
di/
  RootDetectionModule.kt       ← Hilt module
tests/
  RootDetectorTest.kt
  RootPolicyEnforcerTest.kt
  RootRiskGuardTest.kt
```