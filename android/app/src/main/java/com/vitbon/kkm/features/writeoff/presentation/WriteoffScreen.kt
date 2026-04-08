package com.vitbon.kkm.features.writeoff.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteoffScreen(
    onBack: () -> Unit,
    viewModel: WriteoffViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showReasonDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Списание товара") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.items) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text("Кол-во: ${item.quantity}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { viewModel.remove(item.barcode) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            OutlinedTextField(
                value = state.reason,
                onValueChange = { viewModel.setReason(it) },
                label = { Text("Причина списания") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showReasonDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.items.isNotEmpty() && state.reason.isNotBlank()
            ) {
                Text("Списать")
            }
        }
    }
}
