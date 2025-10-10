package io.github.saeargeir.skanniapp.utils

import android.util.Log
import java.util.regex.Pattern

object IcelandicInvoiceParser {
    
    private const val TAG = "IcelandicInvoiceParser"
    
    private val icelandicVendors = listOf(
        // Matvöruverslanir
        "Bónus", "Nettó", "Krónan", "Hagkaup", "Costco", "Kea", "Rúmfatalagerinn",
        "10-11", "Krambúðin", "Samkaup", "ÁTVR", "Vínbúðin",
        
        // Veitingastaðir og skyndibitastaðir
        "Nonnabiti", "Nonni", "Subway", "McDonald's", "KFC", "Domino's", 
        "Aktu Taktu", "Eldsmiðjan", "Hlölla bátar", "Bæjarins Beztu",
        "Grái kötturinn", "Hamborgarafabrikkan", "Lebowski Bar",
        
        // Íþróttavöruverslanir  
        "Sport24", "Sportis", "Útilíf", "66°Norður", "Icewear",
        "Ellingsen", "Sportbúðin", "Intersport",
        
        // Garðyrkja og heimilisvöruverslanir
        "Garðheimar", "Blómaval", "Garðyrkjustöðin", "Bauhaus",
        "Byko", "Húsasmiðjan", "Rúmfatalagerinn", "IKEA",
        
        // Bensínstöðvar
        "N1", "Olís", "Orkan", "Atlantsolía", "OB", "ÓB",
        
        // Apótek
        "Apótek", "Lyf og heilsa", "Actavis", "Lyfja",
        
        // Tæknibúðir
        "Elko", "Síminn", "Vodafone", "Nova", "Tölvutek", "Computerland",
        
        // Bókabúðir og skrifstofuvörur
        "Rammagerðin", "Penninn", "Eymundsson", "Forlagið", "Máls og menningar",
        
        // Bílaþjónusta
        "TMC", "Bifreiðastöð", "Bílaleigan", "Toyota", "Hyundai", "Suzuki",
        
        // Fatavöruverslanir
        "H&M", "Zara", "Dressman", "KappAhl", "Lindex", "Debenhams",
        "Geysir", "Farmers Market", "Kron Kron", "Spaksmannsspjarir",
        
        // Vefverslanir og þjónusta
        "Já.is", "Origo", "Aha.is", "Amazon", "Advania", "Nova"
    )
    
        private val amountPatterns = listOf(
        // Íslenskir patterns - priority order (most specific first)
        Pattern.compile("samtals:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("alls:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE), 
        Pattern.compile("heildarupphæð:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("til\\s+greiðslu:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("að\\s+greiða:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("greiðsla:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        
        // Total variants
        Pattern.compile("total:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("sum:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        
        // Reversed patterns (amount before label) - IMPROVED FOR ICELANDIC RECEIPTS
        Pattern.compile("([0-9]{1,2}\\.[0-9]{3})\\s*samtals", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9]{1,3}\\.[0-9]{3})\\s*alls", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9., ]+)\\s*kr\\s*samtals", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9., ]+)\\s*kr\\s*alls", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9., ]+)\\s*kr\\s*total", Pattern.CASE_INSENSITIVE),
        
        // Stand-alone amounts on line (common in Icelandic receipts)
        Pattern.compile("^\\s*([0-9]{1,3}\\.[0-9]{3})\\s*$", Pattern.MULTILINE),
        Pattern.compile("^\\s*([0-9]{1,2},[0-9]{3})\\s*$", Pattern.MULTILINE),
        
        // ISK variants
        Pattern.compile("samtals\\s*isk:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("total\\s*isk:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("isk:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        
        // Credit card patterns
        Pattern.compile("kort:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("debet:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("kredit:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        
        // Amount label patterns
        Pattern.compile("upphæð:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("amount:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        
        // Without kr (less reliable, used last)
        Pattern.compile("samtals:?\\s*([0-9., ]{4,})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("total:?\\s*([0-9., ]{4,})", Pattern.CASE_INSENSITIVE)
    )
    
        private val vatPatterns = listOf(
        Pattern.compile("vsk\\s*24%:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("virðisaukaskattur:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vat\\s*24%:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("24%\\s*vsk:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vsk:?\\s*([0-9., ]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mvsk:?\\s*([0-9., ]+)", Pattern.CASE_INSENSITIVE)
    )
    
    private val datePatterns = listOf(
        Pattern.compile("(\\d{1,2})[./](\\d{1,2})[./](\\d{2,4})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d{2,4})[./](\\d{1,2})[./](\\d{1,2})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("dagsetning:?\\s*(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})", Pattern.CASE_INSENSITIVE),
        Pattern.compile("dags:?\\s*(\\d{1,2}[./]\\d{1,2}[./]\\d{2,4})", Pattern.CASE_INSENSITIVE)
    )
    
    private val invoiceNumberPatterns = listOf(
        Pattern.compile("reikningsnúmer:?\\s*([0-9A-Za-z-]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("invoice:?\\s*([0-9A-Za-z-]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("kvittun:?\\s*([0-9A-Za-z-]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("nr\\.?:?\\s*([0-9A-Za-z-]+)", Pattern.CASE_INSENSITIVE)
    )
    
    data class ParsedInvoice(
        val vendor: String,
        val amount: Double,
        val vat: Double,
        val invoiceNumber: String?,
        val date: String?,
        val items: List<String>,
        val confidence: Float
    )
    
            fun parseInvoiceText(text: String): ParsedInvoice {
        Log.d(TAG, "=== Starting Invoice Parsing ===")
        Log.d(TAG, "Input text length: ${text.length}")
        Log.d(TAG, "Input text preview: ${text.take(300)}")
        
        // Pre-process text to fix common OCR errors
        val cleanedText = cleanOcrText(text)
        
        val lines = text.split("\\n").map { it.trim() }
        var confidence = 0f
        
                // Find vendor
        Log.d(TAG, "Finding vendor...")
        val vendor = findVendor(cleanedText.split("\\n").map { it.trim() })
        Log.d(TAG, "Vendor found: '$vendor'")
        if (vendor != "Óþekkt seljandi") confidence += 0.3f
        
                // Find amount
        Log.d(TAG, "Finding amount...")
        val amount = findAmount(cleanedText)
        Log.d(TAG, "Amount found: $amount kr")
        if (amount > 0) confidence += 0.4f
        
                // Find VAT
        Log.d(TAG, "Finding VAT...")
        val vat = findVat(cleanedText, amount)
        Log.d(TAG, "VAT found: $vat kr")
        if (vat > 0) confidence += 0.1f
        
                // Find invoice number
        Log.d(TAG, "Finding invoice number...")
        val invoiceNumber = findInvoiceNumber(cleanedText)
        Log.d(TAG, "Invoice number found: '${invoiceNumber ?: "none"}'")
        if (!invoiceNumber.isNullOrBlank()) confidence += 0.05f
        
                // Find date
        Log.d(TAG, "Finding date...")
        val date = findDate(cleanedText)
        Log.d(TAG, "Date found: '${date ?: "none"}'")
        if (!date.isNullOrBlank()) confidence += 0.05f
        
                // Find items
        Log.d(TAG, "Finding items...")
        val items = findItems(cleanedText)
        Log.d(TAG, "Items found: ${items.size}")
        if (items.isNotEmpty()) confidence += 0.1f
        
        Log.d(TAG, "Final confidence: $confidence")
        Log.d(TAG, "=== Parsing Complete ===")
        
        return ParsedInvoice(
            vendor = vendor,
            amount = amount,
            vat = vat,
            invoiceNumber = invoiceNumber,
            date = date,
            items = items,
            confidence = confidence
        )
    }
    
    private fun findVendor(lines: List<String>): String {
        // Check first few lines for known vendors
        for (line in lines.take(8)) {
            val cleanLine = line.trim().lowercase()
            
            // Exact matches first
            for (vendor in icelandicVendors) {
                if (cleanLine.contains(vendor.lowercase())) {
                    return vendor
                }
            }
            
            // Partial matches for common abbreviations
            when {
                cleanLine.contains("nonni") || cleanLine.contains("nonna") -> return "Nonnabiti"
                cleanLine.contains("sport") && cleanLine.contains("24") -> return "Sport24"
                cleanLine.contains("gardheimar") || cleanLine.contains("garðheimar") -> return "Garðheimar"
                cleanLine.contains("bonus") -> return "Bónus"
                cleanLine.contains("netto") -> return "Nettó"
                cleanLine.contains("kronan") -> return "Krónan"
                cleanLine.contains("hagkaup") -> return "Hagkaup"
                cleanLine.contains("n1") || cleanLine.contains("n-1") -> return "N1"
                cleanLine.contains("olis") || cleanLine.contains("olís") -> return "Olís"
                cleanLine.contains("orkan") -> return "Orkan"
                cleanLine.contains("10-11") || cleanLine.contains("10/11") -> return "10-11"
                cleanLine.contains("mcdonalds") || cleanLine.contains("mcdonald") -> return "McDonald's"
                cleanLine.contains("subway") -> return "Subway"
                cleanLine.contains("dominos") || cleanLine.contains("domino") -> return "Domino's"
                cleanLine.contains("kfc") -> return "KFC"
                cleanLine.contains("byko") -> return "Byko"
                cleanLine.contains("husasmidjan") || cleanLine.contains("húsasmiðjan") -> return "Húsasmiðjan"
                cleanLine.contains("ikea") -> return "IKEA"
                cleanLine.contains("elko") -> return "Elko"
                cleanLine.contains("siminn") || cleanLine.contains("síminn") -> return "Síminn"
                cleanLine.contains("vodafone") -> return "Vodafone"
                cleanLine.contains("nova") -> return "Nova"
            }
        }
        
        // Try to extract company name from first non-empty line that looks like a company name
        val firstLine = lines.firstOrNull { 
            it.isNotBlank() && 
            it.length > 2 && 
            it.length < 50 &&
            !it.matches(Regex(".*\\d{2}[./]\\d{2}[./]\\d{2,4}.*")) && // Skip dates
            !it.matches(Regex(".*\\d{1,3}[.,]\\d{3}.*")) // Skip amounts
        }
        
        if (firstLine != null) {
            return firstLine.trim()
        }
        
        return "Óþekkt seljandi"
    }
    
            private fun findAmount(text: String): Double {
        val amounts = mutableListOf<Pair<Double, String>>()
        
        // First try the regex patterns
        for ((index, pattern) in amountPatterns.withIndex()) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                if (amountStr != null) {
                    Log.d(TAG, "Pattern $index matched: '$amountStr' (pattern: ${pattern.pattern()})")
                    val parsed = parseIcelandicNumber(amountStr)
                    if (parsed != null && parsed > 0) {
                        Log.d(TAG, "  -> Parsed as: $parsed kr")
                        amounts.add(Pair(parsed, amountStr))
                    } else {
                        Log.d(TAG, "  -> Failed to parse or invalid")
                    }
                }
            }
        }
        
        // ADDITIONAL LOGIC: Look for standalone amounts on lines (common in Icelandic receipts)
        val lines = text.split("\\n").map { it.trim() }
        for ((lineIndex, line) in lines.withIndex()) {
            // Look for lines that are likely total amounts
            if (line.matches(Regex("^\\s*[0-9]{1,3}\\.[0-9]{3}\\s*$")) || 
                line.matches(Regex("^\\s*[0-9]{1,2},[0-9]{3}\\s*$")) ||
                line.matches(Regex("^\\s*[0-9]{4,6}\\s*$"))) {
                
                // Check if this line is near "Samtals" or similar
                val contextLines = (maxOf(0, lineIndex-2)..minOf(lines.size-1, lineIndex+2))
                val hasContext = contextLines.any { i -> 
                    lines[i].contains("samtals", ignoreCase = true) ||
                    lines[i].contains("alls", ignoreCase = true) ||
                    lines[i].contains("total", ignoreCase = true) ||
                    lines[i].contains("greiðsla", ignoreCase = true)
                }
                
                if (hasContext) {
                    val parsed = parseIcelandicNumber(line)
                    if (parsed != null && parsed > 0) {
                        Log.d(TAG, "Standalone amount found near total context: '$line' -> $parsed kr")
                        amounts.add(Pair(parsed, line))
                    }
                }
            }
        }
        
        if (amounts.isEmpty()) {
            Log.w(TAG, "No amounts found in text!")
            return 0.0
        }
        
        // Return the largest reasonable amount found
        // This helps prioritize total amounts over item prices
        val filtered = amounts.map { it.first }.filter { it < 10000000 } // Max 10M kr sanity check
        val result = filtered.maxOrNull() ?: 0.0
        
        Log.d(TAG, "All amounts found: ${amounts.joinToString { "${it.second}=${it.first}" }}")
        Log.d(TAG, "Selected amount: $result kr")
        
        return result
    }
    
    /**
     * Parse Icelandic number format
     * Handles formats like: 1.234,56 or 1,234.56 or 1234.56 or 1234,56 or 1.234 or 1,234
     * IMPROVED: Better handling of Icelandic receipts where dot is thousands separator
     */
    private fun parseIcelandicNumber(numStr: String): Double? {
        if (numStr.isBlank()) return null
        
        return try {
            val cleaned = numStr.trim()
            Log.v(TAG, "Parsing number: '$cleaned'")
            
            // Count separators to determine format
            val commaCount = cleaned.count { it == ',' }
            val dotCount = cleaned.count { it == '.' }
            val spaceCount = cleaned.count { it == ' ' }
            
            Log.v(TAG, "  Separators - commas: $commaCount, dots: $dotCount, spaces: $spaceCount")
            
            // Remove all spaces first
            val noSpaces = cleaned.replace(" ", "")
            
            val result = when {
                // No separators - just a number
                commaCount == 0 && dotCount == 0 -> noSpaces.toDouble()
                
                // ICELANDIC RECEIPTS: Single dot with exactly 3 digits after = thousands separator
                dotCount == 1 && commaCount == 0 -> {
                    val parts = noSpaces.split('.')
                    if (parts.size == 2 && parts[1].length == 3 && parts[0].length <= 3) {
                        // This is Icelandic format: 2.979 = 2979 kr
                        Log.v(TAG, "  Detected Icelandic thousands format: ${parts[0]}.${parts[1]} -> ${parts[0]}${parts[1]}")
                        (parts[0] + parts[1]).toDouble()
                    } else if (parts[1].length <= 2) {
                        // Decimal separator: 1234.56
                        parts[0].toDouble() + (parts[1].toDouble() / 100.0)
                    } else {
                        // Thousands separator: 1.234
                        noSpaces.replace(".", "").toDouble()
                    }
                }
                
                // Only comma - could be decimal separator or thousands
                commaCount == 1 && dotCount == 0 -> {
                    val parts = noSpaces.split(',')
                    if (parts[1].length <= 2) {
                        // Decimal separator: 1234,56
                        parts[0].toDouble() + (parts[1].toDouble() / 100.0)
                    } else {
                        // Thousands separator: 1,234
                        noSpaces.replace(",", "").toDouble()
                    }
                }
                
                // Both separators - determine which is decimal
                commaCount > 0 && dotCount > 0 -> {
                    val lastCommaPos = noSpaces.lastIndexOf(',')
                    val lastDotPos = noSpaces.lastIndexOf('.')
                    
                    if (lastCommaPos > lastDotPos) {
                        // Comma is decimal: 1.234,56 (European format)
                        noSpaces.replace(".", "").replace(",", ".").toDouble()
                    } else {
                        // Dot is decimal: 1,234.56 (US format)
                        noSpaces.replace(",", "").toDouble()
                    }
                }
                
                // Multiple separators of same type - thousands separators
                commaCount > 1 -> noSpaces.replace(",", "").toDouble()
                dotCount > 1 -> noSpaces.replace(".", "").toDouble()
                
                else -> noSpaces.replace(",", ".").toDouble()
            }
            
            // Sanity check - Icelandic receipts typically don't have decimals (amounts in whole króna)
            // If we got a very small number, it's likely a parsing error
            val finalResult = if (result < 1.0 && result > 0) {
                // Likely misinterpreted thousands separator as decimal
                Log.v(TAG, "  Small number detected, multiplying by 1000: $result -> ${result * 1000}")
                result * 1000
            } else {
                result
            }
            
            Log.v(TAG, "  Parsed successfully: $finalResult")
            finalResult
            
        } catch (e: NumberFormatException) {
            // If all else fails, try extracting just digits
            try {
                val digitsOnly = numStr.filter { it.isDigit() }
                if (digitsOnly.isNotEmpty()) {
                    digitsOnly.toDouble()
                } else {
                    null
                }
            } catch (e2: Exception) {
                null
            }
        }
    }
    
        private fun findVat(text: String, totalAmount: Double): Double {
        for (pattern in vatPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val vatStr = matcher.group(1)
                if (vatStr != null) {
                    val parsed = parseIcelandicNumber(vatStr)
                    if (parsed != null && parsed > 0) {
                        return parsed
                    }
                }
            }
        }
        
        // If no explicit VAT found, calculate 24% from total
        if (totalAmount > 0) {
            return totalAmount * 0.24 / 1.24
        }
        
        return 0.0
    }
    
    private fun findInvoiceNumber(text: String): String? {
        for (pattern in invoiceNumberPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val number = matcher.group(1)
                if (number != null && number.length >= 3 && number.length <= 20) {
                    return number
                }
            }
        }
        return null
    }
    
    private fun findDate(text: String): String? {
        for (pattern in datePatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(0) // Return the whole matched date
            }
        }
        return null
    }
    
    /**
     * Clean common OCR errors in text
     * IMPROVED: Better handling of Icelandic receipt OCR errors
     */
    private fun cleanOcrText(text: String): String {
        var cleaned = text
        
        // Common OCR number misreads in context of prices
        val numberFixes = mapOf(
            // Letter O misread as 0 (only in number contexts)
            Regex("(\\d+)[Oo](\\d+)") to "$1O$2", // Keep original if ambiguous
            // Letter l (lowercase L) or I as 1
            Regex("(\\d+)[lI](\\d+)") to "$11$2",
            // S as 5 in number context
            Regex("(\\d+)[Ss](\\d+)") to "$15$2",
            // Zero as O in text (reverse of above)
            Regex("([A-Z]+)0([A-Z]+)") to "$1O$2",
            // Common OCR errors in amounts
            Regex("(\\d+)[.]([0-9]{3})([^0-9])") to "$1.$2$3", // Preserve thousands separator
            Regex("(\\d+)[,]([0-9]{3})([^0-9])") to "$1,$2$3"  // Preserve thousands separator
        )
        
        // Apply OCR error corrections
        for ((pattern, replacement) in numberFixes) {
            cleaned = pattern.replace(cleaned, replacement)
        }
        
        // Fix common Icelandic character OCR errors
        val icelandicFixes = mapOf(
            "kr0na" to "króna",
            "kr6na" to "króna", 
            "samta1s" to "samtals",
            "samta15" to "samtals",
            "upp-haeð" to "upphæð",
            "greiðs1a" to "greiðsla",
            "gardheimar" to "garðheimar",
            "GARDHEIMAR" to "GARÐHEIMAR",
            "b0nus" to "bónus",
            "B0NUS" to "BÓNUS",
            "B6NUS" to "BÓNUS"
        )
        
        for ((wrong, right) in icelandicFixes) {
            cleaned = cleaned.replace(wrong, right, ignoreCase = true)
        }
        
        Log.v(TAG, "Text cleaning applied")
        return cleaned
    }
    
    private fun findItems(text: String): List<String> {
        val items = mutableListOf<String>()
        val lines = text.split("\\n").map { it.trim() }
        
        for (line in lines) {
            // Look for lines that might be items (have a price pattern)
            if (line.matches(Regex(".*\\d+[.,]?\\d*\\s*kr?.*", RegexOption.IGNORE_CASE)) &&
                !line.matches(Regex(".*(samtals|alls|total|heildar|upphæð).*", RegexOption.IGNORE_CASE)) &&
                line.length > 5 && line.length < 100) {
                items.add(line)
            }
        }
        
        return items.take(10) // Limit to 10 items to avoid too much data
    }
}