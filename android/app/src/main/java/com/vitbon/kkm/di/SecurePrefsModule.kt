package com.vitbon.kkm.di

import android.content.Context
import android.content.SharedPreferences
import com.vitbon.kkm.data.security.SecurePrefsFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurePrefsModule {

    @Provides
    @Singleton
    @SecurePrefs
    fun provideSecurePrefs(@ApplicationContext context: Context): SharedPreferences {
        return SecurePrefsFactory.createEncrypted(context, "vitbon_secure")
    }
}
