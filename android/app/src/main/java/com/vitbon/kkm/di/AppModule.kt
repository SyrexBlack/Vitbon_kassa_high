package com.vitbon.kkm.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
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
        val token = prefs.getString("auth_token", null)
        return ApiClient.create(prefs.javaClass.toString(), token)
    }
}
