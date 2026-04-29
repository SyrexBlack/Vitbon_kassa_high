# Harden Local Data Security — Design Spec

**Date:** 2026-04-29
**Task:** `vitbon-kassa-3uu` — Harden local data security (EncryptedSharedPreferences + DB encryption decision)
**Status:** Approved

---

## 1. Overview

Migrate sensitive SharedPreferences data from plain to EncryptedSharedPreferences. Room DB encryption — separate task (out of scope).

## 2. Scope

**In scope:**
- `LicenseChecker` — encrypt license status, grace timestamps
- `SyncPrefs` — encrypt device_id, sync metadata
- `RootRiskGuard` — encrypt root detection cache

**Out of scope:**
- Feature flags, FFD policy (not sensitive)
- Room DB encryption (separate task)
- `AuthTokenStore` (already encrypted)

## 3. Architecture

```
data/security/
  SecurePrefsFactory.kt   ← creates EncryptedSharedPreferences with AES-256-GCM
  PrefsMigration.kt       ← fallback reader: plain → encrypted

di/
  SecurePrefsModule.kt    ← Hilt module for encrypted prefs
  AppModule.kt            ← keep plain prefs for non-sensitive data
```

**Separation:** Sensitive data goes to `vitbon_secure` (encrypted), public data stays in `vitbon_prefs` (plain).

## 4. Components

### 4.1 SecurePrefsFactory

```kotlin
object SecurePrefsFactory {
    fun createEncrypted(context: Context, name: String): SharedPreferences {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, name, key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
```

### 4.2 PrefsMigration

Fallback reader with auto-migration on read:
- For each sensitive key: if encrypted is null → read from plain → write to encrypted
- Preserves existing data on upgrade
- Fallback: clean start if migration fails

```kotlin
object PrefsMigration {
    fun migrateLicenseData(encrypted: SharedPreferences, plain: SharedPreferences) {
        listOf(KEY_LICENSE_STATUS, KEY_LAST_CHECK, KEY_GRACE_UNTIL).forEach { key ->
            if (encrypted.getString(key, null) == null) {
                plain.getString(key, null)?.let { encrypted.edit().putString(key, it).apply() }
            }
        }
        listOf(KEY_ROOT_CACHED, KEY_ROOT_TS).forEach { key ->
            if (encrypted.getString(key, null) == null) {
                plain.getString(key, null)?.let { encrypted.edit().putString(key, it).apply() }
            }
        }
        // device_id similar
    }
}
```

### 4.3 Hilt DI

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SecurePrefsModule {
    @Provides
    @Singleton
    fun provideSecurePrefs(@ApplicationContext context: Context): SharedPreferences =
        SecurePrefsFactory.createEncrypted(context, "vitbon_secure")
}
```

## 5. File Changes

| File | Change |
|------|--------|
| `data/security/SecurePrefsFactory.kt` | NEW |
| `data/security/PrefsMigration.kt` | NEW |
| `di/SecurePrefsModule.kt` | NEW |
| `di/AppModule.kt` | keep plain prefs for non-sensitive data |
| `features/licensing/domain/LicenseChecker.kt` | inject encrypted prefs, call migration in init |
| `core/sync/SyncPrefs.kt` | inject encrypted prefs, call migration |
| `features/rootdetection/RootRiskGuard.kt` | inject encrypted prefs, call migration |

## 6. Testing

| Test | Coverage |
|------|----------|
| `PrefsMigrationTest` | fallback reads from plain, writes to encrypted |
| `SecurePrefsFactoryTest` | factory creates valid EncryptedSharedPreferences |
| `LicenseCheckerMigratedTest` | encrypted prefs contain license data after migration |
| `SyncPrefsMigratedTest` | device_id migrated correctly |
| `RootRiskGuardMigratedTest` | root cache migrated |
| `FeatureFlagsNotEncryptedTest` | verify feature flags stay in plain prefs |

## 7. Key Constants

- `vitbon_secure` — encrypted prefs file name (new)
- `vitbon_prefs` — plain prefs file name (existing, keep for non-sensitive)
- `SECURE_PREFS_FILE = "vitbon_secure"`

## 8. Acceptance Criteria

1. Sensitive data (license, device_id, root cache) stored in EncryptedSharedPreferences
2. Existing plain data migrated on first launch (no data loss)
3. Feature flags remain in plain SharedPreferences
4. All tests pass (migration + unit tests)
5. No changes to Room DB encryption (separate task)