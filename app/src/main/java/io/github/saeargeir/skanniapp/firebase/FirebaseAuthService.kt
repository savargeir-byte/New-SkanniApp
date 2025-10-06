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
        }
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("368139082393-4l01beasqkrdds52pqn93cn1utnen384.apps.googleusercontent.com") // Web client ID from google-services.json
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            _isLoading.value = true
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.i("FirebaseAuth", "Sign in successful for user: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Sign in failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Sign in failed", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            _isLoading.value = true
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            if (user != null) {
                Log.i("FirebaseAuth", "Account created successfully for user: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Account creation failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Account creation failed", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            _isLoading.value = true
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user
            if (user != null) {
                Log.i("FirebaseAuth", "Google sign in successful for user: ${user.email}")
                Result.success(user)
            } else {
                Result.failure(Exception("Google sign in failed: No user returned"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Google sign in failed", e)
            Result.failure(e)
        } finally {
            _isLoading.value = false
        }
    }
    
    fun getGoogleSignInClient(): GoogleSignInClient {
        return googleSignInClient
    }
    
    fun signOut() {
        try {
            auth.signOut()
            googleSignInClient.signOut()
            Log.i("FirebaseAuth", "User signed out successfully")
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Sign out failed", e)
        }
    }
    
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Log.i("FirebaseAuth", "Password reset email sent to: $email")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Failed to send password reset email", e)
            Result.failure(e)
        }
    }
    
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }
    
    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }
    
    fun isUserSignedIn(): Boolean {
        return auth.currentUser != null
    }
}