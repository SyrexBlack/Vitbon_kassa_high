# Root Detection & Runtime Policy — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить детекцию root-доступа на Android с политикой RESTRICT: приложение работает, fiscal-операции заблокированы.

**Architecture:** Интерфейс `RootDetector` + sealed result для тестируемости; `RootPolicyEnforcer` отделяет правила от детекции; `RootRiskGuard` — синглтон с кешем SharedPreferences + StateFlow.

**Tech Stack:** Kotlin, Hilt DI, StateFlow, SharedPreferences, JUnit 4

---

## File Map

```
android/app/src/main/java/com/vitbon/kkm/features/rootdetection/
  domain/
    RootCheckResult.kt      ← sealed class + RootIndicator data class
    RootDetector.kt         ← interface
    RootPolicyEnforcer.kt   ← object
  data/
    SystemRootChecker.kt    ← реализация, 6 методов детекции

android/app/src/main/java/com/vitbon/kkm/features/rootdetection/
  RootRiskGuard.kt          ← синглтон, кеш + StateFlow

android/app/src/main/java/com/vitbon/kkm/di/
  RootDetectionModule.kt    ← Hilt module

android/app/src/main/java/com/vitbon/kkm/core/fiscal/runtime/
  FiscalOperationOrchestrator.kt  ← патч, operational gate

android/app/src/test/java/com/vitbon/kkm/features/rootdetection/
  domain/
    RootPolicyEnforcerTest.kt
  data/
    SystemRootCheckerTest.kt
  RootRiskGuardTest.kt
```

---

## Task 1: RootCheckResult & RootDetector interfaces

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/rootdetection/domain/RootCheckResult.kt`
- Create: `android/app/src/main/java/com/vitbon/kkm/features/rootdetection/domain/RootDetector.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vitbon.kkm.features.rootdetection.domain

import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootIndicator
import org.junit.Assert.*
import org.junit.Test

class RootDetectorTest {
    // Test verifies that RootDetector interface exists and check() is suspend
    @Test
    fun `RootDetector is interface with suspend check method`() {
        val detector = object : RootDetector {
            override suspend fun check(context: android.content.Context): RootCheckResult {
                return RootCheckResult.Clean
            }
        }
        val result = detector.check(android.content.Context(android.app.Application()))
        assertTrue(result is RootCheckResult.Clean)
    }

    @Test
    fun `RootIndicator has type and detail`() {
        val indicator = RootIndicator("su_binary", "/system/xbin/su")
        assertEquals("su_binary", indicator.type)
        assertEquals("/system/xbin/su", indicator.detail)
    }

    @Test
    fun `Detected result contains list of indicators`() {
        val result = RootCheckResult.Detected(
            listOf(RootIndicator("magisk", "com.topjohnwu.magisk"))
        )
        val detected = result as RootCheckResult.Detected
        assertEquals(1, detected.indicators.size)
        assertEquals("magisk", detected.indicators[0].type)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.domain.RootDetectorTest"
```
Expected: FAIL — classes do not exist yet

- [ ] **Step 3: Write minimal implementation**

`RootCheckResult.kt`:
```kotlin
package com.vitbon.kkm.features.rootdetection.domain

data class RootIndicator(val type: String, val detail: String)

sealed class RootCheckResult {
    object Clean : RootCheckResult()
    data class Detected(val indicators: List<RootIndicator>) : RootCheckResult()
}
```

`RootDetector.kt`:
```kotlin
package com.vitbon.kkm.features.rootdetection.domain

import android.content.Context

interface RootDetector {
    suspend fun check(context: Context): RootCheckResult
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.domain.RootDetectorTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/rootdetection/domain/RootCheckResult.kt
git add android/app/src/main/java/com/vitbon/kkm/features/rootdetection/domain/RootDetector.kt
git add android/app/src/test/java/com/vitbon/kkm/features/rootdetection/domain/RootDetectorTest.kt
git commit -m "feat(rootdetection): add RootDetector interface + RootCheckResult sealed class

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: RootPolicyEnforcer

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/rootdetection/domain/RootPolicyEnforcer.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/features/rootdetection/domain/RootPolicyEnforcerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vitbon.kkm.features.rootdetection.domain

import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import org.junit.Assert.*
import org.junit.Test

class RootPolicyEnforcerTest {

    @Test
    fun `Clean result maps to Unblocked`() {
        val result = RootCheckResult.Clean
        val state = RootPolicyEnforcer.toBlockingState(result)
        assertTrue(state is AppBlockingState.Unblocked)
    }

    @Test
    fun `Detected result maps to Blocked`() {
        val result = RootCheckResult.Detected(listOf(RootIndicator("su_binary", "/system/xbin/su")))
        val state = RootPolicyEnforcer.toBlockingState(result)
        assertTrue(state is AppBlockingState.Blocked)
    }

    @Test
    fun `Detected result blocked message contains indicator count as code`() {
        val result = RootCheckResult.Detected(
            listOf(
                RootIndicator("su_binary", "/system/xbin/su"),
                RootIndicator("magisk", "com.topjohnwu.magisk"),
                RootIndicator("debuggable", "ro.debuggable=1")
            )
        )
        val state = RootPolicyEnforcer.toBlockingState(result) as AppBlockingState.Blocked
        assertTrue(state.reason.contains("ROOT-3"))
    }

    @Test
    fun `Single indicator gets code ROOT-1`() {
        val result = RootCheckResult.Detected(listOf(RootIndicator("su_binary", "/system/bin/su")))
        val state = RootPolicyEnforcer.toBlockingState(result) as AppBlockingState.Blocked
        assertTrue(state.reason.contains("ROOT-1"))
        assertTrue(state.reason.contains("сокомпрометировано"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.domain.RootPolicyEnforcerTest"
```
Expected: FAIL — RootPolicyEnforcer does not exist

- [ ] **Step 3: Write minimal implementation**

`RootPolicyEnforcer.kt`:
```kotlin
package com.vitbon.kkm.features.rootdetection.domain

import com.vitbon.kkm.features.licensing.domain.AppBlockingState

object RootPolicyEnforcer {
    fun toBlockingState(result: RootCheckResult): AppBlockingState {
        return when (result) {
            is RootCheckResult.Clean -> AppBlockingState.Unblocked
            is RootCheckResult.Detected -> AppBlockingState.Blocked(
                "Устройство скомпрометировано: обнаружен root. Fiscal-операции заблокированы. Код для поддержки: ROOT-${result.indicators.size}"
            )
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.domain.RootPolicyEnforcerTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/rootdetection/domain/RootPolicyEnforcer.kt
git add android/app/src/test/java/com/vitbon/kkm/features/rootdetection/domain/RootPolicyEnforcerTest.kt
git commit -m "feat(rootdetection): add RootPolicyEnforcer — maps result to AppBlockingState

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: SystemRootChecker (implementation)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/rootdetection/data/SystemRootChecker.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/features/rootdetection/data/SystemRootCheckerTest.kt`

- [ ] **Step 1: Write the failing test — stub + detection behavior**

```kotlin
package com.vitbon.kkm.features.rootdetection.data

import android.content.Context
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootIndicator
import org.junit.Assert.*
import org.junit.Test

class SystemRootCheckerTest {

    @Test
    fun `check returns Clean when no indicators found`() {
        val context = fakeContext(
            suBinaries = emptyList(),
            magiskPackage = null,
            debuggable = false,
            secure = true,
            buildTags = "release",
            zygiskDir = emptyList()
        )
        val checker = SystemRootChecker()
        val result = checker.check(context)
        assertTrue(result is RootCheckResult.Clean)
    }

    @Test
    fun `check returns Detected when su binary exists`() {
        val context = fakeContext(
            suBinaries = listOf("/system/xbin/su"),
            magiskPackage = null,
            debuggable = false,
            secure = true,
            buildTags = "release",
            zygiskDir = emptyList()
        )
        val checker = SystemRootChecker()
        val result = checker.check(context)
        assertTrue(result is RootCheckResult.Detected)
        val indicators = (result as RootCheckResult.Detected).indicators
        assertTrue(indicators.any { it.type == "su_binary" })
    }

    @Test
    fun `check returns Detected when Magisk package present`() {
        val context = fakeContext(
            suBinaries = emptyList(),
            magiskPackage = "com.topjohnwu.magisk",
            debuggable = false,
            secure = true,
            buildTags = "release",
            zygiskDir = emptyList()
        )
        val checker = SystemRootChecker()
        val result = checker.check(context)
        assertTrue(result is RootCheckResult.Detected)
        val indicators = (result as RootCheckResult.Detected).indicators
        assertTrue(indicators.any { it.type == "magisk_app" })
    }

    @Test
    fun `check returns Detected when debuggable prop is true`() {
        val context = fakeContext(
            suBinaries = emptyList(),
            magiskPackage = null,
            debuggable = true,
            secure = true,
            buildTags = "release",
            zygiskDir = emptyList()
        )
        val checker = SystemRootChecker()
        val result = checker.check(context)
        assertTrue(result is RootCheckResult.Detected)
        val indicators = (result as RootCheckResult.Detected).indicators
        assertTrue(indicators.any { it.type == "dangerous_props" })
    }

    @Test
    fun `check returns Detected when build tags contain test-keys`() {
        val context = fakeContext(
            suBinaries = emptyList(),
            magiskPackage = null,
            debuggable = false,
            secure = true,
            buildTags = "release,test-keys",
            zygiskDir = emptyList()
        )
        val checker = SystemRootChecker()
        val result = checker.check(context)
        assertTrue(result is RootCheckResult.Detected)
        val indicators = (result as RootCheckResult.Detected).indicators
        assertTrue(indicators.any { it.type == "test_keys" })
    }

    @Test
    fun `check aggregates all indicators from all checks`() {
        val context = fakeContext(
            suBinaries = listOf("/system/xbin/su"),
            magiskPackage = "com.topjohnwu.magisk",
            debuggable = true,
            secure = false,
            buildTags = "release,test-keys",
            zygiskDir = listOf("/data/misc/zaru/daemon.pid")
        )
        val checker = SystemRootChecker()
        val result = checker.check(context)
        val detected = result as RootCheckResult.Detected
        // All 5 positive checks should appear
        assertTrue(detected.indicators.any { it.type == "su_binary" })
        assertTrue(detected.indicators.any { it.type == "magisk_app" })
        assertTrue(detected.indicators.any { it.type == "dangerous_props" })
        assertTrue(detected.indicators.any { it.type == "test_keys" })
        assertTrue(detected.indicators.any { it.type == "rw_system" })
        assertTrue(detected.indicators.any { it.type == "zygisk_detected" })
    }
}

// Fake context helper for testing — uses composition instead of mocking framework
private class FakeRootCheckContext(
    val suBinaries: List<String>,
    val magiskPackage: String?,
    val debuggable: Boolean,
    val secure: Boolean,
    val buildTags: String,
    val zygiskDir: List<String>
)

private fun fakeContext(
    suBinaries: List<String>,
    magiskPackage: String?,
    debuggable: Boolean,
    secure: Boolean,
    buildTags: String,
    zygiskDir: List<String>
): Context = FakeTestContext(FakeRootCheckContext(suBinaries, magiskPackage, debuggable, secure, buildTags, zygiskDir))

private class FakeTestContext(private val fake: FakeRootCheckContext) : Context by mockContext() {
    fun getFakeRootCheckContext() = fake
}
```

**Note for test implementation:** Since mocking `Context` is complex, the test file will use a test-specific extension point: `RootDetector` interface accepts a `RootCheckContext` wrapper that `SystemRootChecker` uses internally. The production code uses real Context; the test code uses `TestRootCheckContext`. See Task 3 implementation for exact abstraction.

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.data.SystemRootCheckerTest"
```
Expected: FAIL — SystemRootChecker does not exist

- [ ] **Step 3: Write implementation**

`SystemRootChecker.kt`:
```kotlin
package com.vitbon.kkm.features.rootdetection.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SystemRootChecker : RootDetector {

    override suspend fun check(context: Context): RootCheckResult = withContext(Dispatchers.IO) {
        val indicators = mutableListOf<RootIndicator>()

        if (detectSuBinary()) {
            indicators += RootIndicator("su_binary", "su binary found on filesystem")
        }
        if (detectMagiskApp(context)) {
            indicators += RootIndicator("magisk_app", "com.topjohnwu.magisk")
        }
        if (detectDangerousProps()) {
            indicators += RootIndicator("dangerous_props", "ro.debuggable=1 or ro.secure=0")
        }
        if (detectRwSystem()) {
            indicators += RootIndicator("rw_system", "writable system partition")
        }
        if (detectTestKeys()) {
            indicators += RootIndicator("test_keys", "build tags contain test-keys")
        }
        if (detectZygisk()) {
            indicators += RootIndicator("zygisk_detected", "Zygisk/Riru modules present")
        }

        if (indicators.isEmpty()) {
            RootCheckResult.Clean
        } else {
            RootCheckResult.Detected(indicators)
        }
    }

    private fun detectSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/vendor/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun detectMagiskApp(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.topjohnwu.magisk", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun detectDangerousProps(): Boolean {
        val debuggable = Build.DEBUGGABLE
        val secure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        val secureProp = try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.secure"))
            val reader = process.inputStream.bufferedReader()
            val value = reader.readLine()?.trim()
            reader.close()
            value == "0"
        } catch (e: Exception) {
            false
        }
        return debuggable || secureProp
    }

    private fun detectRwSystem(): Boolean {
        return try {
            val testFile = File("/system/test_write_permission")
            testFile.createNewFile()
            testFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun detectTestKeys(): Boolean {
        val tags = Build.TAGS
        return tags.contains("test-keys")
    }

    private fun detectZygisk(): Boolean {
        return try {
            val zygiskDir = File("/data/misc/zaru")
            val daemonPid = File("/data/misc/zaru/daemon.pid")
            val mapsFile = File("/proc/self/maps")
            zygiskDir.exists() || daemonPid.exists() || mapsFile.readText().contains("zaru")
        } catch (e: Exception) {
            false
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.data.SystemRootCheckerTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/rootdetection/data/SystemRootChecker.kt
git add android/app/src/test/java/com/vitbon/kkm/features/rootdetection/data/SystemRootCheckerTest.kt
git commit -m "feat(rootdetection): add SystemRootChecker — 6-method detection implementation

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 4: RootRiskGuard (singleton with cache)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/features/rootdetection/RootRiskGuard.kt`
- Test: `android/app/src/test/java/com/vitbon/kkm/features/rootdetection/RootRiskGuardTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vitbon.kkm.features.rootdetection

import android.content.Context
import android.content.SharedPreferences
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootIndicator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RootRiskGuardTest {

    @Test
    fun `starts with cached state from SharedPreferences`() = runTest {
        val prefs = fakePrefs(cachedResult = "DETECTED", cachedTs = System.currentTimeMillis().toString())
        val guard = RootRiskGuard(fakeContext(), fakeDetector(), prefs)
        val state = guard.blockingState.first()
        assertTrue(state is AppBlockingState.Blocked)
    }

    @Test
    fun `clean cached result yields Unblocked`() = runTest {
        val prefs = fakePrefs(cachedResult = "CLEAN", cachedTs = System.currentTimeMillis().toString())
        val guard = RootRiskGuard(fakeContext(), fakeDetector(), prefs)
        val state = guard.blockingState.first()
        assertTrue(state is AppBlockingState.Unblocked)
    }

    @Test
    fun `getCurrentBlockingState returns cached value synchronously`() {
        val prefs = fakePrefs(cachedResult = "DETECTED", cachedTs = System.currentTimeMillis().toString())
        val guard = RootRiskGuard(fakeContext(), fakeDetector(), prefs)
        val state = guard.getCurrentBlockingState()
        assertTrue(state is AppBlockingState.Blocked)
    }
}

// Test helpers — use real SharedPreferences via FakeSharedPreferences or an in-memory map-backed impl
private fun fakePrefs(cachedResult: String, cachedTs: String): SharedPreferences {
    return object : SharedPreferences {
        private val map = mutableMapOf(
            "root_risk_cached" to cachedResult,
            "root_risk_ts" to cachedTs
        )
        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
        // ... implement remaining SharedPreferences interface
    }
}

private fun fakeDetector() = object : RootDetector {
    override suspend fun check(context: Context): RootCheckResult = RootCheckResult.Clean
}

private fun fakeContext(): Context = android.content.Context(android.app.Application())
```

**Note:** The test helper `fakePrefs` needs full SharedPreferences interface. Use a simple in-memory implementation:

```kotlin
class InMemorySharedPreferences(
    private val initial: Map<String, Any> = emptyMap()
) : SharedPreferences {
    private val storage = initial.toMutableMap()
    override fun getAll(): Map<String, *> = storage
    override fun getString(key: String, defValue: String?): String? = storage[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int): Int = storage[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = storage[key] as? Long ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = key in storage
    override fun edit(): SharedPreferences.Editor = InMemoryEditor()
    private inner class InMemoryEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun remove(k: String) = apply { pending.remove(k) }
        override fun apply() { storage.putAll(pending.filterValues { it != null }); pending.clear() }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.RootRiskGuardTest"
```
Expected: FAIL — RootRiskGuard does not exist

- [ ] **Step 3: Write implementation**

`RootRiskGuard.kt`:
```kotlin
package com.vitbon.kkm.features.rootdetection

import android.content.Context
import android.content.SharedPreferences
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.domain.RootCheckResult
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import com.vitbon.kkm.features.rootdetection.domain.RootPolicyEnforcer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_ROOT = "root_risk_prefs"
private const val KEY_CACHED_RESULT = "root_risk_cached"
private const val KEY_CACHED_TS = "root_risk_ts"

@Singleton
class RootRiskGuard @Inject constructor(
    private val context: Context,
    private val detector: RootDetector,
    private val prefs: SharedPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _blockingState = MutableStateFlow<AppBlockingState>(AppBlockingState.Unblocked)
    val blockingState: StateFlow<AppBlockingState> = _blockingState.asStateFlow()

    init {
        loadCachedState()
        triggerAsyncCheck()
    }

    private fun loadCachedState() {
        val cachedResult = prefs.getString(KEY_CACHED_RESULT, null)
        if (cachedResult != null) {
            val checkResult = when (cachedResult) {
                "CLEAN" -> RootCheckResult.Clean
                "DETECTED" -> {
                    val ts = prefs.getLong(KEY_CACHED_TS, 0L)
                    if (ts > 0) {
                        RootCheckResult.Detected(
                            listOf(RootIndicator("cached", "root detected at ${ts}"))
                        )
                    } else {
                        RootCheckResult.Detected(emptyList())
                    }
                }
                else -> null
            }
            if (checkResult != null) {
                _blockingState.value = RootPolicyEnforcer.toBlockingState(checkResult)
            }
        }
    }

    private fun triggerAsyncCheck() {
        scope.launch {
            val result = detector.check(context)
            _blockingState.value = RootPolicyEnforcer.toBlockingState(result)
            persistResult(result)
        }
    }

    private fun persistResult(result: RootCheckResult) {
        val (resultStr, indicators) = when (result) {
            is RootCheckResult.Clean -> "CLEAN" to 0
            is RootCheckResult.Detected -> "DETECTED" to result.indicators.size
        }
        prefs.edit()
            .putString(KEY_CACHED_RESULT, resultStr)
            .putLong(KEY_CACHED_TS, System.currentTimeMillis())
            .apply()
    }

    fun getCurrentBlockingState(): AppBlockingState = _blockingState.value
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.rootdetection.RootRiskGuardTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/rootdetection/RootRiskGuard.kt
git add android/app/src/test/java/com/vitbon/kkm/features/rootdetection/RootRiskGuardTest.kt
git commit -m "feat(rootdetection): add RootRiskGuard — singleton with SharedPreferences cache + StateFlow

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 5: RootDetectionModule (Hilt DI)

**Files:**
- Create: `android/app/src/main/java/com/vitbon/kkm/di/RootDetectionModule.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertNotNull
import org.junit.Test
import javax.inject.Singleton

class RootDetectionModuleTest {
    @Test
    fun `RootDetectionModule is a Hilt Module`() {
        // Verify module annotation exists
        assertNotNull(RootDetectionModule::class.java.getAnnotation(Module::class.java))
        assertNotNull(RootDetectionModule::class.java.getAnnotation(InstallIn::class.java))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.di.RootDetectionModuleTest"
```
Expected: FAIL — RootDetectionModule does not exist

- [ ] **Step 3: Write minimal implementation**

`RootDetectionModule.kt`:
```kotlin
package com.vitbon.kkm.di

import android.content.Context
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import com.vitbon.kkm.features.rootdetection.data.SystemRootChecker
import com.vitbon.kkm.features.rootdetection.domain.RootDetector
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        prefs: android.content.SharedPreferences
    ): RootRiskGuard = RootRiskGuard(context, detector, prefs)
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.di.RootDetectionModuleTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/di/RootDetectionModule.kt
git commit -m "feat(rootdetection): add RootDetectionModule — Hilt DI wiring

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 6: FiscalOperationOrchestrator — operational gate

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/core/fiscal/runtime/FiscalOperationOrchestrator.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vitbon.kkm.core.fiscal.runtime

import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import org.junit.Assert.*
import org.junit.Test

class FiscalOperationOrchestratorSecurityTest {
    @Test
    fun `execute throws SecurityBlockedException when root detected`() {
        val guard = mockRootRiskGuard(AppBlockingState.Blocked("Устройство скомпрометировано"))
        val orchestrator = FiscalOperationOrchestrator(fiscalCore = mockCore, guard = guard)
        val result = orchestrator.execute(FiscalOperation.SELL...)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityBlockedException)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestratorSecurityTest"
```
Expected: FAIL — guard parameter doesn't exist yet

- [ ] **Step 3: Write implementation**

Read `FiscalOperationOrchestrator.kt` first, then modify:

Add constructor parameter and guard at top of `execute()`:
```kotlin
class FiscalOperationOrchestrator(
    private val fiscalCore: FiscalCore,
    private val ffdVersionResolver: FfdVersionResolver,
    private val rootRiskGuard: RootRiskGuard  // ← ADD THIS
) {
    suspend fun execute(op: FiscalOperation): Result<FiscalResult> {
        // Gate: check root status before any fiscal operation
        val blockState = rootRiskGuard.getCurrentBlockingState()
        if (blockState is AppBlockingState.Blocked) {
            return Result.failure(SecurityBlockedException(blockState.reason))
        }
        // ... existing logic unchanged below
    }
}

// Add this exception class
class SecurityBlockedException(message: String) : Exception(message)
```

**Important:** Read the full `FiscalOperationOrchestrator.kt` before editing. Add the guard check as the first line of `execute()`, before any business logic.

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestratorSecurityTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/core/fiscal/runtime/FiscalOperationOrchestrator.kt
git commit -m "feat(rootdetection): add operational gate to FiscalOperationOrchestrator

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 7: AppModule — wire RootRiskGuard into FiscalOperationOrchestrator

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/di/AppModule.kt`

- [ ] **Step 1: Read AppModule.kt**

```bash
cat android/app/src/main/java/com/vitbon/kkm/di/AppModule.kt
```

- [ ] **Step 2: Modify AppModule.kt**

Add `rootRiskGuard: RootRiskGuard` parameter to `provideFiscalOperationOrchestrator()`:

```kotlin
@Provides
@Singleton
fun provideFiscalOperationOrchestrator(
    fiscalCore: FiscalCore,
    ffdVersionResolver: FfdVersionResolver,
    rootRiskGuard: RootRiskGuard
): FiscalOperationOrchestrator = FiscalOperationOrchestrator(
    fiscalCore,
    ffdVersionResolver,
    rootRiskGuard
)
```

- [ ] **Step 3: Run all tests to verify nothing breaks**

```
./gradlew :app:testDebugUnitTest
```
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/di/AppModule.kt
git commit -m "fix(di): wire RootRiskGuard into FiscalOperationOrchestrator

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 8: Startup gate — block on App startup

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/auth/presentation/AuthViewModel.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.vitbon.kkm.features.auth.presentation

import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.rootdetection.RootRiskGuard
import org.junit.Assert.*
import org.junit.Test

class AuthViewModelRootBlockingTest {
    @Test
    fun `initially observes root blocking state`() {
        val viewModel = AuthViewModel(
            authUseCase = mock(),
            licenseChecker = mock(),
            emergencyAdminSessionManager = mock(),
            rootRiskGuard = mockRootRiskGuard(AppBlockingState.Blocked("root"))
        )
        assertTrue(viewModel.rootBlockingState.value is AppBlockingState.Blocked)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.auth.presentation.AuthViewModelRootBlockingTest"
```
Expected: FAIL — rootBlockingState does not exist yet

- [ ] **Step 3: Modify AuthViewModel.kt**

Read the current AuthViewModel, then add:

```kotlin
class AuthViewModel(
    private val authUseCase: AuthUseCase,
    private val licenseChecker: LicenseChecker,
    private val emergencyAdminSessionManager: EmergencyAdminSessionManager,
    private val rootRiskGuard: RootRiskGuard  // ← ADD THIS
) : ViewModel() {

    // ... existing state ...

    val rootBlockingState: StateFlow<AppBlockingState> = rootRiskGuard.blockingState

    // ... rest unchanged ...
}
```

- [ ] **Step 4: Run test to verify it passes**

```
./gradlew :app:testDebugUnitTest --tests "com.vitbon.kkm.features.auth.presentation.AuthViewModelRootBlockingTest"
```
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/auth/presentation/AuthViewModel.kt
git commit -m "feat(auth): wire RootRiskGuard into AuthViewModel for startup blocking

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 9: UI — show blocked screen when root detected

**Files:**
- Modify: `android/app/src/main/java/com/vitbon/kkm/features/auth/presentation/AuthScreen.kt`

- [ ] **Step 1: Read AuthScreen.kt**

- [ ] **Step 2: Modify AuthScreen.kt**

Add blocking UI. When `viewModel.rootBlockingState` is `AppBlockingState.Blocked`, show a fullscreen warning instead of the auth form:

```kotlin
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthSuccess: ...,
    onAdminMode: ...
) {
    val rootBlockingState by viewModel.rootBlockingState.collectAsState()

    // If root detected — show blocked screen
    val rootBlock = rootBlockingState
    if (rootBlock is AppBlockingState.Blocked) {
        RootBlockedScreen(reason = rootBlock.reason)
        return
    }

    // Normal auth flow below...
}
```

Create new composable at end of file:
```kotlin
@Composable
private fun RootBlockedScreen(reason: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Устройство скомпрометировано",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { /* TODO: open support URL or copy error code */ }) {
                Text("Написать в поддержку")
            }
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

```
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/vitbon/kkm/features/auth/presentation/AuthScreen.kt
git commit -m "feat(ui): show RootBlockedScreen when root detected at startup

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Verification Checklist

After all tasks complete, run full test suite:
```
./gradlew :app:testDebugUnitTest
```
All tests pass. ✓

Push to remote:
```
git push origin feature/rbac-navigation-enforcement
```

Close bead:
```
bd close vitbon-kassa-oyy --reason "Root detection implemented: 6-method SystemRootChecker, RESTRICT policy, startup + operational gates, unit tests"
```