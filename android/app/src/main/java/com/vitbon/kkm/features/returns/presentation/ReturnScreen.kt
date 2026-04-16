package com.vitbon.kkm.features.returns.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnScreen(
    onBack: () -> Unit,
    viewModel: ReturnViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showSelectDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Возврат") }, navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ввод чека
            OutlinedTextField(
                value = state.checkInput,
                onValueChange = { viewModel.onCheckInput(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер чека или QR-код") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { showSelectDialog = true }) {
                        Text("📷")
                    }
                }
            )

            if (state.originalCheck != null) {
                Text(
                    "Чек: ${state.originalCheck!!.id.take(8)}... / ${state.originalCheck!!.total / 100.0} ₽",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.returnItems.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.returnItems) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${item.quantity} × ${item.price.rubles} ₽",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { viewModel.toggleItem(item.itemKey) }
                                )
                            }
                        }
                    }
                }

                Text("К возврату: ${state.returnTotal.rubles} ₽",
                    style = MaterialTheme.typography.titleMedium)

                Button(
                    onClick = { viewModel.processReturn() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.returnItems.any { it.selected }
                ) {
                    Text("Оформить возврат")
                }
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
