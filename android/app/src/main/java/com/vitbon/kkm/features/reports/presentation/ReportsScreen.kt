package com.vitbon.kkm.features.reports.presentation

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отчёты") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Период
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.period == "shift", onClick = { viewModel.setPeriod("shift") }, label = { Text("Смена") })
                FilterChip(selected = state.period == "day", onClick = { viewModel.setPeriod("day") }, label = { Text("День") })
                FilterChip(selected = state.period == "week", onClick = { viewModel.setPeriod("week") }, label = { Text("Неделя") })
                FilterChip(selected = state.period == "month", onClick = { viewModel.setPeriod("month") }, label = { Text("Месяц") })
            }

            if (state.report != null) {
                val r = state.report!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        ReportCard(title = "💰 Выручка", items = listOf(
                            "Продажи" to "${r.totalSales / 100.0} ₽",
                            "Возвраты" to "-${r.totalReturns / 100.0} ₽",
                            "Чистыми" to "${(r.totalSales - r.totalReturns) / 100.0} ₽"
                        ))
                    }

                    item {
                        ReportCard(title = "💵 Наличные", items = listOf(
                            "Наличными" to "${r.cashTotal / 100.0} ₽",
                            "Картой" to "${r.cardTotal / 100.0} ₽",
                            "СБП" to "${r.sbpTotal / 100.0} ₽"
                        ))
                    }

                    item {
                        ReportCard(title = "📊 Статистика", items = listOf(
                            "Чеков продаж" to "${r.checkCount}",
                            "Чеков возврата" to "${r.returnCount}",
                            "Средний чек" to "${r.averageCheck / 100.0} ₽"
                        ))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ReportCard(title: String, items: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            items.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
