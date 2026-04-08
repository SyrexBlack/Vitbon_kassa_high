package com.vitbon.kkm.features.correction.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionScreen(onBack: () -> Unit, viewModel: CorrectionViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Чек коррекции") }, navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Тип коррекции
            Text("Тип документа", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.type == "income",
                    onClick = { viewModel.setType("income") },
                    label = { Text("Приход") }
                )
                FilterChip(
                    selected = state.type == "expense",
                    onClick = { viewModel.setType("expense") },
                    label = { Text("Расход") }
                )
            }

            OutlinedTextField(
                value = state.reason,
                onValueChange = { viewModel.setReason(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Основание коррекции *") },
                placeholder = { Text("Например: Ошибка в сумме чека") },
                minLines = 2
            )

            OutlinedTextField(
                value = state.correctionNumber,
                onValueChange = { viewModel.setCorrectionNumber(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер чека коррекции") },
                singleLine = true
            )

            OutlinedTextField(
                value = state.cashAmount,
                onValueChange = { viewModel.setCashAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Сумма наличные (₽)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            OutlinedTextField(
                value = state.cardAmount,
                onValueChange = { viewModel.setCardAmount(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Сумма безналичные (₽)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { viewModel.submit() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = state.reason.isNotBlank() && !state.isSubmitting
            ) {
                if (state.isSubmitting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else Text("Сформировать чек коррекции")
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
