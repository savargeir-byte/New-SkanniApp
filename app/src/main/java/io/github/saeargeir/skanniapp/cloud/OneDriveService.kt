package io.github.saeargeir.skanniapp.cloud

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OneDriveService(private val context: Context) : CloudService {

    private var currentAccount: CloudAccount? = null

    companion object {
        private const val TAG = "OneDriveService"
    }

    // Placeholder implementation to keep compile green while OneDrive is disabled in UI
    override suspend fun authenticate(): Result<CloudAccount> {
        return Result.failure(Exception("OneDrive authentication is not implemented. Please use Google Drive or SAF."))
    }

    override suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): Result<String> {
        return withContext(Dispatchers.IO) {
            Log.w(TAG, "uploadFile called but OneDrive is not implemented")
            Result.failure(Exception("OneDrive upload not implemented"))
        }
    }

    override suspend fun createFolder(folderName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            Log.w(TAG, "createFolder called but OneDrive is not implemented")
            Result.failure(Exception("OneDrive createFolder not implemented"))
        }
    }

    override suspend fun listFiles(folderId: String?): Result<List<CloudFile>> {
        return withContext(Dispatchers.IO) {
            Log.w(TAG, "listFiles called but OneDrive is not implemented")
            Result.success(emptyList())
        }
    }

    override fun isAuthenticated(): Boolean {
        return false
    }

    override suspend fun signOut() {
        currentAccount = null
    }
}