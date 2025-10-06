package io.github.saeargeir.skanniapp.model

data class InvoiceRecord(
    val id: Long,
    val date: String,        // ISO yyyy-MM-dd
    val monthKey: String,    // yyyy-MM
    val vendor: String,
    val amount: Double,
    val vat: Double,         // VAT amount (not percent)
    val imagePath: String,   // absolute path in internal storage
    val invoiceNumber: String? = null,
    val categoryId: String? = null,  // Smart categorization
    val ocrText: String? = null,     // Full OCR text for ML analysis
    val isManuallyClassified: Boolean = false,  // User manually changed category
    val classificationConfidence: Double = 0.0  // ML confidence score
)
