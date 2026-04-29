package com.vitbon.kkm.data.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitbon.kkm.di.SecurePrefsModule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurePrefsFactoryInstrumentedTest {

    @Test
    fun createEncryptedReturnsSharedPreferencesThatIsNotNull() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = SecurePrefsFactory.createEncrypted(context, "test_secure")
        assertNotNull(prefs)
    }

    @Test
    fun createEncryptedCreatesSeparateInstancesForDifferentNames() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs1 = SecurePrefsFactory.createEncrypted(context, "secure1")
        val prefs2 = SecurePrefsFactory.createEncrypted(context, "secure2")
        assertNotSame(prefs1, prefs2)
    }

    @Test
    fun securePrefsModuleProvidesNonNullSharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = SecurePrefsModule.provideSecurePrefs(context)
        assertNotNull(prefs)
    }

    @Test
    fun moduleProvidesValidInstancesAcrossCalls() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs1 = SecurePrefsModule.provideSecurePrefs(context)
        val prefs2 = SecurePrefsModule.provideSecurePrefs(context)
        assertNotNull(prefs1)
        assertNotNull(prefs2)
    }
}
