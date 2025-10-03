package io.github.saeargeir.skanniapp.cloud

enum class CloudProvider {
    GOOGLE_DRIVE,
    ONEDRIVE,
    STORAGE_ACCESS_FRAMEWORK // Current SAF implementation
}

data class CloudAccount(
    val provider: CloudProvider,
    val email: String,
    val displayName: String,
    val accessToken: String? = null
)

interface CloudService {
    suspend fun authenticate(): Result<CloudAccount>
    suspend fun uploadFile(fileName: String, content: ByteArray, mimeType: String): Result<String>
    suspend fun createFolder(folderName: String): Result<String>
    suspend fun listFiles(folderId: String? = null): Result<List<CloudFile>>
    fun isAuthenticated(): Boolean
    suspend fun signOut()
}

data class CloudFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long,
    val modifiedTime: String,
    val downloadUrl: String? = null
)