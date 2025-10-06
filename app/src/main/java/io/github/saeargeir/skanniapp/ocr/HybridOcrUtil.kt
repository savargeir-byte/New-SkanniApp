package io.github.saeargeir.skanniapp.ocr

import android.content.Context
import android.util.Log
import io.github.saeargeir.skanniapp.OcrUtil
import java.io.File

/**
 * Hybrid OCR engine that combines ML Kit and Tesseract for optimal Icelandic text recognition
 */
object HybridOcrUtil {
    
    private const val TAG = "HybridOcr"
    
    enum class OcrEngine { ML_KIT, TESSERACT, AUTO }
    
    data class HybridOcrResult(
        val text: String,
        val confidence: Float,
        val engine: OcrEngine,
        val processingTimeMs: Long,
        val mlKitResult: String? = null,
        val tesseractResult: String? = null,
        val success: Boolean = true,
        val error: String? = null
    )
    
    /**
     * Main OCR function with automatic language detection and preprocessing
     */
    fun recognizeTextHybrid(
        context: Context,
        imageFile: File,
        preferredEngine: OcrEngine = OcrEngine.AUTO,
        usePreprocessing: Boolean = true,
        aggressivePreprocessing: Boolean = false,
        onResult: (HybridOcrResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        // Step 1: Preprocess image if requested
        val imageToProcess = if (usePreprocessing) {
            val preprocessResult = ImagePreprocessor.preprocessForOcr(
                imageFile.absolutePath,
                context.cacheDir,
                aggressivePreprocessing
            )
            if (preprocessResult.success) {
                Log.d(TAG, "Image preprocessing successful: ${preprocessResult.appliedFilters}")
                File(preprocessResult.processedImagePath)
            } else {
                Log.w(TAG, "Image preprocessing failed: ${preprocessResult.error}")
                imageFile
            }
        } else {
            imageFile
        }
        
        when (preferredEngine) {
            OcrEngine.ML_KIT -> {
                Log.d(TAG, "Using ML Kit OCR only")
                processWithMLKitOnly(context, imageToProcess, startTime, onResult)
            }
            OcrEngine.TESSERACT -> {
                Log.d(TAG, "Using Tesseract OCR only")
                processWithTesseractOnly(context, imageToProcess, startTime, onResult)
            }
            OcrEngine.AUTO -> {
                Log.d(TAG, "Using hybrid AUTO mode - trying both engines with smart selection")
                processWithIntelligentSelection(context, imageToProcess, startTime, onResult)
            }
        }
    }
    
    /**
     * Process with ML Kit only
     */
    private fun processWithMLKitOnly(
        context: Context,
        imageFile: File,
        startTime: Long,
        onResult: (HybridOcrResult) -> Unit
    ) {
        OcrUtil.recognizeTextFromImage(context, imageFile) { text ->
            val processingTime = System.currentTimeMillis() - startTime
            onResult(HybridOcrResult(
                text = text,
                confidence = 0.8f, // ML Kit doesn't provide confidence, estimate
                engine = OcrEngine.ML_KIT,
                processingTimeMs = processingTime,
                mlKitResult = text
            ))
        }
    }
    
    /**
     * Process with Tesseract only
     */
    private fun processWithTesseractOnly(
        context: Context,
        imageFile: File,
        startTime: Long,
        onResult: (HybridOcrResult) -> Unit
    ) {
        TesseractOcrUtil.recognizeTextWithTesseract(context, imageFile) { tesseractResult ->
            val totalTime = System.currentTimeMillis() - startTime
            
            if (tesseractResult.success) {
                onResult(HybridOcrResult(
                    text = tesseractResult.text,
                    confidence = tesseractResult.confidence,
                    engine = OcrEngine.TESSERACT,
                    processingTimeMs = totalTime,
                    tesseractResult = tesseractResult.text
                ))
            } else {
                // Fallback to ML Kit if Tesseract fails
                Log.w(TAG, "Tesseract failed, falling back to ML Kit: ${tesseractResult.error}")
                processWithMLKitOnly(context, imageFile, startTime, onResult)
            }
        }
    }
    
    /**
     * Process with intelligent selection based on language detection
     */
    private fun processWithIntelligentSelection(
        context: Context,
        imageFile: File,
        startTime: Long,
        onResult: (HybridOcrResult) -> Unit
    ) {
        var mlKitText: String? = null
        var tesseractResult: TesseractOcrUtil.TesseractResult? = null
        var completedCount = 0
        
        val onEngineComplete = {
            completedCount++
            if (completedCount == 2) {
                // Both engines completed, use intelligent selection
                selectBestResultIntelligent(mlKitText, tesseractResult, startTime, onResult)
            }
        }
        
        // Start ML Kit processing
        OcrUtil.recognizeTextFromImage(context, imageFile) { text ->
            mlKitText = text
            Log.d(TAG, "ML Kit completed with ${text.length} characters")
            onEngineComplete()
        }
        
        // Detect likely language/region and choose Tesseract configuration
        val detectedRegion = detectInvoiceRegion(imageFile)
        Log.d(TAG, "Detected invoice region: $detectedRegion")
        
        // Start Tesseract processing with appropriate language configuration
        TesseractOcrUtil.recognizeTextWithTesseract(context, imageFile, detectedRegion) { result ->
            tesseractResult = result
            Log.d(TAG, "Tesseract completed: success=${result.success}, confidence=${result.confidence}")
            onEngineComplete()
        }
    }
    
    /**
     * Detect likely invoice region based on image analysis (placeholder)
     */
    private fun detectInvoiceRegion(imageFile: File): TesseractOcrUtil.InvoiceRegion {
        // In a real implementation, this could analyze the image for:
        // - Currency symbols (€, $, kr)
        // - Character patterns (ð, þ, æ, ö for Icelandic)
        // - Layout patterns
        // For now, default to Iceland since that's our primary market
        return TesseractOcrUtil.InvoiceRegion.ICELAND
    }
    
    /**
     * Enhanced result selection with international awareness
     */
    private fun selectBestResultIntelligent(
        mlKitText: String?,
        tesseractResult: TesseractOcrUtil.TesseractResult?,
        startTime: Long,
        onResult: (HybridOcrResult) -> Unit
    ) {
        val totalTime = System.currentTimeMillis() - startTime
        
        val mlKit = mlKitText ?: ""
        val tesseract = tesseractResult?.text ?: ""
        
        Log.d(TAG, "ML Kit result: ${mlKit.length} chars")
        Log.d(TAG, "Tesseract result: ${tesseract.length} chars, confidence: ${tesseractResult?.confidence}")
        
        // Detect language from both results
        val mlKitLang = InternationalInvoiceParser.detectLanguage(mlKit)
        val tesseractLang = InternationalInvoiceParser.detectLanguage(tesseract)
        
        Log.d(TAG, "Detected languages - ML Kit: $mlKitLang, Tesseract: $tesseractLang")
        
        // Enhanced selection criteria
        val selectedEngine: OcrEngine
        val selectedText: String
        val confidence: Float
        
        when {
            // If Tesseract failed, use ML Kit
            tesseractResult?.success != true -> {
                Log.d(TAG, "Selecting ML Kit (Tesseract failed)")
                selectedEngine = OcrEngine.ML_KIT
                selectedText = mlKit
                confidence = 0.7f
            }
            
            // If Tesseract has very high confidence, prefer it
            tesseractResult.confidence > 0.8f -> {
                Log.d(TAG, "Selecting Tesseract (very high confidence: ${tesseractResult.confidence})")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If Tesseract detected non-English language, prefer it (better trained for those)
            tesseractLang != null && tesseractLang != "en" -> {
                Log.d(TAG, "Selecting Tesseract (detected non-English language: $tesseractLang)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If Tesseract found currency symbols better
            countCurrencySymbols(tesseract) > countCurrencySymbols(mlKit) -> {
                Log.d(TAG, "Selecting Tesseract (better currency recognition)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If Tesseract found more structured data (invoices are structured)
            countStructuredPatterns(tesseract) > countStructuredPatterns(mlKit) * 1.2 -> {
                Log.d(TAG, "Selecting Tesseract (better structured data)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If ML Kit produced significantly more text, prefer it
            mlKit.length > tesseract.length * 1.5 -> {
                Log.d(TAG, "Selecting ML Kit (more comprehensive text)")
                selectedEngine = OcrEngine.ML_KIT
                selectedText = mlKit
                confidence = 0.8f
            }
            
            // Default to Tesseract for invoice processing
            else -> {
                Log.d(TAG, "Selecting Tesseract (default for invoice processing)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
        }
        
        onResult(HybridOcrResult(
            text = selectedText,
            confidence = confidence,
            engine = selectedEngine,
            processingTimeMs = totalTime,
            mlKitResult = mlKit,
            tesseractResult = tesseract
        ))
    }
    
    /**
     * Count currency symbols in text
     */
    private fun countCurrencySymbols(text: String): Int {
        val currencySymbols = listOf("€", "$", "£", "¥", "kr", "USD", "EUR", "GBP", "ISK", "DKK", "NOK", "SEK")
        return currencySymbols.sumOf { symbol ->
            text.count { it.toString().equals(symbol, ignoreCase = true) }
        }
    }
    
    /**
     * Count structured patterns typical in invoices
     */
    private fun countStructuredPatterns(text: String): Int {
        val patterns = listOf(
            Regex("""\\d+[.,]\\d{2}"""), // Decimal amounts
            Regex("""\\d{1,2}[.,]?\\d?%"""), // Percentages
            Regex("""\\d{2}[-./]\\d{2}[-./]\\d{2,4}"""), // Dates
            Regex("""#\\d+"""), // Invoice numbers
        )
        
        return patterns.sumOf { pattern ->
            pattern.findAll(text).count()
        }
    }
    
    /**
     * Intelligently select the best OCR result based on various criteria
     */
    private fun selectBestResult(
        mlKitText: String?,
        tesseractResult: TesseractOcrUtil.TesseractResult?,
        startTime: Long,
        onResult: (HybridOcrResult) -> Unit
    ) {
        val totalTime = System.currentTimeMillis() - startTime
        
        val mlKit = mlKitText ?: ""
        val tesseract = tesseractResult?.text ?: ""
        
        Log.d(TAG, "ML Kit result: ${mlKit.length} chars")
        Log.d(TAG, "Tesseract result: ${tesseract.length} chars, confidence: ${tesseractResult?.confidence}")
        
        // Selection criteria
        val selectedEngine: OcrEngine
        val selectedText: String
        val confidence: Float
        
        when {
            // If Tesseract failed, use ML Kit
            tesseractResult?.success != true -> {
                Log.d(TAG, "Selecting ML Kit (Tesseract failed)")
                selectedEngine = OcrEngine.ML_KIT
                selectedText = mlKit
                confidence = 0.7f
            }
            
            // If Tesseract has high confidence, prefer it for Icelandic text
            tesseractResult.confidence > 0.7f -> {
                Log.d(TAG, "Selecting Tesseract (high confidence: ${tesseractResult.confidence})")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If Tesseract found Icelandic characters, prefer it
            tesseract.contains(Regex("[þæöðÞÆÖÐ]")) -> {
                Log.d(TAG, "Selecting Tesseract (contains Icelandic characters)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If Tesseract found better number patterns (receipts often have lots of numbers)
            countNumbers(tesseract) > countNumbers(mlKit) * 1.2 -> {
                Log.d(TAG, "Selecting Tesseract (better number recognition)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
            
            // If ML Kit produced significantly more text, prefer it
            mlKit.length > tesseract.length * 1.5 -> {
                Log.d(TAG, "Selecting ML Kit (more comprehensive text)")
                selectedEngine = OcrEngine.ML_KIT
                selectedText = mlKit
                confidence = 0.8f
            }
            
            // Default to Tesseract for Icelandic optimization
            else -> {
                Log.d(TAG, "Selecting Tesseract (default for Icelandic)")
                selectedEngine = OcrEngine.TESSERACT
                selectedText = tesseract
                confidence = tesseractResult.confidence
            }
        }
        
        onResult(HybridOcrResult(
            text = selectedText,
            confidence = confidence,
            engine = selectedEngine,
            processingTimeMs = totalTime,
            mlKitResult = mlKit,
            tesseractResult = tesseract
        ))
    }
    
    /**
     * Count numeric characters in text (receipts typically have many numbers)
     */
    private fun countNumbers(text: String): Int {
        return text.count { it.isDigit() }
    }
    
    /**
     * Enhanced VAT extraction using international parsing
     */
    fun extractVATFromHybridResult(result: HybridOcrResult): OcrUtil.VatExtraction {
        Log.i(TAG, "=== ENHANCED HYBRID VSK EXTRACTION START ===")
        Log.i(TAG, "Selected engine: ${result.engine}")
        Log.i(TAG, "Text length: ${result.text.length}")
        
        // Use international parser for comprehensive extraction
        val internationalResult = InternationalInvoiceParser.extractInternationalVAT(result.text)
        
        Log.i(TAG, "International parsing results:")
        Log.i(TAG, "Detected country: ${internationalResult.detectedCountry}")
        Log.i(TAG, "Detected language: ${internationalResult.detectedLanguage}")
        
        // Convert international result to legacy format
        val rateMap = internationalResult.rateMap.mapValues { (_, currencyAmount) ->
            currencyAmount.amount
        }
        
        val vatExtraction = OcrUtil.VatExtraction(
            subtotal = internationalResult.subtotal?.amount,
            tax = internationalResult.tax?.amount,
            total = internationalResult.total?.amount,
            rateMap = rateMap
        )
        
        // If international parsing didn't find much, try engine-specific extraction as fallback
        if (vatExtraction.total == null && vatExtraction.tax == null) {
            Log.d(TAG, "International parsing incomplete, trying engine-specific extraction")
            
            val fallbackResult = when (result.engine) {
                OcrEngine.TESSERACT -> {
                    if (result.tesseractResult != null) {
                        TesseractOcrUtil.extractIcelandicVAT(result.tesseractResult)
                    } else {
                        TesseractOcrUtil.extractIcelandicVAT(result.text)
                    }
                }
                OcrEngine.ML_KIT -> {
                    if (result.mlKitResult != null) {
                        OcrUtil.extractVatAmounts(result.mlKitResult)
                    } else {
                        OcrUtil.extractVatAmounts(result.text)
                    }
                }
                OcrEngine.AUTO -> {
                    // Try both and pick the better result
                    val tesseractVAT = if (result.tesseractResult != null) {
                        TesseractOcrUtil.extractIcelandicVAT(result.tesseractResult)
                    } else null
                    
                    val mlKitVAT = if (result.mlKitResult != null) {
                        OcrUtil.extractVatAmounts(result.mlKitResult)
                    } else null
                    
                    // Select the result with more complete data
                    when {
                        tesseractVAT?.total != null && mlKitVAT?.total == null -> tesseractVAT
                        mlKitVAT?.total != null && tesseractVAT?.total == null -> mlKitVAT
                        tesseractVAT?.tax != null && mlKitVAT?.tax == null -> tesseractVAT
                        mlKitVAT?.tax != null && tesseractVAT?.tax == null -> mlKitVAT
                        else -> tesseractVAT ?: mlKitVAT ?: vatExtraction
                    }
                }
            }
            
            // Use fallback if it found more data
            if (fallbackResult.total != null || fallbackResult.tax != null) {
                Log.d(TAG, "Using engine-specific fallback result")
                return fallbackResult
            }
        }
        
        Log.i(TAG, "=== FINAL HYBRID VSK RESULT ===")
        Log.i(TAG, "subtotal: ${vatExtraction.subtotal}, tax: ${vatExtraction.tax}, total: ${vatExtraction.total}")
        Log.i(TAG, "Rate map: ${vatExtraction.rateMap}")
        
        return vatExtraction
    }
}