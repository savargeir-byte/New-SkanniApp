package io.github.saeargeir.skanniapp.data

import android.net.Uri
import java.util.Date

/**
 * Data class fyrir batch scanning operations
 * Holds information about multiple receipts scanned in one session
 */
data class BatchScanData(
    val id: String = generateBatchId(),
    val scannedReceipts: MutableList<ScannedReceiptData> = mutableListOf(),
    val createdAt: Date = Date(),
    val isCompleted: Boolean = false
) {
    companion object {
        fun generateBatchId(): String = "batch_${System.currentTimeMillis()}"
    }
    
    fun addReceipt(receipt: ScannedReceiptData) {
        scannedReceipts.add(receipt)
    }
    
    fun removeReceipt(receiptId: String) {
        scannedReceipts.removeAll { it.id == receiptId }
    }
    
    fun getReceiptCount(): Int = scannedReceipts.size
    
    fun getTotalAmount(): Double = scannedReceipts.sumOf { it.totalAmount ?: 0.0 }
    
    fun markCompleted(): BatchScanData = copy(isCompleted = true)
    
    // Computed properties for UI
    val progressPercentage: Float
        get() = if (scannedReceipts.isEmpty()) 0f else {
            scannedReceipts.count { it.processingStatus == ProcessingStatus.COMPLETED }.toFloat() / scannedReceipts.size
        }
    
    val successRate: Float
        get() = if (scannedReceipts.isEmpty()) 0f else {
            scannedReceipts.count { it.processingStatus == ProcessingStatus.COMPLETED }.toFloat() / scannedReceipts.size
        }
}

/**
 * Data class fyrir individual receipt í batch
 */
data class ScannedReceiptData(
    val id: String = generateReceiptId(),
    val imageUri: Uri,
    val ocrText: String? = null,
    val totalAmount: Double? = null,
    val merchant: String? = null,
    val date: Date? = null,
    val qualityScore: Float = 0f, // 0-1 range
    val edgeDetected: Boolean = false,
    val processingStatus: ProcessingStatus = ProcessingStatus.PENDING
) {
    companion object {
        fun generateReceiptId(): String = "receipt_${System.currentTimeMillis()}"
    }
}

enum class ProcessingStatus {
    PENDING,
    PROCESSING, 
    COMPLETED,
    FAILED,
    RETRY_NEEDED
}