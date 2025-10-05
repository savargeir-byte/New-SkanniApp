package io.github.saeargeir.skanniapp.data

import android.content.Context
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InvoiceStore(private val context: Context) {
    private val file: File by lazy { File(context.filesDir, "invoices.json") }

    fun loadAll(): List<InvoiceRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val text = file.readText()
            val arr = JSONArray(text)
            (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAll(list: List<InvoiceRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        file.writeText(arr.toString())
    }

    fun add(record: InvoiceRecord) {
        val current = loadAll().toMutableList()
        current.add(record)
        saveAll(current)
    }

    fun deleteById(id: Long) {
        val current = loadAll().toMutableList()
        val newList = current.filterNot { it.id == id }
        saveAll(newList)
    }

    fun update(record: InvoiceRecord) {
        val current = loadAll().toMutableList()
        val idx = current.indexOfFirst { it.id == record.id }
        if (idx >= 0) {
            current[idx] = record
            saveAll(current)
        }
    }
    
    fun clearAll() {
        try {
            if (file.exists()) {
                file.delete()
            }
            // Also clear any cached images
            val imagesDir = File(context.filesDir, "images")
            if (imagesDir.exists()) {
                imagesDir.listFiles()?.forEach { imageFile ->
                    try {
                        imageFile.delete()
                    } catch (e: Exception) {
                        // Log but don't throw - continue clearing other files
                    }
                }
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
        }
    }

    private fun toJson(r: InvoiceRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("date", r.date)
        put("monthKey", r.monthKey)
        put("vendor", r.vendor)
        put("amount", r.amount)
        put("vat", r.vat)
        put("imagePath", r.imagePath)
        if (r.invoiceNumber != null) put("invoiceNumber", r.invoiceNumber)
    }

    private fun fromJson(o: JSONObject): InvoiceRecord = InvoiceRecord(
        id = o.getLong("id"),
        date = o.getString("date"),
        monthKey = o.getString("monthKey"),
        vendor = o.getString("vendor"),
        amount = o.getDouble("amount"),
        vat = o.getDouble("vat"),
        imagePath = o.getString("imagePath"),
        // org.json's optString(name, fallback) requires a non-null fallback; use empty string and map to null
        invoiceNumber = o.optString("invoiceNumber", "").takeIf { it.isNotEmpty() }
    )
}