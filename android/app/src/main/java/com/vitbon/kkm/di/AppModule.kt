package com.vitbon.kkm.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.vitbon.kkm.BuildConfig
import com.vitbon.kkm.core.fiscal.*
import com.vitbon.kkm.core.fiscal.runtime.FfdPolicyStore
import com.vitbon.kkm.core.fiscal.runtime.FfdVersionResolver
import com.vitbon.kkm.core.fiscal.runtime.FiscalOperationOrchestrator
import com.vitbon.kkm.core.sync.SyncPrefs
import com.vitbon.kkm.data.local.VitbonDatabase
import com.vitbon.kkm.data.local.dao.*
import com.vitbon.kkm.data.remote.ApiClient
import com.vitbon.kkm.data.remote.api.VitbonApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VitbonDatabase {
        return Room.databaseBuilder(
            context,
            VitbonDatabase::class.java,
            "vitbon.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCashierDao(db: VitbonDatabase): CashierDao = db.cashierDao()
    @Provides
    fun provideShiftDao(db: VitbonDatabase): ShiftDao = db.shiftDao()
    @Provides
    fun provideCheckDao(db: VitbonDatabase): CheckDao = db.checkDao()
    @Provides
    fun provideCheckItemDao(db: VitbonDatabase): CheckItemDao = db.checkItemDao()
    @Provides
    fun provideProductDao(db: VitbonDatabase): ProductDao = db.productDao()

    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("vitbon_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideSyncPrefs(prefs: SharedPreferences): SyncPrefs = SyncPrefs(prefs)

    @Provides
    @Singleton
    fun provideVitbonApi(prefs: SharedPreferences): VitbonApi {
        return ApiClient.create(prefs)
    }

    @Provides
    @Singleton
    fun provideFiscalConfig(): FiscalConfig = FiscalConfig()

    @Provides
    @Singleton
    fun provideFiscalCoreProvider(
        @ApplicationContext context: Context,
        config: FiscalConfig
    ): FiscalCoreProvider = FiscalCoreProvider(context, config)

    @Provides
    @Singleton
    fun provideFiscalCore(
        @ApplicationContext context: Context,
        provider: FiscalCoreProvider
    ): FiscalCore = createFiscalCore(
        isDebug = BuildConfig.DEBUG,
        context = context,
        realCoreProvider = {
            runBlocking { provider.get() }
        }
    )

    @Provides
    @Singleton
    fun provideFfdPolicyStore(prefs: SharedPreferences): FfdPolicyStore = FfdPolicyStore(prefs)

    @Provides
    @Singleton
    fun provideFfdVersionResolver(
        fiscalCore: FiscalCore,
        ffdPolicyStore: FfdPolicyStore
    ): FfdVersionResolver = FfdVersionResolver(fiscalCore, ffdPolicyStore)

    @Provides
    @Singleton
    fun provideFiscalOperationOrchestrator(
        fiscalCore: FiscalCore,
        ffdVersionResolver: FfdVersionResolver
    ): FiscalOperationOrchestrator = FiscalOperationOrchestrator(fiscalCore, ffdVersionResolver)
}

internal fun createFiscalCore(
    isDebug: Boolean,
    context: Context,
    realCoreProvider: () -> FiscalCore
): FiscalCore {
    if (isDebug) {
        return runBlocking {
            FakeFiscalCore(context).also { it.initialize() }
        }
    }
    return realCoreProvider()
}

