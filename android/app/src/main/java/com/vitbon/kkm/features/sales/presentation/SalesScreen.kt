package com.vitbon.kkm.features.sales.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vitbon.kkm.features.sales.domain.CartItem
import com.vitbon.kkm.features.sales.domain.SaleResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    cashierName: String,
    shiftNumber: Int,
    onOpenShift: () -> Unit,
    onOpenReturn: () -> Unit,
    onOpenCorrection: () -> Unit,
    onOpenCashDrawer: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenStatuses: () -> Unit,
    viewModel: SalesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saleResult) {
        if (state.saleResult is SaleResult.Success) {
            // Показать подтверждение → очистить корзину
            viewModel.clearCart()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$cashierName  |  Смена $shiftNumber") },
                actions = {
                    IconButton(onClick = onOpenStatuses) { Icon(Icons.Default.Info, "Статусы") }
                    IconButton(onClick = onOpenReports) { Icon(Icons.Default.Assessment, "Отчёты") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Поиск / сканер
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Штрихкод или поиск...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { /* сканер */ }) {
                        Icon(Icons.Default.QrCodeScanner, "Сканер")
                    }
                },
                singleLine = true
            )

            // Результат сканирования
            if (state.scanError != null) {
                Text(
                    text = "⚠️ Товар не найден: ${state.scanError}",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Корзина
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.cart.items, key = { it.productId }) { item ->
                    CartItemRow(
                        item = item,
                        onQuantityChange = { viewModel.updateQuantity(item.productId, it) },
                        onRemove = { viewModel.removeItem(item.productId) }
                    )
                }

                if (state.cart.items.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Скидка", style = MaterialTheme.typography.bodyMedium)
                            Text("-${state.cart.globalDiscount.rubles} ₽",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                        Divider()
                    }
                }
            }

            // Итого
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ИТОГО: ${state.cart.total.rubles} ₽",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // Способ оплаты
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PaymentChip(
                            label = "💵 Наличные",
                            selected = state.cart.paymentType == com.vitbon.kkm.core.fiscal.model.PaymentType.CASH,
                            onClick = { viewModel.setPayment(com.vitbon.kkm.core.fiscal.model.PaymentType.CASH) },
                            modifier = Modifier.weight(1f)
                        )
                        PaymentChip(
                            label = "💳 Карта",
                            selected = state.cart.paymentType == com.vitbon.kkm.core.fiscal.model.PaymentType.CARD,
                            onClick = { viewModel.setPayment(com.vitbon.kkm.core.fiscal.model.PaymentType.CARD) },
                            modifier = Modifier.weight(1f)
                        )
                        PaymentChip(
                            label = "📱 QR",
                            selected = state.cart.paymentType == com.vitbon.kkm.core.fiscal.model.PaymentType.SBP,
                            onClick = { viewModel.setPayment(com.vitbon.kkm.core.fiscal.model.PaymentType.SBP) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.processSale() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = state.cart.items.isNotEmpty() && !state.isProcessing
                    ) {
                        Text(
                            if (state.isProcessing) "ОБРАБОТКА..." else "ПРОДАТЬ  →",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (state.saleResult is SaleResult.FiscalError) {
                        val err = state.saleResult as SaleResult.FiscalError
                        Text("${err.code}: ${err.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }

                    // Результат успешной продажи
                    if (state.saleResult is SaleResult.Success) {
                        val res = state.saleResult as SaleResult.Success
                        Text("✅ Чек ${res.checkId.take(8)}... ФП: ${res.fiscalSign.take(8)}...",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onQuantityChange: (Double) -> Unit,
    onRemove: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall)
                Text("${item.price.rubles} ₽", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onQuantityChange(item.quantity - 1) }, modifier = Modifier.size(32.dp)) {
                    Text("-", style = MaterialTheme.typography.titleMedium)
                }
                Text("${item.quantity}", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 32.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { onQuantityChange(item.quantity + 1) }, modifier = Modifier.size(32.dp)) {
                    Text("+", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text("${item.total.rubles} ₽", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Удалить", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PaymentChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center)
    }
}
