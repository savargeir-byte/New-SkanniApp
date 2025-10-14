package io.github.saeargeir.skanniapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.utils.IcelandicInvoiceParser
import io.github.saeargeir.skanniapp.utils.ImageEnhancementUtil
import io.github.saeargeir.skanniapp.utils.EdgeDetectionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/**
 * Advanced OCR processor með hybrid processing og edge detection
 */
class AdvancedOcrProcessor(private val context: Context) {
    
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    companion object {
        private const val TAG = "AdvancedOcrProcessor"
    }
    
    /**
     * Process ImageProxy með edge detection og enhancement
     */
    suspend fun processImage(imageProxy: ImageProxy): OcrResult = withContext(Dispatchers.IO) {
        try {
            // Convert ImageProxy to Bitmap
            val bitmap = imageProxyToBitmap(imageProxy) ?: return@withContext OcrResult.Failed("Could not convert image")
            
            // Process with bitmap
            processBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            OcrResult.Failed(e.message ?: "Unknown error")
        } finally {
            imageProxy.close()
        }
    }
    
    /**
     * Process Bitmap directly
     */
    suspend fun processBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        try {
            // Step 1: Edge detection
            val edgeResult = EdgeDetectionUtil.detectReceiptEdges(bitmap)
            Log.d(TAG, "Edge detection: hasReceipt=${edgeResult.hasReceiptDetected}, quality=${edgeResult.qualityScore}")
            
            // Step 2: Enhance image for OCR
            val quality = ImageEnhancementUtil.assessQuality(bitmap)
            Log.d(TAG, "Image quality: ${quality.overallScore}, recommendation: ${quality.recommendation}")
            
            val enhancedBitmap = if (quality.needsEnhancement()) {
                Log.d(TAG, "Applying enhancement")
                ImageEnhancementUtil.enhanceForOcr(bitmap)
            } else {
                Log.d(TAG, "Using original image")
                bitmap
            }
            
            // Step 3: Perform OCR
            val ocrText = performOcr(enhancedBitmap)
            Log.d(TAG, "OCR completed, text length: ${ocrText.length}")
            
            if (ocrText.isBlank()) {
                return@withContext OcrResult.Failed("No text detected")
            }
            
            // Step 4: Parse invoice data
            val parsedInvoice = IcelandicInvoiceParser.parseInvoiceText(ocrText)
            Log.d(TAG, "Parsed: vendor=${parsedInvoice.vendor}, amount=${parsedInvoice.amount}")
            
            // Step 5: Create invoice record
            val invoice = InvoiceRecord(
                id = System.currentTimeMillis(),
                date = parsedInvoice.date ?: java.time.LocalDate.now().toString(),
                monthKey = (parsedInvoice.date ?: java.time.LocalDate.now().toString()).substring(0, 7),
                vendor = parsedInvoice.vendor,
                amount = parsedInvoice.amount,
                vat = parsedInvoice.vat,
                imagePath = "",
                invoiceNumber = parsedInvoice.invoiceNumber,
                ocrText = ocrText
            )
            
            OcrResult.Success(
                invoice = invoice,
                ocrText = ocrText,
                edgeDetection = edgeResult,
                quality = quality,
                confidence = parsedInvoice.confidence
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing bitmap", e)
            OcrResult.Failed(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Perform OCR on bitmap
     */
    private suspend fun performOcr(bitmap: Bitmap): String = suspendCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    continuation.resumeWithException(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating input image", e)
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Convert ImageProxy to Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image ?: return null
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            null
        }
    }
    
    /**
     * Hybrid processing með multiple OCR engines (future enhancement)
     */
    suspend fun processWithHybrid(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        // For now, just use the standard processing
        // In the future, this could combine multiple OCR engines
        processBitmap(bitmap)
    }
    
    /**
     * Batch processing
     */
    suspend fun processBatch(bitmaps: List<Bitmap>): List<OcrResult> = withContext(Dispatchers.IO) {
        bitmaps.map { bitmap ->
            processBitmap(bitmap)
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        recognizer.close()
    }
}

/**
 * OCR result sealed class
 */
sealed class OcrResult {
    data class Success(
        val invoice: InvoiceRecord,
        val ocrText: String,
        val edgeDetection: EdgeDetectionUtil.EdgeDetectionResult,
        val quality: ImageEnhancementUtil.ImageQuality,
        val confidence: Float
    ) : OcrResult()
    
    data class Failed(val error: String) : OcrResult()
}
