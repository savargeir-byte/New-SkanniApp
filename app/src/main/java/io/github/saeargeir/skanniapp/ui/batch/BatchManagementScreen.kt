package io.github.saeargeir.skanniapp.ui.batch

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.saeargeir.skanniapp.data.BatchScanData
import io.github.saeargeir.skanniapp.data.ScannedReceiptData
import io.github.saeargeir.skanniapp.data.ProcessingStatus
import io.github.saeargeir.skanniapp.model.InvoiceRecord
import io.github.saeargeir.skanniapp.ocr.BatchProgress
import io.github.saeargeir.skanniapp.ocr.BatchProcessingResult
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.*

/**
 * Professional batch management interface - bank-app style
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchManagementScreen(
    batchData: BatchScanData,
    processedInvoices: List<InvoiceRecord> = emptyList(),
    isProcessing: Boolean = false,
    progress: BatchProgress? = null,
    onBackToScan: () -> Unit,
    onProcessBatch: () -> Unit,
    onRemoveReceipt: (String) -> Unit,
    onEditReceipt: (String) -> Unit,
    onBulkExport: () -> Unit,
    onComplete: () -> Unit,
    onRetryFailed: () -> Unit
) {
    val listState = rememberLazyListState()
    var selectedReceipts by remember { mutableStateOf(setOf<String>()) }
    var showBulkActions by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(BatchTab.OVERVIEW) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Professional header með bank-app design
        ProfessionalHeader(
            batchData = batchData,
            processedInvoices = processedInvoices,
            progress = progress,
            currentTab = currentTab,
            onTabChange = { currentTab = it },
            onComplete = onComplete
        )
        
        // Processing indicator
        if (isProcessing && progress != null) {
            ProcessingIndicator(
                progress = progress,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        // Tab content
        when (currentTab) {
            BatchTab.OVERVIEW -> {
                OverviewTab(
                    batchData = batchData,
                    processedInvoices = processedInvoices,
                    progress = progress,
                    onProcessBatch = onProcessBatch,
                    onBackToScan = onBackToScan,
                    onBulkExport = onBulkExport,
                    isProcessing = isProcessing
                )
            }
            
            BatchTab.RECEIPTS -> {
                ReceiptsTab(
                    batchData = batchData,
                    processedInvoices = processedInvoices,
                    selectedReceipts = selectedReceipts,
                    onSelectionChange = { selectedReceipts = it },
                    onRemoveReceipt = onRemoveReceipt,
                    onEditReceipt = onEditReceipt,
                    listState = listState,
                    showBulkActions = showBulkActions,
                    onBulkActionsToggle = { showBulkActions = it }
                )
            }
            
            BatchTab.RESULTS -> {
                ResultsTab(
                    processedInvoices = processedInvoices,
                    progress = progress,
                    onRetryFailed = onRetryFailed,
                    onBulkExport = onBulkExport
                )
            }
        }
    }
}

@Composable
private fun ProfessionalHeader(
    batchData: BatchScanData,
    processedInvoices: List<InvoiceRecord>,
    progress: BatchProgress?,
    currentTab: BatchTab,
    onTabChange: (BatchTab) -> Unit,
    onComplete: () -> Unit
) {
    val totalAmount = processedInvoices.sumOf { it.amount }
    val receiptCount = batchData.getReceiptCount()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Batch Skanning",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Professional Mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                
                if ((progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0) || processedInvoices.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lokið", color = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics cards með bank-app design
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "Reikningar",
                    value = receiptCount.toString(),
                    subtitle = if (progress != null) "${progress.successCount} tókust" else "Scanned",
                    icon = Icons.Default.Receipt,
                    modifier = Modifier.weight(1f)
                )
                
                StatCard(
                    title = "Samtals",
                    value = NumberFormat.getCurrencyInstance(Locale("is", "IS"))
                        .format(totalAmount).replace("ISK", "kr"),
                    subtitle = "Heildarupphæð",
                    icon = Icons.Default.AttachMoney,
                    modifier = Modifier.weight(1f)
                )
                
                StatCard(
                    title = "Staða",
                    value = when {
                        (progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0) -> "Lokið"
                        progress != null -> "Vinnur..."
                        else -> "Tilbúið"
                    },
                    subtitle = progress?.status ?: "Ready to process",
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Professional tab bar
            TabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = Color.Transparent,
                indicator = { tabPositions ->
                    if (tabPositions.isNotEmpty()) {
                        TabRowDefaults.Indicator(
                            modifier = Modifier
                                .width(tabPositions[currentTab.ordinal].width)
                                .offset(x = tabPositions[currentTab.ordinal].left),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                BatchTab.values().forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { onTabChange(tab) },
                        text = {
                            Text(
                                tab.title,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProcessingIndicator(
    progress: BatchProgress,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    progress.status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${((progress.currentIndex.toFloat() / progress.totalCount.coerceAtLeast(1)) * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = (progress.currentIndex.toFloat() / progress.totalCount.coerceAtLeast(1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            
            if (progress.currentReceipt != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Vinnur úr: ${progress.currentReceipt.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun OverviewTab(
    batchData: BatchScanData,
    processedInvoices: List<InvoiceRecord>,
    progress: BatchProgress?,
    onProcessBatch: () -> Unit,
    onBackToScan: () -> Unit,
    onBulkExport: () -> Unit,
    isProcessing: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Quick actions card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "Aðgerðir",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBackToScan,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Bæta við")
                        }
                        
                        Button(
                            onClick = onProcessBatch,
                            modifier = Modifier.weight(1f),
                            enabled = !isProcessing && batchData.getReceiptCount() > 0
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vinna úr")
                        }
                    }
                    
                    if (processedInvoices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onBulkExport,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Flytja út (${processedInvoices.size} reikningar)")
                        }
                    }
                }
            }
        }
        
        item {
            // Summary statistics
            if (progress != null || processedInvoices.isNotEmpty()) {
                SummaryCard(
                    progress = progress,
                    processedInvoices = processedInvoices
                )
            }
        }
        
        item {
            // Recent receipts preview
            if (batchData.scannedReceipts.isNotEmpty()) {
                RecentReceiptsCard(
                    receipts = batchData.scannedReceipts.take(3),
                    totalCount = batchData.getReceiptCount()
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    progress: BatchProgress?,
    processedInvoices: List<InvoiceRecord>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if ((progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0)) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (((progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0)) == true) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (((progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0)) == true) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (((progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0)) == true) "Vinnslu lokið!" else "Staða vinnslu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (progress != null) {
                Text(
                    "✅ ${progress.successCount} tókust",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (progress.failureCount > 0) {
                    Text(
                        "❌ ${progress.failureCount} mistókust",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                
                if (((progress?.currentIndex ?: 0) >= (progress?.totalCount ?: 0))) {
                    val successRate = ((progress?.successCount?.toFloat() ?: 0f) / (progress?.totalCount?.coerceAtLeast(1) ?: 1) * 100).toInt()
                    Text(
                        "Árangur: $successRate%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (processedInvoices.isNotEmpty()) {
                Text(
                    "${processedInvoices.size} reikningar tilbúnir",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RecentReceiptsCard(
    receipts: List<ScannedReceiptData>,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Nýjustu reikningar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (totalCount > 3) {
                    Text(
                        "+${totalCount - 3} til viðbótar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            receipts.forEach { receipt ->
                ReceiptPreviewItem(receipt = receipt)
                if (receipt != receipts.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ReceiptsTab(
    batchData: BatchScanData,
    processedInvoices: List<InvoiceRecord>,
    selectedReceipts: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    onRemoveReceipt: (String) -> Unit,
    onEditReceipt: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showBulkActions: Boolean,
    onBulkActionsToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Bulk actions toolbar
        AnimatedVisibility(
            visible = showBulkActions || selectedReceipts.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            BulkActionsToolbar(
                selectedCount = selectedReceipts.size,
                onSelectAll = {
                    onSelectionChange(batchData.scannedReceipts.map { it.id }.toSet())
                },
                onClearSelection = {
                    onSelectionChange(emptySet())
                    onBulkActionsToggle(false)
                },
                onBulkDelete = {
                    selectedReceipts.forEach(onRemoveReceipt)
                    onSelectionChange(emptySet())
                }
            )
        }
        
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(batchData.scannedReceipts) { receipt ->
                val isSelected = selectedReceipts.contains(receipt.id)
                val processedInvoice = processedInvoices.find { 
                    it.ocrText?.contains(receipt.id) == true 
                }
                
                ProfessionalReceiptCard(
                    receipt = receipt,
                    processedInvoice = processedInvoice,
                    isSelected = isSelected,
                    showSelection = showBulkActions,
                    onSelectionChange = { selected ->
                        val newSelection = if (selected) {
                            selectedReceipts + receipt.id
                        } else {
                            selectedReceipts - receipt.id
                        }
                        onSelectionChange(newSelection)
                    },
                    onRemove = { onRemoveReceipt(receipt.id) },
                    onEdit = { onEditReceipt(receipt.id) },
                    onLongPress = {
                        if (!showBulkActions) {
                            onBulkActionsToggle(true)
                            onSelectionChange(setOf(receipt.id))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ResultsTab(
    processedInvoices: List<InvoiceRecord>,
    progress: BatchProgress?,
    onRetryFailed: () -> Unit,
    onBulkExport: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (progress != null && progress.failureCount > 0) {
            item {
                ErrorSummaryCard(
                    failureCount = progress.failureCount,
                    onRetryFailed = onRetryFailed
                )
            }
        }
        
        items(processedInvoices) { invoice ->
            ProcessedInvoiceCard(invoice = invoice)
        }
        
        if (processedInvoices.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Tilbúið fyrir útflutning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onBulkExport,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Flytja út ${processedInvoices.size} reikningar")
                        }
                    }
                }
            }
        }
    }
}

// Additional helper composables would go here...
// (Continuing with BulkActionsToolbar, ProfessionalReceiptCard, etc.)

private enum class BatchTab(val title: String) {
    OVERVIEW("Yfirlit"),
    RECEIPTS("Reikningar"),
    RESULTS("Niðurstöður")
}

// Placeholder for additional components
@Composable
private fun BulkActionsToolbar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBulkDelete: () -> Unit
) {
    // Implementation here...
}

@Composable
private fun ProfessionalReceiptCard(
    receipt: ScannedReceiptData,
    processedInvoice: InvoiceRecord?,
    isSelected: Boolean,
    showSelection: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit
) {
    // Implementation here...
}

@Composable
private fun ProcessedInvoiceCard(invoice: InvoiceRecord) {
    // Implementation here...
}

@Composable
private fun ErrorSummaryCard(
    failureCount: Int,
    onRetryFailed: () -> Unit
) {
    // Implementation here...
}

@Composable
private fun ReceiptPreviewItem(receipt: ScannedReceiptData) {
    // Implementation here...
}
