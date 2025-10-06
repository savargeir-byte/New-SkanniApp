package io.github.saeargeir.skanniapp.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Theme Settings Screen
 * Allows users to configure app theme preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val themeManager = remember { ThemeManager(context) }
    val scope = rememberCoroutineScope()
    
    val themeConfig by themeManager.themeConfig.collectAsState(
        initial = ThemeConfig()
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Til baka"
                )
            }
            
            Text(
                text = "Þemastillingar",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Theme Mode Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Þema",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "Veldu hvernig appið á að líta út",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Column(
                    modifier = Modifier.selectableGroup()
                ) {
                    ThemeMode.values().forEach { mode ->
                        ThemeModeOption(
                            mode = mode,
                            isSelected = themeConfig.mode == mode,
                            onSelect = {
                                scope.launch {
                                    themeManager.updateThemeMode(mode)
                                }
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Dynamic Colors (Android 12+)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Dynamic Colors",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = "Nota liti frá bakgrunni tækisins (Android 12+)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = themeConfig.useDynamicColors,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                themeManager.updateDynamicColors(enabled)
                            }
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Quick Actions
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Fljótlegar aðgerðir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Auto theme button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            themeManager.setAutoTheme()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fylgja kerfinu")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Reset button
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            themeManager.resetToDefaults()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Núllstilla stillingar")
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Info section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Þemastillingar eru vistaðar sjálfkrafa og taka strax gildi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Individual theme mode option component
 */
@Composable
private fun ThemeModeOption(
    mode: ThemeMode,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val icon = when (mode) {
        ThemeMode.LIGHT -> Icons.Default.LightMode
        ThemeMode.DARK -> Icons.Default.DarkMode
        ThemeMode.AUTO -> Icons.Default.AutoMode
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null // Click handled by selectable modifier
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = mode.getDisplayName(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * Floating Action Button for quick theme toggle
 */
@Composable
fun ThemeToggleFab(
    themeManager: ThemeManager,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    
    FloatingActionButton(
        onClick = {
            scope.launch {
                themeManager.toggleTheme(isDarkTheme)
            }
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondary
    ) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = if (isDarkTheme) "Skipta yfir í ljóst þema" else "Skipta yfir í dökkt þema"
        )
    }
}