package com.vitbon.kkm.features.statuses.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vitbon.kkm.features.statuses.domain.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusDetailScreen(
    onBack: () -> Unit,
    viewModel: StatusViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статусы системы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { StatusCard(title = "Интернет", status = status.internet.name, icon = "🌐") }
            item { StatusCard(title = "Облачный сервер", status = status.cloudServer.name, icon = "☁️",
                subtitle = status.cloudLastSyncMs?.let { "Последняя синхр.: ${formatTs(it)}" }) }
            item {
                StatusCard(
                    title = "ОФД",
                    status = if (status.ofd.connected) "Подключено" else "Ошибка",
                    icon = "🧾",
                    subtitle = "В очереди: ${status.ofd.pendingChecks}"
                )
            }
            item { StatusCard(title = "Лицензия", status = status.license.name, icon = "🔒") }
            if (status.egaisModule != ModuleStatus.INACTIVE) {
                item { StatusCard(title = "УТМ ЕГАИС", status = status.egaisModule.name, icon = "🍺") }
            }
            if (status.chaseznakModule != ModuleStatus.INACTIVE) {
                item { StatusCard(title = "Честный ЗНАК", status = status.chaseznakModule.name, icon = "🏷️") }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    status: String,
    icon: String,
    subtitle: String? = null
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(status, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun formatTs(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> "${diff / 60_000} мин назад"
        else -> "${diff / 3_600_000} ч назад"
    }
}
