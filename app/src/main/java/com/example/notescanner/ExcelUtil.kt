package com.example.notescanner

import java.io.File
import java.io.FileWriter

object ExcelUtil {
    fun ensureHeader(excelFile: File) {
        if (!excelFile.exists()) {
            excelFile.parentFile?.mkdirs()
            FileWriter(excelFile, true).use { w ->
                w.write("Skrá, Dagsetning, Mánuður, Fyrirtæki, Upphæð, VSK\n")
            }
        }
    }

    fun appendToExcel(rowData: List<String>, excelFile: File) {
        try {
            ensureHeader(excelFile)
            FileWriter(excelFile, true).use { w ->
                w.write(rowData.joinToString(",") + "\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
