package io.github.saeargeir.skanniapp.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.*

/**
 * Edge detection helper fyrir automatic document cropping
 * Notar simplified computer vision algorithms
 */
class EdgeDetectionHelper {

    data class DetectedEdges(
        val corners: List<PointF>,
        val confidence: Float,
        val boundingBox: android.graphics.RectF
    )

    /**
     * Detects document edges in bitmap
     */
    fun detectDocumentEdges(bitmap: Bitmap): DetectedEdges? {
        try {
            // Convert to grayscale for better edge detection
            val grayBitmap = convertToGrayscale(bitmap)
            
            // Apply Gaussian blur to reduce noise
            val blurredBitmap = applyGaussianBlur(grayBitmap)
            
            // Apply edge detection (simplified Canny-like algorithm)
            val edges = detectEdges(blurredBitmap)
            
            // Find document contours
            val contours = findLargestRectangularContour(edges)
            
            return contours?.let { corners ->
                val confidence = calculateConfidence(corners, bitmap.width, bitmap.height)
                val boundingBox = calculateBoundingBox(corners)
                
                DetectedEdges(corners, confidence, boundingBox)
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Creates overlay bitmap with detected edges highlighted
     */
    fun createEdgeOverlay(bitmap: Bitmap, detectedEdges: DetectedEdges): Bitmap {
        val overlay = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlay)
        
        // Draw semi-transparent background
        canvas.drawColor(Color.parseColor("#40000000"))
        
        // Create path for detected document area
        val path = Path()
        if (detectedEdges.corners.isNotEmpty()) {
            path.moveTo(detectedEdges.corners[0].x, detectedEdges.corners[0].y)
            for (i in 1 until detectedEdges.corners.size) {
                path.lineTo(detectedEdges.corners[i].x, detectedEdges.corners[i].y)
            }
            path.close()
        }
        
        // Paint for the detected area (clear)
        val clearPaint = Paint().apply {
            color = Color.TRANSPARENT
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        // Paint for the border
        val borderPaint = Paint().apply {
            color = when {
                detectedEdges.confidence > 0.8f -> Color.parseColor("#4CAF50") // Green
                detectedEdges.confidence > 0.6f -> Color.parseColor("#FFC107") // Yellow
                else -> Color.parseColor("#F44336") // Red
            }
            strokeWidth = 8f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        // Clear the detected document area
        canvas.drawPath(path, clearPaint)
        
        // Draw border around detected area
        canvas.drawPath(path, borderPaint)
        
        // Draw corner indicators
        val cornerPaint = Paint().apply {
            color = borderPaint.color
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        for (corner in detectedEdges.corners) {
            canvas.drawCircle(corner.x, corner.y, 12f, cornerPaint)
        }
        
        return overlay
    }

    /**
     * Crops and perspective-corrects the image based on detected edges
     */
    fun cropAndCorrectPerspective(bitmap: Bitmap, detectedEdges: DetectedEdges): Bitmap? {
        if (detectedEdges.corners.size != 4) return null
        
        try {
            // Order corners: top-left, top-right, bottom-right, bottom-left
            val orderedCorners = orderCorners(detectedEdges.corners)
            
            // Calculate output dimensions based on detected rectangle
            val width = maxOf(
                distance(orderedCorners[0], orderedCorners[1]),
                distance(orderedCorners[2], orderedCorners[3])
            ).toInt()
            
            val height = maxOf(
                distance(orderedCorners[0], orderedCorners[3]),
                distance(orderedCorners[1], orderedCorners[2])
            ).toInt()
            
            // Create transformation matrix for perspective correction
            val matrix = createPerspectiveMatrix(orderedCorners, width, height)
            
            // Apply perspective transformation
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            return null
        }
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return grayBitmap
    }

    private fun applyGaussianBlur(bitmap: Bitmap): Bitmap {
        // Simplified blur - in production would use more sophisticated algorithm
        val blurredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(blurredBitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return blurredBitmap
    }

    private fun detectEdges(bitmap: Bitmap): Array<IntArray> {
        val width = bitmap.width
        val height = bitmap.height
        val edges = Array(height) { IntArray(width) }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Simplified Sobel edge detection
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                val pixel = Color.red(pixels[idx]) // Using red channel as grayscale
                
                // Sobel X kernel
                val gx = -pixels[(y-1)*width + (x-1)] - 2*pixels[y*width + (x-1)] - pixels[(y+1)*width + (x-1)] +
                         pixels[(y-1)*width + (x+1)] + 2*pixels[y*width + (x+1)] + pixels[(y+1)*width + (x+1)]
                
                // Sobel Y kernel
                val gy = -pixels[(y-1)*width + (x-1)] - 2*pixels[(y-1)*width + x] - pixels[(y-1)*width + (x+1)] +
                         pixels[(y+1)*width + (x-1)] + 2*pixels[(y+1)*width + x] + pixels[(y+1)*width + (x+1)]
                
                val magnitude = sqrt((gx*gx + gy*gy).toDouble()).toInt()
                edges[y][x] = if (magnitude > 50) 255 else 0
            }
        }
        
        return edges
    }

    private fun findLargestRectangularContour(edges: Array<IntArray>): List<PointF>? {
        // Simplified contour detection - in production would use more sophisticated algorithm
        val height = edges.size
        val width = edges[0].size
        
        // Find potential corners by looking for edge intersections
        val corners = mutableListOf<PointF>()
        
        // Look for corners in each quadrant
        val centerX = width / 2f
        val centerY = height / 2f
        
        // Top-left corner
        var topLeft: PointF? = null
        for (y in 0 until height/2) {
            for (x in 0 until width/2) {
                if (edges[y][x] == 255) {
                    topLeft = PointF(x.toFloat(), y.toFloat())
                    break
                }
            }
            if (topLeft != null) break
        }
        
        // Top-right corner
        var topRight: PointF? = null
        for (y in 0 until height/2) {
            for (x in width-1 downTo width/2) {
                if (edges[y][x] == 255) {
                    topRight = PointF(x.toFloat(), y.toFloat())
                    break
                }
            }
            if (topRight != null) break
        }
        
        // Bottom-right corner
        var bottomRight: PointF? = null
        for (y in height-1 downTo height/2) {
            for (x in width-1 downTo width/2) {
                if (edges[y][x] == 255) {
                    bottomRight = PointF(x.toFloat(), y.toFloat())
                    break
                }
            }
            if (bottomRight != null) break
        }
        
        // Bottom-left corner
        var bottomLeft: PointF? = null
        for (y in height-1 downTo height/2) {
            for (x in 0 until width/2) {
                if (edges[y][x] == 255) {
                    bottomLeft = PointF(x.toFloat(), y.toFloat())
                    break
                }
            }
            if (bottomLeft != null) break
        }
        
        // Return corners if all found
        return if (topLeft != null && topRight != null && bottomRight != null && bottomLeft != null) {
            listOf(topLeft, topRight, bottomRight, bottomLeft)
        } else null
    }

    private fun calculateConfidence(corners: List<PointF>, width: Int, height: Int): Float {
        if (corners.size != 4) return 0f
        
        // Calculate area of detected rectangle
        val area = calculatePolygonArea(corners)
        val imageArea = width * height
        val areaRatio = area / imageArea
        
        // Check if shape is roughly rectangular
        val rectangularityScore = calculateRectangularityScore(corners)
        
        // Combine scores
        return (areaRatio * 0.6f + rectangularityScore * 0.4f).coerceIn(0f, 1f)
    }

    private fun calculatePolygonArea(corners: List<PointF>): Float {
        var area = 0f
        for (i in corners.indices) {
            val j = (i + 1) % corners.size
            area += corners[i].x * corners[j].y
            area -= corners[j].x * corners[i].y
        }
        return abs(area) / 2f
    }

    private fun calculateRectangularityScore(corners: List<PointF>): Float {
        if (corners.size != 4) return 0f
        
        // Calculate angles between corners
        val angles = mutableListOf<Float>()
        for (i in corners.indices) {
            val prev = corners[(i - 1 + corners.size) % corners.size]
            val curr = corners[i]
            val next = corners[(i + 1) % corners.size]
            
            val angle = calculateAngle(prev, curr, next)
            angles.add(angle)
        }
        
        // Check how close angles are to 90 degrees
        val avgDeviationFrom90 = angles.map { abs(it - 90f) }.average().toFloat()
        return (1f - avgDeviationFrom90 / 90f).coerceIn(0f, 1f)
    }

    private fun calculateAngle(p1: PointF, p2: PointF, p3: PointF): Float {
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y
        
        val dot = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)
        
        val cos = dot / (mag1 * mag2)
        return acos(cos.coerceIn(-1.0, 1.0)).toFloat() * 180f / PI.toFloat()
    }

    private fun calculateBoundingBox(corners: List<PointF>): android.graphics.RectF {
        val minX = corners.minOf { it.x }
        val minY = corners.minOf { it.y }
        val maxX = corners.maxOf { it.x }
        val maxY = corners.maxOf { it.y }
        
        return android.graphics.RectF(minX, minY, maxX, maxY)
    }

    private fun orderCorners(corners: List<PointF>): List<PointF> {
        // Sort by y coordinate first
        val sorted = corners.sortedBy { it.y }
        
        // Top two points
        val top = sorted.take(2).sortedBy { it.x }
        val topLeft = top[0]
        val topRight = top[1]
        
        // Bottom two points
        val bottom = sorted.takeLast(2).sortedBy { it.x }
        val bottomLeft = bottom[0]
        val bottomRight = bottom[1]
        
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun createPerspectiveMatrix(corners: List<PointF>, width: Int, height: Int): Matrix {
        // Create perspective transformation matrix
        val matrix = Matrix()
        
        // Source points (detected corners)
        val src = floatArrayOf(
            corners[0].x, corners[0].y, // top-left
            corners[1].x, corners[1].y, // top-right
            corners[2].x, corners[2].y, // bottom-right
            corners[3].x, corners[3].y  // bottom-left
        )
        
        // Destination points (rectangular output)
        val dst = floatArrayOf(
            0f, 0f,                    // top-left
            width.toFloat(), 0f,       // top-right
            width.toFloat(), height.toFloat(), // bottom-right
            0f, height.toFloat()       // bottom-left
        )
        
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return matrix
    }
}