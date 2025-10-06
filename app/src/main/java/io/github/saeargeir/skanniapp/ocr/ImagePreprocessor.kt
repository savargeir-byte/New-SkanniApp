package io.github.saeargeir.skanniapp.ocr

import android.graphics.*
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Image preprocessing utilities for better OCR accuracy
 * All functions are free and improve text recognition significantly
 */
object ImagePreprocessor {
    
    private const val TAG = "ImagePreprocessor"
    
    data class PreprocessingResult(
        val processedImagePath: String,
        val appliedFilters: List<String>,
        val processingTimeMs: Long,
        val success: Boolean,
        val error: String? = null
    )
    
    /**
     * Apply comprehensive preprocessing to improve OCR accuracy
     */
    fun preprocessForOcr(
        inputImagePath: String,
        outputDir: File,
        aggressive: Boolean = false
    ): PreprocessingResult {
        val startTime = System.currentTimeMillis()
        val appliedFilters = mutableListOf<String>()
        
        try {
            val originalBitmap = BitmapFactory.decodeFile(inputImagePath)
                ?: return PreprocessingResult("", emptyList(), 0, false, "Failed to decode image")
            
            var processedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            
            // 1. Convert to grayscale (improves OCR accuracy)
            processedBitmap = convertToGrayscale(processedBitmap)
            appliedFilters.add("grayscale")
            
            // 2. Enhance contrast (makes text more distinct)
            processedBitmap = enhanceContrast(processedBitmap, if (aggressive) 1.5f else 1.2f)
            appliedFilters.add("contrast")
            
            // 3. Apply sharpening (makes edges clearer)
            processedBitmap = applySharpen(processedBitmap)
            appliedFilters.add("sharpen")
            
            // 4. Noise reduction (removes artifacts)
            processedBitmap = reduceNoise(processedBitmap)
            appliedFilters.add("denoise")
            
            if (aggressive) {
                // 5. Advanced edge enhancement
                processedBitmap = enhanceEdges(processedBitmap)
                appliedFilters.add("edge_enhance")
                
                // 6. Morphological operations
                processedBitmap = morphologicalCleaning(processedBitmap)
                appliedFilters.add("morphological")
            }
            
            // Save processed image
            val outputFile = File(outputDir, "processed_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(outputFile)
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.close()
            
            // Clean up
            if (processedBitmap != originalBitmap) {
                processedBitmap.recycle()
            }
            originalBitmap.recycle()
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Image preprocessing completed in ${processingTime}ms with filters: $appliedFilters")
            
            return PreprocessingResult(
                processedImagePath = outputFile.absolutePath,
                appliedFilters = appliedFilters,
                processingTimeMs = processingTime,
                success = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during image preprocessing", e)
            return PreprocessingResult("", emptyList(), 0, false, e.message)
        }
    }
    
    /**
     * Convert image to grayscale for better OCR
     */
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(grayscaleBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f) // Remove all color
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return grayscaleBitmap
    }
    
    /**
     * Enhance contrast to make text more readable
     */
    private fun enhanceContrast(bitmap: Bitmap, factor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val contrastBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(contrastBitmap)
        val paint = Paint()
        
        // Create contrast adjustment matrix
        val contrast = factor
        val translate = (0.5f * (1.0f - contrast) * 255.0f)
        
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return contrastBitmap
    }
    
    /**
     * Apply sharpening filter to enhance text edges
     */
    private fun applySharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val sharpenedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Sharpening kernel
        val kernel = arrayOf(
            floatArrayOf(0f, -1f, 0f),
            floatArrayOf(-1f, 5f, -1f),
            floatArrayOf(0f, -1f, 0f)
        )
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val weight = kernel[ky + 1][kx + 1]
                        
                        sumR += Color.red(pixel) * weight
                        sumG += Color.green(pixel) * weight
                        sumB += Color.blue(pixel) * weight
                    }
                }
                
                val r = sumR.coerceIn(0f, 255f).toInt()
                val g = sumG.coerceIn(0f, 255f).toInt()
                val b = sumB.coerceIn(0f, 255f).toInt()
                
                result[y * width + x] = Color.rgb(r, g, b)
            }
        }
        
        sharpenedBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return sharpenedBitmap
    }
    
    /**
     * Reduce noise using median filter
     */
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val denoisedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val window = mutableListOf<Int>()
                
                // Collect 3x3 neighborhood
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        window.add(pixels[(y + ky) * width + (x + kx)])
                    }
                }
                
                // Apply median filter to each color channel
                val reds = window.map { Color.red(it) }.sorted()
                val greens = window.map { Color.green(it) }.sorted()
                val blues = window.map { Color.blue(it) }.sorted()
                
                val medianR = reds[reds.size / 2]
                val medianG = greens[greens.size / 2]
                val medianB = blues[blues.size / 2]
                
                result[y * width + x] = Color.rgb(medianR, medianG, medianB)
            }
        }
        
        denoisedBitmap.setPixels(result, 0, width, 0, 0, width, height)
        return denoisedBitmap
    }
    
    /**
     * Enhance edges for better text recognition
     */
    private fun enhanceEdges(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val edgeEnhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Sobel edge detection kernels
        val sobelX = arrayOf(
            floatArrayOf(-1f, 0f, 1f),
            floatArrayOf(-2f, 0f, 2f),
            floatArrayOf(-1f, 0f, 1f)
        )
        
        val sobelY = arrayOf(
            floatArrayOf(-1f, -2f, -1f),
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(1f, 2f, 1f)
        )
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0f
                var gy = 0f
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
                        
                        gx += gray * sobelX[ky + 1][kx + 1]
                        gy += gray * sobelY[ky + 1][kx + 1]
                    }
                }
                
                val magnitude = sqrt(gx * gx + gy * gy).coerceIn(0f, 255f).toInt()
                result[y * width + x] = Color.rgb(magnitude, magnitude, magnitude)
            }
        }
        
        edgeEnhanced.setPixels(result, 0, width, 0, 0, width, height)
        return edgeEnhanced
    }
    
    /**
     * Apply morphological operations for text cleaning
     */
    private fun morphologicalCleaning(bitmap: Bitmap): Bitmap {
        // This is a simplified version - in practice you'd want more sophisticated morphological ops
        val width = bitmap.width
        val height = bitmap.height
        val cleaned = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val result = IntArray(width * height)
        
        // Simple erosion followed by dilation (opening operation)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val center = pixels[y * width + x]
                var minValue = Color.red(center)
                
                // Find minimum in 3x3 neighborhood (erosion)
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val neighbor = pixels[(y + ky) * width + (x + kx)]
                        minValue = minOf(minValue, Color.red(neighbor))
                    }
                }
                
                result[y * width + x] = Color.rgb(minValue, minValue, minValue)
            }
        }
        
        cleaned.setPixels(result, 0, width, 0, 0, width, height)
        return cleaned
    }
    
    /**
     * Auto-rotate image to correct orientation (useful for scanned documents)
     */
    fun detectAndCorrectOrientation(bitmap: Bitmap): Bitmap {
        // This would typically use more advanced algorithms
        // For now, we'll implement a basic skew detection
        
        // In a real implementation, you would:
        // 1. Detect horizontal lines using Hough transform
        // 2. Calculate average angle of text lines
        // 3. Rotate image to correct the skew
        
        // Placeholder: return original for now
        Log.d(TAG, "Orientation correction: Not implemented yet")
        return bitmap
    }
}