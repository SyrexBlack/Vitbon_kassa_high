package com.vitbon.kkm.features.statuses.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vitbon.kkm.features.statuses.domain.*

/**
 * Компактная строка статусов — встраивается в TopAppBar каждого экрана.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBar(
    status: SystemStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Интернет
        StatusIndicator(
            icon = if (status.internet == ConnectionStatus.AVAILABLE)
                Icons.Default.Wifi else Icons.Default.WifiOff,
            active = status.internet == ConnectionStatus.AVAILABLE,
            tooltip = "Интернет"
        )

        // Облако
        StatusIndicator(
            icon = Icons.Default.Cloud,
            active = status.cloudServer == ServiceStatus.OK,
            tooltip = "Облако: ${status.cloudServer}"
        )

        // ОФД
        StatusIndicator(
            icon = Icons.Default.Receipt,
            active = status.ofd.connected,
            tooltip = "ОФД: ${if (status.ofd.connected) "OK" else "ошибка"}"
        )

        // ЕГАИС
        StatusIndicator(
            icon = Icons.Default.LocalBar,
            active = status.egaisModule == ModuleStatus.ACTIVE,
            tooltip = "ЕГАИС: ${status.egaisModule}",
            show = status.egaisModule != ModuleStatus.INACTIVE
        )

        // ЧЗ
        StatusIndicator(
            icon = Icons.Default.QrCode,
            active = status.chaseznakModule == ModuleStatus.ACTIVE,
            tooltip = "ЧЗ: ${status.chaseznakModule}",
            show = status.chaseznakModule != ModuleStatus.INACTIVE
        )

        // Лицензия
        StatusIndicator(
            icon = Icons.Default.Lock,
            active = status.license == LicenseStatus.ACTIVE,
            tooltip = "Лицензия: ${status.license}",
            color = when (status.license) {
                LicenseStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                LicenseStatus.GRACE_PERIOD -> MaterialTheme.colorScheme.tertiary
                LicenseStatus.EXPIRED -> MaterialTheme.colorScheme.error
            }
        )

        Spacer(Modifier.weight(1f))

        // Счётчик чеков в очереди
        if (status.ofd.pendingChecks > 0) {
            Badge(containerColor = MaterialTheme.colorScheme.error) {
                Text("${status.ofd.pendingChecks}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    tooltip: String,
    show: Boolean = true,
    color: Color = if (active) Color(0xFF4CAF50) else Color(0xFFFF5252)
) {
    if (!show) return
    Icon(
        imageVector = icon,
        contentDescription = tooltip,
        modifier = Modifier.size(18.dp),
        tint = color
    )
}
