package com.vitbon.kkm.features.licensing.presentation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitbon.kkm.features.licensing.domain.LicenseStatus

@Composable
fun LicenseStatusBar(
    status: LicenseStatus,
    modifier: Modifier = Modifier
) {
    val (icon, text, color) = when (status) {
        is LicenseStatus.Active -> Triple(
            Icons.Default.CheckCircle,
            "Лицензия активна",
            MaterialTheme.colorScheme.primary
        )
        is LicenseStatus.GracePeriod -> Triple(
            Icons.Default.Warning,
            "Grace ${status.daysLeft} дн.",
            MaterialTheme.colorScheme.tertiary
        )
        is LicenseStatus.Expired -> Triple(
            Icons.Default.Warning,
            "Лицензия просрочена",
            MaterialTheme.colorScheme.error
        )
        is LicenseStatus.Error -> Triple(
            Icons.Default.Warning,
            "Ошибка проверки",
            MaterialTheme.colorScheme.error
        )
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
