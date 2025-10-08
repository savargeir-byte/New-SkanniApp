package io.github.saeargeir.skanniapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import java.time.LocalDate
import java.util.*

class MainActivity : ComponentActivity() {
    private var auth: FirebaseAuth? = null
    private lateinit var authService: FirebaseAuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        authService = FirebaseAuthService(this)
        
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
        var ocrText by remember { mutableStateOf<String?>(null) }
        var currentInvoice by remember { mutableStateOf<io.github.saeargeir.skanniapp.model.InvoiceRecord?>(null) }
        var selectedMonth by remember { mutableStateOf(java.time.YearMonth.now()) }
        var notes by remember { mutableStateOf(listOf<io.github.saeargeir.skanniapp.model.InvoiceRecord>()) }
        val currentUser = auth?.currentUser

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
        } else if (showScanner) {
            InvoiceScannerScreen(
                onClose = { showScanner = false },
                onTextDetected = { text ->
                    val lines = text.split("\n")
                    val amount = lines.find { it.contains("kr") || it.contains("ISK") }?.let { line ->
                        Regex("\\d+[.,]\\d+").find(line)?.value?.replace(",", ".")?.toDoubleOrNull()
                    } ?: 0.0
                    val invoice = io.github.saeargeir.skanniapp.model.InvoiceRecord(
                        id = System.currentTimeMillis(),
                        date = LocalDate.now().toString(),
                        monthKey = LocalDate.now().toString().substring(0, 7),
                        vendor = "Óþekkt seljandi",
                        amount = amount,
                        vat = amount * 0.24 / 1.24,
                        imagePath = "",
                        ocrText = text
                    )
                    currentInvoice = invoice
                    notes = notes + invoice
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
                onSendExcel = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.sendViaEmail(this@MainActivity, csvFile)
                },
                onMenu = { /* TODO: menu actions like sign out */ }
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
                onNotes = { navScreen = "notes" }
            )
            "overview" -> io.github.saeargeir.skanniapp.ui.OverviewScreen(
                notes = notes,
                selectedMonth = selectedMonth,
                onMonthChange = { selectedMonth = it },
                onBack = { navScreen = "home" },
                onExportCsv = {
                    val csvFile = CsvExporter.exportMonthlyReport(this@MainActivity, notes, selectedMonth.year, selectedMonth.monthValue)
                    if (csvFile != null) CsvExporter.shareViaCsv(this@MainActivity, csvFile)
                }
            )
            "form" -> io.github.saeargeir.skanniapp.ui.InvoiceFormScreen(
                invoice = currentInvoice,
                onBack = { navScreen = "notes" },
                onShare = { /* TODO: implement share */ },
                onOpenImage = { /* TODO: implement open image */ },
                onDelete = {
                    notes = notes.filter { it.id != currentInvoice?.id }
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
                }
            )
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
                            Icon(Icons.Default.ExitToApp, contentDescription = "Sign Out")
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
