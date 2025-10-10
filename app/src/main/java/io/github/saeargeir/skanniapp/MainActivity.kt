package io.github.saeargeir.skanniapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import io.github.saeargeir.skanniapp.firebase.FirebaseAuthService
import io.github.saeargeir.skanniapp.ui.auth.AuthScreen
import io.github.saeargeir.skanniapp.ui.theme.SkanniAppTheme
import io.github.saeargeir.skanniapp.ui.scanner.InvoiceScannerScreen
import io.github.saeargeir.skanniapp.utils.CsvExporter
import io.github.saeargeir.skanniapp.utils.IcelandicInvoiceParser
import io.github.saeargeir.skanniapp.utils.JsonExporter
import io.github.saeargeir.skanniapp.data.InvoiceStore
import java.time.LocalDate
import java.util.*

class MainActivity : ComponentActivity() {
    private var auth: FirebaseAuth? = null
    private lateinit var authService: FirebaseAuthService
    private lateinit var invoiceStore: InvoiceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        authService = FirebaseAuthService(this)
        
        // Initialize InvoiceStore for persistent data
        invoiceStore = InvoiceStore(this)
        
        setContent {
            SkanniAppTheme {
                SkanniApp()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SkanniApp() {
        var showAuth by remember { mutableStateOf(false) }
        var navScreen by remember { mutableStateOf("home") }
        var showScanner by remember { mutableStateOf(false) }
        var showBatchScanner by remember { mutableStateOf(false) }
        var ocrText by remember { mutableStateOf<String?>(null) }
        var currentInvoice by remember { mutableStateOf<io.github.saeargeir.skanniapp.model.InvoiceRecord?>(null) }
        var selectedMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
        var notes by remember { mutableStateOf(invoiceStore.loadAll()) }
        var currentBatchData by remember { mutableStateOf<io.github.saeargeir.skanniapp.data.BatchScanData?>(null) }
        val currentUser = auth?.currentUser

        // Function to save notes and update state
        fun updateNotes(newNotes: List<io.github.saeargeir.skanniapp.model.InvoiceRecord>) {
            invoiceStore.saveAll(newNotes)
            notes = newNotes
        }

        // Function to add single note
        fun addNote(note: io.github.saeargeir.skanniapp.model.InvoiceRecord) {
            invoiceStore.add(note)
            notes = invoiceStore.loadAll()
        }

        // Function to delete note
        fun deleteNote(noteId: Long) {
            invoiceStore.deleteById(noteId)
            notes = invoiceStore.loadAll()
        }

        // Check if user is signed in
        LaunchedEffect(currentUser) {
            if (currentUser == null) {
                showAuth = true
            } else {
                showAuth = false
            }
        }

        if (showAuth) {
            AuthScreen(
                authService = authService,
                onAuthSuccess = { showAuth = false }
            )
        } else if (showBatchScanner) {
            io.github.saeargeir.skanniapp.ui.scanner.BatchScannerScreen(
                onComplete = { batchData ->
                    currentBatchData = batchData
                    showBatchScanner = false
                    navScreen = "batch_management"
                },
                onClose = { showBatchScanner = false }
            )
        } else if (showScanner) {
            InvoiceScannerScreen(
                onClose = { showScanner = false },
                onResult = { text, imageUri ->
                    // Use improved Icelandic invoice parser
                    val parsed = IcelandicInvoiceParser.parseInvoiceText(text)
                    
                    val invoice = io.github.saeargeir.skanniapp.model.InvoiceRecord(
                        id = System.currentTimeMillis(),
                        date = LocalDate.now().toString(),
                        monthKey = LocalDate.now().toString().substring(0, 7),
                        vendor = parsed.vendor,
                        amount = parsed.amount,
                        vat = parsed.vat,
                        imagePath = imageUri?.toString() ?: "",
                        invoiceNumber = parsed.invoiceNumber,
                        ocrText = text,
                        classificationConfidence = parsed.confidence.toDouble()
                    )
                    currentInvoice = invoice
                    addNote(invoice)
                    ocrText = text
                    showScanner = false
                    navScreen = "form"
                }
            )
        } else when (navScreen) {
            "home" -> io.github.saeargeir.skanniapp.ui.SkanniHomeScreen(
                onOverview = { navScreen = "overview" },
                onNotes = { navScreen = "notes" },
                onScan = { showScanner = true },
                onBatchScan = { showBatchScanner = true },
                onSendExcel = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.sendViaEmail(this@MainActivity, csvFile)
                },
                onMenu = { /* TODO: menu actions like sign out */ },
                onLogout = { 
                    // Clear all data and go back to login/home
                    updateNotes(emptyList())
                    navScreen = "home"
                    // You can add authentication logout logic here
                    auth?.signOut()
                },
                onExportCsv = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.shareViaCsv(this@MainActivity, csvFile)
                },
                onExportJson = {
                    val jsonFile = JsonExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (jsonFile != null) JsonExporter.share(this@MainActivity, jsonFile)
                }
            )
            "notes" -> io.github.saeargeir.skanniapp.ui.NoteListScreen(
                notes = notes.filter { it.date.startsWith(selectedMonth.toString()) },
                selectedMonth = selectedMonth,
                onMonthChange = { selectedMonth = it },
                onBack = { navScreen = "home" },
                onExportCsv = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.shareViaCsv(this@MainActivity, csvFile)
                },
                onSearchSeller = { /* TODO: implement search */ },
                onSortBy = { /* TODO: implement sort */ },
                onNoteClick = { invoice -> currentInvoice = invoice; navScreen = "form" },
                onOverview = { navScreen = "overview" },
                onNotes = { navScreen = "notes" },
                onExportJson = {
                    val jsonFile = JsonExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (jsonFile != null) JsonExporter.share(this@MainActivity, jsonFile)
                }
            )
            "overview" -> io.github.saeargeir.skanniapp.ui.OverviewScreen(
                notes = notes,
                selectedMonth = selectedMonth,
                onMonthChange = { selectedMonth = it },
                onBack = { navScreen = "home" },
                onExportCsv = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.shareViaCsv(this@MainActivity, csvFile)
                },
                onExportJson = {
                    val jsonFile = JsonExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (jsonFile != null) JsonExporter.share(this@MainActivity, jsonFile)
                }
            )
            "form" -> io.github.saeargeir.skanniapp.ui.InvoiceFormScreen(
                invoice = currentInvoice,
                onBack = { navScreen = "notes" },
                onShare = {
                    val inv = currentInvoice
                    if (inv != null) {
                        val title = (inv.vendor.ifBlank { "Reikningur" }) + " - " + inv.date
                        val text = inv.ocrText ?: ("Seljandi: ${inv.vendor}\nUpphæð: ${inv.amount} kr\nVSK: ${inv.vat}\nNr: ${inv.invoiceNumber ?: ""}")
                        val pdfUri = io.github.saeargeir.skanniapp.utils.PdfUtils.saveTextAsPdf(this@MainActivity, title, text)
                        if (pdfUri != null) {
                            try {
                                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(android.content.Intent.createChooser(share, "Deila PDF"))
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "Gat ekki deilt PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Gat ekki vistað PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenImage = {
                    val path = currentInvoice?.imagePath
                    if (!path.isNullOrBlank()) {
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(path), "image/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Gat ekki opnað mynd: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Engin mynd tengd við þessa færslu", Toast.LENGTH_SHORT).show()
                    }
                },
                onDelete = {
                    currentInvoice?.let { invoice ->
                        deleteNote(invoice.id)
                    }
                    navScreen = "notes"
                },
                onViewOverview = { navScreen = "overview" },
                onViewNotes = { navScreen = "notes" },
                onOpenScanner = { showScanner = true },
                onSignOut = {
                    auth?.signOut()
                    showAuth = true
                },
                onExportCsv = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.shareViaCsv(this@MainActivity, csvFile)
                },
                onSendEmail = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.sendViaEmail(this@MainActivity, csvFile)
                },
                onExportJson = {
                    val jsonFile = JsonExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (jsonFile != null) JsonExporter.share(this@MainActivity, jsonFile)
                }
            )
            "batch_management" -> {
                if (currentBatchData != null) {
                    io.github.saeargeir.skanniapp.ui.batch.BatchManagementScreen(
                        batchData = currentBatchData!!,
                        processedInvoices = emptyList(), // Would be populated after processing
                        isProcessing = false,
                        progress = null,
                        onBackToScan = { showBatchScanner = true },
                        onProcessBatch = {
                            // TODO: Implement batch processing
                            Toast.makeText(this@MainActivity, "Batch processing byrjar...", Toast.LENGTH_SHORT).show()
                        },
                        onRemoveReceipt = { receiptId ->
                            currentBatchData = currentBatchData?.copy(
                                scannedReceipts = currentBatchData!!.scannedReceipts.filterNot { it.id == receiptId }.toMutableList()
                            )
                        },
                        onEditReceipt = { receiptId ->
                            Toast.makeText(this@MainActivity, "Edit receipt: $receiptId", Toast.LENGTH_SHORT).show()
                        },
                        onBulkExport = {
                            Toast.makeText(this@MainActivity, "Bulk export ekki ennþá útfært", Toast.LENGTH_SHORT).show()
                        },
                        onComplete = {
                            navScreen = "home"
                            currentBatchData = null
                        },
                        onRetryFailed = {
                            Toast.makeText(this@MainActivity, "Reyni aftur...", Toast.LENGTH_SHORT).show()
                        }
                    )
                } else {
                    navScreen = "home"
                }
            }
        }

        if (ocrText != null) {
            AlertDialog(
                onDismissRequest = { ocrText = null },
                confirmButton = {
                    TextButton(onClick = { ocrText = null }) { Text("OK") }
                },
                title = { Text("Texti fannst") },
                text = { Text(ocrText ?: "") }
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppScreen(
        onSignOut: () -> Unit,
        onOpenScanner: () -> Unit
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SkanniApp") },
                    actions = {
                        IconButton(onClick = onSignOut) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onOpenScanner,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Scan Invoice") }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Welcome to SkanniApp",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        "Tap the + button to scan your first invoice",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
