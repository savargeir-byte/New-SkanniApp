package io.github.saeargeir.skanniapp.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.saeargeir.skanniapp.R
import io.github.saeargeir.skanniapp.utils.IcelandicInvoiceParser
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.GlobalScope
import java.util.concurrent.Executors
import java.io.ByteArrayOutputStream
import android.hardware.camera2.CaptureRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScannerScreen(
    onClose: () -> Unit,
    onResult: (String, android.net.Uri?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    
    // Camera and OCR state
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    
    // UI state
    var torchEnabled by remember { mutableStateOf(false) }
    var processingImage by remember { mutableStateOf(false) }
    var captureInProgress by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(true) }
    var liveStatus by remember { mutableStateOf("Leita að skjali...") }
    var lastPreviewText by remember { mutableStateOf("") }
    var detectedVendor by remember { mutableStateOf("") }
    var detectedAmount by remember { mutableStateOf("") }
    var documentDetected by remember { mutableStateOf(false) }
    var documentStableCount by remember { mutableStateOf(0) }
    // Prevent overlapping OCR tasks
    var ocrInFlight by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Crop overlay state
    var showCropOverlay by remember { mutableStateOf(true) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var cropBox by remember { mutableStateOf<CropBox?>(null) }
    
    // Camera controls
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var cameraInfo: CameraInfo? by remember { mutableStateOf(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var lastCapturedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    
    // Constants
    val STABLE_THRESHOLD = 3
    
    // Initialize crop box when preview size is available
    LaunchedEffect(previewSize) {
        if (previewSize.width > 0 && previewSize.height > 0 && cropBox == null) {
            cropBox = CropBox(
                left = 0.2f,
                top = 0.2f, 
                right = 0.8f,
                bottom = 0.8f
            )
        }
    }
    
    // Helper functions
    fun setTorch(enabled: Boolean) {
        torchEnabled = enabled
        cameraControl?.enableTorch(enabled)
    }
    
    fun detectDocument(text: String): Boolean {
        val patterns = listOf(
            Regex("\\b\\d{1,3}[.,]\\d{3}\\s*kr\\b", RegexOption.IGNORE_CASE),
            Regex("\\btotal[:\\s]+\\d+", RegexOption.IGNORE_CASE),
            Regex("\\bupphæð[:\\s]+\\d+", RegexOption.IGNORE_CASE),
            Regex("\\bsamtals[:\\s]+\\d+", RegexOption.IGNORE_CASE)
        )
        
        Log.d("InvoiceScanner", "Document detection - text length: ${text.length}")
        val patternMatches = patterns.mapIndexed { index, pattern ->
            val matches = pattern.containsMatchIn(text)
            val foundText = if (matches) pattern.find(text)?.value else "none"
            Log.d("InvoiceScanner", "Pattern $index matches: $matches (found: '$foundText')")
            matches
        }
        
        val hasPattern = patternMatches.any { it }
        val isLongEnough = text.length > 50
        val isDocument = hasPattern && isLongEnough
        
        Log.d("InvoiceScanner", "Document detection result: hasPattern=$hasPattern, isLongEnough=$isLongEnough, isDocument=$isDocument")
        
        return isDocument
    }
    
    fun autoCapture() {
        if (!processingImage && !captureInProgress) {
            captureInProgress = true
            analyzing = false
            processingImage = true
            
            // Simulate capture delay and process text
            scope.launch {
                delay(800)
                processingImage = false
                onResult(lastPreviewText, null)
            }
        }
    }

    // Convert ImageProxy (YUV_420_888) to NV21 byte array
    fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val nv21 = ByteArray(ySize + image.width * image.height / 2)

        // Copy Y
        yBuffer.get(nv21, 0, ySize)

        val u = uBuffer.duplicate()
        val v = vBuffer.duplicate()
        u.rewind(); v.rewind()

        val chromaRowStride = image.planes[1].rowStride
        val chromaPixelStride = image.planes[1].pixelStride
        var outputOffset = ySize

        // Interleave V and U to NV21 (VU order)
        val halfHeight = image.height / 2
        val halfWidth = image.width / 2
        for (row in 0 until halfHeight) {
            var uvBufferPos = row * chromaRowStride
            for (col in 0 until halfWidth) {
                val vByte = v.get(uvBufferPos)
                val uByte = u.get(uvBufferPos)
                nv21[outputOffset++] = vByte
                nv21[outputOffset++] = uByte
                uvBufferPos += chromaPixelStride
            }
        }
        return nv21
    }

    fun rotateBitmapIfNeeded(src: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return src
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }
    
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
                        Image(
                            painter = painterResource(id = R.drawable.logosk),
                            contentDescription = "SkanniApp Logo",
                            modifier = Modifier.size(32.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                        Text("Skanna reikning")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { 
                        Icon(Icons.Default.Close, contentDescription = null) 
                    }
                }
            )
        },
        bottomBar = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logos_new),
                        contentDescription = "Ice Veflausnir",
                        modifier = Modifier.height(24.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        previewSize = size
                    },
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
                        
                        // Configure analysis with higher target resolution and continuous AF
                        val analysisBuilder = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetResolution(Size(1280, 720))
                        Camera2Interop.Extender(analysisBuilder).apply {
                            setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        }
                        val analysis = analysisBuilder.build()
                        
                        // Configure ImageCapture with interop as well for future still shots
                        val imageCaptureBuilder = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        Camera2Interop.Extender(imageCaptureBuilder).apply {
                            setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        }
                        imageCapture = imageCaptureBuilder.build()
                        
                        var lastAnalyzeTs = 0L
                        analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                            try {
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && analyzing && cropBox != null) {
                                    val now = System.currentTimeMillis()
                                    if (!ocrInFlight && (now - lastAnalyzeTs > 500)) {
                                        lastAnalyzeTs = now
                                        ocrInFlight = true

                                        Log.d("InvoiceScanner", "Starting OCR analysis - analyzing: $analyzing, cropBox: $cropBox")

                                        // Create InputImage from a safe NV21 copy and close ImageProxy immediately
                                        val nv21 = yuv420ToNv21(imageProxy)
                                        val width = imageProxy.width
                                        val height = imageProxy.height
                                        val rotation = imageProxy.imageInfo.rotationDegrees
                                        val inputImage = InputImage.fromByteArray(
                                            nv21,
                                            width,
                                            height,
                                            rotation,
                                            android.graphics.ImageFormat.NV21
                                        )
                                        Log.d("InvoiceScanner", "Created InputImage from NV21: ${width}x${height}, rotation: ${rotation}")

                                        Log.d("InvoiceScanner", "Recognizer object: $recognizer")
                                        Log.d("InvoiceScanner", "Processing image with ML Kit...")

                                        recognizer.process(inputImage)
                                            .addOnSuccessListener { visionText ->
                                                Log.d("InvoiceScanner", "OCR Success callback triggered!")
                                                val t = visionText.text
                                                lastPreviewText = t

                                                // Detailed logging for debugging
                                                Log.d("InvoiceScanner", "=== OCR Analysis ===")
                                                Log.d("InvoiceScanner", "Crop box: ${cropBox!!.left}, ${cropBox!!.top}, ${cropBox!!.right}, ${cropBox!!.bottom}")
                                                Log.d("InvoiceScanner", "Raw text length: ${t.length}")
                                                Log.d("InvoiceScanner", "Raw text preview: ${t.take(200)}")
                                                if (t.length > 200) Log.d("InvoiceScanner", "Raw text (full): $t")

                                                if (t.isBlank()) {
                                                    Log.w("InvoiceScanner", "No text detected in image")
                                                    liveStatus = "Enginn texti fannst"
                                                    detectedVendor = ""
                                                    detectedAmount = ""
                                                    documentDetected = false
                                                    documentStableCount = 0
                                                } else {
                                                    Log.d("InvoiceScanner", "Processing text with IcelandicInvoiceParser...")
                                                    val parsed = IcelandicInvoiceParser.parseInvoiceText(t)
                                                    Log.d("InvoiceScanner", "Parsed vendor: '${parsed.vendor}'")
                                                    Log.d("InvoiceScanner", "Parsed amount: ${parsed.amount}")
                                                    Log.d("InvoiceScanner", "Parsed date: '${parsed.date ?: "none"}'")
                                                    Log.d("InvoiceScanner", "Parsed items count: ${parsed.items.size}")
                                                    Log.d("InvoiceScanner", "Parsed items: ${parsed.items.joinToString("; ")}")
                                                    Log.d("InvoiceScanner", "Parser confidence: ${parsed.confidence}")

                                                    detectedVendor = parsed.vendor
                                                    detectedAmount = if (parsed.amount > 0) "${parsed.amount.toInt()} kr" else ""

                                                    val isDocument = detectDocument(t)
                                                    Log.d("InvoiceScanner", "Document detected: $isDocument")
                                                    Log.d("InvoiceScanner", "Document stable count: $documentStableCount")

                                                    if (isDocument) {
                                                        documentStableCount++
                                                        Log.d("InvoiceScanner", "Document stable count increased to: $documentStableCount")
                                                        if (documentStableCount >= STABLE_THRESHOLD) {
                                                            if (!documentDetected) {
                                                                Log.i("InvoiceScanner", "Document confirmed stable! Triggering auto-capture...")
                                                                documentDetected = true
                                                                autoCapture()
                                                            }
                                                        }
                                                    } else {
                                                        if (documentStableCount > 0) {
                                                            Log.d("InvoiceScanner", "Document detection lost, resetting count")
                                                        }
                                                        documentStableCount = 0
                                                        documentDetected = false
                                                    }

                                                    liveStatus = when {
                                                        documentDetected -> "📄 Skjal greint! Tek mynd..."
                                                        documentStableCount > 0 -> "📄 Greini skjal... (${documentStableCount}/${STABLE_THRESHOLD})"
                                                        detectedVendor.isNotEmpty() -> "🏪 ${detectedVendor}"
                                                        detectedAmount.isNotEmpty() -> "💰 ${detectedAmount}"
                                                        else -> "📝 Les texta..."
                                                    }

                                                    Log.d("InvoiceScanner", "Status updated to: $liveStatus")
                                                }
                                                Log.d("InvoiceScanner", "=== End OCR Analysis ===")
                                            }
                                            .addOnFailureListener { e ->
                                                Log.e("InvoiceScanner", "OCR Failure callback triggered!")
                                                Log.e("InvoiceScanner", "Text recognition failed", e)
                                                liveStatus = "OCR villa: ${e.message}"
                                            }
                                            .addOnCompleteListener { task ->
                                                Log.d("InvoiceScanner", "OCR Complete callback triggered! Success: ${task.isSuccessful}")
                                                if (!task.isSuccessful) {
                                                    Log.e("InvoiceScanner", "OCR task failed", task.exception)
                                                }
                                                ocrInFlight = false
                                            }
                                    }
                                }
                            } finally {
                                // Close immediately since we passed a safe byte array to ML Kit
                                try { imageProxy.close() } catch (_: Exception) {}
                            }
                        }
                        
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
                            cameraInfo = camera.cameraInfo
                            setTorch(false)
                            Log.d("InvoiceScanner", "Camera bound with target analysis resolution 1280x720 and continuous AF enabled")
                        } catch (e: Exception) {
                            Log.e("Camera", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    
                    previewView
                }
            )
            
            // Crop overlay
            if (showCropOverlay && !processingImage && !captureInProgress && cropBox != null && previewSize.width > 0) {
                CropOverlay(
                    cropBox = cropBox!!,
                    onCropBoxChange = { newBox -> cropBox = newBox },
                    containerSize = previewSize,
                    enabled = !processingImage && !captureInProgress
                )
            }
            
            // Top logo
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logosk),
                    contentDescription = "SkanniApp",
                    modifier = Modifier.size(32.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SkanniApp",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Top-right controls
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                // Toggle crop overlay
                FilledIconButton(
                    onClick = { showCropOverlay = !showCropOverlay },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(
                        Icons.Default.CenterFocusStrong, 
                        contentDescription = "Sýna/fela skurðkassa",
                        tint = if (showCropOverlay) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
                
                FilledIconButton(onClick = { setTorch(!torchEnabled) }) {
                    Icon(
                        Icons.Default.Bolt, 
                        contentDescription = "Kveikja/Slökkva ljósi", 
                        tint = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }
            
            // Status display and quick actions
            if (!processingImage) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 120.dp)
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
                        
                        if (detectedVendor.isNotEmpty() && detectedAmount.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "$detectedVendor - $detectedAmount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                // One-shot capture using ImageCapture
                                val capture = imageCapture ?: return@Button
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                                    context.contentResolver,
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    android.content.ContentValues().apply {
                                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "skanni_" + System.currentTimeMillis() + ".jpg")
                                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                                    }
                                ).build()
                                processingImage = true
                                capture.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context)
                                , object: ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            lastCapturedUri = outputFileResults.savedUri
                                            processingImage = false
                                            liveStatus = "Mynd vistuð"
                                            onResult(lastPreviewText, lastCapturedUri)
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            processingImage = false
                                            liveStatus = "Villa við vistun: ${exception.message}"
                                            Log.e("InvoiceScanner", "ImageCapture error", exception)
                                        }
                                    }
                                )
                            }) {
                                Icon(Icons.Default.Camera, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Taka mynd")
                            }
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
                            text = "Vinnur úr mynd...",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}