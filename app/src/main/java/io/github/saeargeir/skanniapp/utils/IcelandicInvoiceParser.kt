package io.github.saeargeir.skanniapp.utils

import java.util.regex.Pattern

object IcelandicInvoiceParser {
    
    private val icelandicVendors = listOf(
        "Bónus", "Nettó", "Krónan", "Hagkaup", "Costco", "Kea", "Rúmfatalagerinn",
        "N1", "Olís", "Orkan", "Atlantsolía", "OB", "ÓB",
        "Byko", "Húsasmiðjan", "Blómaval", "Garðyrkjustöðin",
        "Apótek", "Lyf og heilsa", "Actavis",
        "Subway", "McDonald's", "KFC", "Domino's", "Aktu Taktu",
        "Elko", "Síminn", "Vodafone", "Nova",
        "Rammagerðin", "Penninn", "Eymundsson",
        "TMC", "Bifreiðastöð", "Bílaleigan"
    )
    
    private val amountPatterns = listOf(
        Pattern.compile("samtals:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("alls:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE), 
        Pattern.compile("heildarupphæð:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("upphæð:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("total:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9.,]+)\\s*kr\\s*samtals", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9.,]+)\\s*kr\\s*alls", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE)
    )
    
    private val vatPatterns = listOf(
        Pattern.compile("vsk\\s*24%:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("virðisaukaskattur:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vat\\s*24%:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("24%\\s*vsk:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE)
    )
    
    private val invoiceNumberPatterns = listOf(
        Pattern.compile("reikn\\.?\\s*nr\\.?:?\\s*([0-9A-Za-z-]+)", Pattern.CASE_INSENSITIVE),
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
        val confidence: Float
    )
    
    fun parseInvoiceText(text: String): ParsedInvoice {
        val lines = text.split("\\n").map { it.trim() }
        var confidence = 0f
        
        // Find vendor
        val vendor = findVendor(lines)
        if (vendor != "Óþekkt seljandi") confidence += 0.3f
        
        // Find amount
        val amount = findAmount(text)
        if (amount > 0) confidence += 0.4f
        
        // Find VAT
        val vat = findVat(text, amount)
        if (vat > 0) confidence += 0.2f
        
        // Find invoice number
        val invoiceNumber = findInvoiceNumber(text)
        if (!invoiceNumber.isNullOrBlank()) confidence += 0.1f
        
        return ParsedInvoice(
            vendor = vendor,
            amount = amount,
            vat = vat,
            invoiceNumber = invoiceNumber,
            confidence = confidence
        )
    }
    
    private fun findVendor(lines: List<String>): String {
        // Check first few lines for known vendors
        for (line in lines.take(5)) {
            for (vendor in icelandicVendors) {
                if (line.contains(vendor, ignoreCase = true)) {
                    return vendor
                }
            }
        }
        
        // Try to extract company name from first non-empty line
        val firstLine = lines.firstOrNull { it.isNotBlank() && it.length > 2 }
        if (firstLine != null && firstLine.length < 50) {
            return firstLine
        }
        
        return "Óþekkt seljandi"
    }
    
    private fun findAmount(text: String): Double {
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                val cleanAmount = amountStr.replace(",", "").replace(".", "")
                try {
                    val amount = cleanAmount.toDouble()
                    // Handle cases where decimal separator is missing
                    return when {
                        amount > 100000 -> amount / 100 // 123456 -> 1234.56
                        amount > 10000 -> amount / 100  // 12345 -> 123.45
                        else -> amount
                    }
                } catch (e: NumberFormatException) {
                    continue
                }
            }
        }
        return 0.0
    }
    
    private fun findVat(text: String, totalAmount: Double): Double {
        for (pattern in vatPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val vatStr = matcher.group(1)
                try {
                    return vatStr.replace(",", ".").toDouble()
                } catch (e: NumberFormatException) {
                    continue
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
                if (number.length >= 3 && number.length <= 20) {
                    return number
                }
            }
        }
        return null
    }
}