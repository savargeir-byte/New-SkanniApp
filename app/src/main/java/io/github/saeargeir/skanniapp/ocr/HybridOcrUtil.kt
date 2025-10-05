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
     * Main OCR function that intelligently selects the best engine
     */
    fun recognizeTextHybrid(
        context: Context,
        imageFile: File,
        preferredEngine: OcrEngine = OcrEngine.AUTO,
        onResult: (HybridOcrResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        
        when (preferredEngine) {
            OcrEngine.ML_KIT -> {
                Log.d(TAG, "Using ML Kit OCR only")
                processWithMLKitOnly(context, imageFile, startTime, onResult)
            }
            OcrEngine.TESSERACT -> {
                Log.d(TAG, "Using Tesseract OCR only")
                processWithTesseractOnly(context, imageFile, startTime, onResult)
            }
            OcrEngine.AUTO -> {
                Log.d(TAG, "Using hybrid AUTO mode - trying both engines")
                processWithBothEngines(context, imageFile, startTime, onResult)
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
     * Process with both engines and intelligently select the best result
     */
    private fun processWithBothEngines(
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
                // Both engines completed, select best result
                selectBestResult(mlKitText, tesseractResult, startTime, onResult)
            }
        }
        
        // Start ML Kit processing
        OcrUtil.recognizeTextFromImage(context, imageFile) { text ->
            mlKitText = text
            Log.d(TAG, "ML Kit completed with ${text.length} characters")
            onEngineComplete()
        }
        
        // Start Tesseract processing
        TesseractOcrUtil.recognizeTextWithTesseract(context, imageFile) { result ->
            tesseractResult = result
            Log.d(TAG, "Tesseract completed: success=${result.success}, confidence=${result.confidence}")
            onEngineComplete()
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
     * Enhanced VAT extraction using the selected OCR result
     */
    fun extractVATFromHybridResult(result: HybridOcrResult): OcrUtil.VatExtraction {
        Log.i(TAG, "=== HYBRID VSK EXTRACTION START ===")
        Log.i(TAG, "Selected engine: ${result.engine}")
        Log.i(TAG, "Text length: ${result.text.length}")
        
        return when (result.engine) {
            OcrEngine.TESSERACT -> {
                Log.d(TAG, "Using Tesseract-optimized VAT extraction")
                TesseractOcrUtil.extractIcelandicVAT(result.text)
            }
            OcrEngine.ML_KIT -> {
                Log.d(TAG, "Using ML Kit VAT extraction")
                OcrUtil.extractVatAmounts(result.text)
            }
            OcrEngine.AUTO -> {
                Log.d(TAG, "Using AUTO mode - trying both engines")
                // Try both and pick better result
                val tesseractVAT = if (result.tesseractResult != null) {
                    Log.d(TAG, "Extracting VAT from Tesseract result")
                    TesseractOcrUtil.extractIcelandicVAT(result.tesseractResult)
                } else null
                
                val mlKitVAT = if (result.mlKitResult != null) {
                    Log.d(TAG, "Extracting VAT from ML Kit result")
                    OcrUtil.extractVatAmounts(result.mlKitResult)
                } else null
                
                // Select better VAT result
                when {
                    tesseractVAT?.total != null && mlKitVAT?.total == null -> tesseractVAT
                    mlKitVAT?.total != null && tesseractVAT?.total == null -> mlKitVAT
                    tesseractVAT?.tax != null && mlKitVAT?.tax == null -> tesseractVAT
                    else -> tesseractVAT ?: mlKitVAT ?: OcrUtil.extractVatAmounts(result.text)
                }
            }
        }
    }
}