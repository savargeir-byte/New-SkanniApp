package io.github.saeargeir.skanniapp.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.saeargeir.skanniapp.R
import io.github.saeargeir.skanniapp.utils.IcelandicInvoiceParser
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScannerScreen(
    onClose: () -> Unit,
    onTextDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Simple permission request using Accompanist is ideal, but keep inline to reduce deps
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo3_new),
                            contentDescription = "SkanniApp Logo",
                            modifier = Modifier.size(32.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                        Text("Skanna reikning")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = null) }
                }
            )
        },
        bottomBar = {
            // Ice Veflausnir logo at bottom
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
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Powered by",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        painter = painterResource(id = R.drawable.logos_new),
                        contentDescription = "Ice Veflausnir",
                        modifier = Modifier
                            .height(20.dp)
                            .width(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    ) { padding ->
        if (!hasPermission) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("Vantar að leyfa myndavél", modifier = Modifier.padding(16.dp))
            }
            return@Scaffold
        }

        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        val executor = remember { Executors.newSingleThreadExecutor() }
        val dispatcher: ExecutorCoroutineDispatcher = remember { executor.asCoroutineDispatcher() }
        val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
        val imageCapture = remember { ImageCapture.Builder().build() }

        var torchEnabled by remember { mutableStateOf(false) }
        var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
        var cameraInfo by remember { mutableStateOf<androidx.camera.core.CameraInfo?>(null) }

        // Live feedback with better status messages
        var liveStatus by remember { mutableStateOf("Leita að texta...") }
        var lastPreviewText by remember { mutableStateOf("") }
        var analyzing by remember { mutableStateOf(true) }
        var captureInProgress by remember { mutableStateOf(false) }
        var processingImage by remember { mutableStateOf(false) }
        var detectedVendor by remember { mutableStateOf("") }
        var detectedAmount by remember { mutableStateOf("") }
        
        // Document detection variables
        var documentDetected by remember { mutableStateOf(false) }
        var documentStableCount by remember { mutableStateOf(0) }
        var autoCaptureCooldown by remember { mutableStateOf(0L) }
        val STABLE_THRESHOLD = 5 // Need 5 consecutive detections
        val AUTO_CAPTURE_COOLDOWN = 3000L // 3 seconds cooldown between captures

        fun setTorch(on: Boolean) {
            cameraControl?.enableTorch(on)
            torchEnabled = on
        }

        // Simple document detection based on text density and layout
        fun detectDocument(text: String): Boolean {
            if (text.length < 30) return false
            
            val lines = text.lines().filter { it.trim().isNotEmpty() }
            if (lines.size < 3) return false
            
            // Look for typical invoice patterns
            val hasCompanyInfo = text.contains(Regex("(?i)(ehf|hf|slf|kt|kennitala)"))
            val hasAmount = text.contains(Regex("\\d+[.,]\\d+\\s*(kr|krónur)"))
            val hasDate = text.contains(Regex("\\d{1,2}[./]\\d{1,2}[./]\\d{2,4}"))
            val hasVSK = text.contains(Regex("(?i)(vsk|virðisaukaskattur)"))
            
            // Check text density - documents have consistent text layout
            val avgLineLength = lines.map { it.length }.average()
            val hasGoodStructure = avgLineLength > 10 && lines.size > 5
            
            return (hasCompanyInfo && hasAmount) || 
                   (hasDate && hasAmount && hasGoodStructure) ||
                   (hasVSK && hasAmount && lines.size > 8)
        }

        fun autoCapture() {
            val now = System.currentTimeMillis()
            if (!captureInProgress && !processingImage && now - autoCaptureCooldown > AUTO_CAPTURE_COOLDOWN) {
                autoCaptureCooldown = now
                captureInProgress = true
                liveStatus = "Skjal greint! Tek mynd sjálfkrafa..."
                
                // For now, just pass the detected text to the callback
                // In a full implementation, this would capture and process the image
                if (lastPreviewText.isNotBlank()) {
                    analyzing = false
                    onTextDetected(lastPreviewText)
                } else {
                    liveStatus = "Villa við sjálfvirka greiningu"
                    captureInProgress = false
                    processingImage = false
                }
            }
        }

        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        // Throttle OCR: analyze but do not navigate until capture
                        var lastAnalyzeTs = 0L
                        analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                            try {
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && analyzing) {
                                    val now = System.currentTimeMillis()
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    if (now - lastAnalyzeTs > 300) { // ~3 fps OCR for feedback
                                        lastAnalyzeTs = now
                                        recognizer.process(image)
                                            .addOnSuccessListener { visionText ->
                                                val t = visionText.text
                                                lastPreviewText = t
                                                
                                                if (t.isBlank()) {
                                                    liveStatus = "Enginn texti fannst"
                                                    detectedVendor = ""
                                                    detectedAmount = ""
                                                    documentDetected = false
                                                    documentStableCount = 0
                                                } else {
                                                    // Use improved parser for live feedback
                                                    val parsed = IcelandicInvoiceParser.parseInvoiceText(t)
                                                    detectedVendor = parsed.vendor
                                                    detectedAmount = if (parsed.amount > 0) "${parsed.amount.toInt()} kr" else ""
                                                    
                                                    // Document detection logic
                                                    val isDocument = detectDocument(t)
                                                    if (isDocument) {
                                                        documentStableCount++
                                                        if (documentStableCount >= STABLE_THRESHOLD) {
                                                            if (!documentDetected) {
                                                                documentDetected = true
                                                                autoCapture()
                                                            }
                                                        }
                                                    } else {
                                                        documentStableCount = 0
                                                        documentDetected = false
                                                    }
                                                    
                                                    liveStatus = when {
                                                        documentDetected -> "📄 Skjal greint! Tek mynd..."
                                                        documentStableCount > 0 -> "📄 Greini skjal... (${documentStableCount}/${STABLE_THRESHOLD})"
                                                        parsed.confidence > 0.7f -> "Reikningur greindur! ✓"
                                                        parsed.confidence > 0.4f -> "Hluti reiknings greindur..."
                                                        t.length > 50 -> "Texti fannst, leita að upplýsingum..."
                                                        else -> "Texti fannst: ${t.length} stafir"
                                                    }
                                                }
                                            }
                                            .addOnFailureListener { e ->
                                                liveStatus = "OCR villa"
                                                Log.e("Scanner", "OCR failed", e)
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            } catch (e: Exception) {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                analysis,
                                imageCapture
                            )
                            cameraControl = camera.cameraControl
                            cameraInfo = camera.cameraInfo
                            setTorch(false)
                        } catch (e: Exception) {
                            Log.e("Scanner", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            // Full screen document scanner - no border overlay needed
            // Camera fills entire screen for optimal document capture

            // Top SkanniApp logo
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo3),
                    contentDescription = "SkanniApp",
                    modifier = Modifier.size(32.dp),
                    colorFilter = ColorFilter.tint(Color.White)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "SkanniApp",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Top-right torch toggle
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                FilledIconButton(onClick = { setTorch(!torchEnabled) }) {
                    Icon(Icons.Default.Bolt, contentDescription = "Kveikja/Slökkva ljósi", tint = if (torchEnabled) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }

            // Bottom capture bar with enhanced feedback
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live feedback card
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = liveStatus,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Document detection indicator
                        if (documentStableCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📄 ",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                LinearProgressIndicator(
                                    progress = { documentStableCount.toFloat() / STABLE_THRESHOLD },
                                    modifier = Modifier.width(60.dp),
                                    color = if (documentDetected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                        
                        // Debug info for state
                        Text(
                            text = "Debug: capture=$captureInProgress, processing=$processingImage, analyzing=$analyzing",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        
                        if (detectedVendor.isNotEmpty() && detectedVendor != "Óþekkt seljandi") {
                            Text(
                                text = "Seljandi: $detectedVendor",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        
                        if (detectedAmount.isNotEmpty()) {
                            Text(
                                text = "Upphæð: $detectedAmount",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
                
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!captureInProgress && !processingImage) {
                            captureInProgress = true
                            processingImage = true
                            analyzing = false
                            liveStatus = "Tek mynd..."
                            
                            // Take picture using ImageCapture
                            imageCapture.takePicture(
                                executor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        liveStatus = "Greinir mynd..."
                                        
                                        // Process the captured image with OCR
                                        val mediaImage = image.image
                                        if (mediaImage != null) {
                                            val inputImage = InputImage.fromMediaImage(
                                                mediaImage, 
                                                image.imageInfo.rotationDegrees
                                            )
                                            
                                            recognizer.process(inputImage)
                                                .addOnSuccessListener { visionText ->
                                                    val text = visionText.text
                                                    if (text.isNotBlank()) {
                                                        liveStatus = "Texti greindur! ✓"
                                                        // Reset states after successful detection
                                                        captureInProgress = false
                                                        processingImage = false
                                                        onTextDetected(text)
                                                    } else {
                                                        liveStatus = "Enginn texti fannst í myndinni"
                                                        captureInProgress = false
                                                        processingImage = false
                                                        analyzing = true
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    liveStatus = "Villa við greiningu - reyndu aftur"
                                                    Log.e("Scanner", "OCR failed on captured image", e)
                                                    captureInProgress = false
                                                    processingImage = false
                                                    analyzing = true
                                                }
                                                .addOnCompleteListener {
                                                    image.close()
                                                    // Ensure states are reset even if success/failure didn't trigger
                                                    if (captureInProgress || processingImage) {
                                                        captureInProgress = false
                                                        processingImage = false
                                                        analyzing = true
                                                    }
                                                }
                                        } else {
                                            liveStatus = "Villa við myndatöku"
                                            captureInProgress = false
                                            processingImage = false
                                            analyzing = true
                                            image.close()
                                        }
                                    }
                                    
                                    override fun onError(exception: ImageCaptureException) {
                                        liveStatus = "Villa við myndatöku"
                                        Log.e("Scanner", "Image capture failed", exception)
                                        captureInProgress = false
                                        processingImage = false
                                        analyzing = true
                                    }
                                }
                            )
                        }
                    },
                    icon = { 
                        if (processingImage) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(Icons.Default.Camera, contentDescription = null)
                        }
                    },
                    text = { 
                        Text(
                            if (processingImage) "Greinir..." else "Taka mynd"
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(8.dp))
                
                // Ice Veflausnir logo at bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Powered by",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        painter = painterResource(id = R.drawable.ice_veflausnir_logo),
                        contentDescription = "Ice Veflausnir",
                        modifier = Modifier
                            .height(20.dp)
                            .width(60.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
