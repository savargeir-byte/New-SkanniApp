package io.github.saeargeir.skanniapp.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.client.http.ByteArrayContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class GoogleDriveService(private val context: Context) : CloudService {
    
    private var googleSignInClient: GoogleSignInClient? = null
    private var driveService: Drive? = null
    private var currentAccount: GoogleSignInAccount? = null
    
    companion object {
        private const val TAG = "GoogleDriveService"
        const val RC_SIGN_IN = 100
    }
    
    init {
        setupGoogleSignIn()
    }
    
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
            
        googleSignInClient = GoogleSignIn.getClient(context, gso)
        
        // Check if already signed in
        currentAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (currentAccount != null) {
            setupDriveService()
        }
    }
    
    private fun setupDriveService() {
        currentAccount?.let { account ->
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            )
            credential.selectedAccount = account.account
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("Skanni App").build()
        }
    }
    
    override suspend fun authenticate(): Result<CloudAccount> {
        return try {
            currentAccount?.let { account ->
                Result.success(CloudAccount(
                    provider = CloudProvider.GOOGLE_DRIVE,
                    email = account.email ?: "",
                    displayName = account.displayName ?: "",
                    accessToken = null
                ))
            } ?: Result.failure(Exception("Not signed in to Google"))
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            Result.failure(e)
        }
    }
    
    fun getSignInIntent(): Intent? {
        return googleSignInClient?.signInIntent
    }
    
    suspend fun handleSignInResult(data: Intent?): Result<CloudAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            currentAccount = account
            setupDriveService()
            
            Result.success(CloudAccount(
                provider = CloudProvider.GOOGLE_DRIVE,
                email = account.email ?: "",
                displayName = account.displayName ?: ""
            ))
        } catch (e: ApiException) {
            Log.w(TAG, "Sign in failed: ${e.statusCode}")
            Result.failure(e)
        }
    }
    
    override suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = this@GoogleDriveService.driveService
                    ?: return@withContext Result.failure(Exception("Drive service not initialized"))
                
                val fileMetadata = File()
                fileMetadata.name = fileName
                
                val mediaContent = ByteArrayContent(mimeType, content)
                
                val file = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                
                Result.success(file.id)
            } catch (e: IOException) {
                Log.e(TAG, "Upload failed", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun createFolder(folderName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = this@GoogleDriveService.driveService
                    ?: return@withContext Result.failure(Exception("Drive service not initialized"))
                
                val fileMetadata = File()
                fileMetadata.name = folderName
                fileMetadata.mimeType = "application/vnd.google-apps.folder"
                
                val file = driveService.files().create(fileMetadata)
                    .setFields("id")
                    .execute()
                
                Result.success(file.id)
            } catch (e: IOException) {
                Log.e(TAG, "Create folder failed", e)
                Result.failure(e)
            }
        }
    }
    
    override suspend fun listFiles(folderId: String?): Result<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            try {
                val driveService = this@GoogleDriveService.driveService
                    ?: return@withContext Result.failure(Exception("Drive service not initialized"))
                
                val query = if (folderId != null) {
                    "'$folderId' in parents"
                } else {
                    "parents in 'root'"
                }
                
                val result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id,name,mimeType,size,modifiedTime)")
                    .execute()
                
                val cloudFiles = result.files.map { file ->
                    CloudFile(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        size = file.size ?: 0L,
                        modifiedTime = file.modifiedTime?.toString() ?: ""
                    )
                }
                
                Result.success(cloudFiles)
            } catch (e: IOException) {
                Log.e(TAG, "List files failed", e)
                Result.failure(e)
            }
        }
    }
    
    override fun isAuthenticated(): Boolean {
        return currentAccount != null && driveService != null
    }
    
    override suspend fun signOut() {
        googleSignInClient?.signOut()
        currentAccount = null
        driveService = null
    }
}