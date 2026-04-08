package com.vitbon.kkm.features.inventory.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Инвентаризация: фактический подсчёт → сравнение с учётом → расхождения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onBack: () -> Unit,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Инвентаризация") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
                actions = { TextButton(onClick = { viewModel.startInventory() }) { Text("Начать") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.items) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("Учёт: ${item.accounted} шт.", style = MaterialTheme.typography.bodySmall)
                            Text("Факт:", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = item.actual.toString(),
                                onValueChange = { viewModel.setActual(item.barcode, it.toDoubleOrNull() ?: 0.0) },
                                modifier = Modifier.width(80.dp),
                                singleLine = true
                            )
                            val diff = item.actual - item.accounted
                            Text(
                                "Δ ${if (diff >= 0) "+" else ""}$diff",
                                color = if (diff != 0.0) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Нажмите «Начать» для запуска инвентаризации",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

data class InventoryItem(val barcode: String, val name: String, val accounted: Double, var actual: Double = 0.0)
data class InventoryState(val items: List<InventoryItem> = emptyList(), val submitted: Boolean = false)
