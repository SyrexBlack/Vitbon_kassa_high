package com.vitbon.kkm.features.egais.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EgaisScreen(
    onBack: () -> Unit,
    onVerifyAge: () -> Unit,
    viewModel: EgaisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val utmState by viewModel.utmState.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkUtmStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🍺 ЕГАИС") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            UtmStatusCard(utmState = utmState, onRetry = { viewModel.checkUtmStatus() })

            when (utmState) {
                is UtmUiState.Ready -> {
                    EgaisMenuItem(
                        icon = "📥",
                        title = "Приёмка накладных",
                        subtitle = "Загрузить накладную от поставщика",
                        onClick = { }
                    )
                    EgaisMenuItem(
                        icon = "🍾",
                        title = "Акт вскрытия тары",
                        subtitle = "При продаже из кег / бутылок",
                        onClick = { }
                    )
                    EgaisMenuItem(
                        icon = "🔞",
                        title = "Проверка возраста",
                        subtitle = "Цифровой ID Max (паспорт покупателя)",
                        onClick = onVerifyAge
                    )
                    EgaisMenuItem(
                        icon = "📊",
                        title = "Остатки ЕГАИС",
                        subtitle = "Сверка с остатками на складе",
                        onClick = { }
                    )
                    EgaisMenuItem(
                        icon = "🗑️",
                        title = "Списание",
                        subtitle = "Списать алкогольную продукцию",
                        onClick = { }
                    )
                }
                is UtmUiState.Checking -> { /* nothing extra */ }
                else -> { /* blocked — hint card shown via UtmStatusCard */ }
            }
        }
    }
}

@Composable
private fun UtmStatusCard(utmState: UtmUiState, onRetry: () -> Unit) {
    val (color, icon, title, subtitle) = when (utmState) {
        is UtmUiState.Ready -> listOf(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.CheckCircle,
            "УТМ подключён",
            "Модуль ЕГАИС готов к работе"
        )
        is UtmUiState.Checking -> listOf(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Default.Refresh,
            "Проверка УТМ...",
            "Подключение к УТМ"
        )
        is UtmUiState.NotConfigured -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Settings,
            "УТМ не настроен",
            "Настройте УТМ в разделе «Оборудование»"
        )
        is UtmUiState.Unreachable -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.WifiOff,
            "УТМ недоступен",
            utmState.reason
        )
        is UtmUiState.AuthError -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Lock,
            "Ошибка авторизации УТМ",
            utmState.message
        )
        is UtmUiState.Error -> listOf(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Warning,
            "Ошибка УТМ",
            utmState.message
        )
    }

    @Suppress("UNCHECKED_CAST")
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color as androidx.compose.ui.graphics.Color)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title as String, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle as String, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (utmState !is UtmUiState.Ready && utmState !is UtmUiState.Checking) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Повторить проверку")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EgaisMenuItem(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}