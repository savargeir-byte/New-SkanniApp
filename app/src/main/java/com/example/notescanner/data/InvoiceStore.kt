package com.example.notescanner.data

import android.content.Context
import com.example.notescanner.model.InvoiceRecord
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

    private fun toJson(r: InvoiceRecord): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("date", r.date)
        put("monthKey", r.monthKey)
        put("vendor", r.vendor)
        put("amount", r.amount)
        put("vat", r.vat)
        put("imagePath", r.imagePath)
    }

    private fun fromJson(o: JSONObject): InvoiceRecord = InvoiceRecord(
        id = o.getLong("id"),
        date = o.getString("date"),
        monthKey = o.getString("monthKey"),
        vendor = o.getString("vendor"),
        amount = o.getDouble("amount"),
        vat = o.getDouble("vat"),
        imagePath = o.getString("imagePath")
    )
}
