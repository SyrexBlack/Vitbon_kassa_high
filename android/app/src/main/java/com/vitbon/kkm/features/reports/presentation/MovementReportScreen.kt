package com.vitbon.kkm.features.reports.presentation

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
fun MovementReportScreen(
    onBack: () -> Unit,
    viewModel: MovementReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Движение товара") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.period == "day",
                    onClick = { viewModel.setPeriod("day") },
                    label = { Text("День") }
                )
                FilterChip(
                    selected = state.period == "week",
                    onClick = { viewModel.setPeriod("week") },
                    label = { Text("Неделя") }
                )
                FilterChip(
                    selected = state.period == "month",
                    onClick = { viewModel.setPeriod("month") },
                    label = { Text("Месяц") }
                )
            }

            if (state.report != null) {
                val r = state.report!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📦 Остаток на начало", style = MaterialTheme.typography.titleSmall)
                                Text("${r.openingStock} шт.", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }

                    item {
                        MovementRow("📥", "Приход", r.income, "+")
                        MovementRow("💰", "Продажи", r.sales, "-")
                        MovementRow("↩️", "Возвраты", r.returns, "+")
                        MovementRow("🗑️", "Списание", r.writeoff, "-")
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("📦 Остаток на конец", style = MaterialTheme.typography.titleSmall)
                                Text("${r.closingStock} шт.", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }

                    item {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Детализация по товарам",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    items(r.items) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(item.name, style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Приход: ${item.income}", style = MaterialTheme.typography.bodySmall)
                                    Text("Расход: ${item.sales}", style = MaterialTheme.typography.bodySmall)
                                    Text("Остаток: ${item.balance}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun MovementRow(emoji: String, label: String, value: Int, sign: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji)
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
            Text(
                "$sign$value шт.",
                style = MaterialTheme.typography.titleMedium,
                color = if (sign == "+") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}
