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
            // Robust normalization for ISK decimals and common OCR confusions
            val replaced = s
                .replace("\u00A0", " ") // NBSP -> space
                .replace("—", "-")
                .replace("–", "-")
                .replace("−", "-")
                .replace(Regex("(?i)[oO]"), "0")
                .replace(Regex("(?i)[il]"), "1")
                .replace("S", "5")
                .replace("B", "8")
                .replace("Z", "2")
            val cleaned = replaced
                .lowercase()
                .replace("kr", "")
                .replace(" isk", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(".", "") // drop thousand separators
                .replace(",", ".") // comma -> decimal
            return cleaned.toDoubleOrNull()
        }

        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val numPattern = "([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)"
        val pctPattern = "([0-9]{1,2}(?:,[0-9]{1,2})?)\\s*%"
        val numRe = Regex(numPattern)
        val pctRe = Regex(pctPattern)

        var subtotal: Double? = null
        var tax: Double? = null
        var total: Double? = null
        val rateMap = mutableMapOf<Double, Double>()

    // 1) Try structured table capture: line like "Vsk %  Vsk  Nettó  Upphæð" followed by rows like "11% 222,97 2027,03 2250,00"
        var tableStart = -1
        var tableEnd = -1
        run {
            val headerIdx = lines.indexOfFirst { l ->
                val ll = l.lowercase()
        // Support variations: "VSK-upphæð", "Nettó", "Upphæð", etc.
        ll.contains("vsk") && (ll.contains("%") || ll.contains("prós")) &&
            (ll.contains("nett") || ll.contains("netto")) &&
            (ll.contains("upph") || ll.contains("heild") || ll.contains("samtals"))
            }
            if (headerIdx >= 0) {
                val rowRe4 = Regex("^\\s*([0-9]{1,2}(?:,[0-9]{1,2})?)\\s*%\\s+" +
                        "([0-9][0-9\\., ]*)\\s+" +
                        "([0-9][0-9\\., ]*)\\s+" +
                        "([0-9][0-9\\., ]*)\\s*$")
                val rowRe3 = Regex("^\\s*([0-9]{1,2}(?:,[0-9]{1,2})?)\\s*%\\s+" +
                        "([0-9][0-9\\., ]*)\\s+" +
                        "([0-9][0-9\\., ]*)\\s*$")

                var i = headerIdx + 1
                var rows = 0
                var accNet = 0.0
                var accTax = 0.0
                var accTotal = 0.0
                while (i < lines.size) {
                    val line = lines[i]
                    val m4 = rowRe4.find(line)
                    val m3 = rowRe3.find(line)
                    if (m4 != null || m3 != null) {
                        rows++
                        tableStart = if (tableStart == -1) headerIdx else tableStart
                        tableEnd = i
                        val rateStr = (m4?:m3)!!.groupValues[1]
                        val rate = parseNumber(rateStr)
                        var vTax: Double? = null
                        var vNet: Double? = null
                        var vSum: Double? = null
                        if (m4 != null) {
                            vTax = parseNumber(m4.groupValues[2])
                            vNet = parseNumber(m4.groupValues[3])
                            vSum = parseNumber(m4.groupValues[4])
                        } else if (m3 != null) {
                            // Assume columns are VSK and Nettó; derive Upphæð if possible later
                            vTax = parseNumber(m3.groupValues[2])
                            vNet = parseNumber(m3.groupValues[3])
                            vSum = if (vTax != null && vNet != null) vTax + vNet else null
                        }
                        if (rate != null) {
                            val current = rateMap[rate] ?: 0.0
                            if (vTax != null) rateMap[rate] = current + vTax
                        }
                        if (vNet != null) accNet += vNet
                        if (vTax != null) accTax += vTax
                        if (vSum != null) accTotal += vSum else if (vNet != null && vTax != null) accTotal += vNet + vTax
                    } else {
                        // Stop when table ends (hit a non-matching line after at least one row)
                        if (rows > 0) break
                    }
                    i++
                }
                if (rows > 0) {
                    if (accTax > 0.0) tax = accTax
                    if (accNet > 0.0) subtotal = accNet
                    if (accTotal > 0.0) total = accTotal
                }
            }
        }

        lines.forEachIndexed { idx, line ->
            // Skip table lines to prevent double counting
            if (tableStart >= 0 && idx >= tableStart && idx <= tableEnd) return@forEachIndexed
            val l = line.lowercase()

            // Prefer explicit Icelandic labels first (these appear on both receipts)
            if (total == null && (
                    l.contains(Regex("\\b(til\\s*grei[ðd]slu)\\b")) ||
                    l.contains(Regex("\\b(samtals(\\s*isk)?(\\s*me[ðd]\\s*vsk)?)\\b")) ||
                    l.contains(Regex("\\b(heild\\s*(me[ðd])?\\s*vsk)\\b")) ||
                    l.contains(Regex("\\b(upph[aæ][ðd])\\b")) ||
                    l.contains(Regex("\\b(total|amount)\\b"))
                )
            ) {
                // Use last number on the line (right-aligned totals)
                numRe.findAll(line).lastOrNull()?.let { total = parseNumber(it.value) }
            }
            if (subtotal == null && (
                    l.contains(Regex("\\b(heildar\\s*isk\\s*an\\s*vsk)\\b")) ||
                    l.contains(Regex("\\b(\\ban\\s*vsk|ver[ðd]\\s*an\\s*vsk|nett[oó]|netto|subtotal)\\b"))
                )
            ) {
                numRe.findAll(line).lastOrNull()?.let { subtotal = parseNumber(it.value) }
            }

            // Collect per-rate VSK amounts. Support lines both with and without explicit "vsk" text.
            var hadPct = false
            pctRe.findAll(line).forEach { m ->
                hadPct = true
                val rate = parseNumber(m.groupValues[1]) ?: return@forEach
                // Try to bind the amount appearing after the percentage on the same line.
                val afterIdx = m.range.last + 1
                val amtStr = if (afterIdx in 0..line.lastIndex) {
                    numRe.find(line.substring(afterIdx))?.value
                } else null
                val chosen = amtStr ?: numRe.findAll(line).map { it.value }.lastOrNull()
                chosen?.let { s ->
                    parseNumber(s)?.let { amt -> rateMap[rate] = (rateMap[rate] ?: 0.0) + amt }
                }
            }

            // If a tax line mentions VSK but no percentage, treat the last number as the total VSK amount.
            if (!hadPct && (l.contains("vsk") || l.contains("virðisauk") || l.contains("vsk-upph"))) {
                // Prefer explicit VSK-upphæð pattern
                if (tax == null && l.contains("upph")) {
                    numRe.findAll(line).lastOrNull()?.let { tax = parseNumber(it) }
                } else if (tax == null) {
                    numRe.findAll(line).map { it.value }.lastOrNull()?.let { tax = parseNumber(it) }
                }
            }
        }

        // If individual rate amounts were found but total tax was not, derive it as the sum.
        if (tax == null && rateMap.isNotEmpty()) {
            tax = rateMap.values.sum()
        }

        // Ensure we always include the common Icelandic VAT rates 24% and 11% in the map.
        listOf(24.0, 11.0).forEach { r -> if (!rateMap.containsKey(r)) rateMap[r] = 0.0 }

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
                .replace("\u00A0", " ")
                .replace("—", "-")
                .replace("–", "-")
                .replace("−", "-")
                .replace("kr", "", ignoreCase = true)
                .replace("ISK", "", ignoreCase = true)
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
            cleaned.toDoubleOrNull()
        }

        // Try labeled total first
        val amountFromLabel = Regex("""(?i)\b(til\s*grei[ðd]slu|samtals(\s*isk)?(\s*me[ðd]\s*vsk)?|heild(\s*me[ðd])?\s*vsk|upph[aæ]ð|total|amount)\b[^0-9-]*([-]?[0-9][0-9\., ]*)\s*(kr|isk)?""")
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
        val vatText = Regex("""(?i)\b(vsk-?upph[aæ]ð|vsk|vat)\b[^0-9]*([0-9][0-9\., ]*)\s*(kr|isk)?""")
            .find(normalized)?.groupValues?.getOrNull(2)
        val vat = parseAmount(vatText)

        // Date: support yyyy-MM-dd, dd.MM.yyyy, dd/MM/yyyy, dd-MM-yyyy
        val dateRegexes = listOf(
            // yyyy-MM-dd or with time
            Regex("""\b([0-9]{4}-[0-9]{2}-[0-9]{2}(?:\s+[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)\b"""),
            // dd.MM.yyyy [HH:mm]
            Regex("""\b([0-9]{2}\.[0-9]{2}\.[0-9]{4}(?:\s+[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)\b"""),
            // dd/MM/yyyy [HH:mm]
            Regex("""\b([0-9]{2}/[0-9]{2}/[0-9]{4}(?:\s+[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)\b"""),
            // dd-MM-yyyy [HH:mm]
            Regex("""\b([0-9]{2}-[0-9]{2}-[0-9]{4}(?:\s+[0-9]{2}:[0-9]{2}(?::[0-9]{2})?)?)\b""")
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

        // 5) Any of the above with time suffix: split and normalize date part only
        val parts = raw.trim().split(" ")
        if (parts.isNotEmpty()) {
            val base = normalizeDate(parts.first())
            return base
        }

        // Fallback: return as-is
        return raw.trim()
    }
}
