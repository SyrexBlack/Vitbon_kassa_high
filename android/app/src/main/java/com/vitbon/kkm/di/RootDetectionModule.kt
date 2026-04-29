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
        prefs: android.content.SharedPreferences,
        @SecurePrefs securePrefs: android.content.SharedPreferences
    ): RootRiskGuard = RootRiskGuard(context, detector, prefs, securePrefs)
}