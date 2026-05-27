package com.vitbon.kkm.features.licensing.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitbon.kkm.features.statuses.domain.ConnectionStatus
import com.vitbon.kkm.features.statuses.domain.LicenseStatus
import com.vitbon.kkm.features.statuses.domain.ModuleStatus
import com.vitbon.kkm.features.statuses.domain.OfdStatus
import com.vitbon.kkm.features.statuses.domain.ServiceStatus
import com.vitbon.kkm.features.statuses.domain.StatusOperation
import com.vitbon.kkm.features.statuses.domain.StatusOperationPolicy
import com.vitbon.kkm.features.statuses.domain.SystemStatus
import com.vitbon.kkm.ui.navigation.NavRoutes

@Composable
fun LicenseBlockedScreen(
    reason: String,
    onContactSupport: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenStatuses: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⛔",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Лицензия неактивна",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenReports,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Отчёты")
                }
                OutlinedButton(
                    onClick = onOpenStatuses,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Статусы")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onContactSupport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Связаться с поддержкой")
            }
        }
    }
}

fun isRouteAllowedWhenBlocked(route: String): Boolean {
    val operation = when (route.substringBefore("/")) {
        NavRoutes.SALES.substringBefore("/") -> StatusOperation.SALE
        NavRoutes.RETURN -> StatusOperation.RETURN
        NavRoutes.SHIFT -> StatusOperation.SHIFT
        NavRoutes.CASH_DRAWER -> StatusOperation.CASH_DRAWER
        NavRoutes.CORRECTION -> StatusOperation.CORRECTION
        NavRoutes.REPORTS -> StatusOperation.REPORTS
        NavRoutes.STATUSES -> StatusOperation.STATUSES
        NavRoutes.EGAIS -> StatusOperation.EGAIS
        NavRoutes.CHASEZNAK -> StatusOperation.CHASEZNAK
        else -> return false
    }

    return StatusOperationPolicy.evaluate(blockedModeStatus, operation).allowed
}

private val blockedModeStatus = SystemStatus(
    internet = ConnectionStatus.UNKNOWN,
    cloudServer = ServiceStatus.UNKNOWN,
    cloudLastSyncMs = null,
    ofd = OfdStatus(pendingChecks = 0, connected = true),
    chaseznakModule = ModuleStatus.INACTIVE,
    egaisModule = ModuleStatus.INACTIVE,
    license = LicenseStatus.EXPIRED
)
