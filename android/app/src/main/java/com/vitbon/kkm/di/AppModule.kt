package com.vitbon.kkm.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.vitbon.kkm.core.fiscal.*
import com.vitbon.kkm.core.fiscal.model.*
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
    fun provideFiscalCore(
        @ApplicationContext context: Context
    ): FiscalCore {
        // Shell: используем FakeFiscalCore. При подключении реального SDK
        // заменить на FiscalCoreProvider().get() через runBlocking
        return runBlocking {
            FakeFiscalCore(context).also { it.initialize() }
        }
    }
}

/**
 * Заглушка FiscalCore для shell-проекта.
 * Реализует все методы интерфейса без реального SDK.
 */
class FakeFiscalCore(private val context: Context) : FiscalCore {
    private var shiftOpen = false

    override suspend fun initialize(): Boolean = true
    override suspend fun shutdown(): Unit {}

    private fun successResult(): FiscalResult.Success {
        val ts = System.currentTimeMillis()
        return FiscalResult.Success(
            fiscalSign = "FAKE-$ts",
            fnNumber = "FAKE-FN-000000",
            fdNumber = (ts % 1000000).toString(),
            timestamp = ts
        )
    }

    override suspend fun openShift(): FiscalResult {
        shiftOpen = true
        return successResult()
    }

    override suspend fun printSale(check: FiscalCheck): FiscalResult = successResult()
    override suspend fun printReturn(check: FiscalCheck): FiscalResult = successResult()
    override suspend fun printCorrection(doc: CorrectionDoc): FiscalResult = successResult()

    override suspend fun closeShift(): FiscalResult {
        shiftOpen = false
        return successResult()
    }

    override suspend fun printXReport(): FiscalResult = successResult()
    override suspend fun cashIn(amount: Money, comment: String?): FiscalResult = successResult()
    override suspend fun cashOut(amount: Money, comment: String?): FiscalResult = successResult()

    override suspend fun getStatus(): FiscalStatus {
        return FiscalStatus(
            fnRegistered = true,
            fnNumber = "FAKE-FN-000000",
            shiftOpen = shiftOpen,
            shiftAgeHours = if (shiftOpen) 0L else null,
            currentFdNumber = 1,
            ofdConnected = true,
            lastError = null
        )
    }

    override suspend fun getFFDVersion(): FFDVersion = FFDVersion.V1_05
}
