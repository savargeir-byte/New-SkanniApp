package com.example.notescanner

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

object OcrUtil {
    fun recognizeTextFromImage(context: Context, imageFile: File, onResult: (String) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    onResult(visionText.text.ifBlank { "" })
                }
                .addOnFailureListener { e ->
                    onResult("Villa við OCR: ${e.message}")
                }
        } catch (e: Exception) {
            onResult("Villa við OCR: ${e.message}")
        }
    }

    // Offline VSK extraction (án VSK, VSK upphæð, heild) úr OCR texta
    data class VatExtraction(
        val subtotal: Double?, // án VSK
        val tax: Double?,      // VSK upphæð
        val total: Double?,    // með VSK
        val rates: Map<Double, Double> = emptyMap() // prósenta -> VSK upphæð
    )

    fun extractVatAmounts(ocrText: String): VatExtraction {
        fun parseNumber(s: String): Double? {
            val cleaned = s.lowercase()
                .replace("kr", "")
                .replace("\u00A0", "") // non-breaking space
                .replace("\\s+".toRegex(), "")
                .replace(".", "")
                .replace(",", ".")
            return cleaned.toDoubleOrNull()
        }

        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val numPattern = "([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)"
        val pctPattern = "([0-9]{1,2}(?:,[0-9]{1,2})?)\\s*%"

        var subtotal: Double? = null
        var tax: Double? = null
        var total: Double? = null
        val rateMap = mutableMapOf<Double, Double>()

        lines.forEach { line ->
            val l = line.lowercase()

            if (total == null && l.contains(Regex("\\b(heild|alls|samtals|með\\s*vsk|m/\\s*vsk|total|amount)\\b"))) {
                Regex(numPattern).find(line)?.let { total = parseNumber(it.value) }
            }
            if (subtotal == null && l.contains(Regex("\\b(án\\s*vsk|verð\\s*án\\s*vsk|subtotal|nettó|netto)\\b"))) {
                Regex(numPattern).find(line)?.let { subtotal = parseNumber(it.value) }
            }

            if (l.contains("vsk") || l.contains("virðisauk")) {
                Regex(pctPattern).findAll(line).forEach { m ->
                    val rate = parseNumber(m.groupValues[1]) ?: return@forEach
                    val nums = Regex(numPattern).findAll(line).map { it.value }.toList()
                    nums.lastOrNull()?.let { amtStr ->
                        parseNumber(amtStr)?.let { amt -> rateMap[rate] = (rateMap[rate] ?: 0.0) + amt }
                    }
                }
                if (!Regex(pctPattern).containsMatchIn(line) && tax == null) {
                    Regex(numPattern).findAll(line).map { it.value }.lastOrNull()?.let { tax = parseNumber(it) }
                }
            }
        }

        when {
            subtotal != null && total != null && tax == null -> tax = (total!! - subtotal!!).let { if (it >= -0.01) it else null }
            subtotal == null && total != null && tax != null -> subtotal = total!! - tax!!
            subtotal != null && tax != null && total == null -> total = subtotal!! + tax!!
        }

        return VatExtraction(subtotal, tax, total, rateMap)
    }

    data class ParsedInvoice(
        val vendor: String?,
        val amount: Double?,
        val vat: Double?,
        val date: String?
    )

    fun parse(text: String): ParsedInvoice {
        val normalized = text.replace("\r", "")
        val lines = normalized.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Vendor: explicit label first, otherwise first sensible line
        val labeledVendor = Regex("""(?i)\b(fyrirtæki|seller|vendor)\b\s*[:：]\s*(.+)""")
            .find(normalized)?.groupValues?.getOrNull(2)?.trim()
        val vendor = labeledVendor ?: run {
            val skipWords = listOf(
                "kvittun", "kassakvittun", "kassi", "reikningur", "pos",
                "sundurliðun", "sundurliðun", "kvittun nr", "receipt", "total", "upphæð"
            )
            lines.firstOrNull { line ->
                val l = line.lowercase()
                skipWords.none { sw -> l.contains(sw) } && line.length in 2..64
            }
        }

        // Parse amounts like 1.500, 1,500, 1500, with optional 'kr'
        fun parseAmount(str: String?): Double? = str?.let {
            val cleaned = it
                .replace("kr", "", ignoreCase = true)
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
            cleaned.toDoubleOrNull()
        }

        // Try labeled total first
        val amountFromLabel = Regex("""(?i)\b(upphæð|heild|samtals|total|amount)\b[^0-9-]*([-]?[0-9][0-9\., ]*)\s*kr?""")
            .find(normalized)?.groupValues?.getOrNull(2)

        // Fallback: pick the largest plausible number in the text
        val allNumbers = Regex("""(?<![A-Z0-9])([-]?[0-9]{1,3}(?:[\., ][0-9]{3})*(?:[\.,][0-9]{1,2})?)""")
            .findAll(normalized)
            .map { it.groupValues[1] }
            .mapNotNull { parseAmount(it) }
            .filter { it >= 1.0 }
            .toList()
        val maxNumber = allNumbers.maxOrNull()
        val amount = parseAmount(amountFromLabel) ?: maxNumber

        // VAT: explicit kr value
        val vatText = Regex("""(?i)\b(vsk|vat)\b[^0-9]*([0-9][0-9\., ]*)\s*kr?""")
            .find(normalized)?.groupValues?.getOrNull(2)
        val vat = parseAmount(vatText)

        // Date: support yyyy-MM-dd, dd.MM.yyyy, dd/MM/yyyy, dd-MM-yyyy
        val dateRegexes = listOf(
            Regex("""\b([0-9]{4}-[0-9]{2}-[0-9]{2})\b"""),
            Regex("""\b([0-9]{2}\.[0-9]{2}\.[0-9]{4})\b"""),
            Regex("""\b([0-9]{2}/[0-9]{2}/[0-9]{4})\b"""),
            Regex("""\b([0-9]{2}-[0-9]{2}-[0-9]{4})\b""")
        )
        val rawDate = dateRegexes.firstNotNullOfOrNull { it.find(normalized)?.groupValues?.getOrNull(1) }
        val date = try {
            if (rawDate == null) null else normalizeDate(rawDate)
        } catch (_: Exception) { null }

        return ParsedInvoice(vendor, amount, vat, date)
    }

    private fun normalizeDate(raw: String): String {
        // Normalize common formats to yyyy-MM-dd without relying on java.time
        // 1) Already ISO: yyyy-MM-dd
        if (Regex("""^\n?\d{4}-\d{2}-\d{2}$""").matches(raw.trim())) {
            return raw.trim()
        }

        // 2) dd.MM.yyyy -> yyyy-MM-dd
        Regex("""^(\n?)(\d{2})\.(\d{2})\.(\d{4})$""").matchEntire(raw.trim())?.let {
            val (_, dd, MM, yyyy) = it.groupValues
            return "$yyyy-$MM-$dd"
        }

        // 3) dd/MM/yyyy -> yyyy-MM-dd
        Regex("""^(\n?)(\d{2})/(\d{2})/(\d{4})$""").matchEntire(raw.trim())?.let {
            val (_, dd, MM, yyyy) = it.groupValues
            return "$yyyy-$MM-$dd"
        }

        // 4) dd-MM-yyyy -> yyyy-MM-dd
        Regex("""^(\n?)(\d{2})-(\d{2})-(\d{4})$""").matchEntire(raw.trim())?.let {
            val (_, dd, MM, yyyy) = it.groupValues
            return "$yyyy-$MM-$dd"
        }

        // Fallback: return as-is
        return raw.trim()
    }
}
