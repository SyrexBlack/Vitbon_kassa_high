package com.vitbon.kkm.features.auth.presentation

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vitbon.kkm.core.features.FeatureFlag
import com.vitbon.kkm.features.auth.domain.AuthResult

@Composable
fun AuthScreen(
    onAuthSuccess: (String, String) -> Unit,  // cashierId, cashierName
    onAdminMode: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.result) {
        when (val result = state.result) {
            is AuthResult.Success -> {
                onAuthSuccess(result.cashier.id, result.cashier.name)
                viewModel.reset()
            }
            is AuthResult.Error -> {
                // Вибрация при ошибке
                val vibrator = context.getSystemService(Vibrator::class.java)
                vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                viewModel.reset()
            }
            null -> {}
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Text(
                text = "VITBON",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Касса",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            // Индикатор введённых цифр
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (index < state.pin.length)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Сообщение об ошибке
            if (state.result is AuthResult.Error) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = (state.result as AuthResult.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.weight(1f))

            // Цифровая клавиатура
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("A", "0", "⌫")
                ).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { key ->
                            NumberKey(
                                key = key,
                                onClick = {
                                    when (key) {
                                        "⌫" -> viewModel.deleteLast()
                                        "A" -> onAdminMode()
                                        else -> viewModel.appendDigit(key)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumberKey(
    key: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (key == "⌫") {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = key,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
