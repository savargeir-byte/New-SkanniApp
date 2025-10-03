package io.github.saeargeir.skanniapp.model

data class InvoiceRecord(
    val id: String,
    val vendor: String,
    val amount: Double,
    val vat: Double,
    val date: String,
    val month: String,
    val invoiceNumber: String?,
    val imagePath: String
)