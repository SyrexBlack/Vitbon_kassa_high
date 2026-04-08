package com.vitbon.kkm.features.auth.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Экран установки/смены ПИН-кода для администратора.
 * Доступен только ADMIN/SENIOR_CASHIER.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPinSetupScreen(
    cashierId: String,
    cashierName: String,
    onBack: () -> Unit,
    viewModel: AdminPinViewModel = hiltViewModel()
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }  // 0 = enter new, 1 = confirm

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Смена ПИН-кода") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (step == 0) "Введите новый ПИН" else "Подтвердите ПИН",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(32.dp))

            Text(text = "${if (step == 0) pin else confirmPin}".padEnd(6, '·'), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(32.dp))

            // Цифровая клавиатура
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    listOf("1","2","3"), listOf("4","5","6"),
                    listOf("7","8","9"), listOf("","0","⌫")
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.size(72.dp))
                            } else {
                                NumberKey(key = key, onClick = {
                                    val target = if (step == 0) pin else confirmPin
                                    if (key == "⌫") {
                                        if (step == 0) pin = pin.dropLast(1)
                                        else confirmPin = confirmPin.dropLast(1)
                                    } else if (target.length < 6) {
                                        if (step == 0) {
                                            pin += key
                                            if (pin.length >= 4) step = 1
                                        } else {
                                            confirmPin += key
                                            if (confirmPin.length >= 4 && confirmPin == pin) {
                                                // viewModel.savePin(cashierId, pin)
                                                onBack()
                                            } else if (confirmPin.length >= 6) {
                                                confirmPin = ""
                                            }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}
