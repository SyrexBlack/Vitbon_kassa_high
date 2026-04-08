package com.vitbon.kkm.ui.navigation

import androidx.compose.runtime.Composable
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
}

@Composable
fun VitbonNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: String = NavRoutes.AUTH
) {
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
                onAdminMode = { /* отложено */ }
            )
        }

        composable(NavRoutes.SALES) { backStackEntry ->
            val cashierId = backStackEntry.arguments?.getString("cashierId") ?: "unknown"
            val cashierName = backStackEntry.arguments?.getString("cashierName") ?: ""
            val shiftId = backStackEntry.arguments?.getString("shiftId")

            SalesScreen(
                cashierName = cashierName,
                shiftNumber = 1,
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
