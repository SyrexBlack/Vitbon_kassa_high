package com.vitbon.kkm.features.licensing.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val baseRoute = route.substringBefore("/")
    return baseRoute == NavRoutes.REPORTS || baseRoute == NavRoutes.STATUSES
}
