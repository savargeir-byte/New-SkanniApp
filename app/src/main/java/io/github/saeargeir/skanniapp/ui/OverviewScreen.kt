package io.github.saeargeir.skanniapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    notes: List<InvoiceRecord>,
    selectedMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onBack: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit = {}
) {
    var expandedInvoice by remember { mutableStateOf<InvoiceRecord?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val monthNotes = notes.filter {
        val ym = YearMonth.parse(it.date.substring(0, 7))
        ym == selectedMonth
    }
    val totalAmount = monthNotes.sumOf { it.amount }
    val totalVat = monthNotes.sumOf { it.vat }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF66BB6A), // Lighter green at center
                        Color(0xFF4CAF50), // Medium green
                        Color(0xFF388E3C), // Darker green
                        Color(0xFF2E7D32), // Even darker
                        Color(0xFF1B5E20)  // Darkest green at edges
                    ),
                    radius = 1200f
                )
            )
    ) {
        // Top-right menu overlay
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Valmynd", tint = Color.White)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with SkanniApp logo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                // SkanniApp logo
                Icon(
                    Icons.Default.Receipt,
                    contentDescription = "SkanniApp",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    "Yfirlit reikninga",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Month selection dropdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                var expanded by remember { mutableStateOf(false) }
                val months = listOf(
                    "2025-01", "2025-02", "2025-03", "2025-04", "2025-05", "2025-06",
                    "2025-07", "2025-08", "2025-09", "2025-10", "2025-11", "2025-12"
                )
                
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Velja mánuð",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedMonth.toString(),
                            onValueChange = { },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4CAF50),
                                unfocusedBorderColor = Color(0xFF2E7D32)
                            )
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            months.forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month) },
                                    onClick = {
                                        onMonthChange(YearMonth.parse(month))
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "Tölfræði fyrir ${selectedMonth}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatisticItem(
                            title = "Fjöldi",
                            value = "${monthNotes.size}",
                            icon = Icons.Default.Receipt
                        )
                        
                        StatisticItem(
                            title = "Heildartala",
                            value = "${"%.0f".format(totalAmount)} kr",
                            icon = Icons.Default.AttachMoney
                        )
                        
                        StatisticItem(
                            title = "VSK",
                            value = "${"%.0f".format(totalVat)} kr",
                            icon = Icons.Default.Percent
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onExportCsv,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Flytja út CSV", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = onExportJson,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DataObject, contentDescription = null, tint = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Flytja út JSON", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Invoices List
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            "Reikningar (${monthNotes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(monthNotes) { invoice ->
                        InvoiceItem(
                            invoice = invoice,
                            onClick = { expandedInvoice = invoice }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Ice Veflausnir logo at bottom
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Powered by",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "ICE Veflausnir",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
        }
    }
    
    // Expanded invoice dialog
    expandedInvoice?.let { invoice ->
        ExpandedInvoiceDialog(
            invoice = invoice,
            onDismiss = { expandedInvoice = null }
        )
    }
}

@Composable
private fun StatisticItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E7D32)
        )
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF2E7D32).copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InvoiceItem(
    invoice: InvoiceRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    invoice.vendor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    "${"%.0f".format(invoice.amount)} kr",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            
            Text(
                invoice.date,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2E7D32).copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedInvoiceDialog(
    invoice: InvoiceRecord,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Reiknings upplýsingar",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Company/Vendor
                DetailRow("Fyrirtæki:", invoice.vendor)
                
                // Date
                DetailRow("Dagsetning:", invoice.date)
                
                // Amount
                DetailRow("Upphæð:", "${"%.2f".format(invoice.amount)} kr")
                
                // VAT
                DetailRow("VSK:", "${"%.2f".format(invoice.vat)} kr")
                
                // Total
                DetailRow(
                    "Heildarupphæð:", 
                    "${"%.2f".format(invoice.amount + invoice.vat)} kr",
                    isTotal = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Text("Loka")
            }
        },
        containerColor = Color.White,
        titleContentColor = Color(0xFF2E7D32),
        textContentColor = Color(0xFF2E7D32)
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isTotal: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = Color(0xFF2E7D32)
        )
        Text(
            value,
            style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            color = if (isTotal) Color(0xFF4CAF50) else Color(0xFF2E7D32)
        )
    }
}
