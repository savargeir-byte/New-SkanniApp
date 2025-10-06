package io.github.saeargeir.skanniapp.firebase

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FirebaseStorageService {
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
    private val storageRef: StorageReference = storage.reference
    
    private fun getUserImagesRef(userId: String) = 
        storageRef.child("users/$userId/images")
    
    suspend fun uploadInvoiceImage(
        userId: String, 
        imageFile: File, 
        invoiceId: String
    ): Result<String> {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "${invoiceId}_${timestamp}.jpg"
            val imageRef = getUserImagesRef(userId).child(fileName)
            
            val uploadTask = imageRef.putFile(Uri.fromFile(imageFile)).await()
            val downloadUrl = imageRef.downloadUrl.await()
            
            Log.i("FirebaseStorage", "Image uploaded successfully: $fileName")
            Log.i("FirebaseStorage", "Download URL: $downloadUrl")
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Error uploading image", e)
            Result.failure(e)
        }
    }
    
    suspend fun uploadInvoiceImageFromUri(
        userId: String, 
        imageUri: Uri, 
        invoiceId: String
    ): Result<String> {
        return try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val fileName = "${invoiceId}_${timestamp}.jpg"
            val imageRef = getUserImagesRef(userId).child(fileName)
            
            val uploadTask = imageRef.putFile(imageUri).await()
            val downloadUrl = imageRef.downloadUrl.await()
            
            Log.i("FirebaseStorage", "Image uploaded successfully from URI: $fileName")
            Log.i("FirebaseStorage", "Download URL: $downloadUrl")
            
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Error uploading image from URI", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteInvoiceImage(imageUrl: String): Result<Unit> {
        return try {
            val imageRef = storage.getReferenceFromUrl(imageUrl)
            imageRef.delete().await()
            
            Log.i("FirebaseStorage", "Image deleted successfully: $imageUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Error deleting image", e)
            Result.failure(e)
        }
    }
    
    suspend fun getImageDownloadUrl(imagePath: String): Result<String> {
        return try {
            val imageRef = storageRef.child(imagePath)
            val downloadUrl = imageRef.downloadUrl.await()
            
            Log.i("FirebaseStorage", "Got download URL for: $imagePath")
            Result.success(downloadUrl.toString())
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Error getting download URL", e)
            Result.failure(e)
        }
    }
    
    // List all images for a user (for cleanup or debugging)
    suspend fun listUserImages(userId: String): Result<List<String>> {
        return try {
            val userImagesRef = getUserImagesRef(userId)
            val listResult = userImagesRef.listAll().await()
            
            val imagePaths = listResult.items.map { it.path }
            Log.i("FirebaseStorage", "Found ${imagePaths.size} images for user: $userId")
            
            Result.success(imagePaths)
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Error listing user images", e)
            Result.failure(e)
        }
    }
    
    // Clean up orphaned images (images without corresponding Firestore records)
    suspend fun cleanupOrphanedImages(userId: String, validInvoiceIds: Set<String>): Result<Int> {
        return try {
            val userImagesRef = getUserImagesRef(userId)
            val listResult = userImagesRef.listAll().await()
            
            var deletedCount = 0
            
            for (imageRef in listResult.items) {
                val fileName = imageRef.name
                val invoiceId = fileName.substringBefore("_")
                
                if (!validInvoiceIds.contains(invoiceId)) {
                    try {
                        imageRef.delete().await()
                        deletedCount++
                        Log.d("FirebaseStorage", "Deleted orphaned image: $fileName")
                    } catch (e: Exception) {
                        Log.w("FirebaseStorage", "Failed to delete orphaned image: $fileName", e)
                    }
                }
            }
            
            Log.i("FirebaseStorage", "Cleanup completed: $deletedCount orphaned images deleted")
            Result.success(deletedCount)
        } catch (e: Exception) {
            Log.e("FirebaseStorage", "Error during cleanup", e)
            Result.failure(e)
        }
    }
}