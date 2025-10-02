package io.github.saeargeir.skanniapp

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
            // Normalize whitespace and separators for Icelandic formatting
            val cleaned = s
                .replace("\u00A0", " ") // NBSP -> space
                .trim()
                .lowercase()
                .replace("kr", "")
                .replace(" isk", "")
                .replace(" ", "")
            
            // Handle Icelandic number formatting:
            // - Thousands separated by dots: 31.656
            // - Decimals separated by commas: 1.234,50
            // - For amounts like 7.598 we need to determine if it's 7598.0 or 7.598
            val result = when {
                // Pattern: digits.digits,digits (e.g., "1.234,50") - thousands with decimal
                cleaned.matches(Regex("[0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{1,2}")) -> {
                    cleaned.replace(".", "").replace(",", ".")
                }
                // Pattern: digits,digits (e.g., "1234,50") - simple decimal with comma
                cleaned.matches(Regex("[0-9]+,[0-9]{1,2}")) -> {
                    cleaned.replace(",", ".")
                }
                // Pattern: digits.digits where digits is exactly 3 (e.g., "31.656") - likely thousands
                cleaned.matches(Regex("[0-9]{1,3}\\.[0-9]{3}")) -> {
                    cleaned.replace(".", "")
                }
                // Pattern: digits.digit or digits.digits (small decimal, e.g., "5.0", "24.5") - likely decimal
                cleaned.matches(Regex("[0-9]+\\.[0-9]{1,2}")) -> {
                    cleaned // keep as-is, it's already in correct format
                }
                // Pattern: just digits (e.g., "1500") - integer
                cleaned.matches(Regex("[0-9]+")) -> {
                    cleaned
                }
                else -> cleaned.replace(".", "").replace(",", ".") // fallback to old behavior
            }
            
            return result.toDoubleOrNull()
        }

        // Percent parser: keep dot/comma as decimal separator (do not strip '.')
        fun parsePercent(s: String): Double? {
            val cleaned = s.trim()
                .replace("\u00A0", "")
                .replace(" ", "")
                .replace(",", ".")
            return cleaned.toDoubleOrNull()
        }
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val numPattern = "([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,2})?|[0-9]+(?:,[0-9]{1,2})?)"
        // Accept percent with comma or dot decimals (e.g., 24.0% or 24,0%)
        val pctPattern = "([0-9]{1,2}(?:[.,][0-9]{1,2})?)\\s*%"
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
        val rowRe4 = Regex("^\\s*([0-9]{1,2}(?:[.,][0-9]{1,2})?)\\s*%\\s+" +
                        "([0-9][0-9\\., ]*)\\s+" +
                        "([0-9][0-9\\., ]*)\\s+" +
                        "([0-9][0-9\\., ]*)\\s*$")
        val rowRe3 = Regex("^\\s*([0-9]{1,2}(?:[.,][0-9]{1,2})?)\\s*%\\s+" +
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
                        val rate = parsePercent(rateStr)
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

            // Prefer explicit Icelandic total labels and avoid misreading 'Upphæð án vsk.' as total
            if (total == null) {
                val isExplicitTotal =
                    l.contains(Regex("\\b(til\\s*grei[ðd]slu)\\b")) ||
                    l.contains(Regex("\\b(samtals(\\s*isk)?(\\s*me[ðd]\\s*vsk)?)\\b")) ||
                    l.contains(Regex("\\b(heild\\s*(me[ðd])?\\s*vsk)\\b")) ||
                    // Accept generic 'samtals' only if not explicitly 'án vsk'
                    (l.contains("samtals") && !l.contains("an vsk") && !l.contains("án vsk")) ||
                    // English fallbacks
                    l.contains(Regex("\\b(total|amount)\\b"))

                // Exclusions: lines that explicitly describe tax amount or subtotal should not be treated as total
                val isSubtotalLine = l.contains("an vsk") || l.contains("án vsk") || l.contains("nett") || l.contains("netto")
                val isTaxAmountLine = l.contains("vsk-upph") || (l.contains("vsk") && l.contains("upph"))

                if (isExplicitTotal && !isSubtotalLine && !isTaxAmountLine) {
                    // Use last number on the line (right-aligned totals)
                    numRe.findAll(line).lastOrNull()?.let { total = parseNumber(it.value) }
                }
            }
            if (subtotal == null && (
                    l.contains(Regex("\\b(heildar\\s*isk\\s*an\\s*vsk)\\b")) ||
                    l.contains(Regex("\\b(\\ban\\s*vsk|án\\s*vsk|ver[ðd]\\s*an\\s*vsk|upph[aæ][ðd]\\s*an\\s*vsk|nett[oó]|netto|subtotal)\\b"))
                )
            ) {
                numRe.findAll(line).lastOrNull()?.let { subtotal = parseNumber(it.value) }
            }

            // Collect per-rate VSK amounts. Support lines both with and without explicit "vsk" text.
            var hadPct = false
            pctRe.findAll(line).forEach { m ->
                hadPct = true
                val rate = parsePercent(m.groupValues[1]) ?: return@forEach
                // Try to bind the amount appearing after the percentage on the same line.
                val afterIdx = m.range.last + 1
                // Heuristic for receipts like: "VSK 24.0% 31.656 7.598"
                // After the percent, there are usually two numbers: Nettó (bigger) and VSK upphæð (smaller).
                // We collect ALL numeric tokens after the percent and choose the smallest as the VAT amount.
                val tail = if (afterIdx in 0..line.lastIndex) line.substring(afterIdx) else ""
                var numsAfter = numRe.findAll(tail).mapNotNull { n -> parseNumber(n.value) }.toList()
                // Some receipts break lines: the numbers come on the next line after the percent.
                if (numsAfter.isEmpty() && idx + 1 < lines.size) {
                    val nextLine = lines[idx + 1]
                    numsAfter = numRe.findAll(nextLine).mapNotNull { n -> parseNumber(n.value) }.toList()
                }
                val chosenAmt = if (numsAfter.isNotEmpty()) {
                    // Prefer clearly smaller values (tax) over large nets on the same line
                    val maxOnTail = numsAfter.maxOrNull() ?: 0.0
                    val plausibleTax = numsAfter.filter { it > 0 && (maxOnTail == 0.0 || it <= maxOnTail * 0.6) }
                    (plausibleTax.minOrNull() ?: numsAfter.minOrNull())
                } else {
                    numRe.findAll(line).mapNotNull { n -> parseNumber(n.value) }.lastOrNull()
                }
                if (chosenAmt != null) {
                    rateMap[rate] = (rateMap[rate] ?: 0.0) + chosenAmt
                }
            }

            // If a tax line mentions VSK but no percentage, treat the last number as the total VSK amount.
            if (!hadPct && (l.contains("vsk") || l.contains("virðisauk") || l.contains("vsk-upph"))) {
                // Prefer explicit VSK-upphæð lines and ignore numbers that belong to a percentage (e.g., 24.0%).
                if (tax == null) {
                    val hasPercent = line.contains('%')
                    
                    // Highest priority: explicit "VSK upphæð" or "vsk-upphæð" lines
                    if (l.matches(Regex(".*vsk[\\s-]*upph[aæ]ð.*"))) {
                        val tokens = numRe.findAll(line).toList()
                        val last = tokens.lastOrNull()?.value
                        val ok = last != null && !Regex("\\Q$last\\E\\s*%$").containsMatchIn(line)
                        if (ok) tax = parseNumber(last!!)
                    }
                    // Medium priority: lines that contain "vsk" and "upph" somewhere
                    else if (tax == null && l.contains("upph")) {
                        val tokens = numRe.findAll(line).toList()
                        val last = tokens.lastOrNull()?.value
                        val ok = last != null && !Regex("\\Q$last\\E\\s*%$").containsMatchIn(line)
                        if (ok) tax = parseNumber(last!!)
                    }
                    // Fallback: if line has no percent sign, use its last number as tax
                    else if (tax == null && !hasPercent) {
                        numRe.findAll(line).map { it.value }.lastOrNull()?.let { tax = parseNumber(it) }
                    }
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
        val date: String?,
        val invoiceNumber: String?
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

        // Invoice number: prefer explicit labels like "Reikningur nr." then fall back to a standalone numeric token (6-10 digits) near the end
        val invLabelRegex = Regex("""(?i)\b(reikningur\s*nr\.?|reiknings?númer|nót[uú]?númer|kvittun\s*nr\.?|invoice\s*no?\.?|bill\s*no\.?)\b[^A-Za-z0-9]{0,10}([A-Z0-9-]{3,})""")
        val invoiceFromLabel = invLabelRegex.find(normalized)?.groupValues?.getOrNull(2)
        val invoiceFallback = if (invoiceFromLabel == null) {
            // scan from bottom and pick the last pure digit token length 6..10 without separators
            val tailLines = lines.asReversed()
            val tokenRe = Regex("""\b([0-9]{6,10})\b""")
            tailLines.firstNotNullOfOrNull { ln -> tokenRe.findAll(ln).lastOrNull()?.groupValues?.getOrNull(1) }
        } else null
        val invoiceNumber = (invoiceFromLabel ?: invoiceFallback)?.trim()

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
            
            // Use the same improved logic as parseNumber above
            val result = when {
                // Pattern: digits.digits,digits (e.g., "1.234,50") - thousands with decimal
                cleaned.matches(Regex("[0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{1,2}")) -> {
                    cleaned.replace(".", "").replace(",", ".")
                }
                // Pattern: digits,digits (e.g., "1234,50") - simple decimal with comma
                cleaned.matches(Regex("[0-9]+,[0-9]{1,2}")) -> {
                    cleaned.replace(",", ".")
                }
                // Pattern: digits.digits where digits is exactly 3 (e.g., "31.656") - likely thousands
                cleaned.matches(Regex("[0-9]{1,3}\\.[0-9]{3}")) -> {
                    cleaned.replace(".", "")
                }
                // Pattern: digits.digit or digits.digits (small decimal, e.g., "5.0", "24.5") - likely decimal
                cleaned.matches(Regex("[0-9]+\\.[0-9]{1,2}")) -> {
                    cleaned // keep as-is, it's already in correct format
                }
                // Pattern: just digits (e.g., "1500") - integer
                cleaned.matches(Regex("[0-9]+")) -> {
                    cleaned
                }
                else -> cleaned.replace(".", "").replace(",", ".") // fallback to old behavior
            }
            
            result.toDoubleOrNull()
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
        // Prefer lines that explicitly state VSK upphæð and avoid capturing the percentage value after "VSK".
        val vatFromLines: Double? = run {
            val candidates = lines.filter { l -> l.lowercase().contains("vsk") && l.lowercase().contains("upph") }
            val pick = candidates.firstOrNull()
            if (pick != null) {
                // Take the last numeric token on that line
                val tokens = Regex("""(?<![A-Z0-9])([-]?[0-9]{1,3}(?:[\., ][0-9]{3})*(?:[\.,][0-9]{1,2})?|[-]?[0-9]+)""")
                    .findAll(pick)
                    .map { it.groupValues[1] }
                    .toList()
                tokens.lastOrNull()?.let { parseAmount(it) }
            } else null
        }
        // Fallback to a generic VSK capture but ignore numbers that are part of a percentage (e.g., 24.0%)
        val vatTextGeneric = Regex("""(?i)\b(vsk|vat)\b[^0-9%]*([-]?[0-9][0-9\., ]*)(?!\s*%)\s*(kr|isk)?""")
            .find(normalized)?.groupValues?.getOrNull(2)
        val vat = vatFromLines ?: parseAmount(vatTextGeneric)

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

    return ParsedInvoice(vendor, amount, vat, date, invoiceNumber)
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
