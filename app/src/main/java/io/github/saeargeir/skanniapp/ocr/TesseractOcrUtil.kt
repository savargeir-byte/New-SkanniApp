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
    private const val ICELANDIC_LANG = "isl"
    private const val ENGLISH_LANG = "eng"
    
    data class TesseractResult(
        val text: String,
        val confidence: Float,
        val processingTimeMs: Long,
        val success: Boolean,
        val error: String? = null
    )
    
    /**
     * Initialize Tesseract with Icelandic language data
     */
    private fun initTesseract(context: Context): TessBaseAPI? {
        try {
            val dataPath = context.filesDir.absolutePath
            val tessDataDir = File(dataPath, TESSDATA_FOLDER)
            
            // Create tessdata directory if it doesn't exist
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
            }
            
            // Copy language data files if they don't exist
            copyLanguageDataIfNeeded(context, tessDataDir, ICELANDIC_LANG)
            copyLanguageDataIfNeeded(context, tessDataDir, ENGLISH_LANG)
            
            val tesseract = TessBaseAPI()
            
            // Initialize with Icelandic and English
            val langCode = "$ICELANDIC_LANG+$ENGLISH_LANG"
            
            if (tesseract.init(dataPath, langCode)) {
                // Configure for better Icelandic recognition
                tesseract.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                
                // Whitelist Icelandic characters and common receipt symbols
                val icelandicChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZÞÆÖÐabcdefghijklmnopqrstuvwxyzþæöð.,-%kr():/ "
                tesseract.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, icelandicChars)
                
                // Optimize for receipt-like text
                tesseract.setVariable("tessedit_char_blacklist", "")
                tesseract.setVariable("preserve_interword_spaces", "1")
                
                Log.d(TAG, "Tesseract initialized successfully with languages: $langCode")
                return tesseract
            } else {
                Log.e(TAG, "Failed to initialize Tesseract with languages: $langCode")
                tesseract.end()
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Tesseract", e)
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
     * Recognize text using Tesseract OCR with Icelandic optimization
     */
    fun recognizeTextWithTesseract(
        context: Context,
        imageFile: File,
        onResult: (TesseractResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        try {
            val tesseract = initTesseract(context)
            if (tesseract == null) {
                onResult(TesseractResult(
                    text = "",
                    confidence = 0f,
                    processingTimeMs = System.currentTimeMillis() - startTime,
                    success = false,
                    error = "Failed to initialize Tesseract"
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
            
            // Clean up
            bitmap.recycle()
            tesseract.end()
            
            Log.d(TAG, "Tesseract OCR completed in ${processingTime}ms with confidence: $confidence")
            Log.d(TAG, "Recognized text: $recognizedText")
            
            onResult(TesseractResult(
                text = recognizedText,
                confidence = confidence,
                processingTimeMs = processingTime,
                success = true
            ))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during Tesseract OCR", e)
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
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        
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
        
        return OcrUtil.VatExtraction(subtotal, tax, total, rateMap)
    }
}