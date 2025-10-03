package io.github.saeargeir.skanniapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.cloud.CloudProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudProviderSelector(
    selectedProvider: CloudProvider,
    onProviderSelected: (CloudProvider) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Veldu skýjaþjónustu",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Veldu hvaða skýjaþjónustu þú vilt nota til að vista myndir og Excel skrár:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Google Drive option
                CloudProviderOption(
                    icon = "🔵",
                    title = "Google Drive",
                    description = "Vista í Google Drive reikninginn þinn",
                    provider = CloudProvider.GOOGLE_DRIVE,
                    selectedProvider = selectedProvider,
                    onProviderSelected = onProviderSelected
                )
                
                // OneDrive option
                CloudProviderOption(
                    icon = "🔷",
                    title = "OneDrive",
                    description = "Vista í Microsoft OneDrive reikninginn þinn",
                    provider = CloudProvider.ONEDRIVE,
                    selectedProvider = selectedProvider,
                    onProviderSelected = onProviderSelected,
                    isEnabled = false // Temporarily disabled
                )
                
                // Storage Access Framework option
                CloudProviderOption(
                    icon = "📁",
                    title = "Skýjamappa (SAF)",
                    description = "Velja möppu í Drive, OneDrive eða öðrum skýjaforritum",
                    provider = CloudProvider.STORAGE_ACCESS_FRAMEWORK,
                    selectedProvider = selectedProvider,
                    onProviderSelected = onProviderSelected
                )
                
                if (selectedProvider == CloudProvider.ONEDRIVE) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.warningContainer
                        )
                    ) {
                        Text(
                            "OneDrive tenging er í þróun. Notaðu Google Drive eða SAF í bili.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onWarningContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConnect,
                enabled = selectedProvider != CloudProvider.ONEDRIVE
            ) {
                Text("Tengja")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hætta við")
            }
        }
    )
}

@Composable
private fun CloudProviderOption(
    icon: String,
    title: String,
    description: String,
    provider: CloudProvider,
    selectedProvider: CloudProvider,
    onProviderSelected: (CloudProvider) -> Unit,
    isEnabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selectedProvider == provider,
                onClick = { if (isEnabled) onProviderSelected(provider) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedProvider == provider) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (selectedProvider == provider) {
            androidx.compose.foundation.BorderStroke(
                2.dp, 
                MaterialTheme.colorScheme.primary
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                icon,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isEnabled) {
                        if (selectedProvider == provider) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    }
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isEnabled) {
                        if (selectedProvider == provider) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }
            
            if (selectedProvider == provider) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                    contentDescription = "Valið",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}