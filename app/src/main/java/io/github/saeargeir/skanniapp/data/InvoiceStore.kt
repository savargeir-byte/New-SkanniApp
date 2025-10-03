package io.github.saeargeir.skanniapp.data

import android.content.Context
import android.content.SharedPreferences
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import org.json.JSONArray
import org.json.JSONObject

class InvoiceStore(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("invoices", Context.MODE_PRIVATE)
    
    fun save(record: InvoiceRecord) {
        val existingJson = prefs.getString("records", "[]")
        val array = JSONArray(existingJson)
        
        // Check if record with same ID exists and update it
        var found = false
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") == record.id) {
                array.put(i, recordToJson(record))
                found = true
                break
            }
        }
        
        // If not found, add new record
        if (!found) {
            array.put(recordToJson(record))
        }
        
        prefs.edit().putString("records", array.toString()).apply()
    }
    
    fun loadAll(): List<InvoiceRecord> {
        val json = prefs.getString("records", "[]") ?: "[]"
        val array = JSONArray(json)
        val records = mutableListOf<InvoiceRecord>()
        
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                records.add(jsonToRecord(obj))
            } catch (e: Exception) {
                // Skip invalid records
            }
        }
        
        return records.sortedByDescending { it.date }
    }
    
    fun delete(id: String) {
        val existingJson = prefs.getString("records", "[]")
        val array = JSONArray(existingJson)
        val newArray = JSONArray()
        
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") != id) {
                newArray.put(obj)
            }
        }
        
        prefs.edit().putString("records", newArray.toString()).apply()
    }
    
    private fun recordToJson(record: InvoiceRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.id)
            put("vendor", record.vendor)
            put("amount", record.amount)
            put("vat", record.vat)
            put("date", record.date)
            put("month", record.month)
            put("invoiceNumber", record.invoiceNumber ?: "")
            put("imagePath", record.imagePath)
        }
    }
    
    private fun jsonToRecord(obj: JSONObject): InvoiceRecord {
        return InvoiceRecord(
            id = obj.getString("id"),
            vendor = obj.getString("vendor"),
            amount = obj.getDouble("amount"),
            vat = obj.getDouble("vat"),
            date = obj.getString("date"),
            month = obj.getString("month"),
            invoiceNumber = obj.optString("invoiceNumber").takeIf { it.isNotEmpty() },
            imagePath = obj.getString("imagePath")
        )
    }
}