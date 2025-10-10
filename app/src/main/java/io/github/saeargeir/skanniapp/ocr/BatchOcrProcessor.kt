package io.github.saeargeir.skanniapp.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.saeargeir.skanniapp.data.BatchScanData
import io.github.saeargeir.skanniapp.data.ScannedReceiptData
import io.github.saeargeir.skanniapp.data.ProcessingStatus
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.utils.IcelandicInvoiceParser
import io.github.saeargeir.skanniapp.utils.EdgeDetectionUtil
import io.github.saeargeir.skanniapp.utils.ImageEnhancementUtil
import timber.log.Timber
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Batch OCR processor fyrir margfelda reikningar
 */
class BatchOcrProcessor(
    private val context: Context
) {
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "BatchOcrProcessor"
    }

    /**
     * Process multiple receipts in batch
     */
    fun processBatchFlow(
        receipts: List<ScannedReceiptData>,
        onProgress: (BatchProgress) -> Unit
    ): Flow<BatchProcessingResult> = flow {
        emit(BatchProcessingResult.Started)
        
        try {
                        val result = processBatch(receipts, onProgress)
            emit(BatchProcessingResult.Completed(result))
        } catch (e: Exception) {
            Timber.e(e, "Batch processing failed")
            emit(BatchProcessingResult.Failed(e.message ?: "Unknown error"))
        }
    }

    /**
     * Process batch with concurrent processing
     */
    private suspend fun processBatch(
        receipts: List<ScannedReceiptData>,
        onProgress: (BatchProgress) -> Unit
    ): BatchScanData = withContext(Dispatchers.IO) {
        
        val totalCount = receipts.size
        var successCount = 0
        var failureCount = 0
        val processedReceipts = mutableListOf<InvoiceRecord>()
        
        val semaphore = Semaphore(3) // Max 3 concurrent OCR operations
        
        val jobs = receipts.mapIndexed { index, receipt ->
            async {
                try {
                    semaphore.acquire()
                    
                    // Update progress
                    withContext(Dispatchers.Main) {
                        onProgress(BatchProgress(
                            currentIndex = index,
                            totalCount = totalCount,
                            successCount = successCount,
                            failureCount = failureCount,
                            status = "Vinnur úr reikningi ${index + 1}...",
                            processedReceipts = processedReceipts.toList(),
                            currentReceipt = receipt
                        ))
                    }
                    
                    val processed = processReceiptWithRetry(receipt, maxRetries = 2)
                    
                    if (processed != null) {
                        synchronized(processedReceipts) {
                            processedReceipts.add(processed)
                            successCount++
                        }
                        ProcessingResult.Success(processed)
                    } else {
                        synchronized(processedReceipts) {
                            failureCount++
                        }
                        ProcessingResult.Failure("OCR failed after retries")
                    }
                        
                } catch (e: Exception) {
                    synchronized(processedReceipts) {
                        failureCount++
                    }
                    ProcessingResult.Failure(e.message ?: "Unknown error")
                } finally {
                    semaphore.release()
                }
            }
        }
        
        // Await all jobs
        val results = jobs.awaitAll()
        
        // Final progress update
        withContext(Dispatchers.Main) {
            onProgress(BatchProgress(
                currentIndex = totalCount,
                totalCount = totalCount,
                successCount = successCount,
                failureCount = failureCount,
                status = if (successCount == totalCount) "Lokið!" else "Lokið með ${failureCount} villum",
                processedReceipts = processedReceipts.toList(),
                currentReceipt = null
            ))
        }
        
        BatchScanData(
            id = "batch_${System.currentTimeMillis()}",
            scannedReceipts = processedReceipts.map { invoice ->
                ScannedReceiptData(
                    id = invoice.id.toString(),
                    imageUri = android.net.Uri.parse(invoice.imagePath),
                    ocrText = invoice.ocrText,
                    totalAmount = invoice.amount,
                    merchant = invoice.vendor,
                    processingStatus = ProcessingStatus.COMPLETED
                )
            }.toMutableList(),
            isCompleted = successCount == totalCount
        )
    }

    /**
     * Process single receipt with retry logic
     */
    private suspend fun processReceiptWithRetry(
        receipt: ScannedReceiptData,
        maxRetries: Int
    ): InvoiceRecord? = withContext(Dispatchers.IO) {
        
        var attempt = 0
        var lastException: Exception? = null
        
        while (attempt <= maxRetries) {
            try {
                val bitmap = loadBitmapFromUri(receipt.imageUri) ?: return@withContext null
                val enhancedBitmap = enhanceBitmapForOcr(bitmap)
                val text = performOcrOnBitmap(enhancedBitmap)
                
                if (text.isNotBlank()) {
                    val parsedInvoice = IcelandicInvoiceParser.parseInvoiceText(text)
                    val invoice = InvoiceRecord(
                        id = System.currentTimeMillis(),
                        date = java.time.LocalDate.now().toString(),
                        monthKey = java.time.LocalDate.now().toString().substring(0, 7),
                        vendor = parsedInvoice.vendor,
                        amount = parsedInvoice.amount,
                        vat = parsedInvoice.vat,
                        imagePath = receipt.imageUri.toString(),
                        invoiceNumber = parsedInvoice.invoiceNumber,
                        ocrText = text
                    )
                    return@withContext invoice
                }
                
            } catch (e: Exception) {
                                lastException = e
                Timber.w(e, "Attempt ${attempt + 1} failed for ${receipt.id}")
            }
            
            attempt++
            
            if (attempt <= maxRetries) {
                delay(500L * attempt) // Exponential backoff
            }
        }
        
        Timber.e(lastException, "All attempts failed for ${receipt.id}")
        return@withContext null
    }
    
    /**
     * Perform OCR á bitmap
     */
    private suspend fun performOcrOnBitmap(bitmap: Bitmap): String = suspendCoroutine { continuation ->
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
                
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
    
    /**
     * Load bitmap frá URI
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load bitmap from URI")
            null
        }
    }
    
        /**
     * Enhance bitmap for better OCR results
     */
    private fun enhanceBitmapForOcr(bitmap: Bitmap): Bitmap {
        return try {
            Timber.d("Enhancing bitmap for OCR")
            
            // Step 1: Check if enhancement is needed
            val quality = ImageEnhancementUtil.assessQuality(bitmap)
            Timber.d("Image quality: ${quality.overallScore}")
            
            if (quality.isExcellentQuality()) {
                Timber.d("Image quality is excellent, skipping enhancement")
                return bitmap
            }
            
            // Step 2: Apply enhancement
            val enhanced = if (quality.isGoodQuality()) {
                // Quick enhancement for decent images
                ImageEnhancementUtil.quickEnhance(bitmap)
            } else {
                // Full enhancement for poor quality images
                Timber.d("Applying full enhancement: ${quality.recommendation}")
                ImageEnhancementUtil.enhanceForOcr(bitmap)
            }
            
            Timber.d("Enhancement completed successfully")
            enhanced
        } catch (e: Exception) {
            Timber.w(e, "Enhancement failed, using original")
            bitmap
        }
    }
    
    /**
     * Save bitmap til skrár
     */
    private fun saveBitmapToFile(bitmap: Bitmap, filename: String): Uri? {
        return try {
            val file = File(context.filesDir, filename)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
                        Uri.fromFile(file)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save bitmap")
            null
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * Data classes fyrir batch processing
 */
data class BatchProgress(
    val currentIndex: Int,
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val status: String,
    val processedReceipts: List<InvoiceRecord>,
    val currentReceipt: ScannedReceiptData? = null
)

sealed class BatchProcessingResult {
    object Started : BatchProcessingResult()
    data class Completed(val batchData: BatchScanData) : BatchProcessingResult()
    data class Failed(val error: String) : BatchProcessingResult()
}

sealed class ProcessingResult {
    data class Success(val invoice: InvoiceRecord) : ProcessingResult()
    data class Failure(val error: String) : ProcessingResult()
}