package io.github.saeargeir.skanniapp.cloud

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

class CloudManager(private val context: Context) {
    
    private val googleDriveService = GoogleDriveService(context)
    private val oneDriveService = OneDriveService(context)
    
    private var selectedProvider: CloudProvider = CloudProvider.STORAGE_ACCESS_FRAMEWORK
    private var currentCloudService: CloudService? = null
    
    fun selectProvider(provider: CloudProvider) {
        selectedProvider = provider
        currentCloudService = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> googleDriveService
            CloudProvider.ONEDRIVE -> oneDriveService
            CloudProvider.STORAGE_ACCESS_FRAMEWORK -> null // Use existing SAF implementation
        }
    }
    
    fun getCurrentProvider(): CloudProvider = selectedProvider
    
    fun getGoogleSignInIntent(): Intent? {
        return if (selectedProvider == CloudProvider.GOOGLE_DRIVE) {
            googleDriveService.getSignInIntent()
        } else null
    }
    
    suspend fun handleGoogleSignInResult(data: Intent?): Result<CloudAccount> {
        return if (selectedProvider == CloudProvider.GOOGLE_DRIVE) {
            googleDriveService.handleSignInResult(data)
        } else {
            Result.failure(Exception("Wrong provider"))
        }
    }
    
    suspend fun authenticate(): Result<CloudAccount> {
        return currentCloudService?.authenticate() 
            ?: Result.failure(Exception("No cloud service selected"))
    }
    
    suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): Result<String> {
        return currentCloudService?.uploadFile(fileName, content, mimeType)
            ?: Result.failure(Exception("No cloud service selected"))
    }
    
    suspend fun createFolder(folderName: String): Result<String> {
        return currentCloudService?.createFolder(folderName)
            ?: Result.failure(Exception("No cloud service selected"))
    }
    
    suspend fun listFiles(folderId: String? = null): Result<List<CloudFile>> {
        return currentCloudService?.listFiles(folderId)
            ?: Result.failure(Exception("No cloud service selected"))
    }
    
    fun isAuthenticated(): Boolean {
        return currentCloudService?.isAuthenticated() ?: false
    }
    
    suspend fun signOut() {
        currentCloudService?.signOut()
    }
    
    fun getProviderDisplayName(): String {
        return when (selectedProvider) {
            CloudProvider.GOOGLE_DRIVE -> "Google Drive"
            CloudProvider.ONEDRIVE -> "OneDrive"
            CloudProvider.STORAGE_ACCESS_FRAMEWORK -> "Skýjamappa (SAF)"
        }
    }
}