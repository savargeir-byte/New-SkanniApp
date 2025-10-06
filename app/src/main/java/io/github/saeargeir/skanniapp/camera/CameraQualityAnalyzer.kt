package io.github.saeargeir.skanniapp.camera

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Camera quality analyzer fyrir real-time quality assessment
 * Greinar hvort mynd sé nægilega góð fyrir OCR
 */
class CameraQualityAnalyzer(
    private val onQualityUpdate: (QualityResult) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val quality = analyzeImageQuality(image)
            onQualityUpdate(quality)
        } catch (e: Exception) {
            onQualityUpdate(QualityResult.POOR)
        } finally {
            image.close()
        }
    }

    private fun analyzeImageQuality(image: ImageProxy): QualityResult {
        // Convert ImageProxy to ByteArray for analysis
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        
        // Analyze image sharpness using Laplacian variance
        val sharpness = calculateSharpness(data, image.width, image.height)
        
        // Analyze brightness levels
        val brightness = calculateBrightness(data)
        
        // Analyze contrast
        val contrast = calculateContrast(data)
        
        // Calculate overall quality score
        val qualityScore = calculateOverallScore(sharpness, brightness, contrast)
        
        return when {
            qualityScore >= 0.8f -> QualityResult.EXCELLENT
            qualityScore >= 0.6f -> QualityResult.GOOD
            qualityScore >= 0.4f -> QualityResult.FAIR
            else -> QualityResult.POOR
        }
    }

    private fun calculateSharpness(data: ByteArray, width: Int, height: Int): Float {
        // Simplified sharpness calculation using edge detection
        // In real implementation, this would use more sophisticated algorithms
        var edgeSum = 0f
        var count = 0
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                if (idx < data.size - 1) {
                    val pixel = data[idx].toInt() and 0xFF
                    val nextPixel = data[idx + 1].toInt() and 0xFF
                    edgeSum += kotlin.math.abs(pixel - nextPixel)
                    count++
                }
            }
        }
        
        return if (count > 0) edgeSum / count / 255f else 0f
    }

    private fun calculateBrightness(data: ByteArray): Float {
        var sum = 0L
        for (byte in data) {
            sum += (byte.toInt() and 0xFF)
        }
        return (sum.toFloat() / data.size) / 255f
    }

    private fun calculateContrast(data: ByteArray): Float {
        val brightness = calculateBrightness(data)
        var variance = 0f
        
        for (byte in data) {
            val pixelBrightness = (byte.toInt() and 0xFF) / 255f
            variance += (pixelBrightness - brightness) * (pixelBrightness - brightness)
        }
        
        return kotlin.math.sqrt(variance / data.size)
    }

    private fun calculateOverallScore(sharpness: Float, brightness: Float, contrast: Float): Float {
        // Optimal brightness range: 0.3 - 0.7
        val brightnessScore = when {
            brightness in 0.3f..0.7f -> 1.0f
            brightness < 0.2f || brightness > 0.8f -> 0.2f
            else -> 0.6f
        }
        
        // Higher sharpness is better (up to a point)
        val sharpnessScore = kotlin.math.min(sharpness * 2f, 1.0f)
        
        // Good contrast is important for OCR
        val contrastScore = kotlin.math.min(contrast * 3f, 1.0f)
        
        // Weighted average: sharpness 40%, brightness 30%, contrast 30%
        return (sharpnessScore * 0.4f + brightnessScore * 0.3f + contrastScore * 0.3f)
    }
}

/**
 * Quality levels með tilheyrandi visual indicators
 */
enum class QualityResult(
    val score: Float,
    val colorHex: String,
    val message: String
) {
    EXCELLENT(0.9f, "#4CAF50", "Frábær gæði - Taktu myndina!"),
    GOOD(0.7f, "#8BC34A", "Góð gæði - Tilbúið"),
    FAIR(0.5f, "#FFC107", "Sæmileg gæði - Reyndu betri ljós"),
    POOR(0.2f, "#F44336", "Slæm gæði - Þarf betra ljós")
}