package io.github.saeargeir.skanniapp.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import io.github.saeargeir.skanniapp.OcrUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Tesseract OCR implementation optimized for Icelandic receipt text recognition
 */
object TesseractOcrUtil {
    
    private const val TAG = "TesseractOcr"
    private const val TESSDATA_FOLDER = "tessdata"
    
    // Multi-language support for international invoices
    private const val ICELANDIC_LANG = "isl"
    private const val ENGLISH_LANG = "eng" 
    private const val DANISH_LANG = "dan"
    private const val GERMAN_LANG = "deu"
    private const val FRENCH_LANG = "fra"
    private const val SPANISH_LANG = "spa"
    private const val ITALIAN_LANG = "ita"
    
    // Language combinations for different regions
    private const val NORDIC_LANGS = "$ICELANDIC_LANG+$DANISH_LANG+$ENGLISH_LANG"
    private const val EUROPEAN_LANGS = "$ENGLISH_LANG+$GERMAN_LANG+$FRENCH_LANG+$SPANISH_LANG+$ITALIAN_LANG"
    private const val DEFAULT_LANGS = "$ICELANDIC_LANG+$ENGLISH_LANG"
    
    enum class InvoiceRegion {
        ICELAND,      // Icelandic + English
        NORDIC,       // Icelandic + Danish + English  
        EUROPEAN,     // English + German + French + Spanish + Italian
        GLOBAL        // All languages
    }
    
    data class TesseractResult(
        val text: String,
        val confidence: Float,
        val processingTimeMs: Long,
        val success: Boolean,
        val error: String? = null,
        val detectedLanguage: String? = null,
        val usedLanguages: String? = null
    )
    
    /**
     * Initialize Tesseract with multi-language support based on region
     */
    private fun initTesseract(context: Context, region: InvoiceRegion = InvoiceRegion.ICELAND): TessBaseAPI? {
        try {
            val dataPath = context.filesDir.absolutePath
            val tessDataDir = File(dataPath, TESSDATA_FOLDER)
            
            // Create tessdata directory if it doesn't exist
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }
            
            // Determine language combination based on region
            val langCode = when (region) {
                InvoiceRegion.ICELAND -> DEFAULT_LANGS
                InvoiceRegion.NORDIC -> NORDIC_LANGS  
                InvoiceRegion.EUROPEAN -> EUROPEAN_LANGS
                InvoiceRegion.GLOBAL -> "$ICELANDIC_LANG+$EUROPEAN_LANGS"
            }
            
            // Copy required language data files
            val requiredLanguages = langCode.split("+")
            requiredLanguages.forEach { lang ->
                copyLanguageDataIfNeeded(context, tessDataDir, lang)
            }
            
            val tesseract = TessBaseAPI()
            
            if (tesseract.init(dataPath, langCode)) {
                // Configure for optimal invoice recognition
                tesseract.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                
                // Multi-language character whitelist (includes special characters from all supported languages)
                val multiLangChars = "0123456789" +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZÞÆÖÐabcdefghijklmnopqrstuvwxyzþæöð" + // Icelandic
                    "ÄÖÜäöüß" + // German  
                    "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöøùúûüý" + // French/Spanish
                    "ÆØÅæøå" + // Danish/Norwegian
                    ".,-%€$£¥kr():/ "
                
                tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, multiLangChars)
                
                // Optimize for invoice/receipt text
                tesseract.setVariable("tessedit_char_blacklist", "")
                tesseract.setVariable("preserve_interword_spaces", "1")
                tesseract.setVariable("tessedit_create_hocr", "0")
                tesseract.setVariable("tessedit_create_pdf", "0")
                
                Log.d(TAG, "Tesseract initialized successfully with languages: $langCode for region: $region")
                return tesseract
            } else {
                Log.e(TAG, "Failed to initialize Tesseract with languages: $langCode")
                tesseract.end()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Tesseract for region $region", e)
            return null
        }
    }
    
    /**
     * Copy language data from assets to tessdata folder
     */
    private fun copyLanguageDataIfNeeded(context: Context, tessDataDir: File, lang: String) {
        val langFile = File(tessDataDir, "$lang.traineddata")
        
        if (!langFile.exists()) {
            try {
                // For now, we'll create a placeholder - in real implementation,
                // you would include the .traineddata files in assets
                Log.w(TAG, "Language file $lang.traineddata not found. Using fallback.")
                
                // TODO: Copy actual language files from assets
                // context.assets.open("tessdata/$lang.traineddata").use { input ->
                //     FileOutputStream(langFile).use { output ->
                //         input.copyTo(output)
                //     }
                // }
                
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy language data for $lang", e)
            }
        }
    }
    
    /**
     * Recognize text using Tesseract OCR with region-specific optimization
     */
    fun recognizeTextWithTesseract(
        context: Context,
        imageFile: File,
        region: InvoiceRegion = InvoiceRegion.ICELAND,
        onResult: (TesseractResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val tesseract = initTesseract(context, region)
            if (tesseract == null) {
                onResult(TesseractResult(
                    text = "",
                    confidence = 0f,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    success = false,
                    error = "Failed to initialize Tesseract for region $region"
                ))
                return
            }
            
            // Load and set image
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            if (bitmap == null) {
                tesseract.end()
                onResult(TesseractResult(
                    text = "",
                    confidence = 0f,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    success = false,
                    error = "Failed to decode image"
                ))
                return
            }
            
            tesseract.setImage(bitmap)
            
            // Extract text
            val recognizedText = tesseract.utF8Text ?: ""
            val confidence = tesseract.meanConfidence() / 100f
            val processingTime = System.currentTimeMillis() - startTime
            
            // Try to detect the most prominent language in the result
            val detectedLanguage = InternationalInvoiceParser.detectLanguage(recognizedText)
            val usedLanguages = when (region) {
                InvoiceRegion.ICELAND -> DEFAULT_LANGS
                InvoiceRegion.NORDIC -> NORDIC_LANGS  
                InvoiceRegion.EUROPEAN -> EUROPEAN_LANGS
                InvoiceRegion.GLOBAL -> "$ICELANDIC_LANG+$EUROPEAN_LANGS"
            }
            
            // Clean up
            bitmap.recycle()
            tesseract.end()
            
            Log.d(TAG, "Tesseract OCR completed in ${processingTime}ms with confidence: $confidence")
            Log.d(TAG, "Used languages: $usedLanguages, detected: $detectedLanguage")
            Log.d(TAG, "Recognized text: $recognizedText")
            
            onResult(TesseractResult(
                text = recognizedText,
                confidence = confidence,
                processingTimeMs = processingTime,
                success = true,
                detectedLanguage = detectedLanguage,
                usedLanguages = usedLanguages
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Tesseract OCR for region $region", e)
            onResult(TesseractResult(
                text = "",
                confidence = 0f,
                processingTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                error = e.message
            ))
        }
    }
    
    /**
     * Enhanced number parsing for Icelandic formatting
     */
    fun parseIcelandicNumber(text: String): Double? {
        val cleaned = text
            .replace("\u00A0", " ") // NBSP
            .replace("kr", "", ignoreCase = true)
            .replace("ISK", "", ignoreCase = true)
            .replace(" ", "")
            .trim()
        
        return when {
            // Icelandic thousands with decimal: 1.234,56
            cleaned.matches(Regex("""\d{1,3}(?:\.\d{3})*,\d{1,2}""")) -> {
                cleaned.replace(".", "").replace(",", ".").toDoubleOrNull()
            }
            // Simple decimal with comma: 1234,56
            cleaned.matches(Regex("""\d+,\d{1,2}""")) -> {
                cleaned.replace(",", ".").toDoubleOrNull()
            }
            // Thousands without decimal: 1.234
            cleaned.matches(Regex("""\d{1,3}(?:\.\d{3})+""")) -> {
                cleaned.replace(".", "").toDoubleOrNull()
            }
            // Simple decimal with dot: 12.34 (but not thousands like 1.234)
            cleaned.matches(Regex("""\d{1,3}\.\d{1,2}""")) -> {
                cleaned.toDoubleOrNull()
            }
            // Integer: 1234
            cleaned.matches(Regex("""\d+""")) -> {
                cleaned.toDoubleOrNull()
            }
            else -> null
        }
    }
    
    /**
     * Enhanced VAT extraction with Tesseract-specific patterns
     */
    fun extractIcelandicVAT(text: String): OcrUtil.VatExtraction {
        Log.i("TesseractOcrUtil", "=== TESSERACT VSK EXTRACTION START ===")
        Log.i("TesseractOcrUtil", "Text length: ${text.length}")
        Log.i("TesseractOcrUtil", "Text preview: ${text.take(200)}")
        Log.i("TesseractOcrUtil", "Contains 'VSK': ${text.contains("VSK", ignoreCase = true)}")
        Log.i("TesseractOcrUtil", "Contains '24': ${text.contains("24")}")
        Log.d("TesseractOcrUtil", "Full text: $text")
        
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        Log.d("TesseractOcrUtil", "Processing ${lines.size} lines")
        
        var subtotal: Double? = null
        var tax: Double? = null
        var total: Double? = null
        val rateMap = mutableMapOf<Double, Double>()
        
        // Icelandic VAT patterns optimized for Tesseract
        val vatPatterns = listOf(
            // Standard VSK patterns
            Regex("""VSK\s+24[.,]?0?%\s+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""VSK\s+11[.,]?0?%\s+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""24[.,]?0?%\s+VSK[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""11[.,]?0?%\s+VSK[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            
            // Alternative patterns
            Regex("""Virðisaukaskattur\s+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""VAT\s+24%\s+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""VAT\s+11%\s+([0-9.,]+)""", RegexOption.IGNORE_CASE)
        )
        
        // Total patterns
        val totalPatterns = listOf(
            Regex("""Til\s+greiðslu[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""Samtals[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""Heild[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""Total[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE)
        )
        
        // Subtotal patterns
        val subtotalPatterns = listOf(
            Regex("""Án\s+VSK[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""Nettó[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE),
            Regex("""Subtotal[:\s]+([0-9.,]+)""", RegexOption.IGNORE_CASE)
        )
        
        lines.forEach { line ->
            // Check for VAT amounts
            vatPatterns.forEach { pattern ->
                pattern.find(line)?.let { match ->
                    val amountStr = match.groupValues[1]
                    val amount = parseIcelandicNumber(amountStr)
                    if (amount != null) {
                        when {
                            line.contains("24", ignoreCase = true) -> {
                                rateMap[24.0] = (rateMap[24.0] ?: 0.0) + amount
                            }
                            line.contains("11", ignoreCase = true) -> {
                                rateMap[11.0] = (rateMap[11.0] ?: 0.0) + amount
                            }
                        }
                    }
                }
            }
            
            // Check for totals
            if (total == null) {
                totalPatterns.forEach { pattern ->
                    pattern.find(line)?.let { match ->
                        total = parseIcelandicNumber(match.groupValues[1])
                    }
                }
            }
            
            // Check for subtotals
            if (subtotal == null) {
                subtotalPatterns.forEach { pattern ->
                    pattern.find(line)?.let { match ->
                        subtotal = parseIcelandicNumber(match.groupValues[1])
                    }
                }
            }
        }
        
        // Calculate missing values
        if (tax == null && rateMap.isNotEmpty()) {
            tax = rateMap.values.sum()
        }
        
        // Ensure we have both valid VAT rates
        if (!rateMap.containsKey(24.0)) rateMap[24.0] = 0.0
        if (!rateMap.containsKey(11.0)) rateMap[11.0] = 0.0
        
        when {
            subtotal != null && total != null && tax == null -> {
                tax = (total!! - subtotal!!).let { if (it >= -0.01) it else null }
            }
            subtotal == null && total != null && tax != null -> {
                subtotal = total!! - tax!!
            }
            subtotal != null && tax != null && total == null -> {
                total = subtotal!! + tax!!
            }
        }
        
        Log.i("TesseractOcrUtil", "=== TESSERACT VSK RESULT ===")
        Log.i("TesseractOcrUtil", "subtotal: $subtotal, tax: $tax, total: $total")
        Log.i("TesseractOcrUtil", "Rate map: $rateMap")
        
        return OcrUtil.VatExtraction(subtotal, tax, total, rateMap)
    }
}