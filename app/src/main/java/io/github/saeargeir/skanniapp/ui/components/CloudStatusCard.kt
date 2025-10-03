package io.github.saeargeir.skanniapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.cloud.CloudProvider

@Composable
fun CloudStatusCard(
    isConnected: Boolean,
    provider: CloudProvider?,
    accountInfo: String?,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (icon, title) = when {
                    !isConnected -> "☁️" to "Skýjatenging"
                    provider == CloudProvider.GOOGLE_DRIVE -> "🔵" to "Google Drive"
                    provider == CloudProvider.ONEDRIVE -> "🔷" to "OneDrive"
                    provider == CloudProvider.STORAGE_ACCESS_FRAMEWORK -> "📁" to "Skýjamappa"
                    else -> "☁️" to "Skýjatenging"
                }
                
                Text(
                    icon,
                    fontSize = 20.sp
                )
                
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isConnected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(50%)
                        )
                )
            }
            
            if (isConnected && accountInfo != null) {
                Text(
                    "Tengdur við: $accountInfo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    "Ekki tengt við skýjaþjónustu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnectClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Aftengja")
                    }
                } else {
                    Button(
                        onClick = onConnectClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tengja skýjaþjónustu")
                    }
                }
            }
        }
    }
}

@Composable
fun CloudUploadStatus(
    isUploading: Boolean,
    uploadProgress: Float,
    lastUploadResult: String?,
    modifier: Modifier = Modifier
) {
    if (isUploading || lastUploadResult != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isUploading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = uploadProgress,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 3.dp
                        )
                        
                        Text(
                            "Sendi í skýjaþjónustu...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    if (uploadProgress > 0) {
                        LinearProgressIndicator(
                            progress = uploadProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (lastUploadResult != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val (icon, color) = if (lastUploadResult.startsWith("Villa")) {
                            "❌" to MaterialTheme.colorScheme.error
                        } else {
                            "✅" to MaterialTheme.colorScheme.primary
                        }
                        
                        Text(
                            icon,
                            fontSize = 16.sp
                        )
                        
                        Text(
                            lastUploadResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    }
                }
            }
        }
    }
}