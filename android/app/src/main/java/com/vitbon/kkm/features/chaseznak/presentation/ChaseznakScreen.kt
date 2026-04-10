package com.vitbon.kkm.features.chaseznak.presentation

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
import com.vitbon.kkm.features.chaseznak.domain.ChaseznakStatus
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChaseznakScreen(
    onBack: () -> Unit,
    onSellComplete: () -> Unit,
    viewModel: ChaseznakViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🏷️ Маркировка") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Сканирование
            OutlinedTextField(
                value = state.scanInput,
                onValueChange = { viewModel.onScan(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("DataMatrix код или сканер ШК") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { /* открыть камеру */ }) {
                        Icon(Icons.Default.QrCodeScanner, "Сканер")
                    }
                }
            )

            if (state.isValidating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Проверка кода...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Результаты
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.items) { item ->
                    ChaseznakItemCard(item = item, onRemove = { viewModel.remove(item.code) })
                }
            }

            // Итог
            if (state.items.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("К выбытию: ${state.items.size} шт.",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.sellAll(onSellComplete) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isSelling && state.items.all { it.status == ChaseznakStatus.OK }
                        ) {
                            if (state.isSelling) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            else Text("Выбытие при продаже")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChaseznakItemCard(item: ChaseznakValidatedItem, onRemove: () -> Unit) {
    val color = when (item.status) {
        ChaseznakStatus.OK -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Row(modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.code.take(20) + "...", style = MaterialTheme.typography.bodySmall)
                Text(
                    when (item.status) {
                        ChaseznakStatus.OK -> "✅ Можно продавать"
                        ChaseznakStatus.NOT_IN_CIRCULATION -> "❌ Не в обороте"
                        ChaseznakStatus.ALREADY_SOLD -> "❌ Уже выбыл"
                        ChaseznakStatus.EXPIRED -> "⚠️ Истёк срок"
                        ChaseznakStatus.ERROR -> "⚠️ Ошибка"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Удалить") }
        }
    }
}

data class ChaseznakValidatedItem(
    val code: String,
    val status: ChaseznakStatus,
    val productName: String?
)
