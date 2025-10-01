package com.example.notescanner

import java.io.File
import java.io.FileWriter

object ExcelUtil {
    fun ensureHeader(excelFile: File) {
        if (!excelFile.exists()) {
            excelFile.parentFile?.mkdirs()
            FileWriter(excelFile, true).use { w ->
                // CSV header (Excel-compatible). Order: InvoiceNo, Vendor, Date, Month, Net, VAT, Total, File
                w.write("ReikningsNr,Fyrirtæki,Dagsetning,Mánuður,Nettó,VSK,Heild,Skrá\n")
            }
        }
    }

    fun appendToExcel(rowData: List<String>, excelFile: File) {
        try {
            ensureHeader(excelFile)
            FileWriter(excelFile, true).use { w ->
                fun esc(s: String): String {
                    val needsQuotes = s.contains(',') || s.contains('"') || s.contains('\n')
                    val body = if (s.contains('"')) s.replace("\"", "\"\"") else s
                    return if (needsQuotes) "\"$body\"" else body
                }
                w.write(rowData.joinToString(",") { esc(it) } + "\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
