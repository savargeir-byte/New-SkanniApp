package io.github.saeargeir.skanniapp.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import io.github.saeargeir.skanniapp.camera.CameraQualityAnalyzer
import io.github.saeargeir.skanniapp.camera.EdgeDetectionHelper
import io.github.saeargeir.skanniapp.camera.QualityResult
import io.github.saeargeir.skanniapp.data.BatchScanData
import io.github.saeargeir.skanniapp.data.ScannedReceiptData
import io.github.saeargeir.skanniapp.util.ErrorLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Enhanced camera screen með batch scanning, edge detection, og quality preview
 */
@Composable
fun EnhancedCameraScreen(
    onNavigateBack: () -> Unit,
    onBatchCompleted: (BatchScanData) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State variables
    var hasCameraPermission by remember { mutableStateOf(false) }
    var currentBatch by remember { mutableStateOf(BatchScanData()) }
    var isBatchMode by remember { mutableStateOf(false) }
    var currentQuality by remember { mutableStateOf(QualityResult.POOR) }
    var isEdgeDetectionEnabled by remember { mutableStateOf(true) }
    var detectedEdges by remember { mutableStateOf<EdgeDetectionHelper.DetectedEdges?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showManualControls by remember { mutableStateOf(false) }
    
    // Camera objects
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var imageAnalyzer by remember { mutableStateOf<ImageAnalysis?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    
    // Camera controls
    var exposureValue by remember { mutableStateOf(0) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var isFlashOn by remember { mutableStateOf(false) }
    
    // Helper objects
    val edgeDetectionHelper = remember { EdgeDetectionHelper() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }
    
    // Check permission on startup
    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // Setup camera when permission granted
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) {
            setupCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                cameraExecutor = cameraExecutor,
                onCameraReady = { provider, prev, capture, analysis, cam ->
                    cameraProvider = provider
                    preview = prev
                    imageCapture = capture
                    imageAnalyzer = analysis
                    camera = cam
                },
                onQualityUpdate = { quality ->
                    currentQuality = quality
                },
                onEdgeDetected = { edges ->
                    if (isEdgeDetectionEnabled) {
                        detectedEdges = edges
                    }
                }
            )
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // Camera preview
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    preview?.setSurfaceProvider(previewView.surfaceProvider)
                }
            )
            
            // Edge detection overlay
            if (isEdgeDetectionEnabled && detectedEdges != null) {
                EdgeDetectionOverlay(
                    detectedEdges = detectedEdges!!,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Quality indicator
            QualityIndicator(
                quality = currentQuality,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            )
            
            // Batch mode indicator
            if (isBatchMode) {
                BatchModeIndicator(
                    batch = currentBatch,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                )
            }
            
            // Manual controls panel
            if (showManualControls) {
                ManualControlsPanel(
                    camera = camera,
                    exposureValue = exposureValue,
                    zoomRatio = zoomRatio,
                    isFlashOn = isFlashOn,
                    onExposureChange = { value ->
                        exposureValue = value
                        camera?.cameraControl?.setExposureCompensationIndex(value)
                    },
                    onZoomChange = { ratio ->
                        zoomRatio = ratio
                        camera?.cameraControl?.setZoomRatio(ratio)
                    },
                    onFlashToggle = {
                        isFlashOn = !isFlashOn
                        imageCapture?.flashMode = if (isFlashOn) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp)
                )
            }
            
            // Bottom controls
            BottomCameraControls(
                isBatchMode = isBatchMode,
                isProcessing = isProcessing,
                currentQuality = currentQuality,
                showManualControls = showManualControls,
                onCaptureImage = {
                    captureImage(
                        context = context,
                        imageCapture = imageCapture,
                        batch = currentBatch,
                        isBatchMode = isBatchMode,
                        detectedEdges = detectedEdges,
                        edgeDetectionHelper = edgeDetectionHelper,
                        onImageCaptured = { receipt ->
                            if (isBatchMode) {
                                currentBatch.addReceipt(receipt)
                                currentBatch = currentBatch.copy() // Trigger recomposition
                            } else {
                                // Single image mode - complete immediately
                                val singleBatch = BatchScanData().apply {
                                    addReceipt(receipt)
                                }
                                onBatchCompleted(singleBatch.markCompleted())
                            }
                        },
                        onProcessingStart = { isProcessing = true },
                        onProcessingEnd = { isProcessing = false }
                    )
                },
                onToggleBatchMode = {
                    isBatchMode = !isBatchMode
                    if (!isBatchMode && currentBatch.getReceiptCount() > 0) {
                        // Complete current batch
                        onBatchCompleted(currentBatch.markCompleted())
                        currentBatch = BatchScanData()
                    }
                },
                onToggleEdgeDetection = {
                    isEdgeDetectionEnabled = !isEdgeDetectionEnabled
                    if (!isEdgeDetectionEnabled) {
                        detectedEdges = null
                    }
                },
                onToggleManualControls = {
                    showManualControls = !showManualControls
                },
                onCompleteBatch = {
                    if (currentBatch.getReceiptCount() > 0) {
                        onBatchCompleted(currentBatch.markCompleted())
                        currentBatch = BatchScanData()
                        isBatchMode = false
                    }
                },
                onNavigateBack = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
            
            // Batch preview
            if (isBatchMode && currentBatch.getReceiptCount() > 0) {
                BatchPreview(
                    batch = currentBatch,
                    onRemoveReceipt = { receiptId ->
                        currentBatch.removeReceipt(receiptId)
                        currentBatch = currentBatch.copy() // Trigger recomposition
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 80.dp)
                )
            }
            
        } else {
            // Permission denied screen
            PermissionDeniedScreen(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    onCameraReady: (ProcessCameraProvider, Preview, ImageCapture, ImageAnalysis, Camera) -> Unit,
    onQualityUpdate: (QualityResult) -> Unit,
    onEdgeDetected: (EdgeDetectionHelper.DetectedEdges?) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    
    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview use case
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
            
            // Image capture use case
            val imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            
            // Image analysis use case
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            
            // Setup analyzers
            val qualityAnalyzer = CameraQualityAnalyzer(onQualityUpdate)
            val edgeDetectionHelper = EdgeDetectionHelper()
            
            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    // Quality analysis
                    qualityAnalyzer.analyze(imageProxy)
                    
                    // Edge detection (simplified for real-time)
                    // In production, this would be optimized for performance
                    
                } catch (e: Exception) {
                    ErrorLogger.logOcrError(context, "Camera analysis failed", e)
                } finally {
                    imageProxy.close()
                }
            }
            
            // Camera selector (back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                // Unbind all use cases before rebinding
                cameraProvider.unbindAll()
                
                // Bind use cases to camera
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                
                onCameraReady(cameraProvider, preview, imageCapture, imageAnalysis, camera)
                
            } catch (e: Exception) {
                ErrorLogger.logOcrError(context, "Camera binding failed", e)
            }
            
        } catch (e: Exception) {
            ErrorLogger.logOcrError(context, "Camera setup failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun captureImage(
    context: Context,
    imageCapture: ImageCapture?,
    batch: BatchScanData,
    isBatchMode: Boolean,
    detectedEdges: EdgeDetectionHelper.DetectedEdges?,
    edgeDetectionHelper: EdgeDetectionHelper,
    onImageCaptured: (ScannedReceiptData) -> Unit,
    onProcessingStart: () -> Unit,
    onProcessingEnd: () -> Unit
) {
    imageCapture?.let { capture ->
        onProcessingStart()
        
        // Create file for captured image
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val photoFile = File(
            context.getExternalFilesDir(null),
            "receipt_${timestamp}.jpg"
        )
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    try {
                        val imageUri = Uri.fromFile(photoFile)
                        
                        // Create receipt data
                        val receipt = ScannedReceiptData(
                            imageUri = imageUri,
                            qualityScore = detectedEdges?.confidence ?: 0.5f,
                            edgeDetected = detectedEdges != null
                        )
                        
                        onImageCaptured(receipt)
                        
                    } catch (e: Exception) {
                        ErrorLogger.logOcrError(context, "Image processing failed", e)
                    } finally {
                        onProcessingEnd()
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    ErrorLogger.logOcrError(context, "Image capture failed", exception)
                    onProcessingEnd()
                }
            }
        )
    }
}