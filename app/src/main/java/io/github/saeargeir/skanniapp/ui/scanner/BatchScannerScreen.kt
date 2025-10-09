package io.github.saeargeir.skanniapp.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.saeargeir.skanniapp.data.BatchScanData
import io.github.saeargeir.skanniapp.data.ScannedReceiptData
import io.github.saeargeir.skanniapp.data.ProcessingStatus
import io.github.saeargeir.skanniapp.utils.IcelandicInvoiceParser
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import android.hardware.camera2.CaptureRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchScannerScreen(
    onComplete: (BatchScanData) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    
    // Batch state management
    var batchData by remember { mutableStateOf(BatchScanData()) }
    var scanningMode by remember { mutableStateOf(BatchScanMode.CAPTURE) }
    var isProcessing by remember { mutableStateOf(false) }
    var currentProcessingIndex by remember { mutableStateOf(0) }
    
    // Camera state
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    
    // Live scanning feedback
    var liveStatus by remember { mutableStateOf("Leita að reikningi...") }
    var edgeDetected by remember { mutableStateOf(false) }
    var qualityScore by remember { mutableStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Batch Skanning", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${batchData.getReceiptCount()} reikningar skannaðir", 
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { 
                        Icon(Icons.Default.Close, contentDescription = "Loka") 
                    }
                },
                actions = {
                    if (batchData.getReceiptCount() > 0) {
                        TextButton(
                            onClick = { 
                                scanningMode = BatchScanMode.REVIEW 
                            }
                        ) {
                            Text("Skoða (${batchData.getReceiptCount()})")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        
        if (!hasPermission) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Vantar að leyfa myndavél", style = MaterialTheme.typography.titleMedium)
                }
            }
            return@Scaffold
        }

        when (scanningMode) {
            BatchScanMode.CAPTURE -> {
                CaptureMode(
                    modifier = Modifier.padding(padding),
                    batchData = batchData,
                    onCaptureReceipt = { uri, quality, hasEdges ->
                        val receipt = ScannedReceiptData(
                            imageUri = uri,
                            qualityScore = quality,
                            edgeDetected = hasEdges
                        )
                        batchData = batchData.copy(
                            scannedReceipts = batchData.scannedReceipts.toMutableList().apply { add(receipt) }
                        )
                        liveStatus = "Reikningur vistaður! (${batchData.getReceiptCount()})"
                    },
                    onUpdateStatus = { status, quality, edges ->
                        liveStatus = status
                        qualityScore = quality
                        edgeDetected = edges
                    },
                    executor = executor,
                    recognizer = recognizer,
                    lifecycleOwner = lifecycleOwner,
                    torchEnabled = torchEnabled,
                    onTorchToggle = { enabled ->
                        torchEnabled = enabled
                        cameraControl?.enableTorch(enabled)
                    },
                    onCameraReady = { control ->
                        cameraControl = control
                    }
                )
            }
            
            BatchScanMode.REVIEW -> {
                ReviewMode(
                    modifier = Modifier.padding(padding),
                    batchData = batchData,
                    isProcessing = isProcessing,
                    currentProcessingIndex = currentProcessingIndex,
                    onBackToCapture = { scanningMode = BatchScanMode.CAPTURE },
                    onProcessBatch = {
                        isProcessing = true
                        currentProcessingIndex = 0
                        // Start batch processing
                        processBatchReceipts(
                            batchData = batchData,
                            recognizer = recognizer,
                            onProgress = { index ->
                                currentProcessingIndex = index
                            },
                            onComplete = { processedBatch ->
                                isProcessing = false
                                onComplete(processedBatch)
                            }
                        )
                    },
                    onRemoveReceipt = { receiptId ->
                        batchData = batchData.copy(
                            scannedReceipts = batchData.scannedReceipts.filterNot { it.id == receiptId }.toMutableList()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CaptureMode(
    modifier: Modifier = Modifier,
    batchData: BatchScanData,
    onCaptureReceipt: (Uri, Float, Boolean) -> Unit,
    onUpdateStatus: (String, Float, Boolean) -> Unit,
    executor: java.util.concurrent.ExecutorService,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    lifecycleOwner: LifecycleOwner,
    torchEnabled: Boolean,
    onTorchToggle: (Boolean) -> Unit,
    onCameraReady: (CameraControl) -> Unit
) {
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    
    Box(modifier = modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    // Configure Preview with Camera2 interop for continuous AF
                    val previewBuilder = Preview.Builder()
                    Camera2Interop.Extender(previewBuilder).apply {
                        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }
                    val preview = previewBuilder.build().apply {
                        setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    // Configure ImageCapture with interop and prioritize quality for batch captures
                    val imageCaptureBuilder = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    Camera2Interop.Extender(imageCaptureBuilder).apply {
                        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }
                    val imageCapture = imageCaptureBuilder.build().also { imageCaptureRef = it }
                    
                    val analysisBuilder = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(1280, 720))
                    Camera2Interop.Extender(analysisBuilder).apply {
                        setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }
                    val analysis = analysisBuilder.build()
                    
                    // Real-time analysis for edge detection and quality
                    var lastAnalyzeTs = 0L
                    analysis.setAnalyzer(executor) { imageProxy ->
                        try {
                            val now = System.currentTimeMillis()
                            if (now - lastAnalyzeTs > 500) { // 2 FPS for edge detection
                                lastAnalyzeTs = now
                                
                                // Simulate edge detection and quality scoring
                                val quality = simulateQualityAnalysis(imageProxy)
                                val hasEdges = quality > 0.6f
                                
                                val status = when {
                                    quality > 0.8f -> "🟢 Frábær gæði - tilbúinn að skanna!"
                                    quality > 0.6f -> "🟡 Góð gæði - færið nær"
                                    hasEdges -> "🟠 Reikningur fannst - bætið ljós"
                                    else -> "🔴 Leitið að reikningi..."
                                }
                                
                                onUpdateStatus(status, quality, hasEdges)
                            }
                        } catch (e: Exception) {
                            Log.e("BatchScanner", "Analysis error", e)
                        } finally {
                            imageProxy.close()
                        }
                    }
                    
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                            analysis
                        )
                        onCameraReady(camera.cameraControl)
                        Log.d("BatchScanner", "Camera bound with target analysis resolution 1280x720 and continuous AF enabled")
                    } catch (e: Exception) {
                        Log.e("BatchScanner", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                
                previewView
            }
        )
        
        // Professional overlay with edge detection
        ProfessionalCameraOverlay(
            modifier = Modifier.fillMaxSize(),
            edgeDetected = false, // Will be updated with real edge detection
            qualityScore = 0.7f,
            torchEnabled = torchEnabled,
            onTorchToggle = onTorchToggle,
            onCapture = {
                val capture = imageCaptureRef
                if (capture == null) {
                    Log.w("BatchScanner", "ImageCapture not ready")
                    return@ProfessionalCameraOverlay
                }
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "skanni_batch_" + System.currentTimeMillis() + ".jpg")
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                }
                val output = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ).build()
                capture.takePicture(
                    output,
                    ContextCompat.getMainExecutor(context),
                    object: ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            val uri = outputFileResults.savedUri ?: Uri.parse("")
                            onCaptureReceipt(uri, 0.8f, true)
                        }
                        override fun onError(exception: ImageCaptureException) {
                            Log.e("BatchScanner", "Capture error", exception)
                        }
                    }
                )
            }
        )
        
        // Batch progress indicator at bottom
        if (batchData.getReceiptCount() > 0) {
            BatchProgressIndicator(
                modifier = Modifier.align(Alignment.BottomCenter),
                batchData = batchData
            )
        }
    }
}

@Composable
private fun ProfessionalCameraOverlay(
    modifier: Modifier = Modifier,
    edgeDetected: Boolean,
    qualityScore: Float,
    torchEnabled: Boolean,
    onTorchToggle: (Boolean) -> Unit,
    onCapture: () -> Unit
) {
    Box(modifier = modifier) {
        // Edge detection rectangle
        val borderColor = when {
            qualityScore > 0.8f -> Color.Green
            qualityScore > 0.6f -> Color.Yellow
            edgeDetected -> Color(0xFFFFA500) // Orange color
            else -> MaterialTheme.colorScheme.primary
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.85f)
                .aspectRatio(0.7f) // Receipt aspect ratio
                .border(
                    width = 3.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Corner guides
            repeat(4) { corner ->
                val alignment = when (corner) {
                    0 -> Alignment.TopStart
                    1 -> Alignment.TopEnd
                    2 -> Alignment.BottomStart
                    else -> Alignment.BottomEnd
                }
                
                Box(
                    modifier = Modifier
                        .align(alignment)
                        .size(24.dp)
                        .background(
                            borderColor,
                            RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        // Top controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            FilledIconButton(
                onClick = { onTorchToggle(!torchEnabled) },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = "Ljós",
                    tint = if (torchEnabled) Color.Yellow else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // Bottom capture button
        FloatingActionButton(
            onClick = onCapture,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp),
            containerColor = borderColor
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = "Taka mynd",
                modifier = Modifier.size(32.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun BatchProgressIndicator(
    modifier: Modifier = Modifier,
    batchData: BatchScanData
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Batch Progress",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${batchData.getReceiptCount()} reikningar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Thumbnail row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(batchData.scannedReceipts) { receipt ->
                    ReceiptThumbnail(receipt = receipt)
                }
            }
        }
    }
}

@Composable
private fun ReceiptThumbnail(
    receipt: ScannedReceiptData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .size(width = 60.dp, height = 80.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (receipt.processingStatus) {
                ProcessingStatus.COMPLETED -> Color.Green.copy(alpha = 0.1f)
                ProcessingStatus.PROCESSING -> Color.Blue.copy(alpha = 0.1f)
                ProcessingStatus.FAILED -> Color.Red.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            // Quality indicator
            if (receipt.qualityScore > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .background(
                            when {
                                receipt.qualityScore > 0.8f -> Color.Green
                                receipt.qualityScore > 0.6f -> Color.Yellow
                                else -> Color.Red
                            },
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun ReviewMode(
    modifier: Modifier = Modifier,
    batchData: BatchScanData,
    isProcessing: Boolean,
    currentProcessingIndex: Int,
    onBackToCapture: () -> Unit,
    onProcessBatch: () -> Unit,
    onRemoveReceipt: (String) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header with stats
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    "Batch Yfirlit",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(
                        label = "Reikningar",
                        value = batchData.getReceiptCount().toString(),
                        icon = Icons.Default.Receipt
                    )
                    InfoChip(
                        label = "Samtals",
                        value = "${batchData.getTotalAmount().toInt()} kr",
                        icon = Icons.Default.AttachMoney
                    )
                    InfoChip(
                        label = "Stöðu",
                        value = if (isProcessing) "Vinnsla..." else "Tilbúið",
                        icon = Icons.Default.CheckCircle
                    )
                }
            }
        }
        
        // Processing progress
        if (isProcessing) {
            LinearProgressIndicator(
                progress = currentProcessingIndex.toFloat() / batchData.getReceiptCount().toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Text(
                "Vinnur úr reikningi ${currentProcessingIndex + 1} af ${batchData.getReceiptCount()}",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        
        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBackToCapture,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bæta við fleiri")
            }
            
            Button(
                onClick = onProcessBatch,
                modifier = Modifier.weight(1f),
                enabled = !isProcessing && batchData.getReceiptCount() > 0
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Vinna úr öllu")
            }
        }
        
        // Receipt list would go here
        // (Implementation continues with detailed receipt list)
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

private enum class BatchScanMode {
    CAPTURE,
    REVIEW
}

// Utility functions
private fun simulateQualityAnalysis(imageProxy: ImageProxy): Float {
    // In real implementation, this would analyze image sharpness, 
    // contrast, and edge detection
    return kotlin.random.Random.nextFloat()
}

private fun processBatchReceipts(
    batchData: BatchScanData,
    recognizer: com.google.mlkit.vision.text.TextRecognizer,
    onProgress: (Int) -> Unit,
    onComplete: (BatchScanData) -> Unit
) {
    // In real implementation, this would process each receipt with OCR
    // For now, simulate processing
    var currentIndex = 0
    val processedReceipts = mutableListOf<ScannedReceiptData>()
    
    batchData.scannedReceipts.forEach { receipt ->
        onProgress(currentIndex)
        
        // Simulate OCR processing
        val processedReceipt = receipt.copy(
            processingStatus = ProcessingStatus.COMPLETED,
            ocrText = "Simulated OCR text",
            totalAmount = kotlin.random.Random.nextDouble(1000.0, 10000.0),
            merchant = "Test Merchant"
        )
        
        processedReceipts.add(processedReceipt)
        currentIndex++
    }
    
    val completedBatch = batchData.copy(
        scannedReceipts = processedReceipts,
        isCompleted = true
    )
    
    onComplete(completedBatch)
}