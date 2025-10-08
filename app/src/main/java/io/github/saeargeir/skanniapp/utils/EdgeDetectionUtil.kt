package io.github.saeargeir.skanniapp.utils

import android.graphics.*
import android.media.Image
import androidx.camera.core.ImageProxy
import kotlin.math.*

/**
 * Professional edge detection system fyrir automatic receipt cropping
 * Þetta kerfi greinir jaðra reikninga sjálfkrafa og veitir gæðamat
 */
object EdgeDetectionUtil {
    
    /**
     * Main edge detection function sem analyserar ImageProxy frá camera
     */
    fun detectReceiptEdges(imageProxy: ImageProxy): EdgeDetectionResult {
        return try {
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                detectEdgesInBitmap(bitmap)
            } else {
                EdgeDetectionResult(
                    hasReceiptDetected = false,
                    qualityScore = 0f,
                    confidence = 0f,
                    cropRect = null,
                    edgePoints = emptyList()
                )
            }
        } catch (e: Exception) {
            EdgeDetectionResult(
                hasReceiptDetected = false,
                qualityScore = 0f,
                confidence = 0f,
                cropRect = null,
                edgePoints = emptyList()
            )
        }
    }
    
    /**
     * Edge detection function sem analyserar Bitmap directly
     */
    fun detectReceiptEdges(bitmap: Bitmap): EdgeDetectionResult {
        return try {
            detectEdgesInBitmap(bitmap)
        } catch (e: Exception) {
            EdgeDetectionResult(
                hasReceiptDetected = false,
                qualityScore = 0f,
                confidence = 0f,
                cropRect = null,
                edgePoints = emptyList()
            )
        }
    }
    
    /**
     * Detect edges í bitmap með professional algorithm
     */
    private fun detectEdgesInBitmap(bitmap: Bitmap): EdgeDetectionResult {
        // Step 1: Convert to grayscale
        val grayBitmap = toGrayscale(bitmap)
        
        // Step 2: Apply Gaussian blur to reduce noise
        val blurredBitmap = applyGaussianBlur(grayBitmap)
        
        // Step 3: Edge detection með Sobel operator
        val edgeBitmap = applySobelEdgeDetection(blurredBitmap)
        
        // Step 4: Find contours and identify rectangle
        val contours = findContours(edgeBitmap)
        val receiptContour = findBestReceiptContour(contours, bitmap.width, bitmap.height)
        
        // Step 5: Calculate quality metrics
        val qualityScore = calculateQualityScore(bitmap, receiptContour)
        val confidence = calculateConfidence(receiptContour, bitmap.width, bitmap.height)
        
        return if (receiptContour != null && confidence > 0.3f) {
            EdgeDetectionResult(
                hasReceiptDetected = true,
                qualityScore = qualityScore,
                confidence = confidence,
                cropRect = contourToRect(receiptContour),
                edgePoints = receiptContour
            )
        } else {
            EdgeDetectionResult(
                hasReceiptDetected = false,
                qualityScore = qualityScore,
                confidence = confidence,
                cropRect = null,
                edgePoints = emptyList()
            )
        }
    }
    
    /**
     * Convert ImageProxy til Bitmap
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image ?: return null
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Convert bitmap til grayscale fyrir edge detection
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                
                // Luminance formula
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val grayPixel = Color.rgb(gray, gray, gray)
                
                grayBitmap.setPixel(x, y, grayPixel)
            }
        }
        
        return grayBitmap
    }
    
    /**
     * Apply Gaussian blur til að draga úr noise
     */
    private fun applyGaussianBlur(bitmap: Bitmap): Bitmap {
        val kernel = arrayOf(
            arrayOf(1, 2, 1),
            arrayOf(2, 4, 2),
            arrayOf(1, 2, 1)
        )
        val kernelSum = 16
        
        val width = bitmap.width
        val height = bitmap.height
        val blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
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
                
                val blurredPixel = Color.rgb(
                    r.coerceIn(0, 255),
                    g.coerceIn(0, 255),
                    b.coerceIn(0, 255)
                )
                
                blurredBitmap.setPixel(x, y, blurredPixel)
            }
        }
        
        return blurredBitmap
    }
    
    /**
     * Apply Sobel edge detection operator
     */
    private fun applySobelEdgeDetection(bitmap: Bitmap): Bitmap {
        val sobelX = arrayOf(
            arrayOf(-1, 0, 1),
            arrayOf(-2, 0, 2),
            arrayOf(-1, 0, 1)
        )
        
        val sobelY = arrayOf(
            arrayOf(-1, -2, -1),
            arrayOf(0, 0, 0),
            arrayOf(1, 2, 1)
        )
        
        val width = bitmap.width
        val height = bitmap.height
        val edgeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var gx = 0
                var gy = 0
                
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = bitmap.getPixel(x + kx, y + ky)
                        val gray = Color.red(pixel) // Already grayscale
                        
                        gx += gray * sobelX[ky + 1][kx + 1]
                        gy += gray * sobelY[ky + 1][kx + 1]
                    }
                }
                
                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                val edgeValue = magnitude.coerceIn(0, 255)
                
                val edgePixel = Color.rgb(edgeValue, edgeValue, edgeValue)
                edgeBitmap.setPixel(x, y, edgePixel)
            }
        }
        
        return edgeBitmap
    }
    
    /**
     * Find contours í edge detected bitmap
     */
    private fun findContours(edgeBitmap: Bitmap): List<List<Point>> {
        val width = edgeBitmap.width
        val height = edgeBitmap.height
        val threshold = 128
        val contours = mutableListOf<List<Point>>()
        
        // Simplified contour detection
        // Find strong edges and group them into potential rectangles
        val edgePoints = mutableListOf<Point>()
        
        for (y in 0 until height step 4) { // Sample every 4th pixel for performance
            for (x in 0 until width step 4) {
                val pixel = edgeBitmap.getPixel(x, y)
                val intensity = Color.red(pixel)
                
                if (intensity > threshold) {
                    edgePoints.add(Point(x, y))
                }
            }
        }
        
        // Group nearby points into contours
        val grouped = groupNearbyPoints(edgePoints, 50) // 50 pixel threshold
        contours.addAll(grouped)
        
        return contours
    }
    
    /**
     * Group nearby points into contours
     */
    private fun groupNearbyPoints(points: List<Point>, threshold: Int): List<List<Point>> {
        val groups = mutableListOf<MutableList<Point>>()
        val used = BooleanArray(points.size)
        
        for (i in points.indices) {
            if (used[i]) continue
            
            val group = mutableListOf<Point>()
            group.add(points[i])
            used[i] = true
            
            for (j in i + 1 until points.size) {
                if (used[j]) continue
                
                val distance = distanceBetweenPoints(points[i], points[j])
                if (distance < threshold) {
                    group.add(points[j])
                    used[j] = true
                }
            }
            
            if (group.size > 10) { // Only keep groups with enough points
                groups.add(group)
            }
        }
        
        return groups.map { it.toList() }
    }
    
    /**
     * Find best rectangle contour that could be a receipt
     */
    private fun findBestReceiptContour(
        contours: List<List<Point>>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Point>? {
        var bestContour: List<Point>? = null
        var bestScore = 0f
        
        for (contour in contours) {
            if (contour.size < 4) continue
            
            val rect = approximateRectangle(contour)
            if (rect.size == 4) {
                val score = scoreRectangleAsReceipt(rect, imageWidth, imageHeight)
                if (score > bestScore) {
                    bestScore = score
                    bestContour = rect
                }
            }
        }
        
        return if (bestScore > 0.3f) bestContour else null
    }
    
    /**
     * Approximate contour as rectangle
     */
    private fun approximateRectangle(contour: List<Point>): List<Point> {
        if (contour.size < 4) return emptyList()
        
        // Find convex hull and then approximate as rectangle
        val hull = convexHull(contour)
        
        // Simplify to 4 corners
        return if (hull.size >= 4) {
            findFourCorners(hull)
        } else {
            emptyList()
        }
    }
    
    /**
     * Simple convex hull algorithm
     */
    private fun convexHull(points: List<Point>): List<Point> {
        if (points.size < 3) return points
        
        val sorted = points.sortedWith { a, b ->
            when {
                a.x != b.x -> a.x - b.x
                else -> a.y - b.y
            }
        }
        
        val hull = mutableListOf<Point>()
        
        // Build lower hull
        for (p in sorted) {
            while (hull.size >= 2 && crossProduct(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        
        // Build upper hull
        val t = hull.size + 1
        for (i in sorted.size - 2 downTo 0) {
            val p = sorted[i]
            while (hull.size >= t && crossProduct(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) {
                hull.removeAt(hull.size - 1)
            }
            hull.add(p)
        }
        
        hull.removeAt(hull.size - 1) // Remove last point as it's the same as the first
        return hull
    }
    
    /**
     * Cross product fyrir convex hull
     */
    private fun crossProduct(o: Point, a: Point, b: Point): Long {
        return (a.x - o.x).toLong() * (b.y - o.y) - (a.y - o.y).toLong() * (b.x - o.x)
    }
    
    /**
     * Find the 4 corners from convex hull
     */
    private fun findFourCorners(hull: List<Point>): List<Point> {
        if (hull.size < 4) return hull
        
        // Find corners by finding points with maximum distances
        val topLeft = hull.minByOrNull { it.x + it.y } ?: hull[0]
        val topRight = hull.maxByOrNull { it.x - it.y } ?: hull[0]
        val bottomLeft = hull.minByOrNull { it.x - it.y } ?: hull[0]
        val bottomRight = hull.maxByOrNull { it.x + it.y } ?: hull[0]
        
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }
    
    /**
     * Score rectangle sem potential receipt
     */
    private fun scoreRectangleAsReceipt(
        rect: List<Point>,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        if (rect.size != 4) return 0f
        
        val bounds = getBounds(rect)
        val width = bounds.width()
        val height = bounds.height()
        
        // Receipt ratio scoring (receipts eru usually taller than wide)
        val aspectRatio = height.toFloat() / width.toFloat()
        val aspectScore = when {
            aspectRatio > 1.2f && aspectRatio < 3.0f -> 1.0f // Good receipt ratio
            aspectRatio > 1.0f && aspectRatio < 4.0f -> 0.7f // Acceptable
            else -> 0.3f // Poor aspect ratio
        }
        
        // Size scoring (not too small, not too big)
        val area = width * height
        val imageArea = imageWidth * imageHeight
        val areaRatio = area.toFloat() / imageArea.toFloat()
        val sizeScore = when {
            areaRatio > 0.1f && areaRatio < 0.8f -> 1.0f // Good size
            areaRatio > 0.05f && areaRatio < 0.9f -> 0.7f // Acceptable
            else -> 0.3f // Too small or too big
        }
        
        // Position scoring (prefer center of image)
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        val imageCenterX = imageWidth / 2
        val imageCenterY = imageHeight / 2
        
        val centerDistance = sqrt(
            ((centerX - imageCenterX).toFloat()).pow(2) + 
            ((centerY - imageCenterY).toFloat()).pow(2)
        )
        val maxDistance = sqrt((imageWidth/2f).pow(2) + (imageHeight/2f).pow(2))
        val positionScore = 1.0f - (centerDistance / maxDistance)
        
        return (aspectScore * 0.4f + sizeScore * 0.4f + positionScore * 0.2f)
    }
    
    /**
     * Get bounding rectangle frá points
     */
    private fun getBounds(points: List<Point>): Rect {
        val left = points.minOf { it.x }
        val top = points.minOf { it.y }
        val right = points.maxOf { it.x }
        val bottom = points.maxOf { it.y }
        return Rect(left, top, right, bottom)
    }
    
    /**
     * Convert contour til Rect
     */
    private fun contourToRect(contour: List<Point>): Rect {
        return getBounds(contour)
    }
    
    /**
     * Calculate overall quality score
     */
    private fun calculateQualityScore(bitmap: Bitmap, contour: List<Point>?): Float {
        // Calculate sharpness, contrast, and lighting quality
        val sharpness = calculateSharpness(bitmap)
        val contrast = calculateContrast(bitmap)
        val lighting = calculateLighting(bitmap)
        
        val baseQuality = (sharpness + contrast + lighting) / 3f
        
        // Boost score if good contour detected
        return if (contour != null && contour.size == 4) {
            (baseQuality * 0.7f + 0.3f).coerceIn(0f, 1f)
        } else {
            baseQuality
        }
    }
    
    /**
     * Calculate image sharpness (using Laplacian variance)
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
     * Calculate image contrast
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
                val gray = Color.red(pixel)
                min = minOf(min, gray)
                max = maxOf(max, gray)
            }
        }
        
        return (max - min).toFloat() / 255f
    }
    
    /**
     * Calculate lighting quality
     */
    private fun calculateLighting(bitmap: Bitmap): Float {
        var sum = 0L
        var count = 0
        
        val width = bitmap.width
        val height = bitmap.height
        
        // Sample subset for performance
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                val pixel = bitmap.getPixel(x, y)
                val gray = Color.red(pixel)
                sum += gray
                count++
            }
        }
        
        val average = sum.toFloat() / count
        
        // Good lighting er around 128 (middle gray)
        val lightingScore = 1f - abs(average - 128f) / 128f
        return lightingScore.coerceIn(0f, 1f)
    }
    
    /**
     * Calculate confidence in detection
     */
    private fun calculateConfidence(contour: List<Point>?, imageWidth: Int, imageHeight: Int): Float {
        if (contour == null || contour.size != 4) return 0f
        
        val bounds = getBounds(contour)
        val area = bounds.width() * bounds.height()
        val imageArea = imageWidth * imageHeight
        val areaRatio = area.toFloat() / imageArea.toFloat()
        
        return when {
            areaRatio > 0.2f && areaRatio < 0.7f -> 0.9f
            areaRatio > 0.1f && areaRatio < 0.8f -> 0.7f
            areaRatio > 0.05f && areaRatio < 0.9f -> 0.5f
            else -> 0.2f
        }
    }
    
    /**
     * Distance between two points
     */
    private fun distanceBetweenPoints(p1: Point, p2: Point): Double {
        val dx = (p1.x - p2.x).toDouble()
        val dy = (p1.y - p2.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * Result data class frá edge detection
 */
data class EdgeDetectionResult(
    val hasReceiptDetected: Boolean,
    val qualityScore: Float, // 0.0 to 1.0
    val confidence: Float,   // 0.0 to 1.0
    val cropRect: Rect?,     // Cropping rectangle
    val edgePoints: List<Point> // Detected edge points
) {
    /**
     * Overall status fyrir UI feedback
     */
    fun getStatusMessage(): String = when {
        !hasReceiptDetected -> "🔍 Leitið að reikningi..."
        qualityScore > 0.8f && confidence > 0.7f -> "🟢 Frábært! Tilbúið að skanna"
        qualityScore > 0.6f && confidence > 0.5f -> "🟡 Góð gæði - haldið kyrru"
        hasReceiptDetected && confidence > 0.3f -> "🟠 Reikningur greindur - bætið ljós"
        else -> "🔴 Farið nær og bætið ljós"
    }
    
    /**
     * Color fyrir edge overlay
     */
    fun getEdgeColor(): androidx.compose.ui.graphics.Color = when {
        qualityScore > 0.8f && confidence > 0.7f -> androidx.compose.ui.graphics.Color.Green
        qualityScore > 0.6f && confidence > 0.5f -> androidx.compose.ui.graphics.Color(0xFFFFA500) // Orange
        hasReceiptDetected -> androidx.compose.ui.graphics.Color.Yellow
        else -> androidx.compose.ui.graphics.Color.Red
    }
    
    /**
     * Hvort við ættum að auto-capture
     */
    fun shouldAutoCapture(): Boolean = qualityScore > 0.85f && confidence > 0.8f
}