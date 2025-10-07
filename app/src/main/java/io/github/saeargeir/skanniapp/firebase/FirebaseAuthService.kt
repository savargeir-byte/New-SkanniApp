package io.github.saeargeir.skanniapp.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.github.saeargeir.skanniapp.R

/**
 * Firebase Authentication Service - Google Sign-In Only
 * Simplified authentication focused on Google as the primary and only auth method
 */
class FirebaseAuthService(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var googleSignInClient: GoogleSignInClient
    
    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        setupGoogleSignIn()
        
        // Listen for auth state changes
        auth.addAuthStateListener { firebaseAuth ->
            _currentUser.value = firebaseAuth.currentUser
            Log.d("FirebaseAuth", "Auth state changed. User: ${firebaseAuth.currentUser?.email}")
        }
    }
    
    private fun setupGoogleSignIn() {
        val webClientId = context.getString(R.string.default_web_client_id)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId) // From google-services.json generated resource
            .requestEmail()
            .requestProfile()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        Log.d("FirebaseAuth", "Google Sign-In configured successfully with default_web_client_id")
    }
    
    /**
     * Sign in with Google - Primary and only authentication method
     */
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            _isLoading.value = true
            Log.d("FirebaseAuth", "Starting Google sign-in with token")
            
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            
            if (user != null) {
                Log.i("FirebaseAuth", "Google sign-in successful for user: ${user.email}")
                Log.d("FirebaseAuth", "User ID: ${user.uid}, Display Name: ${user.displayName}")
                Result.success(user)
            } else {
                val error = "Google sign-in failed: No user returned"
                Log.e("FirebaseAuth", error)
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Google sign-in failed", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    /**
     * Get the Google Sign-In client for launching sign-in intent
     */
    fun getGoogleSignInClient(): GoogleSignInClient {
        return googleSignInClient
    }
    
    /**
     * Sign out from both Firebase and Google
     */
    fun signOut() {
        try {
            val userEmail = auth.currentUser?.email
            auth.signOut()
            googleSignInClient.signOut()
            Log.i("FirebaseAuth", "User signed out successfully: $userEmail")
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Sign out failed", e)
        }
    }
    
    /**
     * Get current authenticated user
     */
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }
    
    /**
     * Get current user's unique ID
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    /**
     * Get current user's email address
     */
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }
    
    /**
     * Get current user's display name
     */
    fun getCurrentUserDisplayName(): String? {
        return auth.currentUser?.displayName
    }
    
    /**
     * Get current user's profile photo URL
     */
    fun getCurrentUserPhotoUrl(): String? {
        return auth.currentUser?.photoUrl?.toString()
    }
    
    /**
     * Check if a user is currently signed in
     */
    fun isUserSignedIn(): Boolean {
        val isSignedIn = auth.currentUser != null
        Log.d("FirebaseAuth", "User signed in: $isSignedIn")
        return isSignedIn
    }
    
    /**
     * Reload current user data from Firebase
     */
    suspend fun reloadCurrentUser(): Result<Unit> {
        return try {
            auth.currentUser?.reload()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Failed to reload user", e)
            Result.failure(e)
        }
    }
}