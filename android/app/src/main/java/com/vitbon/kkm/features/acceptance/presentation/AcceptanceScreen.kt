package com.vitbon.kkm.features.acceptance.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Приёмка товара: сканирование ШК → количество → отправка в облако.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcceptanceScreen(
    onBack: () -> Unit,
    viewModel: AcceptanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приёмка товара") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    IconButton(onClick = { /* открыть сканер */ }) {
                        Icon(Icons.Default.Scanner, contentDescription = "Сканер ШК")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.addItem() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Добавить товар") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(16.dp))
                        Text("Нажмите + для добавления товара",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                // Список принятых товаров
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        AcceptanceItemCard(
                            item = item,
                            onQuantityChange = { viewModel.updateQuantity(item.id, it) },
                            onRemove = { viewModel.removeItem(item.id) }
                        )
                    }
                }

                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Итого: ${state.items.sumOf { it.quantity * it.price }} ₽",
                        style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = state.items.isNotEmpty() && !state.isSubmitting
                    ) {
                        if (state.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp))
                        else Text("Отправить")
                    }
                }
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AcceptanceItemCard(
    item: AcceptanceItem,
    onQuantityChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.barcode ?: "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) { Text("✕") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Кол-во:", modifier = Modifier.width(80.dp))
                OutlinedTextField(
                    value = item.quantity.toString(),
                    onValueChange = { onQuantityChange(it.toDoubleOrNull() ?: 0.0) },
                    modifier = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Spacer(Modifier.width(16.dp))
                Text("${item.quantity * item.price} ₽",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

data class AcceptanceItem(
    val id: Long,
    val barcode: String,
    val name: String,
    val quantity: Double,
    val price: Double
)
