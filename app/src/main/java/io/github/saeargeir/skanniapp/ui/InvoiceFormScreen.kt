package io.github.saeargeir.skanniapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.model.InvoiceRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceFormScreen(
    invoice: InvoiceRecord? = null,
    onSave: (InvoiceRecord) -> Unit = {},
    onBack: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenImage: () -> Unit = {},
    onDelete: () -> Unit = {},
    onViewOverview: () -> Unit = {},
    onViewNotes: () -> Unit = {},
    onOpenScanner: () -> Unit = {},
        onSignOut: () -> Unit = {},
        onExportCsv: () -> Unit = {},
        onSendEmail: () -> Unit = {},
        onExportJson: () -> Unit = {}
) {
    var seljandi by remember { mutableStateOf(invoice?.vendor ?: "") }
    var reikningsnr by remember { mutableStateOf(invoice?.invoiceNumber ?: "") }
    var dagsetning by remember { mutableStateOf(invoice?.date ?: "") }
    var upphaeð by remember { mutableStateOf(invoice?.amount?.toString() ?: "") }
    var vsk by remember { mutableStateOf(invoice?.vat?.toString() ?: "") }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF6B46C1))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Velkomin í nótuskanna!",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Skrá út") },
                            onClick = {
                                showMenu = false
                                onSignOut()
                            },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Flytja út CSV") },
                            onClick = { showMenu = false; onExportCsv() },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Flytja út JSON") },
                            onClick = { showMenu = false; onExportJson() },
                            leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null) }
                        )
                    }
                }
            }
                            // Export and Email buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onExportCsv,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF059669)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.FileDownload,
                                            contentDescription = "Export CSV",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Flytja út CSV", color = Color.White)
                                    }
                                }
                    
                                Button(
                                    onClick = onSendEmail,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF059669)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Email,
                                            contentDescription = "Send Email",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Senda í pósti", color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

            // Cloud connection status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE0E7FF))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = "Cloud",
                    tint = Color(0xFF6B46C1),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tengt við skýjamöppu",
                    color = Color(0xFF6B46C1),
                    fontSize = 14.sp
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onViewOverview,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Skoða yfirlitt", color = Color.White)
                    }
                    
                    Button(
                        onClick = onViewNotes,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Skoða nótur", color = Color.White)
                    }
                }

                // Action buttons row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Til baka", color = Color.White)
                    }
                    
                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Deila mynd", color = Color.White)
                    }
                }

                // Action buttons row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenImage,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Opna mynd", color = Color.White)
                    }
                    
                    Button(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B46C1)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Eyða", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Form fields
                FormField(
                    label = "Seljandi",
                    value = seljandi,
                    onValueChange = { seljandi = it }
                )

                FormField(
                    label = "Reikningsnr./Nótunúmer",
                    value = reikningsnr,
                    onValueChange = { reikningsnr = it },
                    keyboardType = KeyboardType.Number
                )

                FormField(
                    label = "Dagsetning (yyyy-MM-dd)",
                    value = dagsetning,
                    onValueChange = { dagsetning = it }
                )

                FormField(
                    label = "Upphæð",
                    value = upphaeð,
                    onValueChange = { upphaeð = it },
                    keyboardType = KeyboardType.Decimal
                )

                FormField(
                    label = "VSK",
                    value = vsk,
                    onValueChange = { vsk = it },
                    keyboardType = KeyboardType.Decimal
                )

                Spacer(modifier = Modifier.height(32.dp))

                // IceVeflausnir logo/text at bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Eco,
                        contentDescription = "Ice logo",
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VEFLAUSNIR",
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // Floating Action Button for scanner
        FloatingActionButton(
            onClick = onOpenScanner,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = Color(0xFF6B46C1)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Scan Invoice",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF6B46C1),
                unfocusedBorderColor = Color(0xFFD1D5DB)
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}