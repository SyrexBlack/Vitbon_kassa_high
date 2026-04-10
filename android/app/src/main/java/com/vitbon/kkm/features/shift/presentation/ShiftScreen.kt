package com.vitbon.kkm.features.shift.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vitbon.kkm.features.shift.domain.ShiftStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftScreen(
    onBack: () -> Unit,
    onShiftOpened: () -> Unit,
    viewModel: ShiftViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkStatus() }
    LaunchedEffect(state.shiftStatus) {
        if (state.shiftStatus == ShiftStatus.OPEN && state.opened) onShiftOpened()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Смена") }, navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            Text(
                text = when (state.shiftStatus) {
                    ShiftStatus.OPEN -> "✅ Смена открыта"
                    ShiftStatus.CLOSED -> "Смена закрыта"
                    ShiftStatus.EXPIRED -> "⚠️ Смена открыта более 24 часов"
                },
                style = MaterialTheme.typography.headlineMedium
            )

            if (state.shiftStatus == ShiftStatus.EXPIRED) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "Необходимо закрыть смену для продолжения работы",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            when (state.shiftStatus) {
                ShiftStatus.OPEN -> {
                    Button(onClick = { viewModel.printXReport() }, modifier = Modifier.fillMaxWidth()) {
                        Text("X-отчёт (без закрытия)")
                    }
                    Button(
                        onClick = { viewModel.closeShift() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Закрыть смену (Z-отчёт)")
                    }
                }
                ShiftStatus.CLOSED -> {
                    Button(onClick = { viewModel.openShift() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Открыть смену")
                    }
                }
                ShiftStatus.EXPIRED -> {
                    Button(
                        onClick = { viewModel.closeShift() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Закрыть смену (Z-отчёт)")
                    }
                    OutlinedButton(onClick = { viewModel.openShift() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Продолжить без закрытия")
                    }
                }
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
