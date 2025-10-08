package io.github.saeargeir.skanniapp.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
                title = { Text("Skanna reikning") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = null) }
                }
            )
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

        var torchEnabled by remember { mutableStateOf(false) }
        var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
        var cameraInfo by remember { mutableStateOf<androidx.camera.core.CameraInfo?>(null) }

        // Live feedback with better status messages
        var liveStatus by remember { mutableStateOf("Leita að texta...") }
        var lastPreviewText by remember { mutableStateOf("") }
        var analyzing by remember { mutableStateOf(true) }
        var captureInProgress by remember { mutableStateOf(false) }
        var detectedVendor by remember { mutableStateOf("") }
        var detectedAmount by remember { mutableStateOf("") }

        fun setTorch(on: Boolean) {
            cameraControl?.enableTorch(on)
            torchEnabled = on
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
                                                } else {
                                                    // Use improved parser for live feedback
                                                    val parsed = IcelandicInvoiceParser.parseInvoiceText(t)
                                                    detectedVendor = parsed.vendor
                                                    detectedAmount = if (parsed.amount > 0) "${parsed.amount.toInt()} kr" else ""
                                                    
                                                    liveStatus = when {
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
                                analysis
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

            // Center mask to hint edges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .height(240.dp)
                    .padding(horizontal = 24.dp)
                    .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = MaterialTheme.shapes.medium)
            )

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
                        if (!captureInProgress) {
                            captureInProgress = true
                            analyzing = false
                            val text = lastPreviewText
                            if (text.isNotBlank()) {
                                onTextDetected(text)
                            } else {
                                // fallback: try one-shot OCR immediately using preview text pipeline already attempted
                                analyzing = true
                                captureInProgress = false
                                liveStatus = "Reyndu aftur - færið nær og lýstu betur"
                            }
                        }
                    },
                    icon = { Icon(Icons.Default.Camera, contentDescription = null) },
                    text = { Text("Skanna nótu") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
