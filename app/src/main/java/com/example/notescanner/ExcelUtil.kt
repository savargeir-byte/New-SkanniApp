package com.example.notescanner

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

object ExcelUtil {
    fun writeToExcel(invoiceInfo: Map<String, String>, excelFile: File) {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Reikningar")
        val row = sheet.createRow(0)
        var col = 0
        invoiceInfo.forEach { (key, value) ->
            row.createCell(col++).setCellValue("$key: $value")
        }
        FileOutputStream(excelFile).use { out ->
            workbook.write(out)
        }
        workbook.close()
    }

    fun appendToExcel(rowData: List<String>, excelFile: File) {
        val workbook = if (excelFile.exists()) {
            FileInputStream(excelFile).use { fis ->
                XSSFWorkbook(fis)
            }
        } else {
            XSSFWorkbook()
        }
        val sheet = workbook.getSheet("Reikningar") ?: workbook.createSheet("Reikningar")
        val row = sheet.createRow(sheet.lastRowNum + 1)
        rowData.forEachIndexed { idx, value ->
            row.createCell(idx).setCellValue(value)
        }
        FileOutputStream(excelFile).use { out ->
            workbook.write(out)
        }
        workbook.close()
    }
}
