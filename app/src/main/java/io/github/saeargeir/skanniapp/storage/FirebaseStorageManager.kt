package io.github.saeargeir.skanniapp.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Firebase Storage Manager fyrir reikninga myndir
 * Vistar myndir í skýið og tengir við reikninga
 */
class FirebaseStorageManager(
    private val context: Context
) {
    private val storage = FirebaseStorage.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    companion object {
        private const val TAG = "FirebaseStorageManager"
        private const val RECEIPTS_PATH = "receipts"
        private const val MAX_IMAGE_SIZE = 1920 // Max width/height
        private const val JPEG_QUALITY = 85
    }
    
    /**
     * Vista mynd af reikningi í Firebase Storage
     * @param bitmap Myndin sem á að vista
     * @param invoiceId ID á reikningnum
     * @return Firebase Storage URL eða null ef villa
     */
    suspend fun uploadReceiptImage(bitmap: Bitmap, invoiceId: String): String? {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Timber.w(TAG, "User not logged in, cannot upload image")
                return null
            }
            
            Timber.d(TAG, "Uploading receipt image for invoice: $invoiceId")
            
            // Resize image if too large
            val resizedBitmap = resizeBitmapIfNeeded(bitmap)
            
            // Convert to JPEG byte array
            val baos = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
            val imageData = baos.toByteArray()
            
            Timber.d(TAG, "Image size: ${imageData.size / 1024} KB")
            
            // Create storage reference
            val filename = "receipt_${invoiceId}_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference
                .child(RECEIPTS_PATH)
                .child(userId)
                .child(filename)
            
            // Upload image
            val uploadTask = storageRef.putBytes(imageData).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            
            Timber.i(TAG, "Image uploaded successfully: $downloadUrl")
            downloadUrl.toString()
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload receipt image")
            null
        }
    }
    
    /**
     * Vista mynd frá URI
     */
    suspend fun uploadReceiptImage(imageUri: Uri, invoiceId: String): String? {
        return try {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Timber.w(TAG, "User not logged in, cannot upload image")
                return null
            }
            
            Timber.d(TAG, "Uploading receipt image from URI for invoice: $invoiceId")
            
            // Create storage reference
            val filename = "receipt_${invoiceId}_${System.currentTimeMillis()}.jpg"
            val storageRef = storage.reference
                .child(RECEIPTS_PATH)
                .child(userId)
                .child(filename)
            
            // Upload image
            val uploadTask = storageRef.putFile(imageUri).await()
            
            // Get download URL
            val downloadUrl = storageRef.downloadUrl.await()
            
            Timber.i(TAG, "Image uploaded successfully: $downloadUrl")
            downloadUrl.toString()
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to upload receipt image from URI")
            null
        }
    }
    
    /**
     * Eyða mynd úr Firebase Storage
     */
    suspend fun deleteReceiptImage(imageUrl: String): Boolean {
        return try {
            Timber.d(TAG, "Deleting receipt image: $imageUrl")
            
            val storageRef = storage.getReferenceFromUrl(imageUrl)
            storageRef.delete().await()
            
            Timber.i(TAG, "Image deleted successfully")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete receipt image")
            false
        }
    }
    
    /**
     * Sækja mynd frá Firebase Storage
     */
    suspend fun downloadReceiptImage(imageUrl: String): File? {
        return try {
            Timber.d(TAG, "Downloading receipt image: $imageUrl")
            
            val storageRef = storage.getReferenceFromUrl(imageUrl)
            val localFile = File.createTempFile("receipt_", ".jpg", context.cacheDir)
            
            storageRef.getFile(localFile).await()
            
            Timber.i(TAG, "Image downloaded successfully")
            localFile
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to download receipt image")
            null
        }
    }
    
    /**
     * Resize bitmap ef það er of stórt
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_IMAGE_SIZE && height <= MAX_IMAGE_SIZE) {
            return bitmap
        }
        
        Timber.d(TAG, "Resizing bitmap from ${width}x${height}")
        
        val scale = if (width > height) {
            MAX_IMAGE_SIZE.toFloat() / width
        } else {
            MAX_IMAGE_SIZE.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        Timber.d(TAG, "New size: ${newWidth}x${newHeight}")
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Sækja lista af myndum fyrir notanda
     */
    suspend fun listUserImages(): List<String> {
        return try {
            val userId = auth.currentUser?.uid ?: return emptyList()
            
            val storageRef = storage.reference
                .child(RECEIPTS_PATH)
                .child(userId)
            
            val listResult = storageRef.listAll().await()
            
            listResult.items.mapNotNull { item ->
                try {
                    item.downloadUrl.await().toString()
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get download URL for item")
                    null
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to list user images")
            emptyList()
        }
    }
    
    /**
     * Fá stærð af notanda storage
     */
    suspend fun getUserStorageSize(): Long {
        return try {
            val userId = auth.currentUser?.uid ?: return 0L
            
            val storageRef = storage.reference
                .child(RECEIPTS_PATH)
                .child(userId)
            
            val listResult = storageRef.listAll().await()
            
            var totalSize = 0L
            for (item in listResult.items) {
                try {
                    val metadata = item.metadata.await()
                    totalSize += metadata.sizeBytes
                } catch (e: Exception) {
                    Timber.e(e, "Failed to get metadata for item")
                }
            }
            
            Timber.d(TAG, "User storage size: ${totalSize / 1024 / 1024} MB")
            totalSize
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user storage size")
            0L
        }
    }
}

/**
 * Result data class fyrir upload operations
 */
data class UploadResult(
    val success: Boolean,
    val imageUrl: String?,
    val error: String?
) {
    companion object {
        fun success(url: String) = UploadResult(true, url, null)
        fun failure(error: String) = UploadResult(false, null, error)
    }
}
