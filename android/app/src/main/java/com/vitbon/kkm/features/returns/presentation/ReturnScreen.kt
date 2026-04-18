package com.vitbon.kkm.features.returns.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.CameraState.ERROR_MAX_CAMERAS_IN_USE
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnScreen(
    onBack: () -> Unit,
    viewModel: ReturnViewModel = hiltViewModel()
) {
    val debugTag = "ReturnScannerDebug"
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showScannerDialog by remember { mutableStateOf(false) }
    var scannerError by remember { mutableStateOf<String?>(null) }

    val requestCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scannerError = null
            showScannerDialog = true
        } else {
            scannerError = "Разрешите доступ к камере для сканирования"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Возврат") }, navigationIcon = {
                IconButton(onClick = onBack) { Text("←") }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.checkInput,
                onValueChange = { viewModel.onCheckInput(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер чека или QR-код") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        scannerError = null
                        val hasCameraPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasCameraPermission) {
                            showScannerDialog = true
                        } else {
                            requestCameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Text("📷")
                    }
                }
            )

            if (state.originalCheck != null) {
                Text(
                    "Чек: ${state.originalCheck!!.id.take(8)}... / ${state.originalCheck!!.total / 100.0} ₽",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (state.returnItems.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.returnItems) { item ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${item.quantity} × ${item.price.rubles} ₽",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { viewModel.toggleItem(item.itemKey) }
                                )
                            }
                        }
                    }
                }

                Text(
                    "К возврату: ${state.returnTotal.rubles} ₽",
                    style = MaterialTheme.typography.titleMedium
                )

                Button(
                    onClick = { viewModel.processReturn() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.returnItems.any { it.selected }
                ) {
                    Text("Оформить возврат")
                }
            }

            if (scannerError != null) {
                Text(scannerError!!, color = MaterialTheme.colorScheme.error)
            }

            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showScannerDialog) {
        ReturnScannerDialog(
            onDismiss = { showScannerDialog = false },
            onValueScanned = { scannedValue ->
                Log.d(debugTag, "onValueScanned callback value='${scannedValue.take(120)}'")
                showScannerDialog = false
                viewModel.onCheckInput(scannedValue)
            },
            onScanError = { message ->
                showScannerDialog = false
                scannerError = message
            }
        )
    }
}

@Composable
private fun ReturnScannerDialog(
    onDismiss: () -> Unit,
    onValueScanned: (String) -> Unit,
    onScanError: (String) -> Unit
) {
    val debugTag = "ReturnScannerDebug"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var handled by remember { mutableStateOf(false) }
    var analyzedFrames by remember { mutableStateOf(0) }
    var cameraRef: Camera? by remember { mutableStateOf(null) }
    val sessionId = remember { System.currentTimeMillis().toString(16) }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_EAN_13
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(lifecycleOwner) {
        Log.d(debugTag, "scanner dialog compose start session=$sessionId")
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            Log.d(debugTag, "scanner lifecycle event=$event session=$sessionId")
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        val streamObserver = Observer<PreviewView.StreamState> { state ->
            Log.d(debugTag, "preview streamState=$state session=$sessionId")
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)

        val cameraStateObserver = Observer<CameraState> { state ->
            val errorCode = state.error?.code
            Log.d(
                debugTag,
                "cameraState type=${state.type} error=$errorCode session=$sessionId"
            )
            if (!handled && errorCode == ERROR_MAX_CAMERAS_IN_USE) {
                handled = true
                onScanError("Камера временно недоступна. Повторите попытку через несколько секунд")
            }
        }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            Log.d(debugTag, "camera provider listener fired session=$sessionId")
            val provider = runCatching { cameraProviderFuture.get() }
                .getOrElse {
                    Log.e(debugTag, "camera provider get failed session=$sessionId", it)
                    onScanError("Не удалось открыть камеру")
                    return@Runnable
                }
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        if (analyzedFrames == 0) {
                            Log.d(debugTag, "analyzer first frame session=$sessionId")
                        }
                        analyzedFrames += 1
                        if (analyzedFrames % 20 == 0) {
                            Log.d(debugTag, "analyzer heartbeat frames=$analyzedFrames")
                        }
                        val mediaImage = imageProxy.image
                        if (mediaImage == null || handled) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (handled) return@addOnSuccessListener
                                val value = barcodes
                                    .asSequence()
                                    .mapNotNull { it.rawValue?.trim() }
                                    .firstOrNull { it.isNotEmpty() }
                                if (barcodes.isNotEmpty()) {
                                    Log.d(debugTag, "barcode candidates count=${barcodes.size} first='${value?.take(120)}'")
                                }

                                if (value != null) {
                                    handled = true
                                    onValueScanned(value)
                                }
                            }
                            .addOnFailureListener { error ->
                                Log.e(debugTag, "barcodeScanner.process failure", error)
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    }
                }

            Log.d(debugTag, "calling unbindAll before bind session=$sessionId")
            runCatching { provider.unbindAll() }
            val bindResult = runCatching {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            }
            bindResult
                .onSuccess { camera ->
                    cameraRef = camera
                    Log.d(debugTag, "bindToLifecycle success session=$sessionId")
                    camera.cameraInfo.cameraState.observe(lifecycleOwner, cameraStateObserver)
                }
                .onFailure {
                    Log.e(debugTag, "bindToLifecycle failed session=$sessionId", it)
                }
            if (bindResult.isFailure && !handled) {
                onScanError("Не удалось запустить камеру")
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            Log.d(debugTag, "scanner dialog dispose session=$sessionId")
            runCatching { cameraRef?.cameraInfo?.cameraState?.removeObserver(cameraStateObserver) }
            runCatching { previewView.previewStreamState.removeObserver(streamObserver) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { cameraProvider?.unbindAll() }
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Text("Закрыть")
            }

            Text(
                text = "Наведите камеру на QR или штрихкод",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
            )
        }
    }
}
