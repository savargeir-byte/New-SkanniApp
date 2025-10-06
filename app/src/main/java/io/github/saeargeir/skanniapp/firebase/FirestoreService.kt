package io.github.saeargeir.skanniapp.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreService {
    private val db: FirebaseFirestore = Firebase.firestore
    
    private fun getUserInvoicesCollection(userId: String) = 
        db.collection("users").document(userId).collection("invoices")
    
    suspend fun addInvoice(userId: String, invoice: InvoiceRecord): Result<String> {
        return try {
            val docRef = getUserInvoicesCollection(userId).add(invoice).await()
            Log.i("Firestore", "Invoice added with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("Firestore", "Error adding invoice", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateInvoice(userId: String, invoice: InvoiceRecord): Result<Unit> {
        return try {
            getUserInvoicesCollection(userId)
                .document(invoice.id)
                .set(invoice)
                .await()
            Log.i("Firestore", "Invoice updated: ${invoice.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("Firestore", "Error updating invoice", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteInvoice(userId: String, invoiceId: String): Result<Unit> {
        return try {
            getUserInvoicesCollection(userId)
                .document(invoiceId)
                .delete()
                .await()
            Log.i("Firestore", "Invoice deleted: $invoiceId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("Firestore", "Error deleting invoice", e)
            Result.failure(e)
        }
    }
    
    suspend fun getInvoice(userId: String, invoiceId: String): Result<InvoiceRecord?> {
        return try {
            val document = getUserInvoicesCollection(userId)
                .document(invoiceId)
                .get()
                .await()
            
            if (document.exists()) {
                val invoice = document.toObject(InvoiceRecord::class.java)?.copy(id = document.id)
                Log.i("Firestore", "Invoice retrieved: $invoiceId")
                Result.success(invoice)
            } else {
                Log.w("Firestore", "Invoice not found: $invoiceId")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting invoice", e)
            Result.failure(e)
        }
    }
    
    suspend fun getAllInvoices(userId: String): Result<List<InvoiceRecord>> {
        return try {
            val querySnapshot = getUserInvoicesCollection(userId)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val invoices = querySnapshot.documents.mapNotNull { document ->
                document.toObject(InvoiceRecord::class.java)?.copy(id = document.id)
            }
            
            Log.i("Firestore", "Retrieved ${invoices.size} invoices")
            Result.success(invoices)
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting all invoices", e)
            Result.failure(e)
        }
    }
    
    suspend fun getInvoicesByMonth(userId: String, monthKey: String): Result<List<InvoiceRecord>> {
        return try {
            val querySnapshot = getUserInvoicesCollection(userId)
                .whereEqualTo("monthKey", monthKey)
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val invoices = querySnapshot.documents.mapNotNull { document ->
                document.toObject(InvoiceRecord::class.java)?.copy(id = document.id)
            }
            
            Log.i("Firestore", "Retrieved ${invoices.size} invoices for month $monthKey")
            Result.success(invoices)
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting invoices by month", e)
            Result.failure(e)
        }
    }
    
    // Real-time listener for invoices
    fun getInvoicesFlow(userId: String): Flow<List<InvoiceRecord>> = callbackFlow {
        val listener = getUserInvoicesCollection(userId)
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error listening to invoices", error)
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    val invoices = snapshot.documents.mapNotNull { document ->
                        document.toObject(InvoiceRecord::class.java)?.copy(id = document.id)
                    }
                    trySend(invoices)
                    Log.d("Firestore", "Real-time update: ${invoices.size} invoices")
                }
            }
        
        awaitClose { listener.remove() }
    }
    
    // Search invoices by vendor name
    suspend fun searchInvoicesByVendor(userId: String, vendorName: String): Result<List<InvoiceRecord>> {
        return try {
            val querySnapshot = getUserInvoicesCollection(userId)
                .whereGreaterThanOrEqualTo("vendor", vendorName)
                .whereLessThanOrEqualTo("vendor", vendorName + "\uf8ff")
                .orderBy("vendor")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val invoices = querySnapshot.documents.mapNotNull { document ->
                document.toObject(InvoiceRecord::class.java)?.copy(id = document.id)
            }
            
            Log.i("Firestore", "Found ${invoices.size} invoices for vendor: $vendorName")
            Result.success(invoices)
        } catch (e: Exception) {
            Log.e("Firestore", "Error searching invoices by vendor", e)
            Result.failure(e)
        }
    }
}