package io.github.saeargeir.skanniapp.ocr

import android.util.Log
import java.util.*

/**
 * International invoice parsing utilities for multi-currency and multi-language support
 * Supports invoices from Iceland, Nordic countries, EU, and other international formats
 */
object InternationalInvoiceParser {
    
    private const val TAG = "InternationalParser"
    
    data class CurrencyAmount(
        val amount: Double,
        val currency: String,
        val confidence: Float
    )
    
    data class InternationalVatResult(
        val subtotal: CurrencyAmount?,
        val tax: CurrencyAmount?, 
        val total: CurrencyAmount?,
        val rateMap: Map<Double, CurrencyAmount>,
        val detectedCountry: String?,
        val detectedLanguage: String?
    )
    
    // Currency patterns for different regions
    private val CURRENCY_PATTERNS = mapOf(
        "ISK" to listOf("kr", "krónur", "króna", "ISK"),
        "EUR" to listOf("€", "EUR", "euro", "euros"),
        "USD" to listOf("$", "USD", "dollar", "dollars"),
        "GBP" to listOf("£", "GBP", "pound", "pounds"),
        "DKK" to listOf("kr", "DKK", "danske kroner"),
        "NOK" to listOf("kr", "NOK", "norske kroner"),
        "SEK" to listOf("kr", "SEK", "svenska kronor"),
        "CHF" to listOf("CHF", "franc", "francs")
    )
    
    // VAT/Tax terms in different languages
    private val VAT_TERMS = mapOf(
        "is" to listOf("VSK", "virðisaukaskattur"),
        "en" to listOf("VAT", "tax", "sales tax", "value added tax"),
        "da" to listOf("MOMS", "merværdiafgift"),
        "no" to listOf("MVA", "merverdiavgift"),
        "sv" to listOf("MOMS", "mervärdesskatt"),
        "de" to listOf("MwSt", "Mehrwertsteuer", "Umsatzsteuer"),
        "fr" to listOf("TVA", "taxe sur la valeur ajoutée"),
        "es" to listOf("IVA", "impuesto sobre el valor añadido"),
        "it" to listOf("IVA", "imposta sul valore aggiunto")
    )
    
    // Total terms in different languages
    private val TOTAL_TERMS = mapOf(
        "is" to listOf("samtals", "til greiðslu", "heild", "alls"),
        "en" to listOf("total", "amount due", "grand total", "sum"),
        "da" to listOf("total", "i alt", "til betaling"),
        "no" to listOf("totalt", "til betaling", "sum"),
        "sv" to listOf("totalt", "att betala", "summa"),
        "de" to listOf("gesamt", "summe", "zu zahlen", "total"),
        "fr" to listOf("total", "montant total", "à payer"),
        "es" to listOf("total", "importe total", "a pagar"),
        "it" to listOf("totale", "importo totale", "da pagare")
    )
    
    // Subtotal terms in different languages  
    private val SUBTOTAL_TERMS = mapOf(
        "is" to listOf("án vsk", "nettó", "undirheild"),
        "en" to listOf("subtotal", "net", "before tax", "excl. tax"),
        "da" to listOf("subtotal", "ekskl. moms", "netto"),
        "no" to listOf("subtotal", "ekskl. mva", "netto"),
        "sv" to listOf("subtotal", "exkl. moms", "netto"),
        "de" to listOf("zwischensumme", "netto", "ohne mwst"),
        "fr" to listOf("sous-total", "hors taxes", "net"),
        "es" to listOf("subtotal", "sin iva", "neto"),
        "it" to listOf("subtotale", "senza iva", "netto")
    )
    
    /**
     * Detect the most likely currency from the text
     */
    fun detectCurrency(text: String): String {
        val lowerText = text.lowercase()
        
        // Count currency indicators
        val currencyScores = CURRENCY_PATTERNS.mapValues { (currency, patterns) ->
            patterns.sumOf { pattern ->
                lowerText.split("\\s+".toRegex()).count { word ->
                    word.contains(pattern.lowercase())
                }
            }
        }
        
        val mostLikelyCurrency = currencyScores.maxByOrNull { it.value }?.key
        
        Log.d(TAG, "Currency detection scores: $currencyScores")
        Log.d(TAG, "Detected currency: $mostLikelyCurrency")
        
        return mostLikelyCurrency ?: "EUR" // Default to EUR
    }
    
    /**
     * Detect the language/country from the text
     */
    fun detectLanguage(text: String): String? {
        val lowerText = text.lowercase()
        
        val languageScores = VAT_TERMS.mapValues { (lang, terms) ->
            terms.sumOf { term ->
                lowerText.split("\\s+".toRegex()).count { word ->
                    word.contains(term.lowercase())
                }
            }
        }
        
        val detectedLang = languageScores.filter { it.value > 0 }.maxByOrNull { it.value }?.key
        
        Log.d(TAG, "Language detection scores: $languageScores")
        Log.d(TAG, "Detected language: $detectedLang")
        
        return detectedLang
    }
    
    /**
     * Parse numbers with international formatting
     */
    fun parseInternationalNumber(text: String, detectedCurrency: String): CurrencyAmount? {
        val cleaned = text
            .replace("\u00A0", " ") // NBSP
            .replace("\\s+".toRegex(), " ")
            .trim()
        
        // Remove currency symbols and words
        val currencyPatterns = CURRENCY_PATTERNS[detectedCurrency] ?: emptyList()
        var numberText = cleaned
        currencyPatterns.forEach { pattern ->
            numberText = numberText.replace(pattern, "", ignoreCase = true)
        }
        numberText = numberText.trim()
        
        val amount = when {
            // European format: 1.234,56 or 1 234,56
            numberText.matches(Regex("""\\d{1,3}(?:[. ]\\d{3})*,\\d{1,2}""")) -> {
                numberText.replace("[. ]".toRegex(), "").replace(",", ".").toDoubleOrNull()
            }
            // US format: 1,234.56
            numberText.matches(Regex("""\\d{1,3}(?:,\\d{3})*\\.\\d{1,2}""")) -> {
                numberText.replace(",", "").toDoubleOrNull()
            }
            // Simple decimal with comma: 1234,56
            numberText.matches(Regex("""\\d+,\\d{1,2}""")) -> {
                numberText.replace(",", ".").toDoubleOrNull()
            }
            // Simple decimal with dot: 1234.56
            numberText.matches(Regex("""\\d+\\.\\d{1,2}""")) -> {
                numberText.toDoubleOrNull()
            }
            // Integer: 1234
            numberText.matches(Regex("""\\d+""")) -> {
                numberText.toDoubleOrNull()
            }
            else -> null
        }
        
        return if (amount != null) {
            CurrencyAmount(amount, detectedCurrency, 0.8f)
        } else null
    }
    
    /**
     * Extract VAT information for international invoices
     */
    fun extractInternationalVAT(text: String): InternationalVatResult {
        Log.i(TAG, "=== INTERNATIONAL VAT EXTRACTION START ===")
        
        val detectedCurrency = detectCurrency(text)
        val detectedLanguage = detectLanguage(text)
        val detectedCountry = getCountryFromLanguage(detectedLanguage)
        
        Log.i(TAG, "Detected currency: $detectedCurrency")
        Log.i(TAG, "Detected language: $detectedLanguage")
        Log.i(TAG, "Detected country: $detectedCountry")
        
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        
        var subtotal: CurrencyAmount? = null
        var tax: CurrencyAmount? = null
        var total: CurrencyAmount? = null
        val rateMap = mutableMapOf<Double, CurrencyAmount>()
        
        // Get terms for detected language (fallback to English)
        val vatTerms = VAT_TERMS[detectedLanguage] ?: VAT_TERMS["en"]!!
        val totalTerms = TOTAL_TERMS[detectedLanguage] ?: TOTAL_TERMS["en"]!!
        val subtotalTerms = SUBTOTAL_TERMS[detectedLanguage] ?: SUBTOTAL_TERMS["en"]!!
        
        // Common VAT rates by country
        val commonVatRates = getCommonVatRates(detectedCountry)
        
        lines.forEach { line ->
            val lowerLine = line.lowercase()
            
            // Check for VAT amounts
            vatTerms.forEach { vatTerm ->
                if (lowerLine.contains(vatTerm.lowercase())) {
                    commonVatRates.forEach { rate ->
                        val ratePattern = Regex("""${rate.toInt()}[.,]?0?%?.*?([0-9.,\\s]+)""", RegexOption.IGNORE_CASE)
                        ratePattern.find(line)?.let { match ->
                            parseInternationalNumber(match.groupValues[1], detectedCurrency)?.let { amount ->
                                rateMap[rate] = amount
                            }
                        }
                    }
                }
            }
            
            // Check for totals
            if (total == null) {
                totalTerms.forEach { totalTerm ->
                    if (lowerLine.contains(totalTerm.lowercase())) {
                        val numberPattern = Regex("""([0-9.,\\s]+)""")
                        numberPattern.findAll(line).lastOrNull()?.let { match ->
                            total = parseInternationalNumber(match.value, detectedCurrency)
                        }
                    }
                }
            }
            
            // Check for subtotals
            if (subtotal == null) {
                subtotalTerms.forEach { subtotalTerm ->
                    if (lowerLine.contains(subtotalTerm.lowercase())) {
                        val numberPattern = Regex("""([0-9.,\\s]+)""")
                        numberPattern.findAll(line).lastOrNull()?.let { match ->
                            subtotal = parseInternationalNumber(match.value, detectedCurrency)
                        }
                    }
                }
            }
        }
        
        // Calculate total tax
        if (tax == null && rateMap.isNotEmpty()) {
            val totalTaxAmount = rateMap.values.sumOf { it.amount }
            tax = CurrencyAmount(totalTaxAmount, detectedCurrency, 0.9f)
        }
        
        // Calculate missing values
        when {
            subtotal != null && total != null && tax == null -> {
                val taxAmount = total!!.amount - subtotal!!.amount
                if (taxAmount >= -0.01) {
                    tax = CurrencyAmount(taxAmount, detectedCurrency, 0.8f)
                }
            }
            subtotal == null && total != null && tax != null -> {
                val subtotalAmount = total!!.amount - tax!!.amount
                subtotal = CurrencyAmount(subtotalAmount, detectedCurrency, 0.8f)
            }
            subtotal != null && tax != null && total == null -> {
                val totalAmount = subtotal!!.amount + tax!!.amount
                total = CurrencyAmount(totalAmount, detectedCurrency, 0.9f)
            }
        }
        
        Log.i(TAG, "=== INTERNATIONAL VAT RESULT ===")
        Log.i(TAG, "Subtotal: $subtotal")
        Log.i(TAG, "Tax: $tax") 
        Log.i(TAG, "Total: $total")
        Log.i(TAG, "Rate map: $rateMap")
        
        return InternationalVatResult(
            subtotal = subtotal,
            tax = tax,
            total = total,
            rateMap = rateMap,
            detectedCountry = detectedCountry,
            detectedLanguage = detectedLanguage
        )
    }
    
    /**
     * Get country code from detected language
     */
    private fun getCountryFromLanguage(language: String?): String? {
        return when (language) {
            "is" -> "IS" // Iceland
            "en" -> "US" // Default to US for English
            "da" -> "DK" // Denmark
            "no" -> "NO" // Norway
            "sv" -> "SE" // Sweden
            "de" -> "DE" // Germany
            "fr" -> "FR" // France
            "es" -> "ES" // Spain
            "it" -> "IT" // Italy
            else -> null
        }
    }
    
    /**
     * Get common VAT rates for a country
     */
    private fun getCommonVatRates(country: String?): List<Double> {
        return when (country) {
            "IS" -> listOf(24.0, 11.0, 0.0) // Iceland
            "DK" -> listOf(25.0, 0.0) // Denmark
            "NO" -> listOf(25.0, 15.0, 0.0) // Norway
            "SE" -> listOf(25.0, 12.0, 6.0, 0.0) // Sweden
            "DE" -> listOf(19.0, 7.0, 0.0) // Germany
            "FR" -> listOf(20.0, 10.0, 5.5, 2.1, 0.0) // France
            "ES" -> listOf(21.0, 10.0, 4.0, 0.0) // Spain
            "IT" -> listOf(22.0, 10.0, 5.0, 4.0, 0.0) // Italy
            "US" -> listOf(8.5, 7.0, 6.0, 0.0) // US sales tax (varies by state)
            "GB" -> listOf(20.0, 5.0, 0.0) // UK
            else -> listOf(24.0, 20.0, 19.0, 25.0, 21.0, 0.0) // Common EU rates
        }
    }
}