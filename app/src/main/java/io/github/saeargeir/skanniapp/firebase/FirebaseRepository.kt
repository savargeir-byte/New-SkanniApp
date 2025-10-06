package io.github.saeargeir.skanniapp.firebase

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.utils.logFirebaseError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File

class FirebaseRepository(private val context: Context) {
    val authService = FirebaseAuthService(context)
    private val firestoreService = FirestoreService()
    private val storageService = FirebaseStorageService()
    
    // Auth methods
    val currentUser = authService.currentUser
    val isLoading = authService.isLoading
    
    suspend fun signInWithEmailAndPassword(email: String, password: String) = 
        authService.signInWithEmailAndPassword(email, password)
    
    suspend fun createUserWithEmailAndPassword(email: String, password: String) = 
        authService.createUserWithEmailAndPassword(email, password)
    
    suspend fun signInWithGoogle(idToken: String) = 
        authService.signInWithGoogle(idToken)
    
    fun getGoogleSignInClient() = authService.getGoogleSignInClient()
    
    fun signOut() = authService.signOut()
    
    suspend fun sendPasswordResetEmail(email: String) = 
        authService.sendPasswordResetEmail(email)
    
    fun getCurrentUserId() = authService.getCurrentUserId()
    
    fun isUserSignedIn() = authService.isUserSignedIn()
    
    // Invoice methods - require authentication
    suspend fun addInvoice(invoice: InvoiceRecord, imageFile: File? = null): Result<String> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return try {
            // First add the invoice to Firestore
            val invoiceResult = firestoreService.addInvoice(userId, invoice)
            if (invoiceResult.isFailure) {
                return invoiceResult
            }
            
            val invoiceId = invoiceResult.getOrThrow()
            
            // If there's an image, upload it to Firebase Storage
            if (imageFile != null) {
                val uploadResult = storageService.uploadInvoiceImage(userId, imageFile, invoiceId)
                if (uploadResult.isSuccess) {
                    val imageUrl = uploadResult.getOrThrow()
                    // Update the invoice with the Firebase Storage URL
                    val updatedInvoice = invoice.copy(id = invoiceId, imagePath = imageUrl)
                    firestoreService.updateInvoice(userId, updatedInvoice)
                } else {
                    Log.w("FirebaseRepository", "Failed to upload image, but invoice was saved")
                }
            }
            
            Result.success(invoiceId)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error adding invoice", e)
            context.logFirebaseError("addInvoice", "Failed to add invoice: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun addInvoiceFromUri(invoice: InvoiceRecord, imageUri: Uri? = null): Result<String> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return try {
            // First add the invoice to Firestore
            val invoiceResult = firestoreService.addInvoice(userId, invoice)
            if (invoiceResult.isFailure) {
                return invoiceResult
            }
            
            val invoiceId = invoiceResult.getOrThrow()
            
            // If there's an image URI, upload it to Firebase Storage
            if (imageUri != null) {
                val uploadResult = storageService.uploadInvoiceImageFromUri(userId, imageUri, invoiceId)
                if (uploadResult.isSuccess) {
                    val imageUrl = uploadResult.getOrThrow()
                    // Update the invoice with the Firebase Storage URL
                    val updatedInvoice = invoice.copy(id = invoiceId, imagePath = imageUrl)
                    firestoreService.updateInvoice(userId, updatedInvoice)
                } else {
                    Log.w("FirebaseRepository", "Failed to upload image, but invoice was saved")
                }
            }
            
            Result.success(invoiceId)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error adding invoice from URI", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateInvoice(invoice: InvoiceRecord): Result<Unit> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return firestoreService.updateInvoice(userId, invoice)
    }
    
    suspend fun deleteInvoice(invoiceId: String): Result<Unit> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return try {
            // Get the invoice first to get the image URL
            val invoiceResult = firestoreService.getInvoice(userId, invoiceId)
            if (invoiceResult.isSuccess) {
                val invoice = invoiceResult.getOrThrow()
                
                // Delete the image from Storage if it exists
                if (invoice?.imagePath?.startsWith("https://") == true) {
                    storageService.deleteInvoiceImage(invoice.imagePath)
                }
            }
            
            // Delete the invoice from Firestore
            firestoreService.deleteInvoice(userId, invoiceId)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error deleting invoice", e)
            Result.failure(e)
        }
    }
    
    suspend fun getInvoice(invoiceId: String): Result<InvoiceRecord?> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return firestoreService.getInvoice(userId, invoiceId)
    }
    
    suspend fun getAllInvoices(): Result<List<InvoiceRecord>> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return firestoreService.getAllInvoices(userId)
    }
    
    suspend fun getInvoicesByMonth(monthKey: String): Result<List<InvoiceRecord>> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return firestoreService.getInvoicesByMonth(userId, monthKey)
    }
    
    suspend fun searchInvoicesByVendor(vendorName: String): Result<List<InvoiceRecord>> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return firestoreService.searchInvoicesByVendor(userId, vendorName)
    }
    
    fun getInvoicesFlow(): Flow<List<InvoiceRecord>> {
        val userId = getCurrentUserId()
        if (userId == null) {
            Log.w("FirebaseRepository", "User not authenticated, returning empty flow")
            return emptyFlow()
        }
        
        return firestoreService.getInvoicesFlow(userId)
    }
    
    // Cleanup methods
    suspend fun cleanupOrphanedImages(): Result<Int> {
        val userId = getCurrentUserId()
        if (userId == null) {
            return Result.failure(Exception("User not authenticated"))
        }
        
        return try {
            val invoicesResult = getAllInvoices()
            if (invoicesResult.isFailure) {
                return Result.failure(invoicesResult.exceptionOrNull()!!)
            }
            
            val validInvoiceIds = invoicesResult.getOrThrow().map { it.id }.toSet()
            storageService.cleanupOrphanedImages(userId, validInvoiceIds)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error during cleanup", e)
            Result.failure(e)
        }
    }
}