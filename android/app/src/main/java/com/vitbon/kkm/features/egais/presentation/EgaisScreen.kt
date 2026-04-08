package com.vitbon.kkm.features.egais.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Экран модуля ЕГАИС.
 * Видим только при FeatureFlag.EGAAIS_ENABLED = true.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EgaisScreen(
    onBack: () -> Unit,
    onVerifyAge: () -> Unit,
    viewModel: EgaisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val utmStatus by viewModel.utmStatus.collectAsState()

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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Статус УТМ
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (utmStatus) MaterialTheme.colorScheme.primaryContainer
                                     else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (utmStatus) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (utmStatus) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (utmStatus) "УТМ подключён"
                                   else "⚠️ УТМ недоступен",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!utmStatus) {
                            Text("Продажа алкоголя заблокирована",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (!utmStatus) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Для работы с ЕГАИС:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Text("1. Подключите УТМ ЕГАИС", style = MaterialTheme.typography.bodyMedium)
                        Text("2. Проверьте настройки в разделе «Оборудование»", style = MaterialTheme.typography.bodyMedium)
                        Text("3. Перезапустите приложение", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                // Меню ЕГАИС
                EgaisMenuItem(
                    icon = "📥",
                    title = "Приёмка накладных",
                    subtitle = "Загрузить накладную от поставщика",
                    onClick = { /* открыть сканер / выбор */ }
                )

                EgaisMenuItem(
                    icon = "🍾",
                    title = "Акт вскрытия тары",
                    subtitle = "При продаже из кег / бутылок",
                    onClick = { /* выбор позиции */ }
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
                    onClick = { /* инвентаризация */ }
                )

                EgaisMenuItem(
                    icon = "🗑️",
                    title = "Списание",
                    subtitle = "Списать алкогольную продукцию",
                    onClick = { /* выбор товара */ }
                )
            }
        }
    }
}

@Composable
private fun EgaisMenuItem(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
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
