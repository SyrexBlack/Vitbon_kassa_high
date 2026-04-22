package com.vitbon.kkm.ui.navigation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitbon.kkm.core.features.FeatureFlag
import com.vitbon.kkm.features.auth.presentation.AuthScreen
import com.vitbon.kkm.features.cashdrawer.presentation.CashDrawerScreen
import com.vitbon.kkm.features.chaseznak.presentation.ChaseznakScreen
import com.vitbon.kkm.features.correction.presentation.CorrectionScreen
import com.vitbon.kkm.features.egais.presentation.EgaisScreen
import com.vitbon.kkm.features.reports.presentation.ReportsScreen
import com.vitbon.kkm.features.returns.presentation.ReturnScreen
import com.vitbon.kkm.features.sales.presentation.SalesScreen
import com.vitbon.kkm.features.shift.presentation.ShiftScreen
import com.vitbon.kkm.features.statuses.presentation.StatusDetailScreen

object NavRoutes {
    const val AUTH = "auth"
    const val SALES = "sales/{cashierId}/{cashierName}/{shiftId}"
    const val SHIFT = "shift"
    const val RETURN = "return"
    const val REPORTS = "reports"
    const val STATUSES = "statuses"
    const val CORRECTION = "correction"
    const val CASH_DRAWER = "cash_drawer"
    const val EGAIS = "egais"
    const val AGE_VERIFY = "age_verify"
    const val CHASEZNAK = "chaseznak"
}

@Composable
fun VitbonNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoutes.AUTH
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("vitbon_prefs", Context.MODE_PRIVATE)
    }
    var backendWarningMessage by remember {
        mutableStateOf(prefs.getString("backend_auth_warning", null))
    }
    val enabledFeatures by rememberEnabledFeatures(prefs)

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.AUTH) {
            AuthScreen(
                onAuthSuccess = { cashierId, cashierName ->
                    navController.navigate("sales/$cashierId/$cashierName/null") {
                        popUpTo(NavRoutes.AUTH) { inclusive = true }
                    }
                },
                onAdminMode = { /* отложено */ },
                onBackendWarning = {
                    backendWarningMessage = it
                    prefs.edit().putString("backend_auth_warning", it).apply()
                }
            )
        }

        composable(NavRoutes.SALES) { backStackEntry ->
            val cashierName = backStackEntry.arguments?.getString("cashierName") ?: ""

            SalesScreen(
                cashierName = cashierName,
                shiftNumber = 1,
                warningMessage = backendWarningMessage,
                onWarningShown = {
                    backendWarningMessage = null
                    prefs.edit().remove("backend_auth_warning").apply()
                },
                onOpenShift = { navController.navigate(NavRoutes.SHIFT) },
                onOpenReturn = { navController.navigate(NavRoutes.RETURN) },
                onOpenCorrection = { navController.navigate(NavRoutes.CORRECTION) },
                onOpenCashDrawer = { navController.navigate(NavRoutes.CASH_DRAWER) },
                onOpenReports = { navController.navigate(NavRoutes.REPORTS) },
                onOpenStatuses = { navController.navigate(NavRoutes.STATUSES) },
                onOpenEgais = { navController.navigate(NavRoutes.EGAIS) },
                onOpenChaseznak = { navController.navigate(NavRoutes.CHASEZNAK) },
                enabledFeatures = enabledFeatures
            )
        }

        composable(NavRoutes.SHIFT) {
            ShiftScreen(
                onBack = { navController.popBackStack() },
                onShiftOpened = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.RETURN) {
            ReturnScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.REPORTS) {
            ReportsScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.STATUSES) {
            StatusDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.CORRECTION) {
            CorrectionScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.CASH_DRAWER) {
            CashDrawerScreen(onBack = { navController.popBackStack() })
        }

        composable(NavRoutes.EGAIS) {
            if (FeatureFlag.EGAAIS_ENABLED !in enabledFeatures) {
                LaunchedFeatureRedirect(navController)
            } else {
                EgaisScreen(
                    onBack = { navController.popBackStack() },
                    onVerifyAge = {
                        if (FeatureFlag.CHASEZNAK_ENABLED in enabledFeatures) {
                            navController.navigate(NavRoutes.CHASEZNAK)
                        }
                    }
                )
            }
        }

        composable(NavRoutes.CHASEZNAK) {
            if (FeatureFlag.CHASEZNAK_ENABLED !in enabledFeatures) {
                LaunchedFeatureRedirect(navController)
            } else {
                ChaseznakScreen(
                    onBack = { navController.popBackStack() },
                    onSellComplete = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
private fun rememberEnabledFeatures(
    prefs: SharedPreferences
): State<Set<FeatureFlag>> {
    val enabled = remember(prefs) { mutableStateOf(loadEnabledFeatures(prefs)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("feature_")) {
                enabled.value = loadEnabledFeatures(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return enabled
}

private fun loadEnabledFeatures(prefs: SharedPreferences): Set<FeatureFlag> {
    return FeatureFlag.entries.filter { flag ->
        prefs.getBoolean("feature_${flag.name}", false)
    }.toSet()
}

@Composable
private fun LaunchedFeatureRedirect(navController: NavHostController) {
    LaunchedEffect(Unit) {
        val popped = navController.popBackStack()
        if (!popped) {
            navController.navigate(NavRoutes.AUTH) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }
}
