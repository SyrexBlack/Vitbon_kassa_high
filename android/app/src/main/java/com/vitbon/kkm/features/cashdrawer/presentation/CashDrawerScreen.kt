package com.vitbon.kkm.features.cashdrawer.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashDrawerScreen(onBack: () -> Unit, viewModel: CashDrawerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Внесение / Изъятие") }, navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tabs
            TabRow(
                selectedTabIndex = if (state.type == "in") 0 else 1
            ) {
                Tab(selected = state.type == "in", onClick = { viewModel.setType("in") }) { Text("💵 Внесение") }
                Tab(selected = state.type == "out", onClick = { viewModel.setType("out") }) { Text("💸 Изъятие") }
            }

            OutlinedTextField(
                value = state.amount,
                onValueChange = { viewModel.setAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Сумма (₽)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                prefix = { Text("₽ ") }
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = { viewModel.setComment(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий (необязательно)") },
                singleLine = true
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = state.amount.isNotBlank() && !state.isSubmitting
            ) {
                if (state.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text(if (state.type == "in") "Внести наличные" else "Изъять наличные")
            }

            if (state.success != null) {
                Text("✅ ${state.success}", color = MaterialTheme.colorScheme.primary)
            }
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
