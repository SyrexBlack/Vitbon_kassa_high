package com.vitbon.kkm.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vitbon.kkm.features.auth.presentation.AuthScreen
import com.vitbon.kkm.features.cashdrawer.presentation.CashDrawerScreen
import com.vitbon.kkm.features.correction.presentation.CorrectionScreen
import com.vitbon.kkm.features.reports.presentation.ReportsScreen
import com.vitbon.kkm.features.sales.presentation.SalesScreen
import com.vitbon.kkm.features.shift.presentation.ShiftScreen
import com.vitbon.kkm.features.statuses.presentation.StatusDetailScreen

object NavRoutes {
    const val AUTH = "auth"
    const val SALES = "sales/{cashierId}/{cashierName}/{shiftId}"
    const val SHIFT = "shift"
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
            val cashierId = backStackEntry.arguments?.getString("cashierId") ?: "unknown"
            val cashierName = backStackEntry.arguments?.getString("cashierName") ?: ""
            val shiftId = backStackEntry.arguments?.getString("shiftId")

            SalesScreen(
                cashierName = cashierName,
                shiftNumber = 1,
                warningMessage = backendWarningMessage,
                onWarningShown = {
                    backendWarningMessage = null
                    prefs.edit().remove("backend_auth_warning").apply()
                },
                onOpenShift = { navController.navigate(NavRoutes.SHIFT) },
                onOpenReturn = { /* TODO */ },
                onOpenCorrection = { navController.navigate(NavRoutes.CORRECTION) },
                onOpenCashDrawer = { navController.navigate(NavRoutes.CASH_DRAWER) },
                onOpenReports = { navController.navigate(NavRoutes.REPORTS) },
                onOpenStatuses = { navController.navigate(NavRoutes.STATUSES) }
            )
        }

        composable(NavRoutes.SHIFT) {
            ShiftScreen(
                onBack = { navController.popBackStack() },
                onShiftOpened = { navController.popBackStack() }
            )
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
    }
}
