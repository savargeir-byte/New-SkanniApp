package io.github.saeargeir.skanniapp.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import io.github.saeargeir.skanniapp.ocr.AdvancedOcrProcessor
import io.github.saeargeir.skanniapp.ocr.OcrResult
import io.github.saeargeir.skanniapp.utils.EdgeDetectionUtil
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Enhanced Invoice Scanner með edge detection og advanced OCR
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedInvoiceScannerScreen(
    onClose: () -> Unit,
    onResult: (String, android.net.Uri?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var hasPermission by remember { mutableStateOf(false) }
    
    // Camera state
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val ocrProcessor = remember { AdvancedOcrProcessor(context) }
    
    // UI state
    var torchEnabled by remember { mutableStateOf(false) }
    var processingImage by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(true) }
    var liveStatus by remember { mutableStateOf("Leita að reikningi...") }
    var edgeDetectionResult by remember { mutableStateOf<EdgeDetectionUtil.EdgeDetectionResult?>(null) }
    var qualityScore by remember { mutableStateOf(0f) }
    
    // Crop overlay state
    var showCropOverlay by remember { mutableStateOf(true) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropBox by remember { mutableStateOf<CropBox?>(null) }
    
    // Camera controls
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    
    // Initialize crop box when preview size is available
    LaunchedEffect(previewSize) {
        if (previewSize.width > 0 && previewSize.height > 0 && cropBox == null) {
            cropBox = CropBox(
                left = 0.15f,
                top = 0.15f,
                right = 0.85f,
                bottom = 0.85f
            )
        }
    }
    
    // Helper functions
    fun setTorch(enabled: Boolean) {
        torchEnabled = enabled
        cameraControl?.enableTorch(enabled)
    }
    
    fun captureAndProcess() {
        if (imageCapture != null && !processingImage) {
            processingImage = true
            analyzing = false
            liveStatus = "Tek mynd..."
            
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(
                java.io.File(context.cacheDir, "enhanced_capture_${System.currentTimeMillis()}.jpg")
            ).build()
            
            imageCapture!!.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) {
                        Log.e("EnhancedScanner", "Capture failed", exception)
                        processingImage = false
                        analyzing = true
                        liveStatus = "Villa við myndatöku"
                    }
                    
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        liveStatus = "Vinnur úr mynd..."
                        
                        scope.launch {
                            try {
                                val imageUri = output.savedUri ?: android.net.Uri.fromFile(
                                    java.io.File(context.cacheDir, "enhanced_capture_${System.currentTimeMillis()}.jpg")
                                )
                                
                                // Load bitmap and process
                                val bitmap = android.graphics.BitmapFactory.decodeFile(imageUri.path)
                                if (bitmap != null) {
                                    when (val result = ocrProcessor.processBitmap(bitmap)) {
                                        is OcrResult.Success -> {
                                            Log.d("EnhancedScanner", "OCR success: ${result.ocrText.length} chars")
                                            processingImage = false
                                            onResult(result.ocrText, imageUri)
                                        }
                                        is OcrResult.Failed -> {
                                            Log.e("EnhancedScanner", "OCR failed: ${result.error}")
                                            processingImage = false
                                            analyzing = true
                                            liveStatus = "Villa: ${result.error}"
                                        }
                                    }
                                } else {
                                    processingImage = false
                                    analyzing = true
                                    liveStatus = "Gat ekki lesið mynd"
                                }
                            } catch (e: Exception) {
                                Log.e("EnhancedScanner", "Processing error", e)
                                processingImage = false
                                analyzing = true
                                liveStatus = "Villa við vinnslu"
                            }
                        }
                    }
                }
            )
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Camera, contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Þarf leyfi fyrir myndavél", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Veita leyfi")
            }
        }
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "SkanniApp Logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Text("Aukið Skanni")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .androidx.compose.ui.layout.onSizeChanged { size ->
                        previewSize = size
                    },
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        
                        var lastAnalyzeTime = 0L
                        analysis.setAnalyzer(executor) { imageProxy ->
                            try {
                                if (analyzing) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastAnalyzeTime > 1000) {
                                        lastAnalyzeTime = now
                                        
                                        scope.launch {
                                            try {
                                                val result = EdgeDetectionUtil.detectReceiptEdges(imageProxy)
                                                edgeDetectionResult = result
                                                qualityScore = result.qualityScore
                                                liveStatus = result.getStatusMessage()
                                                
                                                // Auto-capture if quality is excellent
                                                if (result.shouldAutoCapture() && !processingImage) {
                                                    captureAndProcess()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("EnhancedScanner", "Analysis error", e)
                                            }
                                        }
                                    }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                        
                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()
                        
                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                                imageCapture
                            )
                            cameraControl = camera.cameraControl
                            setTorch(false)
                        } catch (e: Exception) {
                            Log.e("EnhancedScanner", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                }
            )
            
            // Edge detection overlay
            edgeDetectionResult?.let { result ->
                if (result.hasReceiptDetected && result.cropRect != null) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val rect = result.cropRect
                        val scaleX = size.width / previewSize.width.toFloat()
                        val scaleY = size.height / previewSize.height.toFloat()
                        
                        drawRect(
                            color = result.getEdgeColor(),
                            topLeft = androidx.compose.ui.geometry.Offset(
                                rect.left * scaleX,
                                rect.top * scaleY
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                rect.width() * scaleX,
                                rect.height() * scaleY
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                        )
                    }
                }
            }
            
            // Crop overlay
            if (showCropOverlay && !processingImage && cropBox != null && previewSize.width > 0) {
                CropOverlay(
                    cropBox = cropBox!!,
                    onCropBoxChange = { newBox -> cropBox = newBox },
                    containerSize = previewSize,
                    enabled = !processingImage
                )
            }
            
            // Top controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                FilledIconButton(
                    onClick = { showCropOverlay = !showCropOverlay },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong,
                        contentDescription = "Toggle crop overlay",
                        tint = if (showCropOverlay) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                
                FilledIconButton(onClick = { setTorch(!torchEnabled) }) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = "Toggle flashlight",
                        tint = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
            
            // Status display
            if (!processingImage) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = liveStatus,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (qualityScore > 0f) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { qualityScore },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Gæði: ${(qualityScore * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { captureAndProcess() },
                            enabled = !processingImage
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Taka mynd")
                        }
                    }
                }
            }
            
            // Processing overlay
            if (processingImage) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = liveStatus,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            ocrProcessor.cleanup()
        }
    }
}
