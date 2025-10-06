package io.github.saeargeir.skanniapp.ui.components

import androidx.camera.core.Camera
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.camera.EdgeDetectionHelper
import io.github.saeargeir.skanniapp.camera.QualityResult
import io.github.saeargeir.skanniapp.data.BatchScanData
import io.github.saeargeir.skanniapp.data.ScannedReceiptData

/**
 * Quality indicator fyrir real-time camera feedback
 */
@Composable
fun QualityIndicator(
    quality: QualityResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(android.graphics.Color.parseColor(quality.colorHex)).copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (quality) {
                    QualityResult.EXCELLENT -> Icons.Filled.CheckCircle
                    QualityResult.GOOD -> Icons.Filled.Check
                    QualityResult.FAIR -> Icons.Filled.Warning
                    QualityResult.POOR -> Icons.Filled.Error
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = quality.message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Batch mode indicator með receipt count
 */
@Composable
fun BatchModeIndicator(
    batch: BatchScanData,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Batch Mode",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${batch.getReceiptCount()} myndir",
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Manual camera controls panel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualControlsPanel(
    camera: Camera?,
    exposureValue: Int,
    zoomRatio: Float,
    isFlashOn: Boolean,
    onExposureChange: (Int) -> Unit,
    onZoomChange: (Float) -> Unit,
    onFlashToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Manual Controls",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Exposure control
            Text(
                text = "Exposure: $exposureValue",
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = exposureValue.toFloat(),
                onValueChange = { onExposureChange(it.toInt()) },
                valueRange = -3f..3f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Zoom control
            Text(
                text = "Zoom: ${String.format("%.1f", zoomRatio)}x",
                color = Color.White,
                fontSize = 12.sp
            )
            Slider(
                value = zoomRatio,
                onValueChange = onZoomChange,
                valueRange = 1f..5f,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Flash toggle
            Button(
                onClick = onFlashToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFlashOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isFlashOn) Icons.Filled.Flash else Icons.Filled.FlashOff,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Flash")
            }
        }
    }
}

/**
 * Bottom camera controls
 */
@Composable
fun BottomCameraControls(
    isBatchMode: Boolean,
    isProcessing: Boolean,
    currentQuality: QualityResult,
    showManualControls: Boolean,
    onCaptureImage: () -> Unit,
    onToggleBatchMode: () -> Unit,
    onToggleEdgeDetection: () -> Unit,
    onToggleManualControls: () -> Unit,
    onCompleteBatch: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Til baka",
                    tint = Color.White
                )
            }
            
            // Batch mode toggle
            IconButton(
                onClick = onToggleBatchMode,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isBatchMode) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = "Batch mode",
                    tint = Color.White
                )
            }
            
            // Capture button
            Button(
                onClick = onCaptureImage,
                enabled = !isProcessing && currentQuality != QualityResult.POOR,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (currentQuality) {
                        QualityResult.EXCELLENT -> Color(0xFF4CAF50)
                        QualityResult.GOOD -> Color(0xFF8BC34A)
                        QualityResult.FAIR -> Color(0xFFFFC107)
                        QualityResult.POOR -> Color.Gray
                    }
                ),
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Camera,
                        contentDescription = "Taka mynd",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            // Edge detection toggle
            IconButton(
                onClick = onToggleEdgeDetection,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.CropFree,
                    contentDescription = "Edge detection",
                    tint = Color.White
                )
            }
            
            // Manual controls toggle
            IconButton(
                onClick = onToggleManualControls,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (showManualControls) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Manual controls",
                    tint = Color.White
                )
            }
        }
        
        // Complete batch button (only show in batch mode)
        if (isBatchMode) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onCompleteBatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Klára Batch")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Batch preview með receipt thumbnails
 */
@Composable
fun BatchPreview(
    batch: BatchScanData,
    onRemoveReceipt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Batch Preview",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${batch.getReceiptCount()} myndir",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(batch.scannedReceipts) { receipt ->
                    ReceiptThumbnail(
                        receipt = receipt,
                        onRemove = { onRemoveReceipt(receipt.id) }
                    )
                }
            }
        }
    }
}

/**
 * Receipt thumbnail með remove option
 */
@Composable
fun ReceiptThumbnail(
    receipt: ScannedReceiptData,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(60.dp)
    ) {
        // Thumbnail (placeholder since we can't easily load image here)
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = when (receipt.processingStatus) {
                    io.github.saeargeir.skanniapp.data.ProcessingStatus.COMPLETED -> Color.Green.copy(alpha = 0.3f)
                    io.github.saeargeir.skanniapp.data.ProcessingStatus.PROCESSING -> Color.Yellow.copy(alpha = 0.3f)
                    io.github.saeargeir.skanniapp.data.ProcessingStatus.FAILED -> Color.Red.copy(alpha = 0.3f)
                    else -> Color.Gray.copy(alpha = 0.3f)
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Receipt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(Color.Red, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Fjarlægja",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
        
        // Quality indicator
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(12.dp)
                .background(
                    when {
                        receipt.qualityScore > 0.8f -> Color.Green
                        receipt.qualityScore > 0.6f -> Color.Yellow
                        else -> Color.Red
                    },
                    CircleShape
                )
        )
    }
}

/**
 * Edge detection overlay
 */
@Composable
fun EdgeDetectionOverlay(
    detectedEdges: EdgeDetectionHelper.DetectedEdges,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawEdgeOverlay(detectedEdges)
    }
}

private fun DrawScope.drawEdgeOverlay(detectedEdges: EdgeDetectionHelper.DetectedEdges) {
    if (detectedEdges.corners.size == 4) {
        val path = Path()
        
        // Create path from corners
        path.moveTo(detectedEdges.corners[0].x, detectedEdges.corners[0].y)
        for (i in 1 until detectedEdges.corners.size) {
            path.lineTo(detectedEdges.corners[i].x, detectedEdges.corners[i].y)
        }
        path.close()
        
        // Draw border
        val borderColor = when {
            detectedEdges.confidence > 0.8f -> Color(0xFF4CAF50) // Green
            detectedEdges.confidence > 0.6f -> Color(0xFFFFC107) // Yellow
            else -> Color(0xFFF44336) // Red
        }
        
        drawPath(
            path = path,
            color = borderColor,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8.dp.toPx())
        )
        
        // Draw corner indicators
        for (corner in detectedEdges.corners) {
            drawCircle(
                color = borderColor,
                radius = 12.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(corner.x, corner.y)
            )
        }
    }
}

/**
 * Permission denied screen
 */
@Composable
fun PermissionDeniedScreen(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "SkanniApp þarf camera permission til að taka myndir af kvittunum þínum.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gefa Camera Permission")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateBack) {
            Text("Til baka")
        }
    }
}