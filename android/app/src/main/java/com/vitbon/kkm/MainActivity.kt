package com.vitbon.kkm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vitbon.kkm.features.auth.presentation.AuthScreen
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.licensing.domain.LicenseChecker
import com.vitbon.kkm.features.licensing.presentation.LicenseBlockedScreen
import com.vitbon.kkm.features.sales.presentation.SalesScreen
import com.vitbon.kkm.ui.theme.VitbonTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var licenseChecker: LicenseChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Проверка лицензии при старте
        // (реальный вызов в Application, здесь — простой UI bootstrap)

        setContent {
            VitbonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Простая навигация: Auth → Sales
                    // Реальная реализация — Navigation Compose
                    LicenseBlockedScreen(
                        reason = "Проверка лицензии...",
                        onContactSupport = {}
                    )
                }
            }
        }
    }
}
