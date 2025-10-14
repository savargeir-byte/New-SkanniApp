package io.github.saeargeir.skanniapp.utils

import android.graphics.*
import kotlin.math.*

/**
 * Image enhancement utility fyrir OCR optimization
 * Bætir gæði mynda fyrir betri OCR niðurstöður
 */
object ImageEnhancementUtil {
    
    /**
     * Assess image quality fyrir OCR
     */
    fun assessQuality(bitmap: Bitmap): ImageQuality {
        val sharpness = calculateSharpness(bitmap)
        val contrast = calculateContrast(bitmap)
        val brightness = calculateBrightness(bitmap)
        val noise = estimateNoise(bitmap)
        
        val overallScore = (sharpness + contrast + brightness * 0.5f + (1f - noise)) / 3.5f
        
        val recommendation = when {
            overallScore > 0.8f -> "Excellent quality"
            overallScore > 0.6f -> "Good quality"
            sharpness < 0.3f -> "Image is blurry"
            contrast < 0.3f -> "Low contrast"
            brightness < 0.3f -> "Too dark"
            brightness > 0.9f -> "Too bright"
            noise > 0.5f -> "Too much noise"
            else -> "Needs enhancement"
        }
        
        return ImageQuality(
            sharpness = sharpness,
            contrast = contrast,
            brightness = brightness,
            noise = noise,
            overallScore = overallScore,
            recommendation = recommendation
        )
    }
    
    /**
     * Quick enhancement fyrir decent images
     */
    fun quickEnhance(bitmap: Bitmap): Bitmap {
        val enhanced = adjustContrast(bitmap, 1.2f)
        return sharpenImage(enhanced, 1.0f)
    }
    
    /**
     * Full enhancement fyrir poor quality images
     */
    fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        var enhanced = bitmap
        
        // Step 1: Noise reduction
        enhanced = reduceNoise(enhanced)
        
        // Step 2: Adjust brightness and contrast
        val quality = assessQuality(enhanced)
        enhanced = if (quality.brightness < 0.4f) {
            adjustBrightness(enhanced, 1.3f)
        } else if (quality.brightness > 0.8f) {
            adjustBrightness(enhanced, 0.7f)
        } else {
            enhanced
        }
        
        enhanced = adjustContrast(enhanced, 1.3f)
        
        // Step 3: Sharpen
        enhanced = sharpenImage(enhanced, 1.2f)
        
        // Step 4: Convert to grayscale for better OCR
        enhanced = toGrayscale(enhanced)
        
        // Step 5: Adaptive thresholding
        enhanced = adaptiveThreshold(enhanced)
        
        return enhanced
    }
    
    /**
     * Calculate sharpness using Laplacian variance
     */
    private fun calculateSharpness(bitmap: Bitmap): Float {
        val laplacian = arrayOf(
            arrayOf(0, -1, 0),
            arrayOf(-1, 4, -1),
            arrayOf(0, -1, 0)
        )
        
        var variance = 0.0
        var mean = 0.0
        var count = 0
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample subset for performance
        for (y in 1 until height - 1 step 4) {
            for (x in 1 until width - 1 step 4) {
                var sum = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val gray = Color.red(pixel)
                        sum += gray * laplacian[ky + 1][kx + 1]
                    }
                }
                mean += sum
                count++
            }
        }
        
        mean /= count
        
        for (y in 1 until height - 1 step 4) {
            for (x in 1 until width - 1 step 4) {
                var sum = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val gray = Color.red(pixel)
                        sum += gray * laplacian[ky + 1][kx + 1]
                    }
                }
                variance += (sum - mean).pow(2)
            }
        }
        
        variance /= count
        
        // Normalize to 0-1 scale
        return (variance / 10000.0).coerceIn(0.0, 1.0).toFloat()
    }
    
    /**
     * Calculate contrast
     */
    private fun calculateContrast(bitmap: Bitmap): Float {
        var min = 255
        var max = 0
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample subset for performance
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                min = minOf(min, gray)
                max = maxOf(max, gray)
            }
        }
        
        return (max - min).toFloat() / 255f
    }
    
    /**
     * Calculate brightness
     */
    private fun calculateBrightness(bitmap: Bitmap): Float {
        var sum = 0L
        var count = 0
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample subset for performance
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                sum += gray
                count++
            }
        }
        
        return (sum.toFloat() / count) / 255f
    }
    
    /**
     * Estimate noise level
     */
    private fun estimateNoise(bitmap: Bitmap): Float {
        var totalVariance = 0.0
        var count = 0
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample subset for performance
        for (y in 1 until height - 1 step 8) {
            for (x in 1 until width - 1 step 8) {
                val centerPixel = bitmap.getPixel(x, y)
                val centerGray = Color.red(centerPixel)
                
                var localVariance = 0.0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val pixel = bitmap.getPixel(x + dx, y + dy)
                        val gray = Color.red(pixel)
                        localVariance += (gray - centerGray).toDouble().pow(2)
                    }
                }
                totalVariance += localVariance / 9.0
                count++
            }
        }
        
        val avgVariance = totalVariance / count
        return (avgVariance / 10000.0).coerceIn(0.0, 1.0).toFloat()
    }
    
    /**
     * Adjust brightness
     */
    private fun adjustBrightness(bitmap: Bitmap, factor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (Color.red(pixel) * factor).toInt().coerceIn(0, 255)
                val g = (Color.green(pixel) * factor).toInt().coerceIn(0, 255)
                val b = (Color.blue(pixel) * factor).toInt().coerceIn(0, 255)
                result.setPixel(x, y, Color.rgb(r, g, b))
            }
        }
        
        return result
    }
    
    /**
     * Adjust contrast
     */
    private fun adjustContrast(bitmap: Bitmap, factor: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                val newR = (((r / 255.0 - 0.5) * factor + 0.5) * 255.0).toInt().coerceIn(0, 255)
                val newG = (((g / 255.0 - 0.5) * factor + 0.5) * 255.0).toInt().coerceIn(0, 255)
                val newB = (((b / 255.0 - 0.5) * factor + 0.5) * 255.0).toInt().coerceIn(0, 255)
                
                result.setPixel(x, y, Color.rgb(newR, newG, newB))
            }
        }
        
        return result
    }
    
    /**
     * Sharpen image
     */
    private fun sharpenImage(bitmap: Bitmap, strength: Float): Bitmap {
        val kernel = arrayOf(
            arrayOf(0f, -strength, 0f),
            arrayOf(-strength, 1f + 4f * strength, -strength),
            arrayOf(0f, -strength, 0f)
        )
        
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val weight = kernel[ky + 1][kx + 1]
                        
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                result.setPixel(
                    x, y, 
                    Color.rgb(
                        r.toInt().coerceIn(0, 255),
                        g.toInt().coerceIn(0, 255),
                        b.toInt().coerceIn(0, 255)
                    )
                )
            }
        }
        
        return result
    }
    
    /**
     * Reduce noise with Gaussian blur
     */
    private fun reduceNoise(bitmap: Bitmap): Bitmap {
        val kernel = arrayOf(
            arrayOf(1, 2, 1),
            arrayOf(2, 4, 2),
            arrayOf(1, 2, 1)
        )
        val kernelSum = 16
        
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0
                var g = 0
                var b = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val weight = kernel[ky + 1][kx + 1]
                        
                        r += Color.red(pixel) * weight
                        g += Color.green(pixel) * weight
                        b += Color.blue(pixel) * weight
                    }
                }
                
                r /= kernelSum
                g /= kernelSum
                b /= kernelSum
                
                result.setPixel(
                    x, y,
                    Color.rgb(
                        r.coerceIn(0, 255),
                        g.coerceIn(0, 255),
                        b.coerceIn(0, 255)
                    )
                )
            }
        }
        
        return result
    }
    
    /**
     * Convert to grayscale
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Luminance formula
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                result.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }
        
        return result
    }
    
    /**
     * Adaptive thresholding fyrir OCR
     */
    private fun adaptiveThreshold(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val windowSize = 15
        val constant = 5
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                
                // Calculate local mean
                for (dy in -(windowSize / 2)..(windowSize / 2)) {
                    for (dx in -(windowSize / 2)..(windowSize / 2)) {
                        val nx = (x + dx).coerceIn(0, width - 1)
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val pixel = bitmap.getPixel(nx, ny)
                        sum += Color.red(pixel)
                        count++
                    }
                }
                
                val localMean = sum / count
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel)
                
                val newValue = if (gray > localMean - constant) 255 else 0
                result.setPixel(x, y, Color.rgb(newValue, newValue, newValue))
            }
        }
        
        return result
    }
}

/**
 * Image quality data class
 */
data class ImageQuality(
    val sharpness: Float,
    val contrast: Float,
    val brightness: Float,
    val noise: Float,
    val overallScore: Float,
    val recommendation: String
) {
    fun isExcellentQuality(): Boolean = overallScore > 0.8f
    fun isGoodQuality(): Boolean = overallScore > 0.6f
    fun needsEnhancement(): Boolean = overallScore < 0.6f
}
