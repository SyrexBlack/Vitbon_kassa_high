package com.vitbon.kkm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.licensing.domain.LicenseChecker
import com.vitbon.kkm.features.licensing.presentation.LicenseBlockedScreen
import com.vitbon.kkm.ui.navigation.VitbonNavHost
import com.vitbon.kkm.ui.theme.VitbonTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var licenseChecker: LicenseChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VitbonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val blockingState by licenseChecker.blockingState.collectAsState()
                    val scope = rememberCoroutineScope()

                    LaunchedEffect(Unit) {
                        scope.launch {
                            licenseChecker.check()
                        }
                    }

                    when (blockingState) {
                        is AppBlockingState.Blocked -> {
                            LicenseBlockedScreen(
                                reason = (blockingState as AppBlockingState.Blocked).reason,
                                onContactSupport = { /* open support URL */ }
                            )
                        }
                        else -> {
                            VitbonNavHost()
                        }
                    }
                }
            }
        }
    }
}
