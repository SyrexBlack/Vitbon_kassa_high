package com.vitbon.kkm.features.egais.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vitbon.kkm.core.fiscal.model.AgeVerificationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgeVerificationScreen(
    onBack: () -> Unit,
    onConfirmed: () -> Unit,
    onDenied: () -> Unit,
    viewModel: AgeVerificationViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.result) {
        when {
            state.result is AgeVerificationResult && (state.result?.confirmed == true) -> {
                // Дождаться подтверждения от кассира
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔞 Проверка возраста") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            Text("📷", style = MaterialTheme.typography.displayLarge)

            Text(
                "Сканируйте QR-код с цифрового паспорта покупателя",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = state.input,
                onValueChange = { viewModel.onInput(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("QR-код или код верификации") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { /* сканер */ }) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканер")
                    }
                }
            )

            if (state.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Проверка возраста...")
                }
            }

            Button(
                onClick = { viewModel.verify() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = state.input.isNotBlank() && !state.isLoading
            ) {
                Text("Проверить")
            }

            when {
                state.result?.confirmed == true -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "✅ Возраст подтверждён",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "ID верификации: ${state.result?.verificationId}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onConfirmed, modifier = Modifier.fillMaxWidth()) {
                                Text("Продолжить продажу")
                            }
                        }
                    }
                }

                state.result?.confirmed == false -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "❌ Возраст не подтверждён",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                state.result?.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onDenied,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Заблокировать продажу")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Для проверки требуется согласие покупателя на обработку персональных данных",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
