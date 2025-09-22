package com.example.notescanner.model

data class InvoiceRecord(
    val id: Long,
    val date: String,        // ISO yyyy-MM-dd
    val monthKey: String,    // yyyy-MM
    val vendor: String,
    val amount: Double,
    val vat: Double,         // VAT amount (not percent)
    val imagePath: String    // absolute path in internal storage
)
