package io.github.saeargeir.skanniapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkanniHomeScreen(
    onOverview: () -> Unit,
    onNotes: () -> Unit,
    onScan: () -> Unit,
    onBatchScan: () -> Unit = {},
    onSendExcel: () -> Unit,
    onMenu: () -> Unit,
    onLogout: () -> Unit = {},
    // New callbacks
    onExportCsv: () -> Unit = {},
    onExportJson: () -> Unit = {}
) {
    // Settings menu state
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showFloatingMenu by remember { mutableStateOf(false) }
    
    ResponsiveContainer {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.primary
                        ),
                        radius = 1200f
                    )
                )
        ) {
        // Settings menu button in top-right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { showSettingsMenu = true },
                modifier = Modifier
                    .background(
                        Color.White.copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Stillingar",
                    tint = Color.White
                )
            }

            // Settings dropdown menu
            DropdownMenu(
                expanded = showSettingsMenu,
                onDismissRequest = { showSettingsMenu = false },
                modifier = Modifier.background(Color.White)
            ) {
                DropdownMenuItem(
                    text = { Text("Stillingar") },
                    onClick = {
                        showSettingsMenu = false
                        onMenu()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Um forritið") },
                    onClick = {
                        showSettingsMenu = false
                        // Handle about screen
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Hjálp") },
                    onClick = {
                        showSettingsMenu = false
                        // Handle help screen
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null)
                    }
                )
                // New export actions
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Flytja út CSV") },
                    onClick = {
                        showSettingsMenu = false
                        onExportCsv()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Download, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("Flytja út JSON") },
                    onClick = {
                        showSettingsMenu = false
                        onExportJson()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.DataObject, contentDescription = null)
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Útskrá", color = Color.Red) },
                    onClick = {
                        showSettingsMenu = false
                        onLogout()
                    },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color.Red)
                    }
                )
            }
        }

            // Main content area with improved responsive layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .responsivePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App logo and intro with animation
                Spacer(modifier = Modifier.height(48.dp))

                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationDelay = 100
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Use simple icon instead of image resource temporarily
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "SkanniApp Logo",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            "SkanniApp",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Text(
                            "Íslenski reikningaskannarinn",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Professional action buttons using new components
                AnimatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    animationDelay = 200
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            "Skanna reikninga",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // Main scanning buttons
                        SkanniActionButton(
                            onClick = onScan,
                            text = "Skanna einn reikning",
                            icon = Icons.Default.Camera,
                            style = ButtonStyle.Primary
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        SkanniActionButton(
                            onClick = onBatchScan,
                            text = "Fjöldaskanning (Pro)",
                            icon = Icons.Default.CameraAlt,
                            style = ButtonStyle.Secondary
                        )
                    }
                }

            Spacer(modifier = Modifier.height(24.dp))

            // Secondary actions in elegant grid
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Reikninga stjórnun",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Overview button
                        OutlinedButton(
                            onClick = onOverview,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF2E7D32)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Yfirlit", fontSize = 14.sp)
                        }

                        // Notes button
                        OutlinedButton(
                            onClick = onNotes,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF2E7D32)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Note,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mínir", fontSize = 14.sp)
                        }
                    }

                    // Excel export button
                    OutlinedButton(
                        onClick = onSendExcel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2E7D32)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Senda Excel skrá", fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Ice Veflausnir branding  
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Powered by Ice Veflausnir",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Menu button at bottom
            TextButton(
                onClick = onMenu,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White.copy(alpha = 0.8f)
                )
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Valmynd", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Floating action menu for export functions
        SkanniFloatingActionMenu(
            isExpanded = showFloatingMenu,
            onExpandedChange = { showFloatingMenu = it },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            mainAction = FloatingActionConfig(
                text = "Aðgerðir",
                icon = Icons.Default.MoreVert,
                onClick = { }
            ),
            secondaryActions = listOf(
                FloatingActionConfig(
                    text = "Flytja út CSV",
                    icon = Icons.Default.FileDownload,
                    onClick = onExportCsv
                ),
                FloatingActionConfig(
                    text = "Flytja út JSON",
                    icon = Icons.Default.DataObject,
                    onClick = onExportJson
                ),
                FloatingActionConfig(
                    text = "Senda Excel",
                    icon = Icons.Default.AttachFile,
                    onClick = onSendExcel
                )
            )
        )
    }
}}
