package io.github.saeargeir.skanniapp.utils

import java.util.regex.Pattern

object IcelandicInvoiceParser {
    
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
        // Íslenskir patterns
        Pattern.compile("samtals:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("alls:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE), 
        Pattern.compile("heildarupphæð:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("upphæð:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("samtals\\s*isk\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("total:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9.,]+)\\s*kr\\s*samtals", Pattern.CASE_INSENSITIVE),
        Pattern.compile("([0-9.,]+)\\s*kr\\s*alls", Pattern.CASE_INSENSITIVE),
        
        // Common receipt patterns
        Pattern.compile("til\\s+greiðslu:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("að\\s+greiða:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^\\s*([0-9.,]+)\\s*kr\\s*$", Pattern.CASE_INSENSITIVE and Pattern.MULTILINE),
        
        // Credit card patterns
        Pattern.compile("kort:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        Pattern.compile("debet:?\\s*([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE),
        
        // General fallback patterns  
        Pattern.compile("([0-9.,]+)\\s*kr", Pattern.CASE_INSENSITIVE)
    )
    
    private val vatPatterns = listOf(
        Pattern.compile("vsk\\s*24%:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("virðisaukaskattur:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vat\\s*24%:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("24%\\s*vsk:?\\s*([0-9.,]+)", Pattern.CASE_INSENSITIVE)
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
        if (vat > 0) confidence += 0.1f
        
        // Find invoice number
        val invoiceNumber = findInvoiceNumber(text)
        if (!invoiceNumber.isNullOrBlank()) confidence += 0.05f
        
        // Find date
        val date = findDate(text)
        if (!date.isNullOrBlank()) confidence += 0.05f
        
        // Find items
        val items = findItems(text)
        if (items.isNotEmpty()) confidence += 0.1f
        
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
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                if (amountStr != null) {
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
        }
        return 0.0
    }
    
    private fun findVat(text: String, totalAmount: Double): Double {
        for (pattern in vatPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val vatStr = matcher.group(1)
                if (vatStr != null) {
                    try {
                        return vatStr.replace(",", ".").toDouble()
                    } catch (e: NumberFormatException) {
                        continue
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