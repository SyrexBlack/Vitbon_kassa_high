package com.vitbon.kkm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vitbon.kkm.features.licensing.domain.AppBlockingState
import com.vitbon.kkm.features.licensing.domain.LicenseChecker
import com.vitbon.kkm.features.licensing.presentation.LicenseBlockedScreen
import com.vitbon.kkm.features.licensing.presentation.isRouteAllowedWhenBlocked
import com.vitbon.kkm.features.reports.presentation.ReportsScreen
import com.vitbon.kkm.features.statuses.presentation.StatusDetailScreen
import com.vitbon.kkm.ui.navigation.VitbonNavHost
import com.vitbon.kkm.ui.theme.VitbonTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class BlockedDestination {
    REPORTS,
    STATUSES
}

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
                    var blockedDestination by remember { mutableStateOf<BlockedDestination?>(null) }

                    LaunchedEffect(Unit) {
                        scope.launch {
                            licenseChecker.check()
                        }
                    }

                    LaunchedEffect(blockingState) {
                        if (blockingState !is AppBlockingState.Blocked) {
                            blockedDestination = null
                        }
                    }

                    when (blockingState) {
                        is AppBlockingState.Blocked -> {
                            if (blockedDestination != null) {
                                BackHandler {
                                    blockedDestination = null
                                }
                            }
                            when (blockedDestination) {
                                BlockedDestination.REPORTS -> {
                                    ReportsScreen(onBack = { blockedDestination = null })
                                }
                                BlockedDestination.STATUSES -> {
                                    StatusDetailScreen(onBack = { blockedDestination = null })
                                }
                                null -> {
                                    LicenseBlockedScreen(
                                        reason = (blockingState as AppBlockingState.Blocked).reason,
                                        onContactSupport = { /* open support URL */ },
                                        onOpenReports = {
                                            if (isRouteAllowedWhenBlocked("reports")) {
                                                blockedDestination = BlockedDestination.REPORTS
                                            }
                                        },
                                        onOpenStatuses = {
                                            if (isRouteAllowedWhenBlocked("statuses")) {
                                                blockedDestination = BlockedDestination.STATUSES
                                            }
                                        }
                                    )
                                }
                            }
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
